package `in`.inzamulhoque.meshtalk.protocol

import `in`.inzamulhoque.meshtalk.crypto.IdentityManager
import `in`.inzamulhoque.meshtalk.data.local.dao.GroupDao
import `in`.inzamulhoque.meshtalk.data.local.dao.MessageDao
import `in`.inzamulhoque.meshtalk.data.local.dao.PeerDao
import `in`.inzamulhoque.meshtalk.data.local.entity.Message
import `in`.inzamulhoque.meshtalk.data.local.entity.MessageStatus
import `in`.inzamulhoque.meshtalk.data.local.entity.Peer
import `in`.inzamulhoque.meshtalk.protocol.proto.*
import `in`.inzamulhoque.meshtalk.util.BloomFilter
import `in`.inzamulhoque.meshtalk.util.FileUtils
import `in`.inzamulhoque.meshtalk.util.NotificationHelper
import `in`.inzamulhoque.meshtalk.util.SettingsManager
import `in`.inzamulhoque.meshtalk.util.ToastHelper
import android.content.Context
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

class MeshProtocol(
    private val context: Context,
    private val myId: String,
    private val messageDao: MessageDao,
    private val peerDao: PeerDao,
    private val groupDao: GroupDao,
    private val identityManager: IdentityManager,
    private val settingsManager: SettingsManager
) {
    private val heardCounts = ConcurrentHashMap<String, Int>()

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

    fun serializeHandshake(handshake: ProtoHandshake): ByteArray {
        return MeshPacket.newBuilder().setHandshake(handshake).build().toByteArray()
    }

    fun serializeSyncUpdate(update: ProtoSyncUpdate): ByteArray {
        return MeshPacket.newBuilder().setSyncUpdate(update).build().toByteArray()
    }

    fun serializeMessage(message: Message): ByteArray {
        val proto = ProtoMessage.newBuilder()
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
        return MeshPacket.newBuilder().setMessage(proto).build().toByteArray()
    }

    fun parsePacket(data: ByteArray): Any? {
        return try {
            val packet = MeshPacket.parseFrom(data)
            when (packet.payloadCase) {
                MeshPacket.PayloadCase.HANDSHAKE -> packet.handshake
                MeshPacket.PayloadCase.MESSAGE -> {
                    val proto = packet.message
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
                }
                MeshPacket.PayloadCase.SYNC_UPDATE -> packet.syncUpdate
                else -> null
            }
        } catch (e: Exception) {
            Log.e("MeshProtocol", "Error parsing packet", e)
            null
        }
    }

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
        
        val avatarPath = if (avatarBase64 != null && avatarBase64.isNotEmpty()) {
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
            val name = if (displayName.isNullOrBlank() || displayName.length > 50) "Mesh Peer" else displayName
            peerDao.insertPeer(Peer(
                id = peerId, 
                publicKey = publicKey, 
                encryptionKey = encryptionKey, 
                displayName = name, 
                deviceAddress = deviceAddress, 
                lastSeen = System.currentTimeMillis(),
                bio = bio,
                avatarUri = avatarPath,
                rssi = rssi
            ))
        } else {
            val name = if (!displayName.isNullOrBlank() && displayName.length <= 50) displayName else existingPeer.displayName
            peerDao.updatePeer(existingPeer.copy(
                lastSeen = System.currentTimeMillis(), 
                deviceAddress = deviceAddress ?: existingPeer.deviceAddress,
                encryptionKey = encryptionKey ?: existingPeer.encryptionKey,
                displayName = name,
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
        val existing = messageDao.getMessageByUuid(message.uuid)
        if (existing != null) {
            heardCounts[message.uuid] = (heardCounts[message.uuid] ?: 0) + 1
            return null to null
        }

        val isForMe = message.receiverId == myId || (message.groupId != null && (message.groupId == PUBLIC_GROUP_ID || isMemberOf(message.groupId)))

        if (isForMe) {
            // Only process/store public shout if enabled in settings
            if (message.groupId == PUBLIC_GROUP_ID && !settingsManager.isPublicShoutEnabled) {
                // Do not store, but allow forwarding below
            } else {
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
                
                if (message.groupId != PUBLIC_GROUP_ID) {
                    val update = ProtoSyncUpdate.newBuilder()
                        .setType(SyncUpdateType.DELIVERED)
                        .setTargetUuid(message.uuid)
                        .setSenderId(myId)
                        .setTimestamp(System.currentTimeMillis())
                        .build()
                    return update to null
                }
                return null to null
            }
        } 
        
        if (message.hopCount < MAX_HOPS && message.expiryTimestamp > System.currentTimeMillis()) {
            if (settingsManager.isForwardingEnabled) {
                val delayMs = (20..150).random().toLong()
                delay(delayMs.milliseconds)
                
                val heardCount = heardCounts.getOrDefault(message.uuid, 0)
                if (heardCount >= 3) {
                    messageDao.insertMessage(message.copy(id = 0, hopCount = message.hopCount + 1, status = MessageStatus.CARRYING))
                    return null to null
                }

                val carrierMessage = message.copy(
                    id = 0, 
                    hopCount = message.hopCount + 1,
                    status = MessageStatus.CARRYING
                )
                messageDao.insertMessage(carrierMessage)
                
                if (heardCounts.size > 100) heardCounts.clear() 
                
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
        Log.d("MeshProtocol", "Processing SyncUpdate ${update.type} for ${update.targetUuid.take(8)}")
        
        when (update.type) {
            SyncUpdateType.DELETE_MESSAGE -> {
                if (msg.senderId == update.senderId) {
                    messageDao.deleteMessage(msg)
                }
            }
            SyncUpdateType.DELIVERED -> {
                if (msg.senderId == myId && msg.receiverId == update.senderId) {
                    Log.d("MeshProtocol", "Message ${msg.uuid.take(8)} marked as DELIVERED")
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

    companion object {
        private const val MAX_HOPS = 10
        const val PUBLIC_GROUP_ID = "shout_channel"
    }
}
