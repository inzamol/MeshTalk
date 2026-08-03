package `in`.inzamulhoque.meshtalk.data.local.dao

import androidx.room.*
import `in`.inzamulhoque.meshtalk.data.local.entity.Peer
import kotlinx.coroutines.flow.Flow

@Dao
interface PeerDao {
    @Query("SELECT * FROM peers ORDER BY lastSeen DESC")
    fun getAllPeers(): Flow<List<Peer>>

    @Query("SELECT * FROM peers WHERE id = :peerId")
    fun getPeerFlowById(peerId: String): Flow<Peer?>

    @Query("SELECT * FROM peers WHERE id = :peerId")
    suspend fun getPeerById(peerId: String): Peer?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPeer(peer: Peer)

    @Update
    suspend fun updatePeer(peer: Peer)

    @Delete
    suspend fun deletePeer(peer: Peer)
}
