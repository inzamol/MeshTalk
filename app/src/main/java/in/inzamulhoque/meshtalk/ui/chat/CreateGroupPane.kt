package `in`.inzamulhoque.meshtalk.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `in`.inzamulhoque.meshtalk.data.local.entity.Peer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupPane(
    peers: List<Peer>,
    onCreateGroup: (String, List<String>) -> Unit,
    onBack: () -> Unit
) {
    var groupName by remember { mutableStateOf("") }
    val selectedPeers = remember { mutableStateListOf<String>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Group") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (groupName.isNotBlank() && selectedPeers.isNotEmpty()) {
                        IconButton(onClick = { onCreateGroup(groupName, selectedPeers.toList()) }) {
                            Icon(Icons.Rounded.Check, contentDescription = "Create")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
            OutlinedTextField(
                value = groupName,
                onValueChange = { groupName = it },
                label = { Text("Group Name") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Rounded.GroupAdd, contentDescription = null) }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            Text("Select Members", style = MaterialTheme.typography.titleMedium)
            
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(peers.filter { !it.encryptionKey.isNullOrBlank() && it.id != "shout_channel" }) { peer ->
                    val isSelected = selectedPeers.contains(peer.id)
                    ListItem(
                        headlineContent = { Text(peer.displayName ?: "Unknown") },
                        trailingContent = {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { 
                                    if (it) selectedPeers.add(peer.id) 
                                    else selectedPeers.remove(peer.id) 
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}
