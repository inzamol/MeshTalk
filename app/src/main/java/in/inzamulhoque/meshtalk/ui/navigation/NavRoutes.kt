package `in`.inzamulhoque.meshtalk.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface NavRoute : NavKey {
    @Serializable
    data object Home : NavRoute

    @Serializable
    data class Chat(val peerId: String) : NavRoute
}
