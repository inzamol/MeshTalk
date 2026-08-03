package `in`.inzamulhoque.meshtalk.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.runtime.rememberNavBackStack
import `in`.inzamulhoque.meshtalk.MeshApplication
import `in`.inzamulhoque.meshtalk.ui.chat.ChatPane
import `in`.inzamulhoque.meshtalk.ui.chat.ChatViewModel
import `in`.inzamulhoque.meshtalk.ui.home.HomeViewModel
import `in`.inzamulhoque.meshtalk.ui.home.PeerListPane
import `in`.inzamulhoque.meshtalk.ui.navigation.NavRoute
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun MainScreen(
    myId: String,
    app: MeshApplication
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<NavRoute>()
    val backstack = rememberNavBackStack(NavRoute.Home)
    val coroutineScope = rememberCoroutineScope()

    ListDetailPaneScaffold(
        modifier = Modifier.fillMaxSize(),
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            val homeViewModel: HomeViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return HomeViewModel(app.database.peerDao()) as T
                    }
                }
            )
            PeerListPane(
                viewModel = homeViewModel,
                onPeerClick = { peerId ->
                    coroutineScope.launch {
                        navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, NavRoute.Chat(peerId))
                    }
                }
            )
        },
        detailPane = {
            val currentRoute = navigator.currentDestination?.contentKey
            if (currentRoute is NavRoute.Chat) {
                val chatViewModel: ChatViewModel = viewModel(
                    key = currentRoute.peerId,
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return ChatViewModel(
                                peerId = currentRoute.peerId,
                                myId = myId,
                                messageDao = app.database.messageDao(),
                                peerDao = app.database.peerDao(),
                                identityManager = app.identityManager
                            ) as T
                        }
                    }
                )
                ChatPane(
                    viewModel = chatViewModel,
                    myId = myId,
                    onBack = {
                        coroutineScope.launch {
                            navigator.navigateBack()
                        }
                    },
                    isTwoPane = navigator.scaffoldDirective.maxHorizontalPartitions > 1
                )
            }
        }
    )
}
