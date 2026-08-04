package `in`.inzamulhoque.meshtalk

import android.app.Application
import androidx.room.Room
import `in`.inzamulhoque.meshtalk.crypto.CryptoManager
import `in`.inzamulhoque.meshtalk.crypto.IdentityManager
import `in`.inzamulhoque.meshtalk.data.local.AppDatabase

class MeshApplication : Application() {
    lateinit var database: AppDatabase
        private set
    lateinit var identityManager: IdentityManager
        private set
    lateinit var cryptoManager: CryptoManager
        private set

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "meshtalk_db"
        ).fallbackToDestructiveMigration()
            .build()
        cryptoManager = CryptoManager(this)
        identityManager = IdentityManager(cryptoManager)
    }
}
