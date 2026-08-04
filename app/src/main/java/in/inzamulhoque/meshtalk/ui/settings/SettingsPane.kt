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

@androidx.camera.core.ExperimentalGetImage
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPane(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val displayName by viewModel.displayName.collectAsState()
    val bio by viewModel.bio.collectAsState()
    val avatarBase64 by viewModel.avatarBase64.collectAsState()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val continuousSearchEnabled by viewModel.continuousSearchEnabled.collectAsState()
    val forwardingEnabled by viewModel.forwardingEnabled.collectAsState()
    val connectionToastEnabled by viewModel.connectionToastEnabled.collectAsState()
    
    var showDeleteMessagesDialog by remember { mutableStateOf(false) }
    var showDeletePeersDialog by remember { mutableStateOf(false) }
    var showQRCodeDialog by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }

    val myId = viewModel.myId
    val myQRBitmap = remember(myId) { QRUtils.generateQRCode(myId, 400) }

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
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .clickable { avatarPicker.launch("image/*") }
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
                            Icons.Rounded.AddAPhoto,
                            contentDescription = null,
                            modifier = Modifier
                                .size(40.dp)
                                .padding(24.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            
            Text(
                displayName.ifBlank { "New User" },
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

                OutlinedTextField(
                    value = bio,
                    onValueChange = { viewModel.updateBio(it) },
                    label = { Text("Bio") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                    placeholder = { Text("Tell us about yourself...") }
                )

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Trust & Identity", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    IconButton(onClick = { showQRCodeDialog = true }) {
                        Icon(Icons.Rounded.QrCode, contentDescription = "Show My QR")
                    }
                }

                Button(
                    onClick = { showScanner = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                ) {
                    Icon(Icons.Rounded.QrCodeScanner, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Verify Peer (Scan QR)")
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
                    "Mesh Talk v1.1.0",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }

    if (showQRCodeDialog) {
        AlertDialog(
            onDismissRequest = { showQRCodeDialog = false },
            title = { Text("My Mesh Identity") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Others can scan this to verify your identity.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(16.dp))
                    if (myQRBitmap != null) {
                        AsyncImage(
                            model = myQRBitmap,
                            contentDescription = "My QR Code",
                            modifier = Modifier.size(200.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(myId.take(32) + "...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            },
            confirmButton = {
                TextButton(onClick = { showQRCodeDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showScanner) {
        QRScannerDialog(
            onResult = { result ->
                viewModel.verifyPeer(result)
                showScanner = false
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
