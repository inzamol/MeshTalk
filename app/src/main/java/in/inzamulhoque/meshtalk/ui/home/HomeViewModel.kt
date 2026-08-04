package `in`.inzamulhoque.meshtalk.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.inzamulhoque.meshtalk.data.local.dao.PeerDao
import `in`.inzamulhoque.meshtalk.data.local.entity.Peer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val peerDao: PeerDao) : ViewModel() {
    val peers: StateFlow<List<Peer>> = peerDao.getAllPeers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deletePeer(peer: Peer) {
        viewModelScope.launch {
            peerDao.deletePeer(peer)
        }
    }
}
