package `in`.inzamulhoque.meshtalk.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.inzamulhoque.meshtalk.data.local.AppDatabase
import `in`.inzamulhoque.meshtalk.util.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val database: AppDatabase,
    private val settingsManager: SettingsManager,
    private val identityManager: `in`.inzamulhoque.meshtalk.crypto.IdentityManager,
    private val meshNetworkManager: `in`.inzamulhoque.meshtalk.ble.MeshNetworkManager
) : ViewModel() {

    val myId = identityManager.getMyId()

    private val _displayName = MutableStateFlow(settingsManager.displayName ?: "")
    val displayName = _displayName.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(settingsManager.isNotificationEnabled)
    val notificationsEnabled = _notificationsEnabled.asStateFlow()

    private val _forwardingEnabled = MutableStateFlow(settingsManager.isForwardingEnabled)
    val forwardingEnabled = _forwardingEnabled.asStateFlow()

    private val _connectionToastEnabled = MutableStateFlow(settingsManager.isConnectionToastEnabled)
    val connectionToastEnabled = _connectionToastEnabled.asStateFlow()

    private val _continuousSearchEnabled = MutableStateFlow(settingsManager.isContinuousSearchEnabled)
    val continuousSearchEnabled = _continuousSearchEnabled.asStateFlow()

    private val _bio = MutableStateFlow(settingsManager.bio ?: "")
    val bio = _bio.asStateFlow()

    private val _avatarBase64 = MutableStateFlow(settingsManager.avatarBase64)
    val avatarBase64 = _avatarBase64.asStateFlow()

    fun updateDisplayName(name: String) {
        settingsManager.displayName = name
        _displayName.value = name
    }

    fun updateBio(bio: String) {
        settingsManager.bio = bio
        _bio.value = bio
    }

    fun updateAvatar(base64: String?) {
        settingsManager.avatarBase64 = base64
        _avatarBase64.value = base64
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        settingsManager.isNotificationEnabled = enabled
        _notificationsEnabled.value = enabled
    }

    fun setForwardingEnabled(enabled: Boolean) {
        settingsManager.isForwardingEnabled = enabled
        _forwardingEnabled.value = enabled
    }

    fun setConnectionToastEnabled(enabled: Boolean) {
        settingsManager.isConnectionToastEnabled = enabled
        _connectionToastEnabled.value = enabled
    }

    fun setContinuousSearchEnabled(enabled: Boolean) {
        settingsManager.isContinuousSearchEnabled = enabled
        _continuousSearchEnabled.value = enabled
        meshNetworkManager.updateScanningState()
    }

    fun deleteAllMessages() {
        viewModelScope.launch {
            database.messageDao().deleteAllMessages()
        }
    }

    fun deleteAllPeers() {
        viewModelScope.launch {
            database.peerDao().deleteAllPeers()
        }
    }

    fun verifyPeer(peerId: String) {
        viewModelScope.launch {
            val peer = database.peerDao().getPeerById(peerId)
            if (peer != null) {
                database.peerDao().updatePeer(peer.copy(isVerified = true))
            }
        }
    }
}
