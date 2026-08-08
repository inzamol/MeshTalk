package `in`.inzamulhoque.meshtalk.util

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("mesh_talk_settings", Context.MODE_PRIVATE)

    var displayName: String?
        get() = prefs.getString("display_name", null)
        set(value) = prefs.edit().putString("display_name", value).apply()

    var isNotificationEnabled: Boolean
        get() = prefs.getBoolean("notifications_enabled", true)
        set(value) = prefs.edit().putBoolean("notifications_enabled", value).apply()

    var isForwardingEnabled: Boolean
        get() = prefs.getBoolean("forwarding_enabled", true)
        set(value) = prefs.edit().putBoolean("forwarding_enabled", value).apply()

    var isConnectionToastEnabled: Boolean
        get() = prefs.getBoolean("connection_toast_enabled", true)
        set(value) = prefs.edit().putBoolean("connection_toast_enabled", value).apply()

    var bio: String?
        get() = prefs.getString("user_bio", null)
        set(value) = prefs.edit().putString("user_bio", value).apply()

    var avatarBase64: String?
        get() = prefs.getString("user_avatar", null)
        set(value) = prefs.edit().putString("user_avatar", value).apply()

    var isContinuousSearchEnabled: Boolean
        get() = prefs.getBoolean("continuous_search_enabled", true)
        set(value) = prefs.edit().putBoolean("continuous_search_enabled", value).apply()

    var isShowConnectingDevicesEnabled: Boolean
        get() = prefs.getBoolean("show_connecting_devices", true)
        set(value) = prefs.edit().putBoolean("show_connecting_devices", value).apply()

    var pruneOthersMessagesDays: Int
        get() = prefs.getInt("prune_others_days", 30)
        set(value) = prefs.edit().putInt("prune_others_days", value).apply()

    var isPruningOwnMessagesEnabled: Boolean
        get() = prefs.getBoolean("prune_own_enabled", false)
        set(value) = prefs.edit().putBoolean("prune_own_enabled", value).apply()

    var pruneOwnMessagesDays: Int
        get() = prefs.getInt("prune_own_days", 180)
        set(value) = prefs.edit().putInt("prune_own_days", value).apply()

    var isMovementSensingEnabled: Boolean
        get() = prefs.getBoolean("movement_sensing_enabled", true)
        set(value) = prefs.edit().putBoolean("movement_sensing_enabled", value).apply()
}
