package com.jarvis.island.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

data class UpdateProgress(
    val percent: Int = 0,
    val downloadedMb: Float = 0f,
    val totalMb: Float = 0f,
    val speedMbps: String = "0.0 MB/s"
)

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class Available(
        val tag: String,
        val sizeMb: Float,
        val downloadUrl: String,
        val releaseNotes: String
    ) : UpdateState()
    data class Downloading(val progress: UpdateProgress) : UpdateState()
    data class ReadyToInstall(val apkFile: File) : UpdateState()
    data class Error(val message: String) : UpdateState()
    object UpToDate : UpdateState()
}

class JarvisAppUpdater(private val context: Context) {

    private val updaterScope = CoroutineScope(Dispatchers.IO + Job())
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val currentVersion = "v1.0.0"

    fun checkForUpdates() {
        _state.value = UpdateState.Checking
        updaterScope.launch {
            try {
                val url = URL("https://api.github.com/repos/NandiVardhan2007/JARVIS/releases/latest")
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "JARVIS-Android-Updater")
                conn.connectTimeout = 8000
                conn.readTimeout = 8000

                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val tagName = json.optString("tag_name", currentVersion)
                    val notes = json.optString("body", "Bug fixes and performance improvements.")
                    val assets = json.optJSONArray("assets")

                    var downloadUrl: String? = null
                    var sizeBytes = 0L

                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                downloadUrl = asset.optString("browser_download_url")
                                sizeBytes = asset.optLong("size", 0L)
                                break
                            }
                        }
                    }

                    if (downloadUrl != null && tagName != currentVersion) {
                        val sizeMb = if (sizeBytes > 0) sizeBytes / (1024f * 1024f) else 18.5f
                        _state.value = UpdateState.Available(
                            tag = tagName,
                            sizeMb = sizeMb,
                            downloadUrl = downloadUrl,
                            releaseNotes = notes
                        )
                    } else {
                        _state.value = UpdateState.UpToDate
                    }
                } else {
                    _state.value = UpdateState.UpToDate
                }
            } catch (e: Exception) {
                _state.value = UpdateState.Error(e.message ?: "Failed to check for updates")
            }
        }
    }

    fun startDownload(downloadUrl: String) {
        _state.value = UpdateState.Downloading(UpdateProgress(0, 0f, 0f, "Connecting..."))
        updaterScope.launch {
            try {
                val url = URL(downloadUrl)
                var conn = url.openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", "JARVIS-Android-Updater")
                conn.connectTimeout = 12000
                conn.readTimeout = 20000

                var responseCode = conn.responseCode
                if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == 307 || responseCode == 308) {
                    val newUrl = conn.getHeaderField("Location")
                    conn.disconnect()
                    conn = URL(newUrl).openConnection() as HttpURLConnection
                    conn.setRequestProperty("User-Agent", "JARVIS-Android-Updater")
                }

                val totalLength = conn.contentLength.toLong()
                val totalMb = if (totalLength > 0) totalLength / (1024f * 1024f) else 20.0f

                val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
                val targetApk = File(updatesDir, "JARVIS-Update.apk")
                if (targetApk.exists()) targetApk.delete()

                val input: InputStream = conn.inputStream
                val output = FileOutputStream(targetApk)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead = 0L
                var lastTime = System.currentTimeMillis()
                var lastBytes = 0L

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead

                    val now = System.currentTimeMillis()
                    if (now - lastTime >= 200) {
                        val durationSec = (now - lastTime) / 1000f
                        val speedBytesPerSec = if (durationSec > 0) (totalBytesRead - lastBytes) / durationSec else 0f
                        val speedMb = speedBytesPerSec / (1024f * 1024f)
                        val speedStr = "%.1f MB/s".format(speedMb)

                        val downloadedMb = totalBytesRead / (1024f * 1024f)
                        val percent = if (totalLength > 0) ((totalBytesRead * 100) / totalLength).toInt() else 50

                        lastTime = now
                        lastBytes = totalBytesRead

                        _state.value = UpdateState.Downloading(
                            UpdateProgress(
                                percent = percent.coerceIn(0, 99),
                                downloadedMb = downloadedMb,
                                totalMb = totalMb,
                                speedMbps = speedStr
                            )
                        )
                    }
                }

                output.flush()
                output.close()
                input.close()

                _state.value = UpdateState.ReadyToInstall(targetApk)
            } catch (e: Exception) {
                _state.value = UpdateState.Error(e.message ?: "Download interrupted")
            }
        }
    }

    fun installApk(apkFile: File) {
        if (!apkFile.exists()) {
            _state.value = UpdateState.Error("Update file missing")
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            _state.value = UpdateState.Error("Installation failed: ${e.message}")
        }
    }
}
