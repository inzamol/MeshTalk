package `in`.inzamulhoque.meshtalk.data.local.dao

import androidx.room.*
import `in`.inzamulhoque.meshtalk.data.local.entity.Group
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups")
    fun getAllGroups(): Flow<List<Group>>

    @Query("SELECT * FROM groups WHERE groupId = :groupId LIMIT 1")
    suspend fun getGroupById(groupId: String): Group?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: Group)

    @Delete
    suspend fun deleteGroup(group: Group)

    @Query("SELECT * FROM groups WHERE memberIds LIKE :peerId")
    suspend fun getGroupsForPeer(peerId: String): List<Group>
}
