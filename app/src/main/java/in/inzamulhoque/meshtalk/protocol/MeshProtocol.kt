package `in`.inzamulhoque.meshtalk.protocol

import `in`.inzamulhoque.meshtalk.data.local.dao.MessageDao
import `in`.inzamulhoque.meshtalk.data.local.dao.PeerDao
import `in`.inzamulhoque.meshtalk.data.local.entity.Message
import `in`.inzamulhoque.meshtalk.data.local.entity.Peer
import `in`.inzamulhoque.meshtalk.data.local.entity.MessageStatus
import `in`.inzamulhoque.meshtalk.util.NotificationHelper
import `in`.inzamulhoque.meshtalk.util.ToastHelper
import android.content.Context
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class MeshProtocol(
    private val context: Context,
    private val myId: String,
    private val messageDao: MessageDao,
    private val peerDao: PeerDao
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val messageAdapter = moshi.adapter(Message::class.java)
    private val handshakeAdapter = moshi.adapter(Handshake::class.java)

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
            publicKey = handshake.peerId, // Initial ID is the public key
            encryptionKey = handshake.encryptionKey,
            displayName = handshake.displayName,
            deviceAddress = deviceAddress
        )
        ToastHelper.showToast(context, "Securely connected to ${handshake.displayName}")
        return getMessagesToSync(handshake.inventory)
    }

    fun serializeHandshake(handshake: Handshake): String = handshakeAdapter.toJson(handshake)
    fun deserializeHandshake(json: String): Handshake? = try { handshakeAdapter.fromJson(json) } catch (e: Exception) { null }

    suspend fun onPeerDiscovered(peerId: String, publicKey: String, encryptionKey: String?, displayName: String?, deviceAddress: String?) {
        // Handle identity changes for the same device address
        if (deviceAddress != null && peerId.length > 20) { // Only do this check for "strong" IDs (Public Keys)
            val allPeers = peerDao.getAllPeersSync()
            val stalePeers = allPeers.filter { it.deviceAddress == deviceAddress && it.id != peerId }
            if (stalePeers.isNotEmpty()) {
                Log.w("MeshProtocol", "Identity change detected for $deviceAddress. Removing ${stalePeers.size} stale peer(s).")
                stalePeers.forEach { peerDao.deletePeer(it) }
            }
        }

        val existingPeer = peerDao.getPeerById(peerId)
        if (existingPeer == null) {
            Log.d("MeshProtocol", "New peer discovered: $peerId ($displayName)")
            peerDao.insertPeer(Peer(peerId, publicKey, encryptionKey, displayName, deviceAddress, System.currentTimeMillis()))
        } else {
            Log.d("MeshProtocol", "Existing peer seen: $peerId")
            peerDao.updatePeer(existingPeer.copy(
                lastSeen = System.currentTimeMillis(), 
                deviceAddress = deviceAddress,
                encryptionKey = encryptionKey ?: existingPeer.encryptionKey,
                displayName = displayName ?: existingPeer.displayName
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

    suspend fun processReceivedMessage(message: Message) {
        if (messageDao.getMessageByUuid(message.uuid) != null) {
            Log.d("MeshProtocol", "Message ${message.uuid} already exists, skipping")
            return
        }

        if (message.receiverId == myId) {
            Log.d("MeshProtocol", "Received message for me from ${message.senderId}")
            messageDao.insertMessage(message.copy(id = 0, status = MessageStatus.DELIVERED))
            
            // Notification
            val peer = peerDao.getPeerById(message.senderId)
            NotificationHelper.showMessageNotification(
                context = context,
                senderName = peer?.displayName ?: "Unknown Peer",
                message = if (message.isEncrypted) "[Encrypted Message]" else message.content
            )
        } else if (message.hopCount < MAX_HOPS && message.expiryTimestamp > System.currentTimeMillis()) {
            Log.d("MeshProtocol", "Received message to forward for ${message.receiverId}")
            messageDao.insertMessage(message.copy(
                id = 0, 
                hopCount = message.hopCount + 1,
                status = MessageStatus.CARRYING
            ))
        } else {
            Log.d("MeshProtocol", "Received message discarded (max hops or expired)")
        }
    }

    fun serializeMessage(message: Message): String {
        // Exclude localPlaintext when sending over the mesh to save space and privacy
        val cleanMessage = message.copy(localPlaintext = null)
        return messageAdapter.toJson(cleanMessage)
    }

    fun deserializeMessage(json: String): Message? {
        return try {
            messageAdapter.fromJson(json)
        } catch (e: Exception) {
            Log.e("MeshProtocol", "Deserialization error", e)
            null
        }
    }

    companion object {
        private const val MAX_HOPS = 10
    }
}
