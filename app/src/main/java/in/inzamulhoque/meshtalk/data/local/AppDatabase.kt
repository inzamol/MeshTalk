package `in`.inzamulhoque.meshtalk.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import `in`.inzamulhoque.meshtalk.data.local.dao.MessageDao
import `in`.inzamulhoque.meshtalk.data.local.dao.PeerDao
import `in`.inzamulhoque.meshtalk.data.local.entity.Message
import `in`.inzamulhoque.meshtalk.data.local.entity.Peer

@Database(entities = [Message::class, Peer::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun peerDao(): PeerDao
}
