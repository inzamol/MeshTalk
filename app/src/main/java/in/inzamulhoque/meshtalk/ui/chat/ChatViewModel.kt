package `in`.inzamulhoque.meshtalk.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.inzamulhoque.meshtalk.crypto.IdentityManager
import `in`.inzamulhoque.meshtalk.data.local.dao.MessageDao
import `in`.inzamulhoque.meshtalk.data.local.dao.PeerDao
import `in`.inzamulhoque.meshtalk.data.local.entity.Message
import `in`.inzamulhoque.meshtalk.data.local.entity.MessageStatus
import `in`.inzamulhoque.meshtalk.data.local.entity.Peer
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatViewModel(
    private val peerId: String,
    private val myId: String,
    private val messageDao: MessageDao,
    private val peerDao: PeerDao,
    private val identityManager: IdentityManager
) : ViewModel() {

    val peer: StateFlow<Peer?> = peerDao.getPeerFlowById(peerId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val messages: StateFlow<List<Message>> = messageDao.getMessagesForPeer(peerId)
        .map { list ->
            list.map { msg ->
                if (msg.isEncrypted && msg.receiverId == myId) {
                    try {
                        msg.copy(content = identityManager.decryptMessage(msg.content), isEncrypted = false)
                    } catch (e: Exception) {
                        msg.copy(content = "[Decryption Failed]")
                    }
                } else if (msg.isEncrypted && msg.senderId == myId) {
                    // We sent it, maybe we should have stored the plaintext locally?
                    // For now, let's just show it. Ideally we store plaintext for 'sent' messages or re-decrypt if we have a way.
                    // But we don't have the peer's private key, so we can't decrypt what we encrypted for them.
                    // Usually we store the plaintext locally and only encrypt for the mesh.
                    msg
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
                    content
                }
            } else {
                content
            }

            val message = Message(
                senderId = myId,
                receiverId = peerId,
                content = finalContent,
                isEncrypted = encryptionKey != null,
                status = MessageStatus.SENT
            )
            messageDao.insertMessage(message)
        }
    }
}
