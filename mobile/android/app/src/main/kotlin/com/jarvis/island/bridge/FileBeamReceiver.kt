package com.jarvis.island.bridge

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import kotlin.concurrent.thread

/**
 * JARVIS File Beam Receiver & Local LAN Server on Android:
 * Handles incoming file beams and clipboard synchronization from JARVIS Windows Desktop.
 */
class FileBeamReceiver(private val context: Context) {

    private val TAG = "JarvisFileBeam"
    private var serverSocket: ServerSocket? = null
    private var isRunning: Boolean = false
    private val mainHandler = Handler(Looper.getMainLooper())

    fun startServer(port: Int = 53391, onFileReceived: ((File) -> Unit)? = null) {
        if (isRunning) return
        isRunning = true

        thread(isDaemon = true, name = "Jarvis-Mobile-Bridge-Server") {
            try {
                serverSocket = ServerSocket(port)
                Log.i(TAG, "JARVIS Mobile Bridge Server listening on port $port")

                while (isRunning) {
                    val socket = serverSocket?.accept() ?: break
                    thread {
                        try {
                            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                            val output = socket.getOutputStream()

                            val requestLine = reader.readLine() ?: return@thread
                            val headers = mutableMapOf<String, String>()
                            var line: String?
                            var contentLength = 0

                            while (reader.readLine().also { line = it } != null && line!!.isNotEmpty()) {
                                val parts = line!!.split(":", limit = 2)
                                if (parts.size == 2) {
                                    val key = parts[0].trim().lowercase()
                                    val value = parts[1].trim()
                                    headers[key] = value
                                    if (key == "content-length") {
                                        contentLength = value.toIntOrNull() ?: 0
                                    }
                                }
                            }

                            val body = if (contentLength > 0) {
                                val buffer = CharArray(contentLength)
                                var totalRead = 0
                                while (totalRead < contentLength) {
                                    val read = reader.read(buffer, totalRead, contentLength - totalRead)
                                    if (read == -1) break
                                    totalRead += read
                                }
                                String(buffer, 0, totalRead)
                            } else ""

                            if (requestLine.startsWith("GET /api/status")) {
                                val resp = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"status\":\"ok\",\"device\":\"android-island\"}"
                                output.write(resp.toByteArray(Charsets.UTF_8))
                            } else if (requestLine.startsWith("POST /api/clipboard")) {
                                if (body.isNotEmpty()) {
                                    val json = JSONObject(body)
                                    val text = json.optString("text")
                                    val origin = json.optString("originDeviceId")

                                    if (origin != "jarvis-android-mobile" && text.isNotEmpty()) {
                                        mainHandler.post {
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cm.setPrimaryClip(ClipData.newPlainText("JARVIS Ecosystem", text))
                                            Log.i(TAG, "Copied text from Windows to Android clipboard: ${text.take(30)}...")
                                        }
                                    }
                                }
                                val resp = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"copied\":true}"
                                output.write(resp.toByteArray(Charsets.UTF_8))
                            } else if (requestLine.startsWith("POST /api/transfer")) {
                                if (body.isNotEmpty()) {
                                    val json = JSONObject(body)
                                    val rawFileName = json.optString("fileName", "Received_File.bin")
                                    val safeFileName = rawFileName.replace("/", "_").replace("\\", "_")
                                    val base64Data = json.optString("dataBase64")

                                    val downloadDir = File(
                                        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                                            ?: context.filesDir,
                                        "JARVIS"
                                    ).apply { mkdirs() }

                                    val destFile = File(downloadDir, safeFileName)
                                    val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                                    FileOutputStream(destFile).use { it.write(bytes) }
                                    Log.i(TAG, "Received file: ${destFile.absolutePath} (${bytes.size} bytes)")

                                    mainHandler.post {
                                        onFileReceived?.invoke(destFile)
                                    }
                                }
                                val resp = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"received\":true}"
                                output.write(resp.toByteArray(Charsets.UTF_8))
                            } else {
                                val resp = "HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n"
                                output.write(resp.toByteArray(Charsets.UTF_8))
                            }
                            output.flush()
                            socket.close()
                        } catch (e: Exception) {
                            Log.w(TAG, "Bridge request handling error: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Bridge server socket error: ${e.message}")
            }
        }
    }

    fun stopServer() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // ignore
        }
    }

    companion object {
        fun beamFileToDesktop(
            file: File,
            desktopIp: String,
            desktopPort: Int = 53391,
            onProgress: (Float) -> Unit,
            onComplete: (Boolean) -> Unit
        ) {
            thread(isDaemon = true) {
                try {
                    val bytes = file.readBytes()
                    val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    val payload = JSONObject().apply {
                        put("fileName", file.name)
                        put("fileSize", file.length())
                        put("dataBase64", b64)
                        put("originDeviceId", "jarvis-android-mobile")
                    }

                    val url = URL("http://$desktopIp:$desktopPort/api/transfer")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.doOutput = true
                    conn.connectTimeout = 5000
                    conn.readTimeout = 10000

                    conn.outputStream.use { os: OutputStream ->
                        os.write(payload.toString().toByteArray(Charsets.UTF_8))
                    }

                    val success = conn.responseCode in 200..299
                    conn.disconnect()
                    onProgress(1.0f)
                    onComplete(success)
                } catch (e: Exception) {
                    Log.w("JarvisFileBeam", "Beaming file failed: ${e.message}")
                    onComplete(false)
                }
            }
        }
    }
}
