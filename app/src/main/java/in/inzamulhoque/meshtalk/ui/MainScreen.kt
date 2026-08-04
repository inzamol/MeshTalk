package `in`.inzamulhoque.meshtalk.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.rememberNavBackStack
import `in`.inzamulhoque.meshtalk.MeshApplication
import `in`.inzamulhoque.meshtalk.ui.chat.ChatPane
import `in`.inzamulhoque.meshtalk.ui.chat.ChatViewModel
import `in`.inzamulhoque.meshtalk.ui.home.HomeViewModel
import `in`.inzamulhoque.meshtalk.ui.home.PeerListPane
import `in`.inzamulhoque.meshtalk.ui.navigation.NavRoute
import `in`.inzamulhoque.meshtalk.ui.settings.SettingsPane
import `in`.inzamulhoque.meshtalk.ui.settings.SettingsViewModel
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun MainScreen(
    myId: String,
    app: MeshApplication,
    initialPeerId: String? = null
) {
    val scaffoldDirective = calculatePaneScaffoldDirective(currentWindowAdaptiveInfo())
    val navigator = rememberListDetailPaneScaffoldNavigator<NavRoute>(scaffoldDirective)
    val coroutineScope = rememberCoroutineScope()

    BackHandler(navigator.canNavigateBack()) {
        coroutineScope.launch {
            navigator.navigateBack()
        }
    }

    LaunchedEffect(initialPeerId) {
        if (initialPeerId != null) {
            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, NavRoute.Chat(initialPeerId))
        }
    }

    ListDetailPaneScaffold(
        modifier = Modifier.fillMaxSize(),
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            AnimatedPane(modifier = Modifier.fillMaxSize()) {
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
                    },
                    onSettingsClick = {
                        coroutineScope.launch {
                            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, NavRoute.Settings)
                        }
                    }
                )
            }
        },
        detailPane = {
            AnimatedPane(modifier = Modifier.fillMaxSize()) {
                val currentRoute = navigator.currentDestination?.contentKey
                when (currentRoute) {
                    is NavRoute.Chat -> {
                        val chatViewModel: ChatViewModel = viewModel(
                            key = currentRoute.peerId,
                            factory = object : ViewModelProvider.Factory {
                                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                    return ChatViewModel(
                                        application = app,
                                        meshNetworkManager = app.meshNetworkManager,
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
                    is NavRoute.Settings -> {
                        val settingsViewModel: SettingsViewModel = viewModel(
                            factory = object : ViewModelProvider.Factory {
                                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                    return SettingsViewModel(
                                        database = app.database,
                                        settingsManager = app.settingsManager
                                    ) as T
                                }
                            }
                        )
                        SettingsPane(
                            viewModel = settingsViewModel,
                            onBack = {
                                coroutineScope.launch {
                                    navigator.navigateBack()
                                }
                            }
                        )
                    }
                    else -> {
                        Box(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    )
}
