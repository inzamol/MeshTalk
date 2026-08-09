package `in`.inzamulhoque.meshtalk.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker
import `in`.inzamulhoque.meshtalk.MeshApplication

class IdentityRotationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): ListenableWorker.Result {
        val app = (applicationContext as? MeshApplication) ?: return ListenableWorker.Result.failure()
        
        return try {
            Log.d("IdentityRotationWorker", "Rotating Stealth ID via WorkManager...")
            val identityManager = app.identityManager
            val networkManager = app.meshNetworkManager
            
            val newStealthId = identityManager.getStealthId()
            networkManager.rotateStealthId(newStealthId)
            
            Log.i("IdentityRotationWorker", "Stealth ID rotated successfully")
            ListenableWorker.Result.success()
        } catch (e: Exception) {
            Log.e("IdentityRotationWorker", "Failed to rotate Stealth ID", e)
            ListenableWorker.Result.retry()
        }
    }
}
