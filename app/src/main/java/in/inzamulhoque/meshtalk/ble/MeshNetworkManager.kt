package `in`.inzamulhoque.meshtalk.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import `in`.inzamulhoque.meshtalk.crypto.IdentityManager
import `in`.inzamulhoque.meshtalk.data.local.AppDatabase
import `in`.inzamulhoque.meshtalk.data.local.entity.Message
import `in`.inzamulhoque.meshtalk.data.local.entity.MessageStatus
import `in`.inzamulhoque.meshtalk.protocol.MeshProtocol
import `in`.inzamulhoque.meshtalk.util.FileUtils
import `in`.inzamulhoque.meshtalk.util.MovementDetector
import `in`.inzamulhoque.meshtalk.util.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.nio.ByteBuffer
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class MeshNetworkManager(
    private val context: Context,
    private val database: AppDatabase,
    private val identityManager: IdentityManager,
    private val settingsManager: SettingsManager
) {
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    
    private val protocol = MeshProtocol(
        context = context,
        myId = identityManager.getMyId(),
        messageDao = database.messageDao(),
        peerDao = database.peerDao(),
        groupDao = database.groupDao(),
        identityManager = identityManager,
        settingsManager = settingsManager
    )

    private val bleManager = bluetoothAdapter?.let { adapter ->
        MeshBLEManager(context, adapter, identityManager.getStealthId()) { deviceAddress, peerShortId, rssi ->
            onPeerSeen(deviceAddress, peerShortId, rssi)
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
    
    private var lastMovementTime = System.currentTimeMillis()
    private val movementDetector = MovementDetector(context) {
        lastMovementTime = System.currentTimeMillis()
    }
    
    private val _activeAddresses = kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(emptySet())
    val activeAddresses = _activeAddresses.asStateFlow()

    val connectedPeerAddresses = combine(
        activeAddresses,
        gattServer.connectedAddresses
    ) { active, server ->
        active + server
    }.stateIn(scope, SharingStarted.Eagerly, emptySet())

    private var autoReconnectJob: kotlinx.coroutines.Job? = null
    private var timeoutCheckJob: kotlinx.coroutines.Job? = null
    private var pruningJob: kotlinx.coroutines.Job? = null
    private var rotationJob: kotlinx.coroutines.Job? = null

    fun start() {
        Log.d("MeshNetworkManager", "Starting MeshNetworkManager. Stealth ID: ${identityManager.getStealthId().joinToString("") { "%02x".format(it) }}")
        myDisplayName = identityManager.getDisplayName()
        
        if (bluetoothAdapter?.isEnabled == true) {
            bleManager?.startAdvertising()
            if (settingsManager.isContinuousSearchEnabled) {
                bleManager?.startScanning()
            }
            if (settingsManager.isMovementSensingEnabled) {
                movementDetector.start()
            }
            gattServer.start()
            startAutoReconnectLoop()
            startTimeoutCheckLoop()
            startPruningLoop()
            startRotationLoop()
        }
    }

    fun refreshSearch() {
        scope.launch {
            Log.d("MeshNetworkManager", "Manual search refresh triggered")
            bleManager?.startScanning()
            delay(10.seconds) 
            if (!settingsManager.isContinuousSearchEnabled) {
                bleManager?.stopScanning()
            }
        }
    }

    fun updateScanningState() {
        if (settingsManager.isContinuousSearchEnabled) {
            bleManager?.startScanning()
        } else {
            bleManager?.stopScanning()
        }
        
        if (settingsManager.isMovementSensingEnabled) {
            movementDetector.start()
        } else {
            movementDetector.stop()
        }
    }

    private fun startTimeoutCheckLoop() {
        timeoutCheckJob?.cancel()
        timeoutCheckJob = scope.launch {
            while (true) {
                delay(30.seconds) 
                val threshold = System.currentTimeMillis() - 5 * 60 * 1000
                database.messageDao().markTimedOutMessagesAsFailed(threshold)
            }
        }
    }

    private fun startPruningLoop() {
        pruningJob?.cancel()
        pruningJob = scope.launch {
            while (true) {
                try {
                    val now = System.currentTimeMillis()
                    val myId = identityManager.getMyId()
                    
                    // Prune others messages
                    val othersThreshold = now - (settingsManager.pruneOthersMessagesDays.toLong() * 24 * 60 * 60 * 1000)
                    database.messageDao().pruneOthersMessages(myId, othersThreshold)
                    
                    // Prune own messages if enabled
                    if (settingsManager.isPruningOwnMessagesEnabled) {
                        val ownThreshold = now - (settingsManager.pruneOwnMessagesDays.toLong() * 24 * 60 * 60 * 1000)
                        database.messageDao().pruneOwnMessages(myId, ownThreshold)
                    }
                } catch (e: Exception) {
                    Log.e("MeshNetworkManager", "Error in pruning loop", e)
                }
                delay(12.hours)
            }
        }
    }

    private fun startRotationLoop() {
        rotationJob?.cancel()
        rotationJob = scope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                val windowMs = 15 * 60 * 1000
                val nextWindow = ((now / windowMs) + 1) * windowMs
                
                // Sleep until next window
                delay(nextWindow - now + 1000) 
                
                Log.d("MeshNetworkManager", "Rotating Stealth ID...")
                bleManager?.updateAdvertisingId(identityManager.getStealthId())
            }
        }
    }

    private fun startAutoReconnectLoop() {
        autoReconnectJob?.cancel()
        autoReconnectJob = scope.launch {
            while (true) {
                val nowTime = System.currentTimeMillis()
                
                // Adaptive delay based on movement
                val isMoving = nowTime - lastMovementTime < 30000 // 30 seconds since last movement
                val scanInterval = if (!settingsManager.isMovementSensingEnabled || isMoving) {
                    15.seconds
                } else {
                    2.minutes
                }
                
                delay(scanInterval)
                
                if (settingsManager.isContinuousSearchEnabled) {
                    // If stationary, maybe stop scanning temporarily to save battery?
                    if (settingsManager.isMovementSensingEnabled && !isMoving) {
                        Log.d("MeshNetworkManager", "Stationary detected, pausing scan to save battery")
                        bleManager?.stopScanning()
                        delay(10.seconds) // Keep it stopped for a bit
                    } else {
                        bleManager?.startScanning()
                    }
                }

                val peers = database.peerDao().getAllPeersSync()
                val now = System.currentTimeMillis()
                peers.forEach { peer ->
                    if (peer.deviceAddress != null && now - peer.lastSeen < 45000) {
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
        timeoutCheckJob?.cancel()
        pruningJob?.cancel()
        rotationJob?.cancel()
        movementDetector.stop()
        bleManager?.stopAdvertising()
        bleManager?.stopScanning()
        gattServer.stop()
        activeClients.values.forEach { it.disconnect() }
        activeClients.clear()
        _activeAddresses.value = emptySet()
    }

    fun connectToPeerById(peerId: String) {
        scope.launch {
            val peer = database.peerDao().getPeerById(peerId)
            peer?.deviceAddress?.let { connectToPeer(it, force = true) }
        }
    }

    private fun onPeerSeen(deviceAddress: String, peerShortId: ByteArray, rssi: Int) {
        // If we are seeing our own current stealth ID, ignore it
        val myStealthId = identityManager.getStealthId()
        if (peerShortId.contentEquals(myStealthId)) return
        
        scope.launch {
            val existing = database.peerDao().getPeerByAddress(deviceAddress)
            val now = System.currentTimeMillis()

            if (existing == null) {
                val initialName = if (settingsManager.isShowConnectingDevicesEnabled) "Connecting..." else "Mesh Peer"
                protocol.onPeerDiscovered(deviceAddress, "", null, initialName, deviceAddress, rssi = rssi)
            } else if (now - existing.lastSeen > 10000 || Math.abs(existing.rssi - rssi) > 5) {
                database.peerDao().updatePeer(existing.copy(lastSeen = now, rssi = rssi))
            }
            
            val myIdHash = if (myStealthId.size >= 4) ByteBuffer.wrap(myStealthId).int.toLong() and 0xFFFFFFFFL else 0L
            val peerIdHash = try { 
                if (peerShortId.size >= 4) ByteBuffer.wrap(peerShortId).int.toLong() and 0xFFFFFFFFL
                else java.lang.Long.parseLong(deviceAddress.replace(":", ""), 16)
            } catch (e: Exception) { 0L }
            
            if (myIdHash < peerIdHash) {
                connectToPeer(deviceAddress)
            }
        }
    }

    fun connectToPeer(deviceAddress: String, force: Boolean = false) {
        synchronized(activeClients) {
            if (activeClients.containsKey(deviceAddress)) return
            
            val lastConnect = connectionCooldowns[deviceAddress] ?: 0
            val now = System.currentTimeMillis()
            if (!force && now - lastConnect < 20000) return 

            val device = bluetoothAdapter?.getRemoteDevice(deviceAddress) ?: return
            connectionCooldowns[deviceAddress] = now
            
            Log.d("MeshNetworkManager", "Initiating GATT client connection to $deviceAddress")
            val client = MeshGattClient(context, device, protocol, identityManager.getMyEncryptionKey(), identityManager.getDisplayName(), { this }) {
                synchronized(activeClients) {
                    activeClients.remove(deviceAddress)
                    _activeAddresses.value = activeClients.keys.toSet()
                }
            }
            activeClients[deviceAddress] = client
            _activeAddresses.value = activeClients.keys.toSet()
            client.connect()
        }
    }

    fun broadcastMessage(message: Message) {
        val data = protocol.serializeMessage(message)
        scope.launch {
            var sent = false
            
            // Try sending to currently active clients
            activeClients.values.forEach { client ->
                client.sendData(data)
                sent = true
            }
            
            // If no active clients, try to connect to known nearby peers immediately
            if (!sent) {
                val peers = database.peerDao().getAllPeersSync()
                val now = System.currentTimeMillis()
                peers.filter { it.deviceAddress != null && now - it.lastSeen < 60000 }.forEach { peer ->
                    connectToPeer(peer.deviceAddress!!, force = true)
                }
            }

            gattServer.broadcastData(data)
            if (gattServer.getConnectedDevicesCount() > 0) sent = true

            if (sent) {
                database.messageDao().updateMessageStatus(message.id, MessageStatus.SENT.name)
            }
        }
    }

    fun broadcastSyncUpdate(update: `in`.inzamulhoque.meshtalk.protocol.proto.ProtoSyncUpdate) {
        val data = protocol.serializeSyncUpdate(update)
        scope.launch {
            activeClients.values.forEach { it.sendData(data) }
            gattServer.broadcastData(data)
        }
    }

    fun forwardToOthers(message: Message, excludeAddress: String?) {
        val data = protocol.serializeMessage(message)
        scope.launch {
            activeClients.forEach { (address, client) ->
                if (address != excludeAddress) {
                    client.sendData(data)
                }
            }
            gattServer.broadcastData(data, excludeAddress)
        }
    }
}
