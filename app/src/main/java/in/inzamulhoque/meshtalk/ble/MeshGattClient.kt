package `in`.inzamulhoque.meshtalk.ble

import android.bluetooth.*
import android.content.Context
import android.util.Log
import `in`.inzamulhoque.meshtalk.protocol.MeshProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MeshGattClient(
    private val context: Context,
    private val device: BluetoothDevice,
    private val protocol: MeshProtocol,
    private val onSyncComplete: () -> Unit
) {
    private var gatt: BluetoothGatt? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val writeQueue = mutableListOf<ByteArray>()
    private var isWriting = false
    private var currentPeerId: String? = null

    fun connect() {
        try {
            gatt = device.connectGatt(context, false, gattCallback)
        } catch (e: SecurityException) {
            Log.e("MeshGattClient", "Permission denied for connecting GATT", e)
        }
    }

    fun disconnect() {
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (e: SecurityException) {
            Log.e("MeshGattClient", "Permission denied for disconnecting GATT", e)
        }
    }

    private fun processWriteQueue(gatt: BluetoothGatt) {
        if (isWriting || writeQueue.isEmpty()) {
            if (writeQueue.isEmpty() && !isWriting) {
                try { gatt.disconnect() } catch (e: SecurityException) {}
            }
            return
        }
        val data = writeQueue.removeAt(0)
        val char = gatt.getService(MeshConstants.SERVICE_UUID)
            ?.getCharacteristic(MeshConstants.MESSAGE_EXCHANGE_CHAR_UUID)
        if (char != null) {
            char.value = data
            try {
                gatt.writeCharacteristic(char)
                isWriting = true
            } catch (e: SecurityException) {
                Log.e("MeshGattClient", "Permission denied writing characteristic", e)
                gatt.disconnect()
            }
        } else {
            gatt.disconnect()
        }
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
                    Log.d("MeshGattClient", "Connected to ${device.address}. Requesting MTU...")
                    gatt?.requestMtu(512)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d("MeshGattClient", "Disconnected from ${device.address}")
                    onSyncComplete()
                }
            } catch (e: SecurityException) {
                Log.e("MeshGattClient", "Permission denied in connection state change", e)
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            Log.d("MeshGattClient", "MTU changed to $mtu, status: $status")
            try {
                gatt?.discoverServices()
            } catch (e: SecurityException) {
                Log.e("MeshGattClient", "Permission denied discovering services", e)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt?.getService(MeshConstants.SERVICE_UUID)
                val identityChar = service?.getCharacteristic(MeshConstants.IDENTITY_CHAR_UUID)
                try {
                    gatt?.readCharacteristic(identityChar)
                } catch (e: SecurityException) {
                    Log.e("MeshGattClient", "Permission denied in services discovered", e)
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
                                scope.launch {
                                    protocol.onPeerDiscovered(peerId, "", null, device.address)
                                    readInventory(gatt)
                                }
                            }
                        } catch (e: SecurityException) {
                            gatt.disconnect()
                        }
                    }
                    MeshConstants.ENCRYPTION_KEY_CHAR_UUID -> {
                        val value = characteristic.value ?: byteArrayOf()
                        val encryptionKey = String(value)
                        val peerId = currentPeerId ?: return
                        scope.launch {
                            protocol.onPeerDiscovered(peerId, "", encryptionKey, device.address)
                            readInventory(gatt)
                        }
                    }
                    MeshConstants.INVENTORY_CHAR_UUID -> {
                        val value = characteristic.value ?: byteArrayOf()
                        val inventoryStr = String(value)
                        val remoteInventory = if (inventoryStr.isEmpty()) emptyList() else inventoryStr.split(",").filter { it.isNotEmpty() }.map { it.toLong() }
                        Log.d("MeshGattClient", "Remote Inventory: $remoteInventory")
                        
                        scope.launch {
                            val messagesToSync = protocol.getMessagesToSync(remoteInventory)
                            Log.d("MeshGattClient", "Messages to sync: ${messagesToSync.size}")
                            messagesToSync.forEach { msg ->
                                writeQueue.add(protocol.serializeMessage(msg).toByteArray())
                            }
                            processWriteQueue(gatt)
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
            isWriting = false
            if (gatt != null) processWriteQueue(gatt)
        }
    }
}
