package `in`.inzamulhoque.meshtalk.ble

import java.util.UUID

object MeshConstants {
    val SERVICE_UUID: UUID = UUID.fromString("00001805-0000-1000-8000-00805f9b34fb") // Example UUID, should be unique
    val IDENTITY_CHAR_UUID: UUID = UUID.fromString("00002a2b-0000-1000-8000-00805f9b34fb")
    val ENCRYPTION_KEY_CHAR_UUID: UUID = UUID.fromString("00002a2e-0000-1000-8000-00805f9b34fb")
    val INVENTORY_CHAR_UUID: UUID = UUID.fromString("00002a2c-0000-1000-8000-00805f9b34fb")
    val MESSAGE_EXCHANGE_CHAR_UUID: UUID = UUID.fromString("00002a2d-0000-1000-8000-00805f9b34fb")
}
