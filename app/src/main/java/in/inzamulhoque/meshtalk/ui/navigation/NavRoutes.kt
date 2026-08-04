package `in`.inzamulhoque.meshtalk.ui.navigation

import android.os.Parcelable
import androidx.navigation3.runtime.NavKey
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
sealed interface NavRoute : NavKey, Parcelable {
    @Serializable
    @Parcelize
    data object Home : NavRoute

    @Serializable
    @Parcelize
    data class Chat(val peerId: String) : NavRoute
}
