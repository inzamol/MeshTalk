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
}
