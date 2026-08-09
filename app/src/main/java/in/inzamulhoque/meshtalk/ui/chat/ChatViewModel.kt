package `in`.inzamulhoque.meshtalk.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `in`.inzamulhoque.meshtalk.ble.MeshNetworkManager
import `in`.inzamulhoque.meshtalk.crypto.IdentityManager
import `in`.inzamulhoque.meshtalk.data.local.dao.MessageDao
import `in`.inzamulhoque.meshtalk.data.local.dao.PeerDao
import `in`.inzamulhoque.meshtalk.data.local.entity.Message
import `in`.inzamulhoque.meshtalk.data.local.entity.MessageStatus
import `in`.inzamulhoque.meshtalk.data.local.entity.Peer
import `in`.inzamulhoque.meshtalk.util.ToastHelper
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

import android.net.Uri
import android.util.Base64
import `in`.inzamulhoque.meshtalk.data.local.entity.MessageType
import java.io.InputStream
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

import `in`.inzamulhoque.meshtalk.protocol.MeshProtocol
import `in`.inzamulhoque.meshtalk.protocol.proto.ProtoSyncUpdate
import `in`.inzamulhoque.meshtalk.protocol.proto.SyncUpdateType

class ChatViewModel(
    application: Application,
    private val meshNetworkManager: MeshNetworkManager,
    private val peerId: String,
    private val myId: String,
    private val messageDao: MessageDao,
    private val peerDao: PeerDao,
    private val identityManager: IdentityManager
) : AndroidViewModel(application) {

    init {
        meshNetworkManager.connectToPeerById(peerId)
        (application as? `in`.inzamulhoque.meshtalk.MeshApplication)?.currentChatPeerId = peerId
    }

    override fun onCleared() {
        super.onCleared()
        (getApplication() as? `in`.inzamulhoque.meshtalk.MeshApplication)?.currentChatPeerId = null
    }

    val peer: StateFlow<Peer?> = peerDao.getPeerFlowById(peerId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isOnline: StateFlow<Boolean> = meshNetworkManager.connectedPeerAddresses
        .map { addresses ->
            val p = peerDao.getPeerById(peerId)
            p?.deviceAddress?.let { addresses.contains(it) } ?: false
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initialValue = false)

    val messages: StateFlow<List<Message>> = (if (peerId == MeshProtocol.PUBLIC_GROUP_ID) {
        messageDao.getMessagesForGroup(peerId)
    } else {
        messageDao.getMessagesForPeer(peerId)
    }).onEach { list ->
            // Mark unread incoming messages as READ
            val isPublic = peerId == MeshProtocol.PUBLIC_GROUP_ID
            val unread = list.filter { 
                val isIncoming = if (isPublic) it.senderId != myId else it.receiverId == myId
                isIncoming && (it.status != MessageStatus.READ)
            }
            
            if (unread.isNotEmpty()) {
                unread.forEach { msg ->
                    messageDao.updateMessageStatus(msg.id, MessageStatus.READ.name)
                    
                    // Only broadcast READ receipts for private chats
                    if (!isPublic) {
                        meshNetworkManager.broadcastSyncUpdate(
                            ProtoSyncUpdate.newBuilder()
                                .setType(SyncUpdateType.READ)
                                .setTargetUuid(msg.uuid)
                                .setSenderId(myId)
                                .setTimestamp(System.currentTimeMillis())
                                .build()
                        )
                    }
                }
            }
        }
        .map { list ->
            list.map { msg ->
                if (msg.senderId == myId && msg.localPlaintext != null) {
                    // Always prefer local plaintext for our own sent messages
                    msg.copy(content = msg.localPlaintext, isEncrypted = false)
                } else if (msg.isEncrypted && msg.receiverId == myId) {
                    try {
                        msg.copy(content = identityManager.decryptMessage(msg.content), isEncrypted = false)
                    } catch (e: Exception) {
                        msg.copy(content = "[Decryption Failed]")
                    }
                } else {
                    msg
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun sendMessage(content: String) {
        viewModelScope.launch {
            val isPublic = peerId == MeshProtocol.PUBLIC_GROUP_ID
            val peerEntity = if (!isPublic) peerDao.getPeerById(peerId) else null
            val encryptionKey = if (isPublic) null else (peerEntity?.encryptionKey ?: peerEntity?.publicKey)
            
            val finalContent = if (encryptionKey != null) {
                try {
                    identityManager.encryptMessage(content, encryptionKey)
                } catch (e: Exception) {
                    ToastHelper.showToast(getApplication(), "Encryption failed: key mismatch")
                    content
                }
            } else {
                content
            }

            val message = Message(
                senderId = myId,
                receiverId = if (isPublic) "" else peerId,
                groupId = if (isPublic) MeshProtocol.PUBLIC_GROUP_ID else null,
                content = finalContent,
                localPlaintext = content, // Store the original text locally
                isEncrypted = encryptionKey != null,
                status = MessageStatus.PENDING, // Start as PENDING
                type = MessageType.TEXT
            )
            
            meshNetworkManager.onActivityDetected() // UI interaction resets stationary timer
            
            val msgId = messageDao.insertMessage(message)
            meshNetworkManager.broadcastMessage(message.copy(id = msgId))
        }
    }

    fun sendImage(uri: Uri) {
        viewModelScope.launch {
            val base64 = compressAndEncodeImage(uri) ?: return@launch
            val peerEntity = peerDao.getPeerById(peerId)
            val encryptionKey = peerEntity?.encryptionKey ?: peerEntity?.publicKey

            val finalContent = if (encryptionKey != null) {
                try {
                    identityManager.encryptMessage(base64, encryptionKey)
                } catch (e: Exception) {
                    base64
                }
            } else {
                base64
            }

            val message = Message(
                senderId = myId,
                receiverId = peerId,
                content = finalContent,
                localPlaintext = uri.toString(), // Store local URI for display
                isEncrypted = encryptionKey != null,
                status = MessageStatus.PENDING,
                type = MessageType.IMAGE,
                mediaUri = uri.toString()
            )
            val msgId = messageDao.insertMessage(message)
            meshNetworkManager.broadcastMessage(message.copy(id = msgId))
        }
    }

    private fun compressAndEncodeImage(uri: Uri): String? {
        return try {
            val inputStream: InputStream? = getApplication<Application>().contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            
            // Resize for mesh transport (max 400px width/height)
            val ratio = kotlin.math.min(400.0 / originalBitmap.width, 400.0 / originalBitmap.height).coerceAtMost(1.0)
            val resized = if (ratio < 1.0) {
                Bitmap.createScaledBitmap(
                    originalBitmap,
                    (originalBitmap.width * ratio).toInt(),
                    (originalBitmap.height * ratio).toInt(),
                    true
                )
            } else originalBitmap

            val outputStream = ByteArrayOutputStream()
            resized.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            val bytes = outputStream.toByteArray()
            Base64.encodeToString(bytes, Base64.DEFAULT)
        } catch (e: Exception) {
            null
        }
    }

    fun retryMessage(message: Message) {
        viewModelScope.launch {
            val updatedMessage = message.copy(
                timestamp = System.currentTimeMillis(),
                status = MessageStatus.PENDING
            )
            messageDao.insertMessage(updatedMessage)
            meshNetworkManager.broadcastMessage(updatedMessage)
        }
    }

    fun deleteMessage(message: Message) {
        viewModelScope.launch {
            messageDao.deleteMessage(message)
            if (message.senderId == myId) {
                meshNetworkManager.broadcastSyncUpdate(
                    ProtoSyncUpdate.newBuilder()
                        .setType(SyncUpdateType.DELETE_MESSAGE)
                        .setTargetUuid(message.uuid)
                        .setSenderId(myId)
                        .setTimestamp(System.currentTimeMillis())
                        .build()
                )
            }
        }
    }

    fun deleteChat() {
        viewModelScope.launch {
            messageDao.deleteMessagesForPeer(peerId)
            val p = peerDao.getPeerById(peerId)
            if (p != null) {
                peerDao.deletePeer(p)
            }
        }
    }

    fun verifyCurrentPeer() {
        viewModelScope.launch {
            val p = peerDao.getPeerById(peerId)
            if (p != null) {
                peerDao.updatePeer(p.copy(isVerified = true))
            }
        }
    }

    fun reconnect() {
        meshNetworkManager.connectToPeerById(peerId)
    }

    fun formatLastSeen(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        return when {
            diff < 60000 -> "Just now"
            diff < 3600000 -> "${diff / 60000}m ago"
            diff < 86400000 -> "${diff / 3600000}h ago"
            else -> {
                val sdf = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
                sdf.format(java.util.Date(timestamp))
            }
        }
    }
}
