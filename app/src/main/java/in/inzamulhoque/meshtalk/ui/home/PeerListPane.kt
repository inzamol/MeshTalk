package `in`.inzamulhoque.meshtalk.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `in`.inzamulhoque.meshtalk.data.local.entity.Peer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeerListPane(
    viewModel: HomeViewModel,
    onPeerClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val peers by viewModel.peers.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Mesh Talk") }
            )
        }
    ) { innerPadding ->
        if (peers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("No peers discovered yet.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                items(peers) { peer ->
                    PeerItem(peer = peer, onClick = { onPeerClick(peer.id) })
                }
            }
        }
    }
}

@Composable
fun PeerItem(peer: Peer, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(peer.displayName ?: "Unknown Peer") },
        supportingContent = { Text(peer.id.take(16) + "...") },
        leadingContent = {
            Icon(Icons.Rounded.Person, contentDescription = null)
        },
        modifier = Modifier.clickable { onClick() }
    )
}
