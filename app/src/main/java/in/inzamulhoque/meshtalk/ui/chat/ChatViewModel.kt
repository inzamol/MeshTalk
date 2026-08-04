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
    }

    val peer: StateFlow<Peer?> = peerDao.getPeerFlowById(peerId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val messages: StateFlow<List<Message>> = messageDao.getMessagesForPeer(peerId)
        .map { list ->
            list.map { msg ->
                if (msg.senderId == myId && msg.localPlaintext != null) {
                    // Always prefer local plaintext for our own sent messages
                    msg.copy(content = msg.localPlaintext, isEncrypted = false)
                } else if (msg.isEncrypted && msg.receiverId == myId) {
                    try {
                        msg.copy(content = identityManager.decryptMessage(msg.content), isEncrypted = false)
                    } catch (e: Exception) {
                        ToastHelper.showToast(getApplication(), "Failed to decrypt message from peer")
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
            val peerEntity = peerDao.getPeerById(peerId)
            val encryptionKey = peerEntity?.encryptionKey ?: peerEntity?.publicKey // Fallback to publicKey if available
            
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
                receiverId = peerId,
                content = finalContent,
                localPlaintext = content, // Store the original text locally
                isEncrypted = encryptionKey != null,
                status = MessageStatus.SENT
            )
            messageDao.insertMessage(message)
        }
    }
}
