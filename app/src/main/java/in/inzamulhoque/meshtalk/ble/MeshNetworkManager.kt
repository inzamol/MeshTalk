package `in`.inzamulhoque.meshtalk.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import `in`.inzamulhoque.meshtalk.crypto.IdentityManager
import `in`.inzamulhoque.meshtalk.data.local.AppDatabase
import `in`.inzamulhoque.meshtalk.protocol.MeshProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class MeshNetworkManager(
    private val context: Context,
    private val database: AppDatabase,
    private val identityManager: IdentityManager
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    
    private val protocol = MeshProtocol(
        myId = identityManager.getMyId(),
        messageDao = database.messageDao(),
        peerDao = database.peerDao()
    )

    private val bleManager = bluetoothAdapter?.let { adapter ->
        MeshBLEManager(adapter) { deviceAddress ->
            connectToPeer(deviceAddress)
        }
    }

    private val gattServer = MeshGattServer(
        context,
        bluetoothManager,
        protocol,
        identityManager.getMyId(),
        identityManager.getMyEncryptionKey(),
        identityManager.getDisplayName()
    )

    private val scope = CoroutineScope(Dispatchers.IO)
    private val activeClients = mutableMapOf<String, MeshGattClient>()

    fun start() {
        Log.d("MeshNetworkManager", "Starting MeshNetworkManager")
        if (bluetoothAdapter == null) {
            Log.e("MeshNetworkManager", "BluetoothAdapter is null!")
            return
        }
        if (!bluetoothAdapter.isEnabled) {
            Log.w("MeshNetworkManager", "Bluetooth is disabled!")
            return
        }

        bleManager?.startAdvertising()
        bleManager?.startScanning()
        gattServer.start()
    }

    fun stop() {
        Log.d("MeshNetworkManager", "Stopping MeshNetworkManager")
        bleManager?.stopAdvertising()
        bleManager?.stopScanning()
        gattServer.stop()
        activeClients.values.forEach { it.disconnect() }
        activeClients.clear()
    }

    private fun connectToPeer(deviceAddress: String) {
        if (activeClients.containsKey(deviceAddress)) return

        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress) ?: return
        val client = MeshGattClient(context, device, protocol) {
            activeClients.remove(deviceAddress)
        }
        activeClients[deviceAddress] = client
        client.connect()
    }
}
