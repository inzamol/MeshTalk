package `in`.inzamulhoque.meshtalk.protocol

import `in`.inzamulhoque.meshtalk.data.local.dao.MessageDao
import `in`.inzamulhoque.meshtalk.data.local.dao.PeerDao
import `in`.inzamulhoque.meshtalk.data.local.entity.Message
import `in`.inzamulhoque.meshtalk.data.local.entity.Peer
import `in`.inzamulhoque.meshtalk.data.local.entity.MessageStatus
import `in`.inzamulhoque.meshtalk.util.NotificationHelper
import `in`.inzamulhoque.meshtalk.util.ToastHelper
import `in`.inzamulhoque.meshtalk.util.SettingsManager
import `in`.inzamulhoque.meshtalk.crypto.IdentityManager
import android.content.Context
import android.util.Log
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

@JsonClass(generateAdapter = true)
data class SyncUpdate(
    val type: SyncUpdateType,
    val targetUuid: String,
    val senderId: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class SyncUpdateType {
    DELETE_MESSAGE,
    DELIVERED,
    READ
}

class MeshProtocol(
    private val context: Context,
    private val myId: String,
    private val messageDao: MessageDao,
    private val peerDao: PeerDao,
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
            inventory = getInventory()
        )
    }

    suspend fun handleHandshake(handshake: Handshake, deviceAddress: String?): List<Message> {
        onPeerDiscovered(
            peerId = handshake.peerId,
            publicKey = handshake.peerId,
            encryptionKey = handshake.encryptionKey,
            displayName = handshake.displayName,
            deviceAddress = deviceAddress
        )
        if (settingsManager.isConnectionToastEnabled) {
            ToastHelper.showToast(context, "Securely connected to ${handshake.displayName}")
        }
        return getMessagesToSync(handshake.inventory)
    }

    fun serializeHandshake(handshake: Handshake): String = handshakeAdapter.toJson(handshake)
    fun deserializeHandshake(json: String): Handshake? = try { handshakeAdapter.fromJson(json) } catch (e: Exception) { null }

    fun serializeSyncUpdate(update: SyncUpdate): String = syncUpdateAdapter.toJson(update)
    fun deserializeSyncUpdate(json: String): SyncUpdate? = try { syncUpdateAdapter.fromJson(json) } catch (e: Exception) { null }

    suspend fun onPeerDiscovered(peerId: String, publicKey: String, encryptionKey: String?, displayName: String?, deviceAddress: String?) {
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
            peerDao.insertPeer(Peer(peerId, publicKey, encryptionKey, displayName, deviceAddress, System.currentTimeMillis()))
        } else {
            peerDao.updatePeer(existingPeer.copy(
                lastSeen = System.currentTimeMillis(), 
                deviceAddress = deviceAddress ?: existingPeer.deviceAddress,
                encryptionKey = encryptionKey ?: existingPeer.encryptionKey,
                displayName = displayName ?: existingPeer.displayName,
                publicKey = if (publicKey.isNotEmpty()) publicKey else existingPeer.publicKey
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

    suspend fun processReceivedMessage(message: Message): SyncUpdate? {
        if (messageDao.getMessageByUuid(message.uuid) != null) {
            return null
        }

        if (message.receiverId == myId) {
            val decryptedContent = if (message.isEncrypted) {
                try {
                    identityManager.decryptMessage(message.content)
                } catch (e: Exception) { "[Encrypted Message]" }
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
            
            // Return a DELIVERED update to send back to the sender
            return SyncUpdate(SyncUpdateType.DELIVERED, message.uuid, myId)
            
        } else if (message.hopCount < MAX_HOPS && message.expiryTimestamp > System.currentTimeMillis()) {
            if (settingsManager.isForwardingEnabled) {
                messageDao.insertMessage(message.copy(
                    id = 0, 
                    hopCount = message.hopCount + 1,
                    status = MessageStatus.CARRYING
                ))
            }
        }
        return null
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

    fun deserializeMessage(json: String): Message? = try { messageAdapter.fromJson(json) } catch (e: Exception) { null }

    companion object {
        private const val MAX_HOPS = 10
    }
}
