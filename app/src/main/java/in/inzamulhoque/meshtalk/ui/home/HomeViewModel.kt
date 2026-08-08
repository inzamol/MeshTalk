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

import `in`.inzamulhoque.meshtalk.util.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine

class HomeViewModel(
    private val peerDao: PeerDao,
    private val settingsManager: SettingsManager,
    connectedPeerAddresses: StateFlow<Set<String>>
) : ViewModel() {

    private val _showConnectingDevices = MutableStateFlow(settingsManager.isShowConnectingDevicesEnabled)
    val showConnectingDevices = _showConnectingDevices.asStateFlow()

    val activePeerAddresses = connectedPeerAddresses

    private val shoutPeer = Peer(
        id = MeshProtocol.PUBLIC_GROUP_ID,
        publicKey = "",
        displayName = "Public Shout",
        deviceAddress = null,
        bio = "Broadcast to everyone nearby",
        encryptionKey = "public"
    )

    val peers: StateFlow<List<Peer>> = combine(
        peerDao.getAllPeers(),
        _showConnectingDevices
    ) { list, showConnecting ->
        val filtered = if (showConnecting) {
            list
        } else {
            list.filter { it.publicKey.isNotEmpty() }
        }
        listOf(shoutPeer) + filtered
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf(shoutPeer))

    fun refreshSettings() {
        _showConnectingDevices.value = settingsManager.isShowConnectingDevicesEnabled
    }

    fun deletePeer(peer: Peer) {
        viewModelScope.launch {
            peerDao.deletePeer(peer)
        }
    }
}
