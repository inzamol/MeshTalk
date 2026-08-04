package `in`.inzamulhoque.meshtalk.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.UUID

@Entity(
    tableName = "messages",
    indices = [Index(value = ["uuid"], unique = true)]
)
@JsonClass(generateAdapter = true)
data class Message(
    @PrimaryKey(autoGenerate = true) 
    @Json(name = "i") val id: Long = 0,
    
    @Json(name = "u") val uuid: String = UUID.randomUUID().toString(),
    
    @Json(name = "s") val senderId: String,
    
    @Json(name = "r") val receiverId: String,
    
    @Json(name = "c") val content: String,
    
    val localPlaintext: String? = null,
    
    @Json(name = "t") val timestamp: Long = System.currentTimeMillis(),
    
    @Json(name = "e") val isEncrypted: Boolean = true,
    
    @Json(name = "st") val status: MessageStatus = MessageStatus.SENT,
    
    @Json(name = "h") val hopCount: Int = 0,
    
    @Json(name = "x") val expiryTimestamp: Long = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000, // 7 days default
    
    @Json(name = "ty") val type: MessageType = MessageType.TEXT,
    
    val mediaUri: String? = null,

    @Json(name = "g") val groupId: String? = null
)

enum class MessageStatus {
    PENDING, SENT, DELIVERED, READ, FAILED, CARRYING
}

enum class MessageType {
    TEXT, IMAGE, FILE
}
