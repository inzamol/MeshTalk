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
    private val settingsManager: SettingsManager
) : ViewModel() {

    private val _displayName = MutableStateFlow(settingsManager.displayName ?: "")
    val displayName = _displayName.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(settingsManager.isNotificationEnabled)
    val notificationsEnabled = _notificationsEnabled.asStateFlow()

    private val _forwardingEnabled = MutableStateFlow(settingsManager.isForwardingEnabled)
    val forwardingEnabled = _forwardingEnabled.asStateFlow()

    private val _connectionToastEnabled = MutableStateFlow(settingsManager.isConnectionToastEnabled)
    val connectionToastEnabled = _connectionToastEnabled.asStateFlow()

    fun updateDisplayName(name: String) {
        settingsManager.displayName = name
        _displayName.value = name
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
}
