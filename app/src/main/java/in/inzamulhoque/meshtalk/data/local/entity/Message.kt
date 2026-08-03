package `in`.inzamulhoque.meshtalk.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@Entity(tableName = "messages")
@JsonClass(generateAdapter = true)
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val senderId: String,
    val receiverId: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isEncrypted: Boolean = true,
    val status: MessageStatus = MessageStatus.SENT,
    val hopCount: Int = 0,
    val expiryTimestamp: Long = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000 // 7 days default
)

enum class MessageStatus {
    SENT, DELIVERED, READ, FAILED, CARRYING
}
