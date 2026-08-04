package `in`.inzamulhoque.meshtalk.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.inzamulhoque.meshtalk.data.local.dao.PeerDao
import `in`.inzamulhoque.meshtalk.data.local.entity.Peer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import `in`.inzamulhoque.meshtalk.protocol.MeshProtocol
import kotlinx.coroutines.flow.map

class HomeViewModel(private val peerDao: PeerDao) : ViewModel() {
    private val shoutPeer = Peer(
        id = MeshProtocol.PUBLIC_GROUP_ID,
        publicKey = "",
        displayName = "Public Shout",
        deviceAddress = null,
        bio = "Broadcast to everyone nearby",
        encryptionKey = "public"
    )

    val peers: StateFlow<List<Peer>> = peerDao.getAllPeers()
        .map { list -> listOf(shoutPeer) + list }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf(shoutPeer))

    fun deletePeer(peer: Peer) {
        viewModelScope.launch {
            peerDao.deletePeer(peer)
        }
    }
}
