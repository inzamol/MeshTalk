package `in`.inzamulhoque.meshtalk.protocol

import `in`.inzamulhoque.meshtalk.crypto.IdentityManager
import `in`.inzamulhoque.meshtalk.data.local.dao.GroupDao
import `in`.inzamulhoque.meshtalk.data.local.dao.MessageDao
import `in`.inzamulhoque.meshtalk.data.local.dao.PeerDao
import `in`.inzamulhoque.meshtalk.data.local.entity.Message
import `in`.inzamulhoque.meshtalk.data.local.entity.MessageStatus
import `in`.inzamulhoque.meshtalk.data.local.entity.MessageType
import `in`.inzamulhoque.meshtalk.data.local.entity.Peer
import `in`.inzamulhoque.meshtalk.protocol.proto.*
import `in`.inzamulhoque.meshtalk.util.BloomFilter
import `in`.inzamulhoque.meshtalk.util.FileUtils
import `in`.inzamulhoque.meshtalk.util.NotificationHelper
import `in`.inzamulhoque.meshtalk.util.SettingsManager
import `in`.inzamulhoque.meshtalk.util.ToastHelper
import android.content.Context
import android.util.Log
import `in`.inzamulhoque.meshtalk.util.RateLimiter
import java.security.MessageDigest
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
    private val rateLimiter = RateLimiter(maxMessages = 30, windowMs = 60000)
    private val verifiedRateLimiter = RateLimiter(maxMessages = 150, windowMs = 60000)

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
        val protoBuilder = ProtoMessage.newBuilder()
            .setUuid(message.uuid)
            .setSenderId(message.senderId)
            .setReceiverId(message.receiverId)
            .setGroupId(message.groupId ?: "")
            .setContent(message.content)
            .setTimestamp(message.timestamp)
            .setExpiryTimestamp(message.expiryTimestamp)
            .setHopCount(message.hopCount)
            .setIsEncrypted(message.isEncrypted)
            
        try {
            // Set type (int32 type = 11)
            val typeMethod = protoBuilder.javaClass.methods.find { it.name == "setType" || it.name == "setTypeValue" }
            typeMethod?.invoke(protoBuilder, message.type.ordinal)
            
            // Set powNonce (uint64 pow_nonce = 10)
            val nonceMethod = protoBuilder.javaClass.methods.find { it.name == "setPowNonce" }
            nonceMethod?.invoke(protoBuilder, message.powNonce)
        } catch (_: Exception) {}
        
        return MeshPacket.newBuilder().setMessage(protoBuilder.build()).build().toByteArray()
    }

    fun parsePacket(data: ByteArray): Any? {
        return try {
            val packet = MeshPacket.parseFrom(data)
            when (packet.payloadCase) {
                MeshPacket.PayloadCase.HANDSHAKE -> packet.handshake
                MeshPacket.PayloadCase.MESSAGE -> {
                    val proto = packet.message
                    
                    var nonce = 0L
                    var typeInt = 0
                    
                    try {
                        val nonceMethod = proto.javaClass.methods.find { it.name == "getPowNonce" }
                        nonce = (nonceMethod?.invoke(proto) as? Long) ?: 0L
                        
                        val typeMethod = proto.javaClass.methods.find { it.name == "getType" || it.name == "getTypeValue" }
                        typeInt = (typeMethod?.invoke(proto) as? Int) ?: 0
                    } catch (_: Exception) {}
                    
                    Message(
                        uuid = proto.uuid,
                        senderId = proto.senderId,
                        receiverId = proto.receiverId,
                        groupId = proto.groupId.ifEmpty { null },
                        content = proto.content,
                        timestamp = proto.timestamp,
                        expiryTimestamp = proto.expiryTimestamp,
                        hopCount = proto.hopCount,
                        isEncrypted = proto.isEncrypted,
                        powNonce = nonce,
                        type = try { MessageType.entries[typeInt] } catch (_: Exception) { MessageType.TEXT }
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

    suspend fun solvePoW(protoBuilder: ProtoMessage.Builder) {
        val baseData = (protoBuilder.uuid + protoBuilder.senderId + protoBuilder.content + protoBuilder.timestamp).toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        var nonce = 0L
        
        val start = System.currentTimeMillis()
        while (true) {
            md.reset()
            md.update(baseData)
            md.update(nonce.toString().toByteArray())
            val hash = md.digest()
            
            if (checkDifficulty(hash, POW_DIFFICULTY)) {
                try {
                    val method = protoBuilder.javaClass.methods.find { it.name == "setPowNonce" }
                    method?.invoke(protoBuilder, nonce)
                } catch (_: Exception) {}
                Log.d("MeshProtocol", "PoW solved in ${System.currentTimeMillis() - start}ms. Nonce: $nonce")
                return
            }
            nonce++
            if ((nonce % 2000L) == 0L) delay(1.milliseconds) 
        }
    }

    suspend fun verifyPoW(message: Message): Boolean {
        if (message.groupId != PUBLIC_GROUP_ID && message.receiverId != myId && !settingsManager.isForwardingEnabled) return true 
        
        // Backward Compatibility: Allow legacy messages (0 nonce) if from a verified peer
        // or during the transition period (messages older than current release date).
        if (message.powNonce == 0L) {
            val peer = peerDao.getPeerById(message.senderId)
            return peer?.isVerified == true || message.timestamp < 1786270000000L 
        }

        val baseData = (message.uuid + message.senderId + message.content + message.timestamp).toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        md.update(baseData)
        md.update(message.powNonce.toString().toByteArray())
        val hash = md.digest()
        
        return checkDifficulty(hash, POW_DIFFICULTY)
    }

    private fun checkDifficulty(hash: ByteArray, difficulty: Int): Boolean {
        var bits = 0
        for (byte in hash) {
            val b = byte.toInt() and 0xFF
            if (b == 0) {
                bits += 8
            } else {
                bits += Integer.numberOfLeadingZeros(b) - 24
                break
            }
        }
        return bits >= difficulty
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
        // Anti-Spam Layer 1: Verify PoW
        if (!verifyPoW(message)) {
            Log.w("MeshProtocol", "Message ${message.uuid.take(8)} rejected: Invalid PoW")
            return null to null
        }

        // Anti-Spam Layer 2: Rate Limiting
        val peer = peerDao.getPeerById(message.senderId)
        val isVerified = peer?.isVerified == true
        val limiter = if (isVerified) verifiedRateLimiter else rateLimiter
        
        if (!limiter.isAllowed(message.senderId)) {
            Log.w("MeshProtocol", "Message ${message.uuid.take(8)} rejected: Rate limit exceeded for ${message.senderId}")
            return null to null
        }

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
                val isChatOpen = app?.currentChatPeerId == message.senderId
                val initialStatus = if (isChatOpen) MessageStatus.READ else MessageStatus.DELIVERED

                messageDao.insertMessage(message.copy(id = 0, status = initialStatus))

                if (settingsManager.isNotificationEnabled && (!isForeground || !isChatOpen)) {
                    val peer = peerDao.getPeerById(message.senderId)
                    val notificationText = if (message.type == MessageType.IMAGE) "Sent an image" else decryptedContent
                    
                    NotificationHelper.showMessageNotification(
                        context = context,
                        senderId = message.senderId,
                        senderName = peer?.displayName ?: "Unknown Peer",
                        message = notificationText
                    )
                } else if (isForeground && isChatOpen) {
                    Log.d("MeshProtocol", "Notification skipped: Chat with ${message.senderId} is currently open")
                }
                
                if (message.groupId != PUBLIC_GROUP_ID) {
                    val syncType = if (isChatOpen) SyncUpdateType.READ else SyncUpdateType.DELIVERED
                    val update = ProtoSyncUpdate.newBuilder()
                        .setType(syncType)
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
        private const val POW_DIFFICULTY = 6 // Even lower for maximum reliability during testing
        const val PUBLIC_GROUP_ID = "shout_channel"
    }
}
