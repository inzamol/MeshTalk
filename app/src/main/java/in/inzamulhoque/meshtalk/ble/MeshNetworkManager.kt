package `in`.inzamulhoque.meshtalk.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import `in`.inzamulhoque.meshtalk.crypto.IdentityManager
import `in`.inzamulhoque.meshtalk.util.ToastHelper
import `in`.inzamulhoque.meshtalk.data.local.AppDatabase
import `in`.inzamulhoque.meshtalk.protocol.MeshProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MeshNetworkManager(
    private val context: Context,
    private val database: AppDatabase,
    private val identityManager: IdentityManager
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    
    private val protocol = MeshProtocol(
        context = context,
        myId = identityManager.getMyId(),
        messageDao = database.messageDao(),
        peerDao = database.peerDao()
    )

    private val myShortId = String.format("%08x", identityManager.getMyId().hashCode()).toByteArray()

    private val bleManager = bluetoothAdapter?.let { adapter ->
        MeshBLEManager(context, adapter, myShortId) { deviceAddress, peerShortId ->
            onPeerSeen(deviceAddress, peerShortId)
        }
    }

    private var myDisplayName = identityManager.getDisplayName()

    private val gattServer = MeshGattServer(
        context,
        bluetoothManager,
        protocol,
        identityManager.getMyId(),
        identityManager.getMyEncryptionKey(),
        { myDisplayName },
        { this }
    )

    fun getMyEncryptionKey() = identityManager.getMyEncryptionKey()
    fun getMyDisplayName() = myDisplayName

    private val scope = CoroutineScope(Dispatchers.IO)
    private val activeClients = mutableMapOf<String, MeshGattClient>()
    private val connectionCooldowns = mutableMapOf<String, Long>()
    private var autoReconnectJob: kotlinx.coroutines.Job? = null

    fun start() {
        Log.d("MeshNetworkManager", "Starting MeshNetworkManager")
        myDisplayName = identityManager.getDisplayName() // Refresh after permissions
        
        if (bluetoothAdapter == null) {
            Log.e("MeshNetworkManager", "BluetoothAdapter is null!")
            return
        }
        if (!bluetoothAdapter.isEnabled) {
            Log.w("MeshNetworkManager", "Bluetooth is disabled!")
            ToastHelper.showToast(context, "Please enable Bluetooth to start mesh network")
            return
        }

        bleManager?.startAdvertising()
        bleManager?.startScanning()
        gattServer.start()
        startAutoReconnectLoop()
    }

    private fun startAutoReconnectLoop() {
        autoReconnectJob?.cancel()
        autoReconnectJob = scope.launch {
            while (true) {
                delay(10000) // Every 10 seconds
                val peers = database.peerDao().getAllPeersSync()
                val now = System.currentTimeMillis()
                peers.forEach { peer ->
                    if (peer.deviceAddress != null && now - peer.lastSeen < 60000) { // Seen in last minute
                        if (!activeClients.containsKey(peer.deviceAddress)) {
                            // Basic election: smaller ID initiates
                            if (identityManager.getMyId() < peer.id) {
                                connectToPeer(peer.deviceAddress)
                            }
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        Log.d("MeshNetworkManager", "Stopping MeshNetworkManager")
        autoReconnectJob?.cancel()
        bleManager?.stopAdvertising()
        bleManager?.stopScanning()
        gattServer.stop()
        activeClients.values.forEach { it.disconnect() }
        activeClients.clear()
    }

    fun connectToPeerById(peerId: String) {
        scope.launch {
            val peer = database.peerDao().getPeerById(peerId)
            val address = peer?.deviceAddress
            if (address != null) {
                Log.d("MeshNetworkManager", "Proactive connection attempt to $peerId at $address")
                connectToPeer(address, force = true)
            } else {
                Log.w("MeshNetworkManager", "Cannot connect to $peerId: last known address is null")
            }
        }
    }

    private fun onPeerSeen(deviceAddress: String, peerShortId: ByteArray) {
        scope.launch {
            val existingPeerByAddress = database.peerDao().getPeerByAddress(deviceAddress)
            
            if (existingPeerByAddress == null) {
                // Immediate visibility: Insert placeholder if new
                protocol.onPeerDiscovered(
                    peerId = deviceAddress, // Use MAC as temporary ID
                    publicKey = "",
                    encryptionKey = null,
                    displayName = "Connecting...",
                    deviceAddress = deviceAddress
                )
            } else if (System.currentTimeMillis() - existingPeerByAddress.lastSeen > 10000) {
                // Throttled update to avoid database hammering
                database.peerDao().updatePeer(existingPeerByAddress.copy(lastSeen = System.currentTimeMillis()))
            }
            
            // Election: only connect if my IDHash <= peerIdHash
            // Using a hash-based ID comparison ensures stable roles and tie-breaking
            val myIdHash = java.lang.Long.parseLong(String.format("%08x", identityManager.getMyId().hashCode()), 16)
            val peerIdHashStr = if (peerShortId.isNotEmpty()) String(peerShortId) else ""
            val peerIdHash = try { java.lang.Long.parseLong(peerIdHashStr, 16) } catch (e: Exception) { 0L }
            
            if (peerIdHash == 0L || myIdHash <= peerIdHash) {
                Log.d("MeshNetworkManager", "Election won/unknown ($myIdHash vs $peerIdHash). Connecting to $deviceAddress")
                connectToPeer(deviceAddress)
            } else {
                Log.d("MeshNetworkManager", "Election lost ($myIdHash vs $peerIdHash). Waiting for $deviceAddress")
            }
        }
    }

    fun connectToPeer(deviceAddress: String, force: Boolean = false) {
        if (activeClients.containsKey(deviceAddress)) return
        
        val lastConnect = connectionCooldowns[deviceAddress] ?: 0
        val now = System.currentTimeMillis()
        if (!force && now - lastConnect < 30000) { // 30 seconds cooldown
            return
        }

        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress) ?: return
        connectionCooldowns[deviceAddress] = now
        
        val client = MeshGattClient(
            context = context,
            device = device,
            protocol = protocol,
            myEncryptionKey = identityManager.getMyEncryptionKey(),
            myDisplayName = identityManager.getDisplayName(),
            onSyncComplete = {
                activeClients.remove(deviceAddress)
            }
        )
        activeClients[deviceAddress] = client
        client.connect()
    }
}
