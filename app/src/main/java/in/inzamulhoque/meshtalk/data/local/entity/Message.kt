package `in`.inzamulhoque.meshtalk.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass
import java.util.UUID

@Entity(
    tableName = "messages",
    indices = [Index(value = ["uuid"], unique = true)]
)
@JsonClass(generateAdapter = true)
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val senderId: String,
    val receiverId: String,
    val content: String,
    val localPlaintext: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isEncrypted: Boolean = true,
    val status: MessageStatus = MessageStatus.SENT,
    val hopCount: Int = 0,
    val expiryTimestamp: Long = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000 // 7 days default
)

enum class MessageStatus {
    SENT, DELIVERED, READ, FAILED, CARRYING
}
