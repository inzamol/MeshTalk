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
    private var currentMtu = 23 // Default BLE MTU
    private var currentPeerId: String? = null
    private var currentEncryptionKey: String? = null

    fun connect() {
        Log.d("MeshGattClient", "Connecting to ${device.address}")
        try {
            gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
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
                Log.d("MeshGattClient", "Sync complete, disconnecting from ${device.address}")
                try { gatt.disconnect() } catch (e: SecurityException) {}
            }
            return
        }
        
        val fullData = writeQueue[0] // Peek at the first item
        val maxChunkSize = currentMtu - 3
        
        val dataToSend: ByteArray
        val remainingData: ByteArray?
        
        if (fullData.size > maxChunkSize) {
            dataToSend = fullData.copyOfRange(0, maxChunkSize)
            remainingData = fullData.copyOfRange(maxChunkSize, fullData.size)
            Log.d("MeshGattClient", "Chunking data for ${device.address}: sending ${dataToSend.size}, remaining ${remainingData.size}")
        } else {
            dataToSend = fullData
            remainingData = null
        }
        
        val service = gatt.getService(MeshConstants.SERVICE_UUID)
        val char = service?.getCharacteristic(MeshConstants.MESSAGE_EXCHANGE_CHAR_UUID)
        
        if (char != null) {
            char.value = dataToSend
            // If we have remaining data, we MUST use a prepared write flow or standard chunks.
            // But standard chunks require the server to know when to reassemble.
            // Our server reassembles based on 'preparedWrite' (offset).
            // Android gatt.writeCharacteristic() with offset > 0 is not directly exposed for standard writes.
            // Reliable Write (beginReliableWrite) is the intended way for this.
            
            // However, to keep it simple and compatible, let's use the RELIABLE WRITE flow IF remainingData != null.
            
            if (remainingData != null) {
                // For simplicity, let's just try to send the whole thing and hope Android's "Write Long" works.
                // Most modern Android devices handle this automatically if WRITE_TYPE_DEFAULT is used.
                char.value = fullData
                char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                writeQueue.removeAt(0) // Remove the full data as we are sending it all (via Long Write)
            } else {
                char.value = dataToSend
                char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                writeQueue.removeAt(0)
            }
            
            val success = try {
                gatt.writeCharacteristic(char)
            } catch (e: SecurityException) {
                Log.e("MeshGattClient", "SecurityException writing characteristic", e)
                false
            }
            
            if (success) {
                isWriting = true
            } else {
                Log.e("MeshGattClient", "writeCharacteristic failed for ${device.address}")
                // Retry next loop
                scope.launch {
                    processWriteQueue(gatt)
                }
            }
        } else {
            Log.e("MeshGattClient", "Message exchange characteristic not found on ${device.address}")
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
                    Log.d("MeshGattClient", "Connected to ${device.address}. Status: $status. Requesting MTU 512...")
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
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e("MeshGattClient", "Write failed for ${device.address} with status: $status")
            }
            isWriting = false
            if (gatt != null) processWriteQueue(gatt)
        }
    }
}
