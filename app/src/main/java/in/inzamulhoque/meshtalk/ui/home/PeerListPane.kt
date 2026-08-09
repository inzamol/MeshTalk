package `in`.inzamulhoque.meshtalk.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import `in`.inzamulhoque.meshtalk.data.local.entity.Peer
import `in`.inzamulhoque.meshtalk.protocol.MeshProtocol
import android.util.Base64
import coil.compose.AsyncImage
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeerListPane(
    viewModel: HomeViewModel,
    onPeerClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onCreateGroupClick: () -> Unit,
    onRefresh: () -> Unit,
    onAddPeerClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val peers by viewModel.peers.collectAsState()
    val showConnectingDevices by viewModel.showConnectingDevices.collectAsState()
    val activePeerAddresses by viewModel.activePeerAddresses.collectAsState()
    var peerToDelete by remember { mutableStateOf<Peer?>(null) }
    
    LaunchedEffect(Unit) {
        viewModel.refreshSettings()
    }
    var isRefreshing by remember { mutableStateOf(value = false) }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Mesh Talk") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                FloatingActionButton(
                    onClick = onCreateGroupClick,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    Icon(Icons.Rounded.GroupAdd, contentDescription = "Create Group")
                }
                
                ExtendedFloatingActionButton(
                    onClick = onAddPeerClick,
                    icon = { Icon(Icons.Rounded.QrCodeScanner, contentDescription = null) },
                    text = { Text("Add Peer") }
                )
            }
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                coroutineScope.launch {
                    isRefreshing = true
                    onRefresh()
                    kotlinx.coroutines.delay(2000.milliseconds)
                    isRefreshing = false
                }
            },
            modifier = Modifier.padding(innerPadding)
        ) {
            if (peers.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(id = `in`.inzamulhoque.meshtalk.R.drawable.ic_mesh_logo),
                        contentDescription = null,
                        modifier = Modifier.size(120.dp),
                        tint = Color.Unspecified
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No peers discovered yet.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        "Pull down to search nearby...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(peers, key = { it.peer.deviceAddress ?: it.peer.id }) { model ->
                        PeerItem(
                            model = model,
                            showConnectingDevices = showConnectingDevices,
                            isActive = activePeerAddresses.contains(model.peer.deviceAddress),
                            onClick = { onPeerClick(model.peer.id) },
                        ) {
                            peerToDelete = model.peer
                        }
                    }
                }
            }
        }
    }

    if (peerToDelete != null) {
        AlertDialog(
            onDismissRequest = { peerToDelete = null },
            title = { Text("Delete Device") },
            text = { Text("Remove ${peerToDelete?.displayName ?: "this device"} from your list?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        peerToDelete?.let { viewModel.deletePeer(it) }
                        peerToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { peerToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PeerItem(model: PeerUiModel, showConnectingDevices: Boolean, isActive: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val peer = model.peer
    val isHandshaked = !peer.encryptionKey.isNullOrBlank()
    val hasUnread = model.unreadCount > 0
    val displayName = if ((!isHandshaked && !showConnectingDevices) && ((peer.displayName == "Connecting...") || (peer.displayName == "Mesh Peer"))) {
        "Mesh Peer"
    } else {
        peer.displayName ?: "Unknown Peer"
    }
    
    ListItem(
        headlineContent = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (peer.isVerified) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Rounded.Verified,
                        contentDescription = "Verified",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (isHandshaked) {
                    val rssiIcon = when {
                        peer.rssi > -60 -> Icons.Rounded.SignalCellularAlt
                        peer.rssi > -80 -> Icons.Rounded.SignalCellularAlt2Bar
                        else -> Icons.Rounded.SignalCellularAlt1Bar
                    }
                    Icon(
                        rssiIcon, 
                        contentDescription = null, 
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                }
            }
        },
        supportingContent = { 
            val statusText = model.lastMessage
                ?: if (isHandshaked) {
                    peer.bio ?: (peer.id.take(16) + "...")
                } else if (showConnectingDevices) {
                    "Connecting / Handshaking..."
                } else {
                    peer.id.take(16) + "..."
                }
            Text(
                statusText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (hasUnread) FontWeight.SemiBold else FontWeight.Normal,
                color = if (hasUnread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
            ) 
        },
        leadingContent = {
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                if (peer.avatarUri != null) {
                    val avatarModel = if (peer.avatarUri.startsWith("/")) {
                        peer.avatarUri
                    } else {
                        try { Base64.decode(peer.avatarUri, Base64.DEFAULT) } catch (_: Exception) { null }
                    }
                    AsyncImage(
                        model = avatarModel,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp).clip(CircleShape),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Rounded.Person, 
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (isHandshaked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                }
                
                if ((!isHandshaked) && (peer.id != MeshProtocol.PUBLIC_GROUP_ID)) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (isActive) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .align(Alignment.BottomEnd)
                            .background(Color(0xFF4CAF50), CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    )
                }
            }
        },
        trailingContent = {
            if (hasUnread) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Text(model.unreadCount.toString())
                }
            }
        },
        modifier = Modifier
            .alpha(if (isHandshaked) 1f else 1f) 
            .combinedClickable(
                onClick = { if (isHandshaked) onClick() },
                onLongClick = onLongClick
            )
    )
}
