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
import androidx.compose.ui.unit.dp

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.util.Base64
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import coil.compose.AsyncImage
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip

import `in`.inzamulhoque.meshtalk.util.QRUtils

import androidx.camera.core.ExperimentalGetImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import `in`.inzamulhoque.meshtalk.util.update.UpdateState

@androidx.camera.core.ExperimentalGetImage
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPane(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onNavigateToChat: (String) -> Unit = {},
) {
    val displayName by viewModel.displayName.collectAsState()
    val bio by viewModel.bio.collectAsState()
    val avatarBase64 by viewModel.avatarBase64.collectAsState()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val continuousSearchEnabled by viewModel.continuousSearchEnabled.collectAsState()
    val forwardingEnabled by viewModel.forwardingEnabled.collectAsState()
    val connectionToastEnabled by viewModel.connectionToastEnabled.collectAsState()
    val showConnectingDevicesEnabled by viewModel.showConnectingDevicesEnabled.collectAsState()
    val movementSensingEnabled by viewModel.movementSensingEnabled.collectAsState()
    val pruneOthersDays by viewModel.pruneOthersDays.collectAsState()
    val pruneOwnEnabled by viewModel.pruneOwnEnabled.collectAsState()
    val pruneOwnDays by viewModel.pruneOwnDays.collectAsState()
    val isEditMode by viewModel.isEditMode.collectAsState()
    val publicShoutEnabled by viewModel.publicShoutEnabled.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    
    var showDeleteMessagesDialog by remember { mutableStateOf(false) }
    var showDeletePeersDialog by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }

    val myId = viewModel.myId
    val displayNameState by viewModel.displayName.collectAsState()
    val myQRBitmap = remember(myId, displayNameState) { 
        QRUtils.generateQRCode("mt:$myId:$displayNameState", 600) 
    }

    var tempName by remember { mutableStateOf(displayName) }
    var tempBio by remember { mutableStateOf(bio) }

    LaunchedEffect(displayName, bio) {
        tempName = displayName
        tempBio = bio
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val inputStream = context.contentResolver.openInputStream(it)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            val resized = Bitmap.createScaledBitmap(bitmap, 200, 200, true)
            val outputStream = ByteArrayOutputStream()
            resized.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            val base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
            viewModel.updateAvatar(base64)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile & Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!isEditMode) {
                        IconButton(onClick = { viewModel.setEditMode(true) }) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit Profile")
                        }
                    } else {
                        IconButton(onClick = { viewModel.saveProfile(tempName, tempBio) }) {
                            Icon(Icons.Rounded.Check, contentDescription = "Save")
                        }
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
            // QR Identity Card (1st Card)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Your Mesh Identity",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    if (myQRBitmap != null) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            AsyncImage(
                                model = myQRBitmap,
                                contentDescription = "My QR Code",
                                modifier = Modifier.size(240.dp).clip(MaterialTheme.shapes.medium)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        myId.take(32) + "...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Others can scan this to add you as a verified peer.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Profile Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .clickable(enabled = isEditMode) { avatarPicker.launch("image/*") }
                ) {
                    if (avatarBase64 != null) {
                        val bytes = Base64.decode(avatarBase64, Base64.DEFAULT)
                        AsyncImage(
                            model = bytes,
                            contentDescription = "Avatar",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(
                                if (isEditMode) Icons.Rounded.AddAPhoto else Icons.Rounded.Person,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp).padding(24.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column {
                    if (isEditMode) {
                        OutlinedTextField(
                            value = tempName,
                            onValueChange = { tempName = it },
                            label = { Text("Display Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    } else {
                        Text(
                            displayName.ifBlank { "New User" },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            bio.ifBlank { "No bio yet" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            if (isEditMode) {
                OutlinedTextField(
                    value = tempBio,
                    onValueChange = { tempBio = it },
                    label = { Text("Bio") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Tell us about yourself...") }
                )
                
                Button(
                    onClick = { viewModel.saveProfile(tempName, tempBio) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = tempName.isNotBlank()
                ) {
                    Text("Save Changes")
                }
                
                TextButton(onClick = { viewModel.setEditMode(false) }) {
                    Text("Cancel")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text("Verification", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                
                Button(
                    onClick = { showScanner = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                ) {
                    Icon(Icons.Rounded.QrCodeScanner, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Peer (Scan QR)")
                }

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
                    title = "Continuous Searching",
                    subtitle = "Keep scanning for new peers (Battery intensive)",
                    checked = continuousSearchEnabled,
                    onCheckedChange = { viewModel.setContinuousSearchEnabled(it) },
                    icon = Icons.Rounded.Search
                )

                SettingsToggleItem(
                    title = "Adaptive Scanning",
                    subtitle = "Search more frequently when moving, less when stationary",
                    checked = movementSensingEnabled,
                    onCheckedChange = { viewModel.setMovementSensingEnabled(it) },
                    icon = Icons.Rounded.Search
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

                SettingsToggleItem(
                    title = "Show connecting devices",
                    subtitle = "Show peers that are currently connecting in the list",
                    checked = showConnectingDevicesEnabled,
                    onCheckedChange = { viewModel.setShowConnectingDevicesEnabled(it) },
                    icon = Icons.Rounded.SwapHoriz
                )

                SettingsToggleItem(
                    title = "Public Shout",
                    subtitle = "Enable to send and receive broadcast messages nearby",
                    checked = publicShoutEnabled,
                    onCheckedChange = { viewModel.setPublicShoutEnabled(it) },
                    icon = Icons.Rounded.Campaign
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text("Message Pruning", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                
                Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text("Auto-delete others' messages after $pruneOthersDays days", style = MaterialTheme.typography.bodyLarge)
                    Text("Received messages will be deleted. Your own messages are kept unless specified below.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Slider(
                        value = pruneOthersDays.toFloat(),
                        onValueChange = { viewModel.setPruneOthersDays(it.toInt()) },
                        valueRange = 7f..365f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                SettingsToggleItem(
                    title = "Auto-delete my own messages",
                    subtitle = "Enable to prune your own old sent history",
                    checked = pruneOwnEnabled,
                    onCheckedChange = { viewModel.setPruneOwnEnabled(it) },
                    icon = Icons.Rounded.History
                )
                
                if (pruneOwnEnabled) {
                    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text("Delete my messages after $pruneOwnDays days", style = MaterialTheme.typography.bodyLarge)
                        Slider(
                            value = pruneOwnDays.toFloat(),
                            onValueChange = { viewModel.setPruneOwnDays(it.toInt()) },
                            valueRange = 30f..730f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text("App Update", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                
                when (val state = updateState) {
                    is UpdateState.Idle, UpdateState.NoUpdateAvailable -> {
                        Button(
                            onClick = { viewModel.checkForUpdates() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.SystemUpdate, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (state is UpdateState.NoUpdateAvailable) "Up to date (Check again)" else "Check for Updates")
                        }
                    }
                    is UpdateState.Checking -> {
                        Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Checking...")
                        }
                    }
                    is UpdateState.NewVersionAvailable -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("New version available: ${state.release.tagName}", fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(state.release.body.take(100) + "...", style = MaterialTheme.typography.bodySmall)
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { viewModel.downloadAndInstallUpdate(state.release) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Rounded.Download, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Download & Install")
                                }
                            }
                        }
                    }
                    is UpdateState.Downloading -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text("Downloading update...", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    is UpdateState.ReadyToInstall -> {
                        Button(
                            onClick = { 
                                viewModel.installApk(state.apkFile)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary, contentColor = MaterialTheme.colorScheme.onTertiary)
                        ) {
                            Icon(Icons.Rounded.InstallMobile, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Install Now")
                        }
                    }
                    is UpdateState.Error -> {
                        Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { viewModel.checkForUpdates() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Retry Check")
                        }
                    }
                }

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
                    "Mesh Talk v1.2.0",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }

    if (showScanner) {
        @androidx.camera.core.ExperimentalGetImage
        QRScannerScreen(
            onResult = { result ->
                val peerId = viewModel.verifyPeer(result)
                showScanner = false
                onNavigateToChat(peerId)
            },
            onDismiss = { showScanner = false }
        )
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
