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
import android.util.Base64
import coil.compose.AsyncImage
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeerListPane(
    viewModel: HomeViewModel,
    onPeerClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onCreateGroupClick: () -> Unit,
    onRefresh: () -> Unit,
    onAddPeerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val peers by viewModel.peers.collectAsState()
    val showConnectingDevices by viewModel.showConnectingDevices.collectAsState()
    val activePeerAddresses by viewModel.activePeerAddresses.collectAsState()
    var peerToDelete by remember { mutableStateOf<Peer?>(null) }
    
    LaunchedEffect(Unit) {
        viewModel.refreshSettings()
    }
    var isRefreshing by remember { mutableStateOf(false) }
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
                }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                FloatingActionButton(
                    onClick = onCreateGroupClick,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(bottom = 8.dp)
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
                    kotlinx.coroutines.delay(2000)
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
                    items(peers, key = { it.deviceAddress ?: it.id }) { peer ->
                        PeerItem(
                            peer = peer,
                            showConnectingDevices = showConnectingDevices,
                            isActive = activePeerAddresses.contains(peer.deviceAddress),
                            onClick = { onPeerClick(peer.id) },
                            onLongClick = { peerToDelete = peer }
                        )
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
                TextButton(onClick = {
                    peerToDelete?.let { viewModel.deletePeer(it) }
                    peerToDelete = null
                }) {
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
fun PeerItem(peer: Peer, showConnectingDevices: Boolean, isActive: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val isHandshaked = !peer.encryptionKey.isNullOrBlank()
    val displayName = if (!isHandshaked && !showConnectingDevices && (peer.displayName == "Connecting..." || peer.displayName == "Mesh Peer")) {
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
                    modifier = Modifier.weight(1f)
                )
                if (peer.isVerified) {
                    Icon(
                        Icons.Rounded.Verified,
                        contentDescription = "Verified",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
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
            val statusText = if (isHandshaked) {
                peer.bio ?: (peer.id.take(16) + "...")
            } else if (showConnectingDevices) {
                "Connecting / Handshaking..."
            } else {
                peer.id.take(16) + "..."
            }
            Text(
                statusText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            ) 
        },
        leadingContent = {
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                if (peer.avatarUri != null) {
                    val model = if (peer.avatarUri.startsWith("/")) {
                        peer.avatarUri
                    } else {
                        try { Base64.decode(peer.avatarUri, Base64.DEFAULT) } catch (e: Exception) { null }
                    }
                    AsyncImage(
                        model = model,
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
                
                if (!isHandshaked) {
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
        modifier = Modifier
            .alpha(if (isHandshaked) 1f else 1f) // Keep full alpha to see the indicator clearly
            .combinedClickable(
                onClick = { if (isHandshaked) onClick() },
                onLongClick = onLongClick
            )
    )
}
