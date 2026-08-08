package `in`.inzamulhoque.meshtalk.data.local.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.UUID

@Keep
@Entity(tableName = "chat_groups")
@JsonClass(generateAdapter = true)
data class Group(
    @PrimaryKey 
    @Json(name = "id") val groupId: String = UUID.randomUUID().toString(),
    
    @Json(name = "n") val name: String,
    
    @Json(name = "m") val memberIds: List<String>, // List of Peer IDs
    
    @Json(name = "a") val adminId: String,
    
    @Json(name = "c") val createdAt: Long = System.currentTimeMillis()
)
