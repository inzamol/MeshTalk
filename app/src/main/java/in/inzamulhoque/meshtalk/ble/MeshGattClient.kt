package `in`.inzamulhoque.meshtalk.ble

import android.annotation.SuppressLint
import android.Manifest
import android.bluetooth.*
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import `in`.inzamulhoque.meshtalk.data.local.entity.Message
import `in`.inzamulhoque.meshtalk.protocol.MeshProtocol
import `in`.inzamulhoque.meshtalk.protocol.proto.*
import `in`.inzamulhoque.meshtalk.util.PermissionUtils
import `in`.inzamulhoque.meshtalk.util.ToastHelper
import kotlinx.coroutines.*

class MeshGattClient(
    private val context: Context,
    private val device: BluetoothDevice,
    private val protocol: MeshProtocol,
    private val myEncryptionKey: String,
    private val myDisplayName: String,
    private val networkManagerProvider: () -> MeshNetworkManager?,
    private val onSyncComplete: () -> Unit
) {
    private var gatt: BluetoothGatt? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var connectionTimeoutJob: Job? = null
    private var idleDisconnectJob: Job? = null
    
    private val writeQueue = mutableListOf<ByteArray>()
    private var isWriting = false
    private var currentMtu = 23
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
        
        if (!PermissionUtils.hasPermission(context, Manifest.permission.BLUETOOTH_CONNECT)) {
            Log.e("MeshGattClient", "Missing BLUETOOTH_CONNECT permission")
            return
        }

        startConnectionTimeout()
        try {
            gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            Log.e("MeshGattClient", "Permission denied for connecting GATT", e)
            cancelConnectionTimeout()
        }
    }

    private fun startConnectionTimeout() {
        connectionTimeoutJob?.cancel()
        connectionTimeoutJob = scope.launch {
            delay(30000) // 30 seconds connection timeout
            Log.w("MeshGattClient", "Connection timeout reached for ${device.address}")
            withContext(Dispatchers.Main) {
                disconnect()
            }
        }
    }

    private fun cancelConnectionTimeout() {
        connectionTimeoutJob?.cancel()
        connectionTimeoutJob = null
    }

    private fun resetIdleTimeout() {
        idleDisconnectJob?.cancel()
        idleDisconnectJob = scope.launch {
            delay(120000) // Disconnect after 2 minutes of inactivity
            Log.d("MeshGattClient", "Idle timeout reached for ${device.address}")
            withContext(Dispatchers.Main) {
                disconnect()
            }
        }
    }

    fun disconnect() {
        cancelConnectionTimeout()
        idleDisconnectJob?.cancel()
        try {
            gatt?.disconnect()
            gatt?.close()
            gatt = null
        } catch (_: SecurityException) {}
    }

    fun sendData(data: ByteArray) {
        scope.launch {
            writeQueue.add(data)
            val g = gatt ?: return@launch
            processWriteQueueSuspend(g)
        }
    }

    private fun processWriteQueue(gatt: BluetoothGatt) {
        scope.launch {
            processWriteQueueSuspend(gatt)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun processWriteQueueSuspend(gatt: BluetoothGatt) {
        if (isWriting) return
        resetIdleTimeout()

        if (pendingChunks.isNotEmpty()) {
            val chunk = pendingChunks.removeAt(0)
            val service = gatt.getService(MeshConstants.SERVICE_UUID)
            val char = service?.getCharacteristic(MeshConstants.MESSAGE_EXCHANGE_CHAR_UUID)
            
            if (char != null) {
                char.value = chunk
                char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                
                var success = false
                var attempts = 0
                while (!success && attempts < 5) {
                    success = try {
                        gatt.writeCharacteristic(char)
                    } catch (e: SecurityException) {
                        Log.e("MeshGattClient", "SecurityException writing chunk", e)
                        false
                    }
                    
                    if (!success) {
                        attempts++
                        if (attempts < 5) {
                            delay(attempts * 200L)
                        }
                    }
                }
                
                if (success) {
                    isWriting = true
                    Log.d("MeshGattClient", "Chunk write initiated for ${device.address}")
                } else {
                    Log.e("MeshGattClient", "Failed to write chunk to ${device.address} after retries")
                    withContext(Dispatchers.Main) { disconnect() }
                }
            } else {
                withContext(Dispatchers.Main) { disconnect() }
            }
            return
        }

        if (writeQueue.isEmpty()) return

        val fullData = writeQueue.removeAt(0)
        val msgId = (nextMsgId++ % 256).toByte()
        val maxDataSize = (currentMtu - 10).coerceAtLeast(10)
        val totalChunks = ((fullData.size + maxDataSize - 1) / maxDataSize).coerceAtMost(255)
        
        Log.d("MeshGattClient", "Queueing $totalChunks chunks for message $msgId (Total: ${fullData.size})")
        
        for (i in 0 until totalChunks) {
            val start = i * maxDataSize
            val end = ((i + 1) * maxDataSize).coerceAtMost(fullData.size)
            val chunkData = fullData.copyOfRange(start, end)
            
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
                    Log.d("MeshGattClient", "Connected to ${device.address}. Requesting MTU 512 and High Priority...")
                    gatt?.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                    gatt?.requestMtu(512)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d("MeshGattClient", "Disconnected from ${device.address}")
                    cancelConnectionTimeout()
                    idleDisconnectJob?.cancel()
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
                val service = gatt.getService(MeshConstants.SERVICE_UUID)
                val char = service?.getCharacteristic(MeshConstants.MESSAGE_EXCHANGE_CHAR_UUID)
                if (char != null) {
                    gatt.setCharacteristicNotification(char, true)
                    val descriptor = char.getDescriptor(MeshConstants.CLIENT_CONFIG_DESCRIPTOR_UUID)
                    if (descriptor != null) {
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor)
                    } else {
                        startHandshake(gatt)
                    }
                } else {
                    startHandshake(gatt)
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            if (descriptor?.uuid == MeshConstants.CLIENT_CONFIG_DESCRIPTOR_UUID) {
                if (gatt != null) startHandshake(gatt)
            }
        }

        private fun startHandshake(gatt: BluetoothGatt) {
            if (isHandshakeStarted) return
            isHandshakeStarted = true
            
            scope.launch {
                val handshake = protocol.createHandshake(myEncryptionKey, myDisplayName)
                val data = protocol.serializeHandshake(handshake)
                Log.d("MeshGattClient", "Sending handshake to ${device.address}")
                sendData(data)
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == MeshConstants.MESSAGE_EXCHANGE_CHAR_UUID) {
                val data = characteristic.value ?: return
                resetIdleTimeout()
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
                            Log.d("MeshGattClient", "Reassembled incoming data from ${device.address}")
                            
                            val packet = protocol.parsePacket(fullData)
                            when (packet) {
                                is ProtoHandshake -> {
                                    scope.launch {
                                        val messagesToSync = protocol.handleHandshake(packet, device.address)
                                        messagesToSync.forEach { msg ->
                                            sendData(protocol.serializeMessage(msg))
                                        }
                                        cancelConnectionTimeout()
                                    }
                                }
                                is Message -> {
                                    scope.launch {
                                        val (reply, forward) = protocol.processReceivedMessage(packet)
                                        if (reply != null) {
                                            sendData(protocol.serializeSyncUpdate(reply))
                                        }
                                        if (forward != null) {
                                            networkManagerProvider()?.forwardToOthers(forward, device.address)
                                        }
                                    }
                                }
                                is ProtoSyncUpdate -> {
                                    scope.launch {
                                        protocol.processSyncUpdate(packet)
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
        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            isWriting = false
            if (gatt != null) processWriteQueue(gatt)
        }
    }
}
