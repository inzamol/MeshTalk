package `in`.inzamulhoque.meshtalk.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

object ToastHelper {
    private val handler = Handler(Looper.getMainLooper())

    fun showToast(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        handler.post {
            Toast.makeText(context.applicationContext, message, duration).show()
        }
    }
}
