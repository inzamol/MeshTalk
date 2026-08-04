package `in`.inzamulhoque.meshtalk.ble

import android.annotation.SuppressLint
import android.Manifest
import android.bluetooth.*
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import `in`.inzamulhoque.meshtalk.protocol.MeshProtocol
import `in`.inzamulhoque.meshtalk.util.PermissionUtils
import `in`.inzamulhoque.meshtalk.util.ToastHelper
import kotlinx.coroutines.*

class MeshGattClient(
    private val context: Context,
    private val device: BluetoothDevice,
    private val protocol: MeshProtocol,
    private val myEncryptionKey: String,
    private val myDisplayName: String,
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
    private val reassemblyBuffers = java.util.concurrent.ConcurrentHashMap<Byte, ReassemblyBuffer>()
    private var isHandshakeStarted = false

    data class ReassemblyBuffer(
        val msgId: Byte,
        val total: Int,
        val chunks: MutableMap<Int, ByteArray> = mutableMapOf()
    )

    @SuppressLint("MissingPermission")
    fun connect() {
        Log.d("MeshGattClient", "Connecting to ${device.address}")
        
        // Permission check
        if (!PermissionUtils.hasPermission(context, Manifest.permission.BLUETOOTH_CONNECT)) {
            Log.e("MeshGattClient", "Missing BLUETOOTH_CONNECT permission")
            return
        }

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
        // ... existing code ...
    }

    fun sendData(json: String) {
        scope.launch {
            writeQueue.add(json.toByteArray())
            val g = gatt ?: return@launch
            processWriteQueueSuspend(g)
        }
    }

    private fun processWriteQueue(gatt: BluetoothGatt) {
// ...
        scope.launch {
            processWriteQueueSuspend(gatt)
        }
    }

    @SuppressLint("MissingPermission")
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
                while (!success && attempts < 5) { // Increased to 5 attempts
                    success = try {
                        gatt.writeCharacteristic(char)
                    } catch (e: SecurityException) {
                        Log.e("MeshGattClient", "SecurityException writing chunk", e)
                        false
                    }
                    
                    if (!success) {
                        attempts++
                        if (attempts < 5) {
                            val waitTime = attempts * 150L // Gradual increase in wait time
                            Log.w("MeshGattClient", "Write busy, retrying chunk in ${waitTime}ms... (Attempt $attempts)")
                            delay(waitTime)
                        }
                    }
                }
                
                if (success) {
                    isWriting = true
                    Log.d("MeshGattClient", "Chunk write initiated for ${device.address}")
                } else {
                    Log.e("MeshGattClient", "Failed to write chunk to ${device.address} after 5 attempts")
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

        if (writeQueue.isEmpty() && pendingChunks.isEmpty()) {
            Log.d("MeshGattClient", "Sync queue empty for ${device.address}, holding connection for 10s...")
            scope.launch {
                delay(10000)
                if (writeQueue.isEmpty() && pendingChunks.isEmpty()) {
                    Log.d("MeshGattClient", "Holding complete, disconnecting from ${device.address}")
                    withContext(Dispatchers.Main) {
                        try { gatt.disconnect() } catch (e: SecurityException) {}
                    }
                }
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

    @SuppressLint("MissingPermission")
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

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && !isHandshakeStarted && gatt != null) {
                // Enable notifications first
                val service = gatt.getService(MeshConstants.SERVICE_UUID)
                val char = service?.getCharacteristic(MeshConstants.MESSAGE_EXCHANGE_CHAR_UUID)
                if (char != null) {
                    gatt.setCharacteristicNotification(char, true)
                    val descriptor = char.getDescriptor(MeshConstants.CLIENT_CONFIG_DESCRIPTOR_UUID)
                    if (descriptor != null) {
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        Log.d("MeshGattClient", "Writing notification descriptor for ${device.address}")
                        gatt.writeDescriptor(descriptor)
                    } else {
                        Log.e("MeshGattClient", "Notification descriptor not found!")
                        startHandshake(gatt)
                    }
                } else {
                    Log.e("MeshGattClient", "Message exchange characteristic not found!")
                    startHandshake(gatt)
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            if (descriptor?.uuid == MeshConstants.CLIENT_CONFIG_DESCRIPTOR_UUID && status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("MeshGattClient", "Notification descriptor written for ${device.address}")
                if (gatt != null) startHandshake(gatt)
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e("MeshGattClient", "Failed to write descriptor: $status")
                if (gatt != null) startHandshake(gatt) // Try handshake anyway
            }
        }

        private fun startHandshake(gatt: BluetoothGatt) {
            if (isHandshakeStarted) return
            isHandshakeStarted = true
            
            scope.launch {
                val handshake = protocol.createHandshake(myEncryptionKey, myDisplayName)
                val json = protocol.serializeHandshake(handshake)
                Log.d("MeshGattClient", "Sending handshake to ${device.address} (Size: ${json.length})")
                writeQueue.add(json.toByteArray())
                
                // Permission check
                if (!PermissionUtils.hasPermission(context, Manifest.permission.BLUETOOTH_CONNECT)) {
                    Log.e("MeshGattClient", "Missing BLUETOOTH_CONNECT during handshake")
                    return@launch
                }
                processWriteQueueSuspend(gatt)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == MeshConstants.MESSAGE_EXCHANGE_CHAR_UUID) {
                val data = characteristic.value ?: return
                if (data.size >= 4 && data[0] == 0xCC.toByte()) {
                    val msgId = data[1]
                    val index = data[2].toInt() and 0xFF
                    val total = data[3].toInt() and 0xFF
                    val chunkData = data.copyOfRange(4, data.size)
                    
                    val buffer = reassemblyBuffers.getOrPut(msgId) {
                        ReassemblyBuffer(msgId, total)
                    }
                    buffer.chunks[index] = chunkData
                    
                    if (buffer.chunks.size == total) {
                        val fullData = ByteArray(buffer.chunks.values.sumOf { it.size })
                        var currentOffset = 0
                        for (i in 0 until total) {
                            val chunk = buffer.chunks[i] ?: break
                            chunk.copyInto(fullData, currentOffset)
                            currentOffset += chunk.size
                        }
                        
                        if (currentOffset == fullData.size) {
                            val json = String(fullData)
                            Log.d("MeshGattClient", "Received notification data from ${device.address} ($total chunks, ${fullData.size} bytes)")
                            
                            val handshake = protocol.deserializeHandshake(json)
                            if (handshake != null) {
                                scope.launch {
                                    Log.d("MeshGattClient", "Received handshake reply from ${handshake.displayName}")
                                    protocol.handleHandshake(handshake, device.address)
                                }
                            } else {
                                scope.launch {
                                    val message = protocol.deserializeMessage(json)
                                    if (message != null) {
                                        val reply = protocol.processReceivedMessage(message)
                                        if (reply != null) {
                                            sendData(protocol.serializeSyncUpdate(reply))
                                        }
                                        return@launch
                                    }

                                    val syncUpdate = protocol.deserializeSyncUpdate(json)
                                    if (syncUpdate != null) {
                                        protocol.processSyncUpdate(syncUpdate)
                                    }
                                }
                            }
                        }
                        reassemblyBuffers.remove(msgId)
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e("MeshGattClient", "Write failed for ${device.address} with status: $status")
                ToastHelper.showToast(context, "Sync write failed (status: $status)")
            } else {
                Log.d("MeshGattClient", "Write successful for ${device.address}")
            }
            isWriting = false
            if (gatt != null) processWriteQueue(gatt)
        }
    }
}
