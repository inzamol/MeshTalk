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
import android.util.Log

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

    var currentChatPeerId: String? = null

    var initializationError: Throwable? = null
        private set

    override fun onCreate() {
        super.onCreate()
        Log.i("MeshApplication", "--- Application onCreate Started ---")
        
        try {
            Log.d("MeshApplication", "[1/5] Initializing SettingsManager...")
            settingsManager = SettingsManager(this)

            database = try {
                Room.databaseBuilder(
                    applicationContext,
                    AppDatabase::class.java,
                    "meshtalk_db",
                ).addMigrations(AppDatabase.MIGRATION_11_12)
                    .fallbackToDestructiveMigration(dropAllTables = false)
                    .build()
            } catch (e: Exception) {
                Log.e("MeshApplication", "Database initialization failed", e)
                throw RuntimeException("Database failed to initialize: ${e.message}", e)
            }

            Log.d("MeshApplication", "[3/5] Initializing CryptoManager...")
            try {
                cryptoManager = CryptoManager(this)
            } catch (e: Exception) {
                Log.e("MeshApplication", "CryptoManager initialization failed", e)
                throw RuntimeException("Crypto failed to initialize: ${e.message}", e)
            }

            Log.d("MeshApplication", "[4/5] Initializing IdentityManager...")
            try {
                identityManager = IdentityManager(cryptoManager, settingsManager)
            } catch (e: Exception) {
                Log.e("MeshApplication", "IdentityManager initialization failed", e)
                throw RuntimeException("Identity failed to initialize: ${e.message}", e)
            }

            Log.d("MeshApplication", "[5/5] Initializing MeshNetworkManager...")
            try {
                meshNetworkManager = MeshNetworkManager(this, database, identityManager, settingsManager)
            } catch (e: Exception) {
                Log.e("MeshApplication", "MeshNetworkManager initialization failed", e)
                throw RuntimeException("Network failed to initialize: ${e.message}", e)
            }

            Log.d("MeshApplication", "Registering LifecycleObserver...")
            ProcessLifecycleOwner.get().lifecycle.addObserver(
                LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_START) {
                        isAppInForeground = true
                        Log.d("MeshApplication", "App entered FOREGROUND")
                    } else if (event == Lifecycle.Event.ON_STOP) {
                        isAppInForeground = false
                        Log.d("MeshApplication", "App entered BACKGROUND")
                    }
                }
            )
            Log.i("MeshApplication", "--- Application Initialization Successful ---")
        } catch (e: Throwable) {
            initializationError = e
            Log.e("MeshApplication", "--- Application Initialization FAILED ---", e)
        }
    }
}
