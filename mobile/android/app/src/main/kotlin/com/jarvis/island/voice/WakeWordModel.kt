package com.jarvis.island.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Downloads and unpacks the small English Vosk model used for the offline
 * "Hey JARVIS" wake word. The model is stored in internal app storage.
 */
object WakeWordModel {

    const val MODEL_DIR = "vosk-model-small-en-us-0.15"
    private const val MODEL_URL = "https://alphacephei.com/vosk/models/$MODEL_DIR.zip"
    private const val MAX_BYTES = 120L * 1024 * 1024

    private val handler = Handler(Looper.getMainLooper())

    fun path(context: Context): File = File(context.filesDir, MODEL_DIR)

    fun isInstalled(context: Context): Boolean {
        val dir = path(context)
        return dir.isDirectory && File(dir, "am").isDirectory && File(dir, "conf").isDirectory
    }

    fun download(
        context: Context,
        onProgress: (Int) -> Unit,
        onDone: (Boolean, String?) -> Unit
    ) {
        Thread {
            var error: String? = null
            var success = false
            try {
                val target = path(context)
                val staging = File(context.filesDir, "$MODEL_DIR.partial")
                if (staging.exists()) staging.deleteRecursively()
                staging.mkdirs()

                val connection = URL(MODEL_URL).openConnection() as HttpURLConnection
                connection.connectTimeout = 15_000
                connection.readTimeout = 60_000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "JARVIS-Android/1.0")
                val total = connection.contentLengthLong.coerceAtLeast(-1)
                if (connection.responseCode !in 200..299) {
                    throw IllegalStateException("Model download failed (HTTP ${connection.responseCode})")
                }
                var received = 0L
                var lastPercent = -1
                ZipInputStream(connection.inputStream.buffered()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val outFile = File(staging, entry.name)
                        if (!outFile.canonicalPath.startsWith(staging.canonicalPath)) {
                            throw IllegalStateException("Unsafe model archive entry")
                        }
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                            continue
                        }
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { out ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val read = zip.read(buffer)
                                if (read <= 0) break
                                out.write(buffer, 0, read)
                                received += read
                                if (received > MAX_BYTES) {
                                    throw IllegalStateException("Model archive is unexpectedly large")
                                }
                                if (total > 0) {
                                    val percent = ((received * 100) / total).toInt().coerceIn(0, 99)
                                    if (percent != lastPercent) {
                                        lastPercent = percent
                                        handler.post { onProgress(percent) }
                                    }
                                }
                            }
                        }
                        zip.closeEntry()
                    }
                }
                connection.disconnect()

                val unpacked = staging.listFiles()?.firstOrNull { it.name.startsWith("vosk-model") }
                if (unpacked == null || !File(unpacked, "am").isDirectory) {
                    throw IllegalStateException("The downloaded model is incomplete")
                }
                if (target.exists()) target.deleteRecursively()
                if (!unpacked.renameTo(target)) {
                    unpacked.copyRecursively(target, overwrite = true)
                    unpacked.deleteRecursively()
                }
                staging.deleteRecursively()
                handler.post { onProgress(100) }
                success = true
            } catch (e: Exception) {
                error = e.message ?: "Model download failed"
            }
            handler.post { onDone(success, error) }
        }.apply { isDaemon = true }.start()
    }
}
