package `in`.inzamulhoque.meshtalk.data.local.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.UUID

@Keep
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
    
    @Json(name = "lp") val localPlaintext: String? = null,
    
    @Json(name = "t") val timestamp: Long = System.currentTimeMillis(),
    
    @Json(name = "e") val isEncrypted: Boolean = true,
    
    @Json(name = "st") val status: MessageStatus = MessageStatus.SENT,
    
    @Json(name = "h") val hopCount: Int = 0,
    
    @Json(name = "x") val expiryTimestamp: Long = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000, // 7 days default
    
    @Json(name = "ty") val type: MessageType = MessageType.TEXT,
    
    @Json(name = "mu") val mediaUri: String? = null,

    @Json(name = "g") val groupId: String? = null
)

@Keep
enum class MessageStatus {
    @Json(name = "PENDING") PENDING,
    @Json(name = "SENT") SENT,
    @Json(name = "DELIVERED") DELIVERED,
    @Json(name = "READ") READ,
    @Json(name = "FAILED") FAILED,
    @Json(name = "CARRYING") CARRYING
}

enum class MessageType {
    @Json(name = "TEXT") TEXT,
    @Json(name = "IMAGE") IMAGE,
    @Json(name = "FILE") FILE
}
