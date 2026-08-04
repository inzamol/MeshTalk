package `in`.inzamulhoque.meshtalk.data.local.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey

@Keep
@Entity(tableName = "peers")
data class Peer(
    @PrimaryKey val id: String, // Likely the public key or a hash of it
    val publicKey: String,
    val encryptionKey: String? = null,
    val displayName: String?,
    val deviceAddress: String?, // BLE MAC address
    val lastSeen: Long = System.currentTimeMillis(),
    val lastSyncTimestamp: Long = 0,
    val avatarUri: String? = null,
    val bio: String? = null,
    val rssi: Int = -100,
    val isVerified: Boolean = false
)
