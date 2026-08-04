package `in`.inzamulhoque.meshtalk

import android.annotation.SuppressLint
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import `in`.inzamulhoque.meshtalk.ble.MeshNetworkManager
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
        val myId = app.identityManager.getMyId()

        enableEdgeToEdge()
        setContent {
            MeshTalkTheme {
                val permissions = PermissionUtils.getRequiredPermissions()
                val permissionState = rememberMultiplePermissionsState(permissions)

                var isBluetoothEnabled by remember { mutableStateOf(true) }
                var isLocationEnabled by remember { mutableStateOf(true) }
                var initialPeerId by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(intent) {
                    val peerId = intent.getStringExtra(NotificationHelper.EXTRA_PEER_ID)
                    if (peerId != null) {
                        initialPeerId = peerId
                    }
                }

                LaunchedEffect(Unit) {
                    val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                    val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
                    
                    while(true) {
                        isBluetoothEnabled = bluetoothManager.adapter?.isEnabled == true
                        isLocationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                                            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                        delay(2000)
                    }
                }

                LaunchedEffect(permissionState.allPermissionsGranted, isBluetoothEnabled, isLocationEnabled) {
                    if (permissionState.allPermissionsGranted && isBluetoothEnabled && isLocationEnabled) {
                        (application as MeshApplication).meshNetworkManager.start()
                    }
                }

                if (permissionState.allPermissionsGranted) {
                    if (!isBluetoothEnabled || !isLocationEnabled) {
                        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                            Column(
                                modifier = Modifier.fillMaxSize().padding(innerPadding),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Bluetooth and Location services must be enabled.")
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = {
                                    try {
                                        if (!isBluetoothEnabled) {
                                            @SuppressLint("MissingPermission")
                                            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                                            startActivity(intent)
                                        } else {
                                            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                        }
                                    } catch (e: Exception) {
                                        Log.e("MainActivity", "Failed to start settings intent", e)
                                    }
                                }) {
                                    Text("Enable Services")
                                }
                            }
                        }
                    } else {
                        MainScreen(myId = myId, app = app, initialPeerId = initialPeerId)
                    }
                } else {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        contentWindowInsets = WindowInsets(0)
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
                                TextButton(onClick = {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", packageName, null)
                                    }
                                    startActivity(intent)
                                }) {
                                    Text("Open Settings")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        (application as? MeshApplication)?.meshNetworkManager?.stop()
    }
}
