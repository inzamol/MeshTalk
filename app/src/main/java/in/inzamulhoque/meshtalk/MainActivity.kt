package `in`.inzamulhoque.meshtalk

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import `in`.inzamulhoque.meshtalk.ble.MeshNetworkManager
import `in`.inzamulhoque.meshtalk.ui.MainScreen
import `in`.inzamulhoque.meshtalk.ui.theme.MeshTalkTheme

class MainActivity : ComponentActivity() {
    private lateinit var meshNetworkManager: MeshNetworkManager

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val app = application as MeshApplication
        meshNetworkManager = MeshNetworkManager(this, app.database, app.identityManager)
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

                LaunchedEffect(permissionState.allPermissionsGranted) {
                    if (permissionState.allPermissionsGranted) {
                        meshNetworkManager.start()
                    }
                }

                if (permissionState.allPermissionsGranted) {
                    MainScreen(myId = myId, app = app)
                } else {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
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
        if (::meshNetworkManager.isInitialized) {
            meshNetworkManager.stop()
        }
    }
}
