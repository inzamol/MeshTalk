package `in`.inzamulhoque.meshtalk.data.local.dao

import androidx.room.*
import `in`.inzamulhoque.meshtalk.data.local.entity.Message
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE senderId = :peerId OR receiverId = :peerId ORDER BY timestamp ASC")
    fun getMessagesForPeer(peerId: String): Flow<List<Message>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message)

    @Delete
    suspend fun deleteMessage(message: Message)

    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    suspend fun updateMessageStatus(messageId: Long, status: String)

    @Query("SELECT * FROM messages WHERE receiverId != :myId AND expiryTimestamp > :currentTime")
    suspend fun getMessagesToForward(myId: String, currentTime: Long): List<Message>

    @Query("SELECT * FROM messages WHERE receiverId = :receiverId")
    suspend fun getMessagesForReceiver(receiverId: String): List<Message>

    @Query("SELECT uuid FROM messages")
    suspend fun getAllMessageUuids(): List<String>

    @Query("SELECT * FROM messages WHERE uuid = :uuid LIMIT 1")
    suspend fun getMessageByUuid(uuid: String): Message?

    @Query("SELECT id FROM messages")
    suspend fun getAllMessageIds(): List<Long>
}
