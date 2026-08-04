package `in`.inzamulhoque.meshtalk.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import `in`.inzamulhoque.meshtalk.data.local.entity.Peer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeerListPane(
    viewModel: HomeViewModel,
    onPeerClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val peers by viewModel.peers.collectAsState()
    var peerToDelete by remember { mutableStateOf<Peer?>(null) }

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
        }
    ) { innerPadding ->
        if (peers.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
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
                    "Searching for nearby Mesh Talk devices...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                items(peers, key = { it.id }) { peer ->
                    PeerItem(
                        peer = peer, 
                        onClick = { onPeerClick(peer.id) },
                        onLongClick = { peerToDelete = peer }
                    )
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
fun PeerItem(peer: Peer, onClick: () -> Unit, onLongClick: () -> Unit) {
    val isHandshaked = !peer.encryptionKey.isNullOrBlank()
    
    ListItem(
        headlineContent = { 
            Text(
                text = peer.displayName ?: "Unknown Peer",
                style = MaterialTheme.typography.titleMedium
            ) 
        },
        supportingContent = { 
            val statusText = if (isHandshaked) {
                peer.id.take(16) + "..."
            } else {
                "Connecting / Handshaking..."
            }
            Text(statusText) 
        },
        leadingContent = {
            Icon(
                Icons.Rounded.Person, 
                contentDescription = null,
                tint = if (isHandshaked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
        },
        modifier = Modifier
            .alpha(if (isHandshaked) 1f else 0.7f)
            .combinedClickable(
                onClick = { if (isHandshaked) onClick() },
                onLongClick = onLongClick
            )
    )
}
