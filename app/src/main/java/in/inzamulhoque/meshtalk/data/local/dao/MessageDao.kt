package `in`.inzamulhoque.meshtalk.data.local.dao

import androidx.room.*
import `in`.inzamulhoque.meshtalk.data.local.entity.Message
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE (senderId = :peerId OR receiverId = :peerId) AND groupId IS NULL ORDER BY timestamp ASC")
    fun getMessagesForPeer(peerId: String): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE groupId = :groupId ORDER BY timestamp ASC")
    fun getMessagesForGroup(groupId: String): Flow<List<Message>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message): Long

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

    @Query("UPDATE messages SET status = :failedStatus WHERE status = :pendingStatus AND timestamp < :threshold")
    suspend fun markStaleMessagesAsFailed(threshold: Long, pendingStatus: String, failedStatus: String)

    @Query("SELECT * FROM messages WHERE status = :pendingStatus")
    suspend fun getPendingMessages(pendingStatus: String): List<Message>

    @Query("DELETE FROM messages WHERE senderId = :peerId OR receiverId = :peerId")
    suspend fun deleteMessagesForPeer(peerId: String)

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()

    @Query("UPDATE messages SET status = 'FAILED' WHERE status = 'PENDING' AND timestamp < :timeoutLimit")
    suspend fun markTimedOutMessagesAsFailed(timeoutLimit: Long)

    @Query("DELETE FROM messages WHERE senderId != :myId AND timestamp < :threshold")
    suspend fun pruneOthersMessages(myId: String, threshold: Long)

    @Query("DELETE FROM messages WHERE senderId = :myId AND timestamp < :threshold")
    suspend fun pruneOwnMessages(myId: String, threshold: Long)

    @Query("SELECT COUNT(*) FROM messages WHERE (senderId = :peerId AND receiverId = :myId) AND groupId IS NULL AND status != 'READ'")
    fun getUnreadCountForPeer(peerId: String, myId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM messages WHERE groupId = :groupId AND senderId != :myId AND status != 'READ'")
    fun getUnreadCountForGroup(groupId: String, myId: String): Flow<Int>

    @Query("SELECT * FROM messages WHERE (senderId = :peerId OR receiverId = :peerId) AND groupId IS NULL ORDER BY timestamp DESC LIMIT 1")
    fun getLastMessageForPeer(peerId: String): Flow<Message?>

    @Query("SELECT * FROM messages WHERE groupId = :groupId ORDER BY timestamp DESC LIMIT 1")
    fun getLastMessageForGroup(groupId: String): Flow<Message?>

    @Query("""
        SELECT messages.* FROM messages 
        JOIN messages_fts ON messages.uuid = messages_fts.uuid
        WHERE messages_fts MATCH :query
    """)
    fun searchMessages(query: String): Flow<List<Message>>
}
