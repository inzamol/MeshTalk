package `in`.inzamulhoque.meshtalk.ble

import android.Manifest
import android.bluetooth.*
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import `in`.inzamulhoque.meshtalk.protocol.MeshProtocol
import `in`.inzamulhoque.meshtalk.util.ToastHelper
import kotlinx.coroutines.*

class MeshGattClient(
    private val context: Context,
    private val device: BluetoothDevice,
    private val protocol: MeshProtocol,
    private val onSyncComplete: () -> Unit
) {
    private var gatt: BluetoothGatt? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var timeoutJob: Job? = null
    private val writeQueue = mutableListOf<ByteArray>()
    private var isWriting = false
    private var currentMtu = 23 // Default BLE MTU
    private var nextMsgId = 0
    private val pendingChunks = mutableListOf<ByteArray>()
    private var currentPeerId: String? = null
    private var currentEncryptionKey: String? = null
    private var isHandshakeStarted = false

    fun connect() {
        Log.d("MeshGattClient", "Connecting to ${device.address}")
        startTimeout()
        try {
            gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            Log.e("MeshGattClient", "Permission denied for connecting GATT", e)
            cancelTimeout()
        }
    }

    private fun startTimeout() {
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(15000) // 15 seconds timeout
            Log.w("MeshGattClient", "Connection timeout reached for ${device.address}")
            ToastHelper.showToast(context, "Connection timed out with peer")
            withContext(Dispatchers.Main) {
                disconnect()
            }
        }
    }

    private fun cancelTimeout() {
        timeoutJob?.cancel()
        timeoutJob = null
    }

    fun disconnect() {
        cancelTimeout()
        try {
            gatt?.disconnect()
            gatt?.close()
            gatt = null
        } catch (e: SecurityException) {
            Log.e("MeshGattClient", "Permission denied for disconnecting GATT", e)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun processWriteQueue(gatt: BluetoothGatt) {
        scope.launch {
            processWriteQueueSuspend(gatt)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private suspend fun processWriteQueueSuspend(gatt: BluetoothGatt) {
        if (isWriting) return

        if (pendingChunks.isNotEmpty()) {
            val chunk = pendingChunks.removeAt(0)
            val service = gatt.getService(MeshConstants.SERVICE_UUID)
            val char = service?.getCharacteristic(MeshConstants.MESSAGE_EXCHANGE_CHAR_UUID)
            
            if (char != null) {
                char.value = chunk
                char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                
                var success = false
                var attempts = 0
                while (!success && attempts < 3) {
                    success = try {
                        gatt.writeCharacteristic(char)
                    } catch (e: SecurityException) {
                        Log.e("MeshGattClient", "SecurityException writing chunk", e)
                        false
                    }
                    
                    if (!success) {
                        attempts++
                        if (attempts < 3) {
                            Log.w("MeshGattClient", "Write busy, retrying chunk in 100ms... (Attempt $attempts)")
                            delay(100)
                        }
                    }
                }
                
                if (success) {
                    isWriting = true
                } else {
                    Log.e("MeshGattClient", "Failed to write chunk to ${device.address} after 3 attempts")
                    ToastHelper.showToast(context, "Failed to send message chunk")
                    withContext(Dispatchers.Main) {
                        disconnect()
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    disconnect()
                }
            }
            return
        }

        if (writeQueue.isEmpty()) {
            Log.d("MeshGattClient", "Sync complete, disconnecting from ${device.address}")
            withContext(Dispatchers.Main) {
                try { gatt.disconnect() } catch (e: SecurityException) {}
            }
            return
        }

        val fullData = writeQueue.removeAt(0)
        val msgId = (nextMsgId++ % 256).toByte()
        val maxDataSize = currentMtu - 10 // Safety buffer (MTU - 3 overhead - 4 header - 3 safety)
        val totalChunks = ((fullData.size + maxDataSize - 1) / maxDataSize).coerceAtMost(255)
        
        Log.d("MeshGattClient", "Splitting message into $totalChunks chunks (Total size: ${fullData.size})")
        
        for (i in 0 until totalChunks) {
            val start = i * maxDataSize
            val end = ((i + 1) * maxDataSize).coerceAtMost(fullData.size)
            val chunkData = fullData.copyOfRange(start, end)
            
            // Header: [0xCC (Marker), msgId, index, total]
            val chunk = ByteArray(4 + chunkData.size)
            chunk[0] = 0xCC.toByte()
            chunk[1] = msgId
            chunk[2] = i.toByte()
            chunk[3] = totalChunks.toByte()
            chunkData.copyInto(chunk, 4)
            
            pendingChunks.add(chunk)
        }
        
        processWriteQueueSuspend(gatt)
    }

    private fun readInventory(gatt: BluetoothGatt) {
        val inventoryChar = gatt.getService(MeshConstants.SERVICE_UUID)
            ?.getCharacteristic(MeshConstants.INVENTORY_CHAR_UUID)
        try {
            if (inventoryChar != null) {
                gatt.readCharacteristic(inventoryChar)
            } else {
                gatt.disconnect()
            }
        } catch (e: SecurityException) {
            gatt.disconnect()
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            try {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d("MeshGattClient", "Connected to ${device.address}. Status: $status. Requesting MTU 512...")
                    gatt?.requestMtu(512)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d("MeshGattClient", "Disconnected from ${device.address}")
                    cancelTimeout()
                    onSyncComplete()
                }
            } catch (e: SecurityException) {
                Log.e("MeshGattClient", "Permission denied in connection state change", e)
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            Log.d("MeshGattClient", "MTU changed to $mtu, status: $status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                currentMtu = mtu
            }
            try {
                gatt?.discoverServices()
            } catch (e: SecurityException) {
                Log.e("MeshGattClient", "Permission denied discovering services", e)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && !isHandshakeStarted) {
                isHandshakeStarted = true
                scope.launch {
                    val myName = protocol.createHandshake("", "").displayName // Access via a better way later
                    // We need my encryption key and name here. 
                    // Let's pass them into the client or get them from a provider.
                }
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                when (characteristic.uuid) {
                    MeshConstants.IDENTITY_CHAR_UUID -> {
                        val value = characteristic.value ?: byteArrayOf()
                        val peerId = String(value)
                        Log.d("MeshGattClient", "Discovered Peer ID: $peerId")
                        currentPeerId = peerId
                        val encryptionChar = gatt.getService(MeshConstants.SERVICE_UUID)
                            ?.getCharacteristic(MeshConstants.ENCRYPTION_KEY_CHAR_UUID)
                        try {
                            if (encryptionChar != null) {
                                gatt.readCharacteristic(encryptionChar)
                            } else {
                                // Should not happen with new protocol, but fallback
                                val nameChar = gatt.getService(MeshConstants.SERVICE_UUID)
                                    ?.getCharacteristic(MeshConstants.DISPLAY_NAME_CHAR_UUID)
                                if (nameChar != null) {
                                    gatt.readCharacteristic(nameChar)
                                } else {
                                    scope.launch {
                                        protocol.onPeerDiscovered(peerId, "", null, null, device.address)
                                        readInventory(gatt)
                                    }
                                }
                            }
                        } catch (e: SecurityException) {
                            gatt.disconnect()
                        }
                    }
                    MeshConstants.ENCRYPTION_KEY_CHAR_UUID -> {
                        val value = characteristic.value ?: byteArrayOf()
                        val encryptionKey = String(value)
                        currentEncryptionKey = encryptionKey
                        
                        val nameChar = gatt.getService(MeshConstants.SERVICE_UUID)
                            ?.getCharacteristic(MeshConstants.DISPLAY_NAME_CHAR_UUID)
                        try {
                            if (nameChar != null) {
                                gatt.readCharacteristic(nameChar)
                            } else {
                                val peerId = currentPeerId ?: return
                                scope.launch {
                                    protocol.onPeerDiscovered(peerId, "", encryptionKey, null, device.address)
                                    readInventory(gatt)
                                }
                            }
                        } catch (e: SecurityException) {
                            gatt.disconnect()
                        }
                    }
                    MeshConstants.DISPLAY_NAME_CHAR_UUID -> {
                        val value = characteristic.value ?: byteArrayOf()
                        val displayName = String(value)
                        val peerId = currentPeerId ?: return
                        val encryptionKey = currentEncryptionKey
                        Log.d("MeshGattClient", "Discovered Peer Name: $displayName")
                        
                        scope.launch {
                            protocol.onPeerDiscovered(peerId, "", encryptionKey, displayName, device.address)
                            readInventory(gatt)
                            cancelTimeout() // Successfully synced identity
                        }
                    }
                    MeshConstants.INVENTORY_CHAR_UUID -> {
                        val value = characteristic.value ?: byteArrayOf()
                        val inventoryStr = String(value)
                        val remoteInventory = if (inventoryStr.isEmpty()) emptyList() else inventoryStr.split(",").filter { it.isNotEmpty() }
                        Log.d("MeshGattClient", "Remote Inventory size: ${remoteInventory.size}")
                        
                        scope.launch {
                            val messagesToSync = protocol.getMessagesToSync(remoteInventory)
                            Log.d("MeshGattClient", "Messages to sync: ${messagesToSync.size}")
                            messagesToSync.forEach { msg ->
                                writeQueue.add(protocol.serializeMessage(msg).toByteArray())
                            }
                            delay(200) // Stack settlement delay
                            processWriteQueueSuspend(gatt)
                        }
                    }
                }
            } else {
                try { gatt.disconnect() } catch (e: SecurityException) {}
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e("MeshGattClient", "Write failed for ${device.address} with status: $status")
                ToastHelper.showToast(context, "Sync write failed (status: $status)")
            }
            isWriting = false
            if (gatt != null) processWriteQueue(gatt)
        }
    }
}
