package `in`.inzamulhoque.meshtalk

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.location.LocationManager
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
                val permissions = mutableListOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                }

                val permissionState = rememberMultiplePermissionsState(permissions)

                var isBluetoothEnabled by remember { mutableStateOf(true) }
                var isLocationEnabled by remember { mutableStateOf(true) }

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
                    Log.d("MainActivity", "Permissions: ${permissionState.allPermissionsGranted}, BT: $isBluetoothEnabled, Loc: $isLocationEnabled")
                    if (permissionState.allPermissionsGranted && isBluetoothEnabled && isLocationEnabled) {
                        Log.d("MainActivity", "Starting MeshNetworkManager")
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
                                            startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
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
                        MainScreen(myId = myId, app = app)
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
                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                        ) {
                            Text("Mesh Talk requires permissions to function.")
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { permissionState.launchMultiplePermissionRequest() }) {
                                Text("Grant Permissions")
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
