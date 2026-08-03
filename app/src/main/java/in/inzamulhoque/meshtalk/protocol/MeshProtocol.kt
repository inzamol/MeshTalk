package `in`.inzamulhoque.meshtalk.protocol

import `in`.inzamulhoque.meshtalk.data.local.dao.MessageDao
import `in`.inzamulhoque.meshtalk.data.local.dao.PeerDao
import `in`.inzamulhoque.meshtalk.data.local.entity.Message
import `in`.inzamulhoque.meshtalk.data.local.entity.Peer
import `in`.inzamulhoque.meshtalk.data.local.entity.MessageStatus
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class MeshProtocol(
    private val myId: String,
    private val messageDao: MessageDao,
    private val peerDao: PeerDao
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val messageAdapter = moshi.adapter(Message::class.java)

    suspend fun onPeerDiscovered(peerId: String, publicKey: String, encryptionKey: String?, deviceAddress: String?) {
        val existingPeer = peerDao.getPeerById(peerId)
        if (existingPeer == null) {
            Log.d("MeshProtocol", "New peer discovered: $peerId")
            peerDao.insertPeer(Peer(peerId, publicKey, encryptionKey, null, deviceAddress, System.currentTimeMillis()))
        } else {
            Log.d("MeshProtocol", "Existing peer seen: $peerId")
            peerDao.updatePeer(existingPeer.copy(
                lastSeen = System.currentTimeMillis(), 
                deviceAddress = deviceAddress,
                encryptionKey = encryptionKey ?: existingPeer.encryptionKey
            ))
        }
    }

    suspend fun getInventory(): List<Long> {
        return messageDao.getAllMessageIds()
    }

    suspend fun getMessagesToSync(remoteInventory: List<Long>): List<Message> {
        val messagesToForward = messageDao.getMessagesToForward(myId, System.currentTimeMillis())
        return messagesToForward.filter { !remoteInventory.contains(it.id) }
    }

    suspend fun processReceivedMessage(message: Message) {
        if (message.receiverId == myId) {
            Log.d("MeshProtocol", "Received message for me from ${message.senderId}")
            messageDao.insertMessage(message.copy(id = 0, status = MessageStatus.DELIVERED))
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

    fun serializeMessage(message: Message): String = messageAdapter.toJson(message)
    fun deserializeMessage(json: String): Message? = messageAdapter.fromJson(json)

    companion object {
        private const val MAX_HOPS = 10
    }
}
