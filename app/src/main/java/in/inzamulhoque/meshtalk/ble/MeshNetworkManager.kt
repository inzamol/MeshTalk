package `in`.inzamulhoque.meshtalk.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import `in`.inzamulhoque.meshtalk.crypto.IdentityManager
import `in`.inzamulhoque.meshtalk.data.local.AppDatabase
import `in`.inzamulhoque.meshtalk.protocol.MeshProtocol
import `in`.inzamulhoque.meshtalk.data.local.entity.Message
import `in`.inzamulhoque.meshtalk.data.local.entity.MessageStatus
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
        peerDao = database.peerDao(),
        identityManager = identityManager,
        settingsManager = (context.applicationContext as `in`.inzamulhoque.meshtalk.MeshApplication).settingsManager
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
        Log.d("MeshNetworkManager", "Starting MeshNetworkManager. myIdHash: ${String(myShortId)}")
        myDisplayName = identityManager.getDisplayName()
        
        if (bluetoothAdapter?.isEnabled == true) {
            bleManager?.startAdvertising()
            bleManager?.startScanning()
            gattServer.start()
            startAutoReconnectLoop()
        }
    }

    private fun startAutoReconnectLoop() {
        autoReconnectJob?.cancel()
        autoReconnectJob = scope.launch {
            while (true) {
                delay(10000)
                val peers = database.peerDao().getAllPeersSync()
                val now = System.currentTimeMillis()
                peers.forEach { peer ->
                    if (peer.deviceAddress != null && now - peer.lastSeen < 60000) {
                        if (!activeClients.containsKey(peer.deviceAddress)) {
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
            peer?.deviceAddress?.let { connectToPeer(it, force = true) }
        }
    }

    private fun onPeerSeen(deviceAddress: String, peerShortId: ByteArray) {
        if (peerShortId.contentEquals(myShortId)) return
        
        scope.launch {
            val existing = database.peerDao().getPeerByAddress(deviceAddress)
            val now = System.currentTimeMillis()

            if (existing == null) {
                protocol.onPeerDiscovered(deviceAddress, "", null, "Connecting...", deviceAddress)
            } else {
                if (now - existing.lastSeen > 10000) {
                    database.peerDao().updatePeer(existing.copy(lastSeen = now))
                }
            }
            
            val myIdHash = java.lang.Long.parseLong(String(myShortId), 16)
            val peerIdHash = try { java.lang.Long.parseLong(String(peerShortId), 16) } catch (e: Exception) { 0L }
            
            if (peerIdHash != 0L && myIdHash < peerIdHash) {
                connectToPeer(deviceAddress)
            } else if (peerIdHash == 0L) {
                connectToPeer(deviceAddress)
            }
        }
    }

    fun connectToPeer(deviceAddress: String, force: Boolean = false) {
        if (activeClients.containsKey(deviceAddress)) return
        val now = System.currentTimeMillis()
        if (!force && now - (connectionCooldowns[deviceAddress] ?: 0) < 30000) return
        
        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress) ?: return
        connectionCooldowns[deviceAddress] = now
        val client = MeshGattClient(context, device, protocol, identityManager.getMyEncryptionKey(), identityManager.getDisplayName()) {
            activeClients.remove(deviceAddress)
        }
        activeClients[deviceAddress] = client
        client.connect()
    }

    fun broadcastMessage(message: Message) {
        val json = protocol.serializeMessage(message)
        scope.launch {
            var sent = false
            activeClients.values.forEach { client ->
                client.sendData(json)
                sent = true
            }
            gattServer.broadcastData(json)
            if (gattServer.getConnectedDevicesCount() > 0) sent = true

            if (sent) {
                database.messageDao().updateMessageStatus(message.id, MessageStatus.SENT.name)
            }
        }
    }

    fun broadcastSyncUpdate(update: `in`.inzamulhoque.meshtalk.protocol.SyncUpdate) {
        val json = protocol.serializeSyncUpdate(update)
        scope.launch {
            activeClients.values.forEach { it.sendData(json) }
            gattServer.broadcastData(json)
        }
    }
}
