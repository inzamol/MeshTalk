package `in`.inzamulhoque.meshtalk.util

import android.content.Context
import android.util.Base64
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object FileUtils {
    private const val AVATAR_DIR = "avatars"

    fun saveBase64Avatar(context: Context, base64: String): String? {
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val dir = File(context.filesDir, AVATAR_DIR)
            if (!dir.exists()) dir.mkdirs()

            val fileName = "avatar_${UUID.randomUUID()}.jpg"
            val file = File(dir, fileName)
            
            FileOutputStream(file).use { it.write(bytes) }
            file.absolutePath
        } catch (e: Exception) {
            Log.e("FileUtils", "Error saving avatar", e)
            null
        }
    }

    fun deleteFile(path: String?) {
        if (path == null) return
        try {
            val file = File(path)
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            Log.e("FileUtils", "Error deleting file: $path", e)
        }
    }
}
