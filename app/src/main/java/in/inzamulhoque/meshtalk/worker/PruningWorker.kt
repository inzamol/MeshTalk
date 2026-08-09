package `in`.inzamulhoque.meshtalk.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker
import `in`.inzamulhoque.meshtalk.MeshApplication

class PruningWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): ListenableWorker.Result {
        val app = (applicationContext as? MeshApplication) ?: return ListenableWorker.Result.failure()
        
        return try {
            val database = app.database
            val settingsManager = app.settingsManager
            val myId = app.identityManager.getMyId()
            val now = System.currentTimeMillis()
            
            Log.d("PruningWorker", "Starting scheduled database pruning...")
            
            // Prune others messages
            val othersThreshold = now - (settingsManager.pruneOthersMessagesDays.toLong() * 24 * 60 * 60 * 1000)
            database.messageDao().pruneOthersMessages(myId, othersThreshold)
            
            // Prune own messages if enabled
            if (settingsManager.isPruningOwnMessagesEnabled) {
                val ownThreshold = now - (settingsManager.pruneOwnMessagesDays.toLong() * 24 * 60 * 60 * 1000)
                database.messageDao().pruneOwnMessages(myId, ownThreshold)
            }
            
            Log.i("PruningWorker", "Database pruning completed successfully")
            ListenableWorker.Result.success()
        } catch (e: Exception) {
            Log.e("PruningWorker", "Error during scheduled pruning", e)
            ListenableWorker.Result.retry()
        }
    }
}
