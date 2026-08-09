package `in`.inzamulhoque.meshtalk

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import `in`.inzamulhoque.meshtalk.ble.MeshNetworkManager
import `in`.inzamulhoque.meshtalk.service.MeshForegroundService
import `in`.inzamulhoque.meshtalk.ui.MainScreen
import `in`.inzamulhoque.meshtalk.ui.theme.MeshTalkTheme
import `in`.inzamulhoque.meshtalk.util.PermissionUtils
import `in`.inzamulhoque.meshtalk.util.NotificationHelper
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val app = application as MeshApplication
        
        enableEdgeToEdge()
        setContent {
            MeshTalkTheme {
                val initError = app.initializationError
                if (initError != null) {
                    FatalErrorScreen(error = initError)
                    return@MeshTalkTheme
                }

                val myId = app.identityManager.getMyId()
                val permissions = PermissionUtils.getRequiredPermissions()
                val permissionState = rememberMultiplePermissionsState(permissions)

                var initialPeerId by remember { mutableStateOf<String?>(null) }

                val bluetoothManager = remember { getSystemService(BLUETOOTH_SERVICE) as BluetoothManager }
                val locationManager = remember { getSystemService(LOCATION_SERVICE) as LocationManager }

                var isBluetoothEnabled by remember {
                    mutableStateOf(bluetoothManager.adapter?.isEnabled == true)
                }
                var isLocationEnabled by remember {
                    mutableStateOf(
                        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                    )
                }

                DisposableEffect(Unit) {
                    val receiver = object : android.content.BroadcastReceiver() {
                        override fun onReceive(context: Context?, intent: Intent?) {
                            when (intent?.action) {
                                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                                    isBluetoothEnabled = state == BluetoothAdapter.STATE_ON
                                }
                                LocationManager.MODE_CHANGED_ACTION -> {
                                    isLocationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                                            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                                }
                            }
                        }
                    }

                    val filter = IntentFilter().apply {
                        addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                        addAction(LocationManager.MODE_CHANGED_ACTION)
                    }
                    registerReceiver(receiver, filter)

                    onDispose {
                        unregisterReceiver(receiver)
                    }
                }

                LaunchedEffect(intent) {
                    intent.getStringExtra(NotificationHelper.EXTRA_PEER_ID)?.let {
                        initialPeerId = it
                    }
                }

                LaunchedEffect(permissionState.allPermissionsGranted, isBluetoothEnabled) {
                    if (permissionState.allPermissionsGranted && isBluetoothEnabled) {
                        MeshForegroundService.start(this@MainActivity)
                    }
                }

                if (permissionState.allPermissionsGranted) {
                    if (!isBluetoothEnabled) {
                        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                            Column(
                                modifier = Modifier.fillMaxSize().padding(innerPadding),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Bluetooth must be enabled.")
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = {
                                    try {
                                        @SuppressLint("MissingPermission")
                                        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                                        startActivity(intent)
                                    } catch (e: Exception) {
                                        Log.e("MainActivity", "Failed to start settings intent", e)
                                    } catch (_: SecurityException) {}
                                }) {
                                    Text("Enable Bluetooth")
                                }
                            }
                        }
                    } else {
                        MainScreen(myId = myId, app = app, initialPeerId = initialPeerId)
                    }
                } else {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        contentWindowInsets = WindowInsets(0),
                    ) { innerPadding ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val textToShow = if (permissionState.shouldShowRationale) {
                                "Mesh Talk needs Bluetooth and Location permissions to discover nearby peers."
                            } else {
                                "Mesh Talk requires permissions to function. Please grant them in settings if the dialog doesn't appear."
                            }
                            Text(textToShow, modifier = Modifier.padding(horizontal = 32.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Spacer(modifier = Modifier.height(16.dp))
                            if (permissionState.shouldShowRationale || !permissionState.allPermissionsGranted) {
                                Button(onClick = { permissionState.launchMultiplePermissionRequest() }) {
                                    Text("Grant Permissions")
                                }
                            }
                            
                            if (!permissionState.shouldShowRationale && !permissionState.allPermissionsGranted) {
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(
                                    onClick = {
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.fromParts("package", packageName, null)
                                        }
                                        startActivity(intent)
                                    }
                                ) {
                                    Text("Open Settings")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        (application as? MeshApplication)?.meshNetworkManager?.onActivityDetected()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}

@Composable
fun FatalErrorScreen(error: Throwable) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Fatal Initialization Error",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "The application failed to start correctly. This usually happens due to issues with the Android Keystore or Database.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(24.dp))
            Surface(
                color = Color.Black.copy(alpha = 0.1f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = Log.getStackTraceString(error),
                    modifier = Modifier.padding(16.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
