package `in`.inzamulhoque.meshtalk.ble

import android.annotation.SuppressLint
import android.Manifest
import android.bluetooth.*
import android.content.Context
import android.util.Log
import `in`.inzamulhoque.meshtalk.protocol.MeshProtocol
import `in`.inzamulhoque.meshtalk.util.PermissionUtils
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

class MeshGattServer(
    private val context: Context,
    private val bluetoothManager: BluetoothManager,
    private val protocol: MeshProtocol,
    private val myId: String,
    private val myEncryptionKey: String,
    private val displayNameProvider: () -> String,
    private val networkManagerProvider: () -> MeshNetworkManager?
) {
    private var gattServer: BluetoothGattServer? = null
    
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("MeshGattServer", "Unhandled coroutine exception", throwable)
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + exceptionHandler)
    private val reassemblyBuffers = ConcurrentHashMap<String, ReassemblyBuffer>()
    private val connectedDevices = mutableSetOf<BluetoothDevice>()

    data class ReassemblyBuffer(
        val msgId: Byte,
        val total: Int,
        val chunks: MutableMap<Int, ByteArray> = mutableMapOf(),
        var lastSeen: Long = System.currentTimeMillis()
    )

    @SuppressLint("MissingPermission")
    fun start() {
        if (gattServer != null) return
        
        if (!PermissionUtils.hasPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) ||
            !PermissionUtils.hasPermission(context, Manifest.permission.BLUETOOTH_CONNECT)) {
            return
        }

        try {
            gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
            val service = BluetoothGattService(MeshConstants.SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
            service.addCharacteristic(BluetoothGattCharacteristic(MeshConstants.IDENTITY_CHAR_UUID, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ))
            service.addCharacteristic(BluetoothGattCharacteristic(MeshConstants.ENCRYPTION_KEY_CHAR_UUID, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ))
            service.addCharacteristic(BluetoothGattCharacteristic(MeshConstants.DISPLAY_NAME_CHAR_UUID, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ))
            service.addCharacteristic(BluetoothGattCharacteristic(MeshConstants.INVENTORY_CHAR_UUID, BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE))
            
            val messageChar = BluetoothGattCharacteristic(MeshConstants.MESSAGE_EXCHANGE_CHAR_UUID, BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY, BluetoothGattCharacteristic.PERMISSION_WRITE or BluetoothGattCharacteristic.PERMISSION_READ)
            messageChar.addDescriptor(BluetoothGattDescriptor(MeshConstants.CLIENT_CONFIG_DESCRIPTOR_UUID, BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE))
            service.addCharacteristic(messageChar)

            gattServer?.addService(service)
        } catch (e: SecurityException) {
            Log.e("MeshGattServer", "SecurityException", e)
        }
    }

    fun stop() {
        try {
            gattServer?.close()
        } catch (_: SecurityException) {}
    }

    fun broadcastData(json: String, excludeAddress: String? = null) {
        connectedDevices.forEach { device ->
            if (device.address != excludeAddress) {
                sendDataViaNotification(device, json)
            }
        }
    }

    fun getConnectedDevicesCount(): Int = connectedDevices.size

    @SuppressLint("MissingPermission")
    private fun sendDataViaNotification(device: BluetoothDevice, json: String) {
        val service = gattServer?.getService(MeshConstants.SERVICE_UUID)
        val char = service?.getCharacteristic(MeshConstants.MESSAGE_EXCHANGE_CHAR_UUID) ?: return
        
        val data = json.toByteArray()
        val msgId = (System.currentTimeMillis() % 256).toByte()
        val maxDataSize = 180 
        val totalChunks = (data.size + maxDataSize - 1) / maxDataSize
        
        scope.launch {
            for (i in 0 until totalChunks) {
                val start = i * maxDataSize
                val end = ((i + 1) * maxDataSize).coerceAtMost(data.size)
                val chunkData = data.copyOfRange(start, end)
                
                val chunk = ByteArray(4 + chunkData.size)
                chunk[0] = 0xCC.toByte()
                chunk[1] = msgId
                chunk[2] = i.toByte()
                chunk[3] = totalChunks.toByte()
                chunkData.copyInto(chunk, 4)
                
                char.value = chunk
                gattServer?.notifyCharacteristicChanged(device, char, false)
                kotlinx.coroutines.delay(50.milliseconds) 
            }
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            val dev = device ?: return
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectedDevices.add(dev)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connectedDevices.remove(dev)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicReadRequest(device: BluetoothDevice?, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic?) {
            val dev = device ?: return
            try {
                when (characteristic?.uuid) {
                    MeshConstants.IDENTITY_CHAR_UUID -> gattServer?.sendResponse(dev, requestId, BluetoothGatt.GATT_SUCCESS, offset, myId.toByteArray().let { if (offset < it.size) it.copyOfRange(offset, it.size) else byteArrayOf() })
                    MeshConstants.ENCRYPTION_KEY_CHAR_UUID -> gattServer?.sendResponse(dev, requestId, BluetoothGatt.GATT_SUCCESS, offset, myEncryptionKey.toByteArray().let { if (offset < it.size) it.copyOfRange(offset, it.size) else byteArrayOf() })
                    MeshConstants.DISPLAY_NAME_CHAR_UUID -> gattServer?.sendResponse(dev, requestId, BluetoothGatt.GATT_SUCCESS, offset, displayNameProvider().toByteArray().let { if (offset < it.size) it.copyOfRange(offset, it.size) else byteArrayOf() })
                    MeshConstants.INVENTORY_CHAR_UUID -> scope.launch {
                        val inv = protocol.getInventory().joinToString(",").toByteArray()
                        gattServer?.sendResponse(dev, requestId, BluetoothGatt.GATT_SUCCESS, offset, if (offset < inv.size) inv.copyOfRange(offset, inv.size) else byteArrayOf())
                    }
                    else -> gattServer?.sendResponse(dev, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
                }
            } catch (e: SecurityException) {}
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(device: BluetoothDevice?, requestId: Int, characteristic: BluetoothGattCharacteristic?, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
            val dev = device ?: return
            val char = characteristic ?: return
            val data = value ?: byteArrayOf()
            
            if (char.uuid == MeshConstants.MESSAGE_EXCHANGE_CHAR_UUID) {
                if (data.size >= 4 && data[0] == 0xCC.toByte()) {
                    val msgId = data[1]
                    val index = data[2].toInt() and 0xFF
                    val total = totalChunksFromData(data)
                    val bufferKey = "${dev.address}_$msgId"
                    val buffer = reassemblyBuffers.getOrPut(bufferKey) { ReassemblyBuffer(msgId, total) }
                    buffer.chunks[index] = data.copyOfRange(4, data.size)
                    
                    if (buffer.chunks.size == total) {
                        val fullData = ByteArray(buffer.chunks.values.sumOf { it.size })
                        var curr = 0
                        for (i in 0 until total) { buffer.chunks[i]?.let { it.copyInto(fullData, curr); curr += it.size } }
                        val json = String(fullData)
                        val handshake = protocol.deserializeHandshake(json)
                        if (handshake != null) {
                            scope.launch {
                                protocol.handleHandshake(handshake, dev.address)
                                sendDataViaNotification(dev, protocol.serializeHandshake(protocol.createHandshake(myEncryptionKey, displayNameProvider())))
                                protocol.getMessagesToSync(handshake.inventory).forEach { sendDataViaNotification(dev, protocol.serializeMessage(it)) }
                            }
                        } else processReceivedJson(json, dev)
                        reassemblyBuffers.remove(bufferKey)
                    }
                } else processReceivedJson(String(data), dev)
                if (responseNeeded) gattServer?.sendResponse(dev, requestId, BluetoothGatt.GATT_SUCCESS, offset, data)
            } else if (responseNeeded) gattServer?.sendResponse(dev, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWriteRequest(device: BluetoothDevice?, requestId: Int, descriptor: BluetoothGattDescriptor?, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
            val dev = device ?: return
            if (responseNeeded) gattServer?.sendResponse(dev, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
        }

        private fun totalChunksFromData(data: ByteArray): Int = data[3].toInt() and 0xFF

        private fun processReceivedJson(json: String, fromDevice: BluetoothDevice) {
            scope.launch {
                protocol.deserializeMessage(json)?.let { 
                    val (reply, forward) = protocol.processReceivedMessage(it)
                    if (reply != null) {
                        sendDataViaNotification(fromDevice, protocol.serializeSyncUpdate(reply))
                    }
                    if (forward != null) {
                        networkManagerProvider()?.forwardToOthers(forward, fromDevice.address)
                    }
                    return@launch 
                }
                protocol.deserializeSyncUpdate(json)?.let { protocol.processSyncUpdate(it); return@launch }
            }
        }
    }
}
