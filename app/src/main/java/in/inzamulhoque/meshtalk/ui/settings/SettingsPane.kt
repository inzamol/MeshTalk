package `in`.inzamulhoque.meshtalk.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPane(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val displayName by viewModel.displayName.collectAsState()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val forwardingEnabled by viewModel.forwardingEnabled.collectAsState()
    val connectionToastEnabled by viewModel.connectionToastEnabled.collectAsState()
    
    var showDeleteMessagesDialog by remember { mutableStateOf(false) }
    var showDeletePeersDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = androidx.compose.ui.res.painterResource(id = `in`.inzamulhoque.meshtalk.R.drawable.ic_mesh_logo),
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = Color.Unspecified
            )
            
            Text(
                "Mesh Talk",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text("Profile", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { viewModel.updateDisplayName(it) },
                    label = { Text("Display Name") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) }
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text("Network & Notifications", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

                SettingsToggleItem(
                    title = "Show Notifications",
                    subtitle = "Alert me when new messages arrive",
                    checked = notificationsEnabled,
                    onCheckedChange = { viewModel.setNotificationsEnabled(it) },
                    icon = Icons.Rounded.Notifications
                )

                SettingsToggleItem(
                    title = "Message Forwarding",
                    subtitle = "Help the mesh by forwarding encrypted messages for others",
                    checked = forwardingEnabled,
                    onCheckedChange = { viewModel.setForwardingEnabled(it) },
                    icon = Icons.Rounded.Share
                )

                SettingsToggleItem(
                    title = "Connection Status Toasts",
                    subtitle = "Show popups when peers connect or disconnect",
                    checked = connectionToastEnabled,
                    onCheckedChange = { viewModel.setConnectionToastEnabled(it) },
                    icon = Icons.Rounded.Info
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text("Data Management", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)

                Button(
                    onClick = { showDeleteMessagesDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete All Messages")
                }

                Button(
                    onClick = { showDeletePeersDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete All Devices")
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "Mesh Talk v1.0.0",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }

    if (showDeleteMessagesDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteMessagesDialog = false },
            title = { Text("Delete All Messages") },
            text = { Text("Are you sure you want to delete all messages? This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAllMessages()
                    showDeleteMessagesDialog = false
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteMessagesDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeletePeersDialog) {
        AlertDialog(
            onDismissRequest = { showDeletePeersDialog = false },
            title = { Text("Delete All Devices") },
            text = { Text("Are you sure you want to delete all discovered devices?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAllPeers()
                    showDeletePeersDialog = false
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeletePeersDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SettingsToggleItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
