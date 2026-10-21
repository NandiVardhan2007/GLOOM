package com.jarvis.island.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.jarvis.island.BuildConfig
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
        val releaseNotes: String,
        val checksumUrl: String? = null
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

    private val currentVersion = "v${BuildConfig.VERSION_NAME}"

    private fun isNewerVersion(target: String): Boolean {
        val current = BuildConfig.VERSION_NAME.trim().removePrefix("v").split("-")[0]
            .split(".").map { it.toIntOrNull() ?: 0 }
        val candidate = target.trim().removePrefix("v").split("-")[0]
            .split(".").map { it.toIntOrNull() ?: 0 }
        val size = maxOf(current.size, candidate.size)
        for (i in 0 until size) {
            val diff = (candidate.getOrElse(i) { 0 }) - (current.getOrElse(i) { 0 })
            if (diff != 0) return diff > 0
        }
        return false
    }

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
                    var checksumUrl: String? = null
                    var sizeBytes = 0L

                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                downloadUrl = asset.optString("browser_download_url")
                                sizeBytes = asset.optLong("size", 0L)
                            } else if (name.endsWith(".apk.sha256", ignoreCase = true)) {
                                checksumUrl = asset.optString("browser_download_url")
                            }
                        }
                    }

                    if (downloadUrl != null && isNewerVersion(tagName)) {
                        val sizeMb = if (sizeBytes > 0) sizeBytes / (1024f * 1024f) else 18.5f
                        _state.value = UpdateState.Available(
                            tag = tagName,
                            sizeMb = sizeMb,
                            downloadUrl = downloadUrl,
                            releaseNotes = notes,
                            checksumUrl = checksumUrl
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

    fun startDownload(downloadUrl: String, checksumUrl: String? = null) {
        _state.value = UpdateState.Downloading(UpdateProgress(0, 0f, 0f, "Connecting..."))
        updaterScope.launch {
            try {
                val url = URL(downloadUrl)
                if (!url.protocol.equals("https", ignoreCase = true)) {
                    _state.value = UpdateState.Error("Update rejected: Insecure transport protocol.")
                    return@launch
                }
                val host = url.host.lowercase()
                val isApprovedHost = host == "github.com" || 
                    host == "api.github.com" || 
                    host == "objects.githubusercontent.com" || 
                    host.endsWith(".github.com") || 
                    host.endsWith(".githubusercontent.com")
                if (!isApprovedHost) {
                    _state.value = UpdateState.Error("Update rejected: Host '$host' is not an approved release host.")
                    return@launch
                }
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
                    val redirectUrl = URL(newUrl)
                    if (!redirectUrl.protocol.equals("https", ignoreCase = true)) {
                        _state.value = UpdateState.Error("Update rejected: Redirect downgraded to insecure transport.")
                        return@launch
                    }
                    val redirectHost = redirectUrl.host.lowercase()
                    val isApprovedRedirect = redirectHost == "github.com" ||
                        redirectHost.endsWith(".github.com") ||
                        redirectHost.endsWith(".githubusercontent.com")
                    if (!isApprovedRedirect) {
                        _state.value = UpdateState.Error("Update rejected: Redirect to '$redirectHost' is not approved.")
                        return@launch
                    }
                    conn = redirectUrl.openConnection() as HttpURLConnection
                    conn.setRequestProperty("User-Agent", "JARVIS-Android-Updater")
                    conn.connectTimeout = 12000
                    conn.readTimeout = 20000
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

                if (checksumUrl.isNullOrBlank()) {
                    targetApk.delete()
                    _state.value = UpdateState.Error("Update rejected: the release does not publish a SHA-256 checksum.")
                    return@launch
                }
                val expected = fetchChecksum(checksumUrl)
                val actual = sha256Of(targetApk)
                if (expected == null || actual == null || !expected.equals(actual, ignoreCase = true)) {
                    targetApk.delete()
                    _state.value = UpdateState.Error("SHA-256 verification failed. Update aborted.")
                    return@launch
                }

                _state.value = UpdateState.ReadyToInstall(targetApk)
            } catch (e: Exception) {
                _state.value = UpdateState.Error(e.message ?: "Download interrupted")
            }
        }
    }

    private fun fetchChecksum(urlString: String): String? {
        return try {
            val url = URL(urlString)
            if (!url.protocol.equals("https", ignoreCase = true)) return null
            val host = url.host.lowercase()
            val approved = host == "github.com" ||
                host.endsWith(".github.com") ||
                host.endsWith(".githubusercontent.com")
            if (!approved) return null

            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "JARVIS-Android-Updater")
            conn.connectTimeout = 12000
            conn.readTimeout = 12000
            if (conn.responseCode != 200) return null
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            Regex("[a-fA-F0-9]{64}").find(text)?.value?.lowercase()
        } catch (_: Exception) {
            null
        }
    }

    private fun sha256Of(file: File): String? {
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(65536)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
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
