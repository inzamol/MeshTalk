package `in`.inzamulhoque.meshtalk.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import `in`.inzamulhoque.meshtalk.MainActivity
import `in`.inzamulhoque.meshtalk.MeshApplication
import `in`.inzamulhoque.meshtalk.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class MeshForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isStarted = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isStarted) {
            startForegroundService()
            isStarted = true
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        val notification = createNotification("Initializing mesh network...")
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)

        val app = application as MeshApplication
        if (app.initializationError != null) {
            updateNotification("Mesh initialization failed")
            stopSelf()
            return
        }

        try {
            app.meshNetworkManager.start()

            // Observe connected peers to update notification
            app.meshNetworkManager.connectedPeerAddresses
                .onEach { addresses ->
                    val count = addresses.size
                    updateNotification("Mesh Active: $count peer${if (count != 1) "s" else ""} nearby")
                }
                .launchIn(serviceScope)
        } catch (e: Exception) {
            Log.e("MeshService", "Failed to start network manager", e)
            updateNotification("Mesh failed to start")
            stopSelf()
        }
    }

    private fun createNotification(content: String): Notification {
        val pendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Mesh Talk")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_mesh_logo)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(content: String) {
        val notification = createNotification(content)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Mesh Network Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        (application as MeshApplication).meshNetworkManager.stop()
        isStarted = false
    }

    companion object {
        private const val CHANNEL_ID = "MeshServiceChannel"
        private const val NOTIFICATION_ID = 101

        fun start(context: Context) {
            val intent = Intent(context, MeshForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, MeshForegroundService::class.java)
            context.stopService(intent)
        }
    }
}
