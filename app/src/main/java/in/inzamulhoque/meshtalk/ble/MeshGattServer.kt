package `in`.inzamulhoque.meshtalk.ble

import android.bluetooth.*
import android.content.Context
import android.util.Log
import `in`.inzamulhoque.meshtalk.protocol.MeshProtocol
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class MeshGattServer(
    private val context: Context,
    private val bluetoothManager: BluetoothManager,
    private val protocol: MeshProtocol,
    private val myId: String,
    private val myEncryptionKey: String,
    private val myDisplayName: String
) {
    private var gattServer: BluetoothGattServer? = null
    
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("MeshGattServer", "Unhandled coroutine exception", throwable)
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + exceptionHandler)
    private val writeBuffers = ConcurrentHashMap<String, ByteArray>() // Device address to data

    fun start() {
        try {
            gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
            val service = BluetoothGattService(
                MeshConstants.SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY
            )

            val identityChar = BluetoothGattCharacteristic(
                MeshConstants.IDENTITY_CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
            )
            service.addCharacteristic(identityChar)

            val encryptionChar = BluetoothGattCharacteristic(
                MeshConstants.ENCRYPTION_KEY_CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
            )
            service.addCharacteristic(encryptionChar)

            val nameChar = BluetoothGattCharacteristic(
                MeshConstants.DISPLAY_NAME_CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
            )
            service.addCharacteristic(nameChar)

            val inventoryChar = BluetoothGattCharacteristic(
                MeshConstants.INVENTORY_CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
            )
            service.addCharacteristic(inventoryChar)

            val messageChar = BluetoothGattCharacteristic(
                MeshConstants.MESSAGE_EXCHANGE_CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )
            service.addCharacteristic(messageChar)

            gattServer?.addService(service)
            Log.d("MeshGattServer", "GATT Server started and service added")
        } catch (e: SecurityException) {
            Log.e("MeshGattServer", "Permission denied for opening GATT server", e)
        }
    }

    fun stop() {
        try {
            gattServer?.close()
        } catch (e: SecurityException) {
            Log.e("MeshGattServer", "Permission denied for closing GATT server", e)
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("MeshGattServer", "Device connected: ${device?.address}")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d("MeshGattServer", "Device disconnected: ${device?.address}")
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic?
        ) {
            val device = device ?: return
            try {
                when (characteristic?.uuid) {
                    MeshConstants.IDENTITY_CHAR_UUID -> {
                        val data = myId.toByteArray()
                        val response = if (offset < data.size) data.copyOfRange(offset, data.size) else byteArrayOf()
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, response)
                    }
                    MeshConstants.ENCRYPTION_KEY_CHAR_UUID -> {
                        val data = myEncryptionKey.toByteArray()
                        val response = if (offset < data.size) data.copyOfRange(offset, data.size) else byteArrayOf()
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, response)
                    }
                    MeshConstants.DISPLAY_NAME_CHAR_UUID -> {
                        val data = myDisplayName.toByteArray()
                        val response = if (offset < data.size) data.copyOfRange(offset, data.size) else byteArrayOf()
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, response)
                    }
                    MeshConstants.INVENTORY_CHAR_UUID -> {
                        scope.launch {
                            val inventory = protocol.getInventory().joinToString(",").toByteArray()
                            val response = if (offset < inventory.size) inventory.copyOfRange(offset, inventory.size) else byteArrayOf()
                            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, response)
                        }
                    }
                    else -> {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
                    }
                }
            } catch (e: SecurityException) {
                Log.e("MeshGattServer", "Permission denied in read request", e)
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            val device = device ?: return
            val characteristic = characteristic ?: return
            val data = value ?: byteArrayOf()
            
            try {
                if (characteristic.uuid == MeshConstants.MESSAGE_EXCHANGE_CHAR_UUID) {
                    if (preparedWrite) {
                        // Buffer the data
                        val currentBuffer = writeBuffers[device.address] ?: byteArrayOf()
                        if (offset == 0) {
                            writeBuffers[device.address] = data
                        } else if (offset == currentBuffer.size) {
                            writeBuffers[device.address] = currentBuffer + data
                        } else {
                            Log.w("MeshGattServer", "Invalid offset in prepared write: $offset, buffer size: ${currentBuffer.size}")
                        }
                        
                        if (responseNeeded) {
                            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, data)
                        }
                    } else {
                        // Immediate write
                        val json = String(data)
                        processReceivedJson(json)
                        if (responseNeeded) {
                            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
                        }
                    }
                } else if (characteristic.uuid == MeshConstants.INVENTORY_CHAR_UUID) {
                    if (responseNeeded) {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
                    }
                } else {
                    if (responseNeeded) {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
                    }
                }
            } catch (e: SecurityException) {
                Log.e("MeshGattServer", "Permission denied in write request", e)
            }
        }

        override fun onExecuteWrite(device: BluetoothDevice?, requestId: Int, execute: Boolean) {
            val device = device ?: return
            try {
                if (execute) {
                    val data = writeBuffers.remove(device.address)
                    if (data != null) {
                        val json = String(data)
                        processReceivedJson(json)
                    }
                } else {
                    writeBuffers.remove(device.address)
                }
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            } catch (e: SecurityException) {
                Log.e("MeshGattServer", "Permission denied in execute write", e)
            }
        }

        private fun processReceivedJson(json: String) {
            scope.launch {
                try {
                    val message = protocol.deserializeMessage(json)
                    if (message != null) {
                        Log.d("MeshGattServer", "Processed message from ${message.senderId} (size: ${json.length})")
                        protocol.processReceivedMessage(message)
                    } else {
                        Log.e("MeshGattServer", "Failed to deserialize message: ${json.take(50.coerceAtMost(json.length))}...")
                    }
                } catch (e: Exception) {
                    Log.e("MeshGattServer", "Error processing received JSON", e)
                }
            }
        }
    }
}
