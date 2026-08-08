package `in`.inzamulhoque.meshtalk.protocol

import `in`.inzamulhoque.meshtalk.data.local.dao.MessageDao
import `in`.inzamulhoque.meshtalk.data.local.dao.PeerDao
import `in`.inzamulhoque.meshtalk.data.local.dao.GroupDao
import `in`.inzamulhoque.meshtalk.data.local.entity.Message
import `in`.inzamulhoque.meshtalk.data.local.entity.Peer
import `in`.inzamulhoque.meshtalk.data.local.entity.MessageStatus
import `in`.inzamulhoque.meshtalk.util.NotificationHelper
import `in`.inzamulhoque.meshtalk.util.ToastHelper
import `in`.inzamulhoque.meshtalk.util.SettingsManager
import `in`.inzamulhoque.meshtalk.crypto.IdentityManager
import `in`.inzamulhoque.meshtalk.protocol.proto.*
import `in`.inzamulhoque.meshtalk.util.BloomFilter
import `in`.inzamulhoque.meshtalk.util.FileUtils
import androidx.annotation.Keep
import android.content.Context
import android.util.Log

class MeshProtocol(
    private val context: Context,
    private val myId: String,
    private val messageDao: MessageDao,
    private val peerDao: PeerDao,
    private val groupDao: GroupDao,
    private val identityManager: IdentityManager,
    private val settingsManager: SettingsManager
) {

    suspend fun createHandshake(encryptionKey: String, displayName: String): ProtoHandshake {
        val bloomFilter = BloomFilter(512, 5)
        messageDao.getAllMessageUuids().forEach { bloomFilter.add(it) }

        return ProtoHandshake.newBuilder()
            .setPeerId(myId)
            .setEncryptionKey(encryptionKey)
            .setDisplayName(displayName)
            .setBloomFilter(com.google.protobuf.ByteString.copyFrom(bloomFilter.toByteArray()))
            .setBio(settingsManager.bio ?: "")
            .setAvatarBase64(settingsManager.avatarBase64 ?: "")
            .build()
    }

    suspend fun handleHandshake(handshake: ProtoHandshake, deviceAddress: String?): List<Message> {
        onPeerDiscovered(
            peerId = handshake.peerId,
            publicKey = handshake.peerId,
            encryptionKey = handshake.encryptionKey,
            displayName = handshake.displayName,
            deviceAddress = deviceAddress,
            bio = handshake.bio,
            avatarBase64 = handshake.avatarBase64
        )
        if (settingsManager.isConnectionToastEnabled) {
            ToastHelper.showToast(context, "Securely connected to ${handshake.displayName}")
        }
        val messages = getMessagesToSync(handshake.bloomFilter)
        Log.d("MeshProtocol", "Handshake from ${handshake.displayName}, syncing ${messages.size} messages")
        return messages
    }

    fun serializeHandshake(handshake: ProtoHandshake): ByteArray = handshake.toByteArray()
    fun deserializeHandshake(data: ByteArray): ProtoHandshake? = try { ProtoHandshake.parseFrom(data) } catch (_: Exception) { null }

    fun serializeSyncUpdate(update: ProtoSyncUpdate): ByteArray = update.toByteArray()
    fun deserializeSyncUpdate(data: ByteArray): ProtoSyncUpdate? = try { ProtoSyncUpdate.parseFrom(data) } catch (_: Exception) { null }

    suspend fun onPeerDiscovered(
        peerId: String, 
        publicKey: String, 
        encryptionKey: String?, 
        displayName: String?, 
        deviceAddress: String?,
        bio: String? = null,
        avatarBase64: String? = null,
        rssi: Int = -100
    ) {
        val existingPeer = peerDao.getPeerById(peerId)
        
        val avatarPath = if (avatarBase64 != null) {
            // Delete old file if exists
            if (existingPeer?.avatarUri != null && existingPeer.avatarUri.startsWith("/")) {
                FileUtils.deleteFile(existingPeer.avatarUri)
            }
            FileUtils.saveBase64Avatar(context, avatarBase64)
        } else existingPeer?.avatarUri

        val allPeers = peerDao.getAllPeersSync()
        
        if (deviceAddress != null) {
            val sameAddressPeers = allPeers.filter { 
                it.deviceAddress?.equals(deviceAddress, ignoreCase = true) == true 
            }
            
            if (publicKey.isNotEmpty()) {
                val stale = sameAddressPeers.filter { it.id != peerId }
                if (stale.isNotEmpty()) {
                    stale.forEach { peerDao.deletePeer(it) }
                }
            } else {
                if (sameAddressPeers.any { it.publicKey.isNotEmpty() }) {
                    return
                }
                if (sameAddressPeers.any { it.id == peerId }) {
                    return
                }
            }
        }

        if (existingPeer == null) {
            peerDao.insertPeer(Peer(
                id = peerId, 
                publicKey = publicKey, 
                encryptionKey = encryptionKey, 
                displayName = displayName, 
                deviceAddress = deviceAddress, 
                lastSeen = System.currentTimeMillis(),
                bio = bio,
                avatarUri = avatarPath,
                rssi = rssi
            ))
        } else {
            peerDao.updatePeer(existingPeer.copy(
                lastSeen = System.currentTimeMillis(), 
                deviceAddress = deviceAddress ?: existingPeer.deviceAddress,
                encryptionKey = encryptionKey ?: existingPeer.encryptionKey,
                displayName = displayName ?: existingPeer.displayName,
                publicKey = if (publicKey.isNotEmpty()) publicKey else existingPeer.publicKey,
                bio = bio ?: existingPeer.bio,
                avatarUri = avatarPath,
                rssi = rssi
            ))
        }
    }

    suspend fun getMessagesToSync(remoteBloomBytes: com.google.protobuf.ByteString): List<Message> {
        val filter = BloomFilter.fromByteArray(remoteBloomBytes.toByteArray(), 512, 5)
        val messagesToForward = messageDao.getMessagesToForward(myId, System.currentTimeMillis())
        return messagesToForward.filter { !filter.contains(it.uuid) }
    }

    suspend fun processReceivedMessage(message: Message): Pair<ProtoSyncUpdate?, Message?> {
        if (messageDao.getMessageByUuid(message.uuid) != null) {
            return null to null
        }

        // Check if it's for me (Direct or Group)
        val isForMe = message.receiverId == myId || (message.groupId != null && (message.groupId == PUBLIC_GROUP_ID || isMemberOf(message.groupId)))

        if (isForMe) {
            val decryptedContent = if (message.isEncrypted && message.groupId != PUBLIC_GROUP_ID) {
                try {
                    identityManager.decryptMessage(message.content)
                } catch (_: Exception) { "[Encrypted Message]" }
            } else message.content

            messageDao.insertMessage(message.copy(id = 0, status = MessageStatus.DELIVERED))
            
            val app = context.applicationContext as? `in`.inzamulhoque.meshtalk.MeshApplication
            val isForeground = app?.isAppInForeground == true

            if (settingsManager.isNotificationEnabled && !isForeground) {
                val peer = peerDao.getPeerById(message.senderId)
                NotificationHelper.showMessageNotification(
                    context = context,
                    senderId = message.senderId,
                    senderName = peer?.displayName ?: "Unknown Peer",
                    message = decryptedContent
                )
            }
            
            // Return a DELIVERED update back to the sender
            val update = ProtoSyncUpdate.newBuilder()
                .setType(SyncUpdateType.DELIVERED)
                .setTargetUuid(message.uuid)
                .setSenderId(myId)
                .setTimestamp(System.currentTimeMillis())
                .build()
            return update to null
            
        } else if (message.hopCount < MAX_HOPS && message.expiryTimestamp > System.currentTimeMillis()) {
            if (settingsManager.isForwardingEnabled) {
                val carrierMessage = message.copy(
                    id = 0, 
                    hopCount = message.hopCount + 1,
                    status = MessageStatus.CARRYING
                )
                messageDao.insertMessage(carrierMessage)
                return null to carrierMessage
            }
        }
        return null to null
    }

    private suspend fun isMemberOf(groupId: String): Boolean {
        return groupDao.getGroupById(groupId) != null
    }

    suspend fun processSyncUpdate(update: ProtoSyncUpdate) {
        val msg = messageDao.getMessageByUuid(update.targetUuid) ?: return
        
        when (update.type) {
            SyncUpdateType.DELETE_MESSAGE -> {
                if (msg.senderId == update.senderId) {
                    messageDao.deleteMessage(msg)
                }
            }
            SyncUpdateType.DELIVERED -> {
                if (msg.senderId == myId && msg.receiverId == update.senderId) {
                    messageDao.updateMessageStatus(msg.id, MessageStatus.DELIVERED.name)
                }
            }
            SyncUpdateType.READ -> {
                if (msg.senderId == myId && msg.receiverId == update.senderId) {
                    messageDao.updateMessageStatus(msg.id, MessageStatus.READ.name)
                }
            }
            else -> {}
        }
    }

    fun serializeMessage(message: Message): ByteArray {
        return ProtoMessage.newBuilder()
            .setUuid(message.uuid)
            .setSenderId(message.senderId)
            .setReceiverId(message.receiverId)
            .setGroupId(message.groupId ?: "")
            .setContent(message.content)
            .setTimestamp(message.timestamp)
            .setExpiryTimestamp(message.expiryTimestamp)
            .setHopCount(message.hopCount)
            .setIsEncrypted(message.isEncrypted)
            .build()
            .toByteArray()
    }

    fun deserializeMessage(data: ByteArray): Message? {
        return try {
            val proto = ProtoMessage.parseFrom(data)
            Message(
                uuid = proto.uuid,
                senderId = proto.senderId,
                receiverId = proto.receiverId,
                groupId = if (proto.groupId.isEmpty()) null else proto.groupId,
                content = proto.content,
                timestamp = proto.timestamp,
                expiryTimestamp = proto.expiryTimestamp,
                hopCount = proto.hopCount,
                isEncrypted = proto.isEncrypted
            )
        } catch (_: Exception) { null }
    }

    companion object {
        private const val MAX_HOPS = 10
        const val PUBLIC_GROUP_ID = "shout_channel"
    }
}
