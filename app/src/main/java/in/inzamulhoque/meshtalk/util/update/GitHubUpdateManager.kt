package `in`.inzamulhoque.meshtalk.util.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import `in`.inzamulhoque.meshtalk.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class NewVersionAvailable(val release: GithubRelease) : UpdateState()
    object NoUpdateAvailable : UpdateState()
    data class Downloading(val progress: Int) : UpdateState()
    data class ReadyToInstall(val apkFile: File) : UpdateState()
    data class Error(val message: String) : UpdateState()
}

class GitHubUpdateManager(private val context: Context) {

    private val owner = "inzamol"
    private val repo = "MeshTalk"

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState = _updateState.asStateFlow()

    private val apiService: GithubApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(GithubApiService::class.java)
    }

    suspend fun checkForUpdates() {
        _updateState.value = UpdateState.Checking
        try {
            val latestRelease = apiService.getLatestRelease(owner, repo)
            val currentVersion = BuildConfig.VERSION_NAME
            
            if (isNewerVersion(latestRelease.tagName, currentVersion)) {
                _updateState.value = UpdateState.NewVersionAvailable(latestRelease)
            } else {
                _updateState.value = UpdateState.NoUpdateAvailable
            }
        } catch (e: Exception) {
            Log.e("UpdateManager", "Check failed", e)
            _updateState.value = UpdateState.Error("Failed to check for updates: ${e.localizedMessage}")
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestClean = latest.removePrefix("v").removePrefix("V").trim()
        val currentClean = current.removePrefix("v").removePrefix("V").trim()

        // 1. Split base version from pre-release tag (e.g., 1.1.0-rc1 -> 1.1.0 and rc1)
        val latestBase = latestClean.split("-")[0]
        val currentBase = currentClean.split("-")[0]

        val latestParts = latestBase.split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = currentBase.split(".").map { it.toIntOrNull() ?: 0 }

        // 2. Compare numeric parts (major.minor.patch)
        for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }

        // 3. Numeric parts are equal, compare pre-release suffixes
        val latestHasTag = latestClean.contains("-")
        val currentHasTag = currentClean.contains("-")

        // Rule: A version without a tag is newer than a version with a tag (1.1.0 > 1.1.0-rc1)
        if (!latestHasTag && currentHasTag) return true
        if (latestHasTag && !currentHasTag) return false

        // Both have tags, compare them (rc1 > beta1 > alpha1)
        if (latestHasTag && currentHasTag) {
            val latestTag = latestClean.substringAfter("-")
            val currentTag = currentClean.substringAfter("-")
            return compareTags(latestTag, currentTag) > 0
        }

        return false
    }

    private fun compareTags(latest: String, current: String): Int {
        if (latest.equals(current, ignoreCase = true)) return 0
        
        fun getPriority(tag: String): Int {
            val t = tag.lowercase()
            return when {
                t.startsWith("rc") -> 3
                t.startsWith("beta") -> 2
                t.startsWith("alpha") -> 1
                else -> 0
            }
        }
        
        val pL = getPriority(latest)
        val pC = getPriority(current)
        
        return if (pL != pC) pL - pC else latest.compareTo(current, ignoreCase = true)
    }

    fun downloadAndInstall(release: GithubRelease) {
        val apkAsset = release.assets.find { it.name.endsWith(".apk") }
            ?: return _updateState.apply { value = UpdateState.Error("No APK found in release assets") }.let {}

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(apkAsset.downloadUrl))
            .setTitle("Mesh Talk Update ${release.tagName}")
            .setDescription("Downloading latest version...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, apkAsset.name)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadId = downloadManager.enqueue(request)
        _updateState.value = UpdateState.Downloading(0)

        val onDownloadComplete = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id == downloadId) {
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursor = downloadManager.query(query)
                    if (cursor.moveToFirst()) {
                        val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        if (DownloadManager.STATUS_SUCCESSFUL == cursor.getInt(statusIdx)) {
                            val localUriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                            val uriString = cursor.getString(localUriIdx)
                            Log.d("UpdateManager", "Download successful. Local URI: $uriString")
                            
                            val uri = Uri.parse(uriString)
                            val file = if (uri.scheme == "file") {
                                File(uri.path!!)
                            } else {
                                // Fallback for content URIs or older Android versions
                                val fileIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_FILENAME)
                                val fileName = if (fileIdx != -1) cursor.getString(fileIdx) else null
                                if (fileName != null) File(fileName) else null
                            }

                            if (file != null && file.exists()) {
                                _updateState.value = UpdateState.ReadyToInstall(file)
                            } else {
                                Log.e("UpdateManager", "File not found after download: $uriString")
                                _updateState.value = UpdateState.Error("Downloaded file not found on disk")
                            }
                        } else {
                            _updateState.value = UpdateState.Error("Download failed")
                        }
                    }
                    cursor.close()
                    context.unregisterReceiver(this)
                }
            }
        }
        
        ContextCompat.registerReceiver(
            context,
            onDownloadComplete,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    fun installApk(file: File) {
        val canInstall = try {
            context.packageManager.canRequestPackageInstalls()
        } catch (_: Exception) { false }

        if (!canInstall) {
            Log.w("UpdateManager", "Requesting INSTALL_UNKNOWN_APPS permission")
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return
        }

        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("UpdateManager", "Failed to start installation", e)
            _updateState.value = UpdateState.Error("Installation failed: ${e.localizedMessage}")
        }
    }
}
