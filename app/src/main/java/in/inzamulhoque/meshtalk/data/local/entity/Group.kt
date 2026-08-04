package `in`.inzamulhoque.meshtalk.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass
import java.util.UUID

@Entity(tableName = "chat_groups")
@JsonClass(generateAdapter = true)
data class Group(
    @PrimaryKey val groupId: String = UUID.randomUUID().toString(),
    val name: String,
    val memberIds: List<String>, // List of Peer IDs
    val adminId: String,
    val createdAt: Long = System.currentTimeMillis()
)
