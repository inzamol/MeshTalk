package `in`.inzamulhoque.meshtalk.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.inzamulhoque.meshtalk.data.local.dao.PeerDao
import `in`.inzamulhoque.meshtalk.data.local.dao.MessageDao
import `in`.inzamulhoque.meshtalk.data.local.entity.Peer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

import `in`.inzamulhoque.meshtalk.crypto.IdentityManager
import `in`.inzamulhoque.meshtalk.protocol.MeshProtocol
import `in`.inzamulhoque.meshtalk.util.SettingsManager
import `in`.inzamulhoque.meshtalk.data.local.entity.MessageType

class HomeViewModel(
    private val peerDao: PeerDao,
    private val messageDao: MessageDao,
    private val settingsManager: SettingsManager,
    private val identityManager: IdentityManager,
    connectedPeerAddresses: StateFlow<Set<String>>,
    private val myId: String
) : ViewModel() {

    private val _showConnectingDevices = MutableStateFlow(settingsManager.isShowConnectingDevicesEnabled)
    val showConnectingDevices = _showConnectingDevices.asStateFlow()

    private val _isPublicShoutEnabled = MutableStateFlow(settingsManager.isPublicShoutEnabled)
    val isPublicShoutEnabled = _isPublicShoutEnabled.asStateFlow()

    val activePeerAddresses = connectedPeerAddresses

    private val shoutPeer = Peer(
        id = MeshProtocol.PUBLIC_GROUP_ID,
        publicKey = "",
        displayName = "Public Shout",
        deviceAddress = null,
        bio = "Broadcast to everyone nearby",
        encryptionKey = "public"
    )

    private val basePeers: Flow<List<Peer>> = combine(
        peerDao.getAllPeers(),
        _showConnectingDevices,
        _isPublicShoutEnabled
    ) { list, showConnecting, shoutEnabled ->
        val filtered = if (showConnecting) {
            list
        } else {
            list.filter { it.publicKey.isNotEmpty() }
        }
        if (shoutEnabled) listOf(shoutPeer) + filtered else filtered
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val peers: StateFlow<List<PeerUiModel>> = basePeers.flatMapLatest { list ->
        if (list.isEmpty()) return@flatMapLatest flowOf(emptyList<PeerUiModel>())
        
        val flows = list.map { peer ->
            val unreadFlow = if (peer.id == MeshProtocol.PUBLIC_GROUP_ID) {
                messageDao.getUnreadCountForGroup(peer.id, myId)
            } else {
                messageDao.getUnreadCountForPeer(peer.id, myId)
            }
            
            val lastMsgFlow = if (peer.id == MeshProtocol.PUBLIC_GROUP_ID) {
                messageDao.getLastMessageForGroup(peer.id)
            } else {
                messageDao.getLastMessageForPeer(peer.id)
            }

            combine(unreadFlow, lastMsgFlow) { unread, lastMsg ->
                val preview = when {
                    lastMsg == null -> null
                    lastMsg.type == MessageType.IMAGE -> "📷 Image"
                    lastMsg.type == MessageType.FILE -> "📁 File"
                    lastMsg.senderId == myId -> lastMsg.localPlaintext ?: lastMsg.content
                    lastMsg.isEncrypted && lastMsg.receiverId == myId -> {
                        try {
                            identityManager.decryptMessage(lastMsg.content)
                        } catch (e: Exception) {
                            "[Encrypted Message]"
                        }
                    }
                    else -> lastMsg.content
                }

                PeerUiModel(
                    peer = peer,
                    unreadCount = unread,
                    lastMessage = preview,
                    lastMessageTime = lastMsg?.timestamp ?: 0
                )
            }
        }
        
        combine(flows) { it.toList().sortedByDescending { model -> 
            if (model.peer.id == MeshProtocol.PUBLIC_GROUP_ID) Long.MAX_VALUE // Keep public shout at top if enabled
            else model.lastMessageTime 
        } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun refreshSettings() {
        _showConnectingDevices.value = settingsManager.isShowConnectingDevicesEnabled
        _isPublicShoutEnabled.value = settingsManager.isPublicShoutEnabled
    }

    fun deletePeer(peer: Peer) {
        viewModelScope.launch {
            peerDao.deletePeer(peer)
        }
    }

    fun verifyPeer(qrResult: String): String {
        val parts = qrResult.split(":")
        val (peerId, name) = if (parts.size >= 3 && parts[0] == "mt") {
            parts[1] to parts[2]
        } else {
            qrResult to "Verified Peer"
        }

        viewModelScope.launch {
            val existing = peerDao.getPeerById(peerId)
            if (existing != null) {
                peerDao.updatePeer(existing.copy(isVerified = true, displayName = if (existing.displayName == "Mesh Peer" || existing.displayName == "Connecting...") name else existing.displayName))
            } else {
                peerDao.insertPeer(Peer(
                    id = peerId,
                    publicKey = peerId,
                    displayName = name,
                    deviceAddress = null,
                    isVerified = true,
                    bio = "Added via QR"
                ))
            }
        }
        return peerId
    }
}
