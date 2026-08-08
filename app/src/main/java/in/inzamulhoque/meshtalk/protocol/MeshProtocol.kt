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
import androidx.annotation.Keep
import android.content.Context
import android.util.Log
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

@Keep
@JsonClass(generateAdapter = true)
data class SyncUpdate(
    @Json(name = "t") val type: SyncUpdateType,
    @Json(name = "u") val targetUuid: String,
    @Json(name = "s") val senderId: String,
    @Json(name = "ts") val timestamp: Long = System.currentTimeMillis()
)

@Keep
enum class SyncUpdateType {
    @Json(name = "DELETE_MESSAGE") DELETE_MESSAGE,
    @Json(name = "DELIVERED") DELIVERED,
    @Json(name = "READ") READ
}



class MeshProtocol(
    private val context: Context,
    private val myId: String,
    private val messageDao: MessageDao,
    private val peerDao: PeerDao,
    private val groupDao: GroupDao,
    private val identityManager: IdentityManager,
    private val settingsManager: SettingsManager
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val messageAdapter = moshi.adapter(Message::class.java)
    private val handshakeAdapter = moshi.adapter(Handshake::class.java)
    private val syncUpdateAdapter = moshi.adapter(SyncUpdate::class.java)

    suspend fun createHandshake(encryptionKey: String, displayName: String): Handshake {
        return Handshake(
            peerId = myId,
            encryptionKey = encryptionKey,
            displayName = displayName,
            inventory = getInventory(),
            bio = settingsManager.bio,
            avatarBase64 = settingsManager.avatarBase64
        )
    }

    suspend fun handleHandshake(handshake: Handshake, deviceAddress: String?): List<Message> {
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
        val messages = getMessagesToSync(handshake.inventory)
        Log.d("MeshProtocol", "Handshake from ${handshake.displayName}, syncing ${messages.size} messages")
        return messages
    }

    fun serializeHandshake(handshake: Handshake): String = handshakeAdapter.toJson(handshake)
    fun deserializeHandshake(json: String): Handshake? = try { handshakeAdapter.fromJson(json) } catch (_: Exception) { null }

    fun serializeSyncUpdate(update: SyncUpdate): String = syncUpdateAdapter.toJson(update)
    fun deserializeSyncUpdate(json: String): SyncUpdate? = try { syncUpdateAdapter.fromJson(json) } catch (_: Exception) { null }

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

        val existingPeer = peerDao.getPeerById(peerId)
        if (existingPeer == null) {
            peerDao.insertPeer(Peer(
                id = peerId, 
                publicKey = publicKey, 
                encryptionKey = encryptionKey, 
                displayName = displayName, 
                deviceAddress = deviceAddress, 
                lastSeen = System.currentTimeMillis(),
                bio = bio,
                avatarUri = avatarBase64, // Store base64 in avatarUri for now
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
                avatarUri = avatarBase64 ?: existingPeer.avatarUri,
                rssi = rssi
            ))
        }
    }

    suspend fun getInventory(): List<String> {
        return messageDao.getAllMessageUuids().takeLast(20)
    }

    suspend fun getMessagesToSync(remoteInventory: List<String>): List<Message> {
        val messagesToForward = messageDao.getMessagesToForward(myId, System.currentTimeMillis())
        return messagesToForward.filter { !remoteInventory.contains(it.uuid) }
    }

    suspend fun processReceivedMessage(message: Message): Pair<SyncUpdate?, Message?> {
        if (messageDao.getMessageByUuid(message.uuid) != null) {
            return null to null
        }

        // Check if it's for me (Direct or Group)
        val isForMe = message.receiverId == myId || (message.groupId != null && (message.groupId == PUBLIC_GROUP_ID || isMemberOf(message.groupId)))

        if (isForMe) {
            val decryptedContent = if (message.isEncrypted && message.groupId != PUBLIC_GROUP_ID) {
                try {
                    // For group messages, we would ideally use a group key.
                    // For now, we'll assume group messages are unencrypted or handled differently.
                    // Implementing full group encryption is complex (DH for each pair or a shared group key).
                    // As per plan, let's stick to basic group sync first.
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
            return SyncUpdate(SyncUpdateType.DELIVERED, message.uuid, myId) to null
            
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

    suspend fun processSyncUpdate(update: SyncUpdate) {
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
        }
    }

    fun serializeMessage(message: Message): String {
        val cleanMessage = message.copy(localPlaintext = null)
        return messageAdapter.toJson(cleanMessage)
    }

    fun deserializeMessage(json: String): Message? = try { messageAdapter.fromJson(json) } catch (_: Exception) { null }

    companion object {
        private const val MAX_HOPS = 10
        const val PUBLIC_GROUP_ID = "shout_channel"
    }
}
