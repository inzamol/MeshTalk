package `in`.inzamulhoque.meshtalk

import android.app.Application
import androidx.room.Room
import `in`.inzamulhoque.meshtalk.ble.MeshNetworkManager
import `in`.inzamulhoque.meshtalk.crypto.CryptoManager
import `in`.inzamulhoque.meshtalk.crypto.IdentityManager
import `in`.inzamulhoque.meshtalk.data.local.AppDatabase
import `in`.inzamulhoque.meshtalk.util.SettingsManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner

class MeshApplication : Application() {
    lateinit var database: AppDatabase
        private set
    lateinit var identityManager: IdentityManager
        private set
    lateinit var cryptoManager: CryptoManager
        private set
    lateinit var meshNetworkManager: MeshNetworkManager
        private set
    lateinit var settingsManager: SettingsManager
        private set

    var isAppInForeground: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "meshtalk_db"
        ).fallbackToDestructiveMigration()
            .build()
        settingsManager = SettingsManager(this)
        cryptoManager = CryptoManager(this)
        identityManager = IdentityManager(cryptoManager, settingsManager)
        meshNetworkManager = MeshNetworkManager(this, database, identityManager)

        ProcessLifecycleOwner.get().lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                isAppInForeground = true
            } else if (event == Lifecycle.Event.ON_STOP) {
                isAppInForeground = false
            }
        })
    }
}
