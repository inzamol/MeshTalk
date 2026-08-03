package `in`.inzamulhoque.meshtalk.ble

import android.bluetooth.*
import android.content.Context
import android.util.Log
import `in`.inzamulhoque.meshtalk.protocol.MeshProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MeshGattServer(
    private val context: Context,
    private val bluetoothManager: BluetoothManager,
    private val protocol: MeshProtocol,
    private val myId: String,
    private val myEncryptionKey: String
) {
    private var gattServer: BluetoothGattServer? = null
    private val scope = CoroutineScope(Dispatchers.IO)

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
            try {
                when (characteristic?.uuid) {
                    MeshConstants.MESSAGE_EXCHANGE_CHAR_UUID -> {
                        val json = String(value ?: byteArrayOf())
                        scope.launch {
                            val message = protocol.deserializeMessage(json)
                            if (message != null) {
                                protocol.processReceivedMessage(message)
                            }
                        }
                        if (responseNeeded) {
                            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
                        }
                    }
                    MeshConstants.INVENTORY_CHAR_UUID -> {
                        Log.d("MeshGattServer", "Received inventory data")
                        if (responseNeeded) {
                            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
                        }
                    }
                    else -> {
                        if (responseNeeded) {
                            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
                        }
                    }
                }
            } catch (e: SecurityException) {
                Log.e("MeshGattServer", "Permission denied in write request", e)
            }
        }
    }
}
