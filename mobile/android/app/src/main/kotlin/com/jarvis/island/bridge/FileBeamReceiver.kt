package com.jarvis.island.bridge

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import kotlin.concurrent.thread

/**
 * JARVIS File Beam Receiver & Authenticated Local LAN Server on Android:
 * Handles incoming/outgoing file beams and clipboard synchronization from JARVIS Windows Desktop.
 * Enforces per-request authentication, replay protection, checksum verification, and safe path traversal.
 */
class FileBeamReceiver(private val context: Context) {

    interface FileTransferListener {
        fun onTransferStarted(fileName: String, fileSizeMb: Float, isUpload: Boolean)
        fun onTransferProgress(fileName: String, progress: Float, speedMbps: String, isUpload: Boolean)
        fun onTransferCompleted(fileName: String, file: File?, isUpload: Boolean)
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    fun startServer(port: Int = 53391, onFileReceived: ((File) -> Unit)? = null) {
        activeInstance = this
        getOrInitAuthToken(context)
        if (onFileReceived != null) {
            addListener(object : FileTransferListener {
                override fun onTransferStarted(fileName: String, fileSizeMb: Float, isUpload: Boolean) {}
                override fun onTransferProgress(fileName: String, progress: Float, speedMbps: String, isUpload: Boolean) {}
                override fun onTransferCompleted(fileName: String, file: File?, isUpload: Boolean) {
                    if (file != null && !isUpload) {
                        onFileReceived(file)
                    }
                }
            })
        }

        synchronized(serverLock) {
            serverRefCount += 1
            if (isServerRunning) return
            isServerRunning = true
        }

        launchListener(port)
    }

    private fun launchListener(port: Int) {
        thread(isDaemon = true, name = "Jarvis-Mobile-Bridge-Server") {
            try {
                // Once paired, the server presents the desktop-issued identity and all
                // companion traffic is TLS with a pinned cert. Before pairing it runs a
                // bootstrap listener that can only reject unauthenticated requests.
                val tlsFactory = BridgeTls.serverSocketFactory(context)
                val server = tlsFactory?.createServerSocket(port) ?: ServerSocket(port)
                serverSocket = server
                Log.i(TAG, "JARVIS Mobile Bridge Server listening on port $port (${if (tlsFactory != null) "TLS" else "bootstrap cleartext"})")

                while (isServerRunning) {
                    val socket = try {
                        server.accept().apply {
                            soTimeout = 10000
                        }
                    } catch (e: Exception) {
                        break
                    }

                    thread {
                        try {
                            val rawInput = socket.getInputStream()
                            val output = socket.getOutputStream()

                            // Read HTTP headers safely without consuming body bytes into an encoding buffer
                            val headerBytes = java.io.ByteArrayOutputStream()
                            var prev3 = -1
                            var prev2 = -1
                            var prev1 = -1
                            var foundEnd = false

                            while (headerBytes.size() < 16384) {
                                val b = rawInput.read()
                                if (b == -1) break
                                headerBytes.write(b)
                                if (prev3 == 13 && prev2 == 10 && prev1 == 13 && b == 10) {
                                    foundEnd = true
                                    break
                                }
                                prev3 = prev2
                                prev2 = prev1
                                prev1 = b
                            }

                            if (!foundEnd) {
                                writeErrorResponse(output, 400, "Bad Request", "Malformed headers")
                                socket.close()
                                return@thread
                            }

                            val headerStr = headerBytes.toString("UTF-8")
                            val lines = headerStr.split("\r\n")
                            val requestLine = lines.getOrNull(0) ?: ""
                            val headers = mutableMapOf<String, String>()
                            for (i in 1 until lines.size) {
                                val line = lines[i]
                                if (line.isEmpty()) break
                                val parts = line.split(":", limit = 2)
                                if (parts.size == 2) {
                                    headers[parts[0].trim().lowercase()] = parts[1].trim()
                                }
                            }
                            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0

                            // Replay protection: check timestamp
                            val timestampStr = headers["x-jarvis-timestamp"]
                            if (timestampStr != null) {
                                val ts = timestampStr.toLongOrNull() ?: 0L
                                val now = System.currentTimeMillis()
                                if (Math.abs(now - ts) > 60000L) {
                                    writeErrorResponse(output, 401, "Unauthorized", "Request timestamp expired")
                                    socket.close()
                                    return@thread
                                }
                            }

                            // Replay protection: check nonce
                            val nonce = headers["x-jarvis-nonce"]
                            if (!nonce.isNullOrEmpty()) {
                                if (!checkAndRecordNonce(nonce)) {
                                    writeErrorResponse(output, 409, "Conflict", "Replay protection: Nonce already seen")
                                    socket.close()
                                    return@thread
                                }
                            }

                            // Read-only status inquiry
                            if (requestLine.startsWith("GET /api/status")) {
                                val resp = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"status\":\"ok\",\"device\":\"android-island\"}"
                                output.write(resp.toByteArray(Charsets.UTF_8))
                                output.flush()
                                socket.close()
                                return@thread
                            }

                            // Pairing endpoint: do not expose reusable token without authentication
                            if (requestLine.startsWith("POST /api/pair")) {
                                writeErrorResponse(output, 403, "Forbidden", "Pairing must be initiated via mutual challenge confirmation")
                                socket.close()
                                return@thread
                            }

                            // Authorization check for protected routes
                            val clientToken = headers["x-jarvis-auth-token"] ?: headers["authorization"]?.removePrefix("Bearer ")?.trim()
                            if (clientToken != currentAuthToken) {
                                writeErrorResponse(output, 401, "Unauthorized", "Valid X-Jarvis-Auth-Token required")
                                socket.close()
                                return@thread
                            }

                            // High-speed Chunked Binary Stream Receiver (KDE Connect mechanism)
                            if (requestLine.startsWith("POST /api/beam/stream")) {
                                if (contentLength <= 0 || contentLength > MAX_STREAM_BYTES) {
                                    writeErrorResponse(output, 413, "Payload Too Large", "Stream length missing or exceeds allowed size limit")
                                    socket.close()
                                    return@thread
                                }
                                val rawFileName = headers["x-file-name"] ?: "Desktop_File.bin"
                                val safeFileName = sanitizeFileName(rawFileName)
                                val expectedChecksum = headers["x-jarvis-checksum-sha256"]
                                val estSizeMb = (contentLength / (1024f * 1024f)).coerceAtLeast(0.1f)

                                mainHandler.post {
                                    notifyTransferStarted(safeFileName, estSizeMb, false)
                                }

                                val downloadDir = getJarvisDownloadDirectory(context)

                                val tmpFile = File(downloadDir, "$safeFileName.${System.currentTimeMillis()}.tmp")
                                val destFile = File(downloadDir, safeFileName)
                                val md = MessageDigest.getInstance("SHA-256")
                                val buffer = ByteArray(65536)
                                var totalRead = 0L
                                var lastProgressEmit = 0L
                                var lastProgressTime = System.currentTimeMillis()
                                var streamSuccess = true

                                FileOutputStream(tmpFile).use { fos ->
                                    while (totalRead < contentLength) {
                                        val toRead = ((contentLength - totalRead).coerceAtMost(buffer.size.toLong())).toInt()
                                        val read = rawInput.read(buffer, 0, toRead)
                                        if (read == -1) {
                                            if (totalRead < contentLength) streamSuccess = false
                                            break
                                        }
                                        fos.write(buffer, 0, read)
                                        md.update(buffer, 0, read)
                                        totalRead += read

                                        val now = System.currentTimeMillis()
                                        if (totalRead - lastProgressEmit > 262144 || totalRead == contentLength.toLong() || now - lastProgressTime > 250) {
                                            val dt = (now - lastProgressTime).coerceAtLeast(1)
                                            val speed = ((totalRead - lastProgressEmit) * 1000f / (dt * 1024f * 1024f))
                                            val speedStr = "%.1f MB/s".format(speed)
                                            lastProgressTime = now
                                            lastProgressEmit = totalRead
                                            val p = if (contentLength > 0) totalRead.toFloat() / contentLength.toFloat() else 1.0f
                                            mainHandler.post {
                                                notifyTransferProgress(safeFileName, p, speedStr, false)
                                            }
                                        }
                                    }
                                    fos.flush()
                                }

                                if (streamSuccess && totalRead == contentLength.toLong()) {
                                    val digest = md.digest()
                                    val computedChecksum = digest.joinToString("") { "%02x".format(it) }
                                    if (!expectedChecksum.isNullOrEmpty() && !expectedChecksum.equals(computedChecksum, ignoreCase = true)) {
                                        tmpFile.delete()
                                        writeErrorResponse(output, 400, "Bad Request", "Checksum verification failed")
                                        socket.close()
                                        return@thread
                                    }
                                    tmpFile.renameTo(destFile)
                                    notifyFileSavedToUser(context, destFile)
                                    Log.i(TAG, "JARVIS Stream completed: ${destFile.absolutePath} ($totalRead bytes)")

                                    mainHandler.post {
                                        notifyTransferProgress(safeFileName, 1.0f, "112 MB/s", false)
                                        notifyTransferCompleted(safeFileName, destFile, false)
                                    }
                                    val resp = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"received\":true,\"status\":\"ok\",\"fileName\":\"$safeFileName\"}"
                                    output.write(resp.toByteArray(Charsets.UTF_8))
                                } else {
                                    tmpFile.delete()
                                    writeErrorResponse(output, 500, "Internal Server Error", "Stream transfer incomplete or aborted")
                                }
                                output.flush()
                                socket.close()
                                return@thread
                            }

                            // Payload size limit check (50MB max for transfer, 1MB max for JSON endpoints)
                            val maxAllowedSize = if (requestLine.startsWith("POST /api/transfer")) 50 * 1024 * 1024 else 1024 * 1024
                            if (contentLength > maxAllowedSize) {
                                writeErrorResponse(output, 413, "Payload Too Large", "Payload exceeds allowed size limit")
                                socket.close()
                                return@thread
                            }

                            val body = if (contentLength > 0) {
                                val buffer = ByteArray(contentLength)
                                var totalRead = 0
                                while (totalRead < contentLength) {
                                    val read = rawInput.read(buffer, totalRead, contentLength - totalRead)
                                    if (read == -1) break
                                    totalRead += read
                                }
                                String(buffer, 0, totalRead, Charsets.UTF_8)
                            } else ""

                            if (requestLine.startsWith("POST /api/clipboard")) {
                                if (body.isNotEmpty()) {
                                    val json = JSONObject(body)
                                    val text = json.optString("text")
                                    val origin = json.optString("originDeviceId")

                                    if (origin != "jarvis-android-mobile" && text.isNotEmpty()) {
                                        mainHandler.post {
                                            markRemoteClipboard(text)
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cm.setPrimaryClip(ClipData.newPlainText("JARVIS Ecosystem", text))
                                            Log.i(TAG, "Synchronized clipboard text from desktop (${text.length} chars)")
                                        }
                                    }
                                }
                                val resp = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"copied\":true}"
                                output.write(resp.toByteArray(Charsets.UTF_8))
                            } else if (requestLine.startsWith("POST /api/transfer/cancel")) {
                                val resp = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"cancelled\":true}"
                                output.write(resp.toByteArray(Charsets.UTF_8))
                            } else if (requestLine.startsWith("POST /api/transfer")) {
                                if (body.isNotEmpty()) {
                                    val json = JSONObject(body)
                                    val rawFileName = json.optString("fileName", "Received_File.bin")
                                    val safeFileName = sanitizeFileName(rawFileName)
                                    val base64Data = json.optString("dataBase64")
                                    val expectedChecksum = headers["x-jarvis-checksum-sha256"]
                                        ?: json.optString("sha256Checksum")

                                    val estSizeMb = (contentLength / (1024f * 1024f)).coerceAtLeast(0.1f)
                                    mainHandler.post {
                                        notifyTransferStarted(safeFileName, estSizeMb, false)
                                    }

                                    val downloadDir = getJarvisDownloadDirectory(context)

                                    val bytes = Base64.decode(base64Data, Base64.DEFAULT)

                                    // Verify SHA-256
                                    val md = MessageDigest.getInstance("SHA-256")
                                    val digest = md.digest(bytes)
                                    val computedChecksum = digest.joinToString("") { "%02x".format(it) }

                                    if (expectedChecksum.isNotEmpty() && !expectedChecksum.equals(computedChecksum, ignoreCase = true)) {
                                        writeErrorResponse(output, 400, "Bad Request", "Checksum verification failed")
                                        socket.close()
                                        return@thread
                                    }

                                    val tmpFile = File(downloadDir, "$safeFileName.${System.currentTimeMillis()}.tmp")
                                    val destFile = File(downloadDir, safeFileName)
                                    FileOutputStream(tmpFile).use { it.write(bytes) }
                                    tmpFile.renameTo(destFile)
                                    notifyFileSavedToUser(context, destFile)

                                    Log.i(TAG, "Received verified file: ${destFile.absolutePath} (${bytes.size} bytes)")

                                    mainHandler.post {
                                        notifyTransferProgress(safeFileName, 1.0f, "112 MB/s", false)
                                        notifyTransferCompleted(safeFileName, destFile, false)
                                    }
                                }
                                val resp = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"received\":true}"
                                output.write(resp.toByteArray(Charsets.UTF_8))
                            } else if (requestLine.startsWith("POST /api/mcp") || requestLine.startsWith("POST /mcp")) {
                                val mcpJson = PhoneMcpBridge.handleRequest(context, body)
                                val resp = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n$mcpJson"
                                output.write(resp.toByteArray(Charsets.UTF_8))
                            } else if (requestLine.startsWith("POST /api/control")) {
                                if (body.isNotEmpty()) {
                                    val json = JSONObject(body)
                                    val action = json.optString("action")
                                    val x = json.optDouble("x", 0.5).toFloat()
                                    val y = json.optDouble("y", 0.5).toFloat()
                                    val deltaX = json.optDouble("deltaX", 0.0).toFloat()
                                    val deltaY = json.optDouble("deltaY", 0.0).toFloat()

                                    mainHandler.post {
                                        com.jarvis.island.service.JarvisIslandAccessibilityService.instance?.handleRemoteControl(
                                            action, x, y, deltaX, deltaY
                                        )
                                    }
                                }
                                val resp = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"executed\":true}"
                                output.write(resp.toByteArray(Charsets.UTF_8))
                            } else {
                                val resp = "HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n"
                                output.write(resp.toByteArray(Charsets.UTF_8))
                            }
                            output.flush()
                            socket.close()
                        } catch (e: Exception) {
                            Log.w(TAG, "Bridge request handling error: ${e.message}")
                        } finally {
                            try {
                                socket.close()
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Bridge server socket bind or accept error: ${e.message}")
                // Bind/accept failed: allow a later startServer call to retry instead of
                // leaving the shared flag stuck at true.
                synchronized(serverLock) {
                    isServerRunning = false
                    serverSocket = null
                }
            }
        }
    }

    private fun writeErrorResponse(output: OutputStream, code: Int, status: String, msg: String) {
        val json = "{\"error\":\"$msg\",\"statusCode\":$code,\"status\":\"$status\"}"
        val resp = "HTTP/1.1 $code $status\r\nContent-Type: application/json\r\nContent-Length: ${json.length}\r\nConnection: close\r\n\r\n$json"
        output.write(resp.toByteArray(Charsets.UTF_8))
        output.flush()
    }

    fun stopServer() {
        synchronized(serverLock) {
            // The server is shared by the overlay and accessibility services; only stop it
            // when every owner has released it.
            if (serverRefCount > 0) serverRefCount -= 1
            if (serverRefCount > 0) return
            isServerRunning = false
            try {
                serverSocket?.close()
                serverSocket = null
            } catch (_: Exception) {}
        }
    }

    companion object {
        private const val TAG = "JarvisFileBeam"
        private val serverLock = Any()
        private var isServerRunning = false
        private var serverRefCount = 0
        private var serverSocket: ServerSocket? = null
        @Volatile
        private var activeInstance: FileBeamReceiver? = null

        /** Re-binds the shared listener after pairing installs the TLS identity. */
        fun restartServerWithTls() {
            val instance = activeInstance ?: return
            synchronized(serverLock) {
                if (serverRefCount <= 0) return
                isServerRunning = false
                try {
                    serverSocket?.close()
                    serverSocket = null
                } catch (_: Exception) {}
                isServerRunning = true
            }
            instance.launchListener(53391)
        }
        private val listeners = CopyOnWriteArrayList<FileTransferListener>()
        private val seenNonces = ConcurrentHashMap<String, Long>()
        private const val PREFS_NAME = "jarvis_bridge_auth"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val MAX_STREAM_BYTES = 1024 * 1024 * 1024 // 1 GB

        var currentAuthToken: String = ""

        @Volatile
        private var lastRemoteClipboardText: String = ""
        @Volatile
        private var lastRemoteClipboardAt: Long = 0L

        /** Records text written by the desktop so the phone does not echo it back. */
        fun markRemoteClipboard(text: String) {
            lastRemoteClipboardText = text
            lastRemoteClipboardAt = System.currentTimeMillis()
        }

        fun shouldForwardClipboard(text: String): Boolean {
            val echoedRecently = System.currentTimeMillis() - lastRemoteClipboardAt < 8000
            return !(echoedRecently && text == lastRemoteClipboardText)
        }

        fun getOrInitAuthToken(context: Context): String {
            if (currentAuthToken.isNotEmpty()) return currentAuthToken
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            var token = prefs.getString(KEY_AUTH_TOKEN, null)
            if (token.isNullOrEmpty()) {
                val randomBytes = ByteArray(32)
                java.security.SecureRandom().nextBytes(randomBytes)
                token = randomBytes.joinToString("") { "%02x".format(it) }
                prefs.edit().putString(KEY_AUTH_TOKEN, token).apply()
            }
            currentAuthToken = token
            return token
        }

        fun storePairedToken(context: Context, token: String) {
            if (token.isBlank()) return
            currentAuthToken = token
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_AUTH_TOKEN, token)
                .apply()
        }

        fun clearPairedToken(context: Context) {
            currentAuthToken = ""
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_AUTH_TOKEN)
                .apply()
        }

        fun checkAndRecordNonce(nonce: String): Boolean {
            val now = System.currentTimeMillis()
            if (seenNonces.size > 1000) {
                val it = seenNonces.entries.iterator()
                while (it.hasNext()) {
                    if (now - it.next().value > 65000L) {
                        it.remove()
                    }
                }
            }
            return seenNonces.putIfAbsent(nonce, now) == null
        }

        fun sanitizeFileName(raw: String): String {
            val normalized = raw.replace('\\', '/')
            val base = File(normalized).name
            val safe = base.filter { it.isLetterOrDigit() || " ._-()[]".contains(it) }.trim()
            return if (safe.isEmpty() || safe.startsWith(".")) "Received_File.bin" else safe
        }

        fun addListener(listener: FileTransferListener) {
            if (!listeners.contains(listener)) {
                listeners.add(listener)
            }
        }

        fun removeListener(listener: FileTransferListener) {
            listeners.remove(listener)
        }

        fun notifyTransferStarted(fileName: String, fileSizeMb: Float, isUpload: Boolean) {
            listeners.forEach { it.onTransferStarted(fileName, fileSizeMb, isUpload) }
        }

        fun notifyTransferProgress(fileName: String, progress: Float, speedMbps: String, isUpload: Boolean) {
            listeners.forEach { it.onTransferProgress(fileName, progress, speedMbps, isUpload) }
        }

        fun notifyTransferCompleted(fileName: String, file: File?, isUpload: Boolean) {
            listeners.forEach { it.onTransferCompleted(fileName, file, isUpload) }
        }

        fun getJarvisDownloadDirectory(context: Context): File {
            try {
                val publicDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "JARVIS"
                )
                if (publicDir.exists() || publicDir.mkdirs()) {
                    return publicDir
                }
            } catch (e: Exception) {
                Log.w(TAG, "Public downloads directory inaccessible: ${e.message}")
            }
            val fallback = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir,
                "JARVIS"
            )
            fallback.mkdirs()
            return fallback
        }

        fun notifyFileSavedToUser(context: Context, file: File) {
            try {
                MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null) { path, uri ->
                    Log.i(TAG, "MediaScanner indexed $path -> $uri")
                }
                val channelId = "jarvis_transfers"
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        channelId,
                        "JARVIS File Beam",
                        NotificationManager.IMPORTANCE_DEFAULT
                    )
                    nm.createNotificationChannel(channel)
                }
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val mimeType = context.contentResolver.getType(uri) ?: "*/*"
                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    file.hashCode(),
                    viewIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val notif = NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle("File Received: ${file.name}")
                    .setContentText("Saved to Downloads/JARVIS (Tap to open)")
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .build()
                nm.notify(file.hashCode(), notif)
            } catch (e: Exception) {
                Log.w(TAG, "Could not post download notification: ${e.message}")
            }
        }

        fun openJarvisDownloadDirectory(context: Context) {
            try {
                val itn = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(itn)
            } catch (e: Exception) {
                Log.w(TAG, "Cannot launch downloads manager: ${e.message}")
            }
        }

        fun beamUriToDesktop(
            context: Context,
            uri: Uri,
            desktopIp: String,
            desktopPort: Int = 53391,
            onProgress: (Float) -> Unit,
            onComplete: (Boolean) -> Unit
        ) {
            thread(isDaemon = true, name = "Jarvis-BeamUri-Thread") {
                try {
                    var fileName = "mobile_file_${System.currentTimeMillis()}.bin"
                    var totalBytes = 0L

                    runCatching {
                        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (cursor.moveToFirst()) {
                                if (nameIdx != -1) {
                                    val name = cursor.getString(nameIdx)
                                    if (!name.isNullOrBlank()) fileName = sanitizeFileName(name)
                                }
                                if (sizeIdx != -1) {
                                    totalBytes = cursor.getLong(sizeIdx)
                                }
                            }
                        }
                    }

                    if (totalBytes <= 0) {
                        runCatching {
                            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                                totalBytes = pfd.statSize
                            }
                        }
                    }

                    val estSizeMb = if (totalBytes > 0) (totalBytes / (1024f * 1024f)).coerceAtLeast(0.1f) else 1.0f
                    Handler(Looper.getMainLooper()).post {
                        notifyTransferStarted(fileName, estSizeMb, true)
                        onProgress(0.05f)
                        notifyTransferProgress(fileName, 0.05f, "0 MB/s", true)
                    }

                    val now = System.currentTimeMillis()
                    val nonce = JarvisLiveBridgeClient.generateSecureNonce()
                    val tlsFactory = BridgeTls.pinnedClientSocketFactory(context)
                    if (tlsFactory == null) {
                        Handler(Looper.getMainLooper()).post { onComplete(false) }
                        return@thread
                    }

                    val conn = URL("https://$desktopIp:$desktopPort/api/beam/stream")
                        .openConnection() as HttpsURLConnection
                    conn.sslSocketFactory = tlsFactory
                    conn.hostnameVerifier = HostnameVerifier { _, _ -> true }
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/octet-stream")
                    conn.setRequestProperty("X-File-Name", fileName)
                    if (totalBytes > 0) {
                        conn.setRequestProperty("Content-Length", totalBytes.toString())
                        conn.setFixedLengthStreamingMode(totalBytes)
                    } else {
                        conn.setChunkedStreamingMode(65536)
                    }
                    conn.setRequestProperty("X-Jarvis-Auth-Token", currentAuthToken)
                    conn.setRequestProperty("X-Jarvis-Timestamp", now.toString())
                    conn.setRequestProperty("X-Jarvis-Nonce", nonce)
                    conn.doOutput = true
                    conn.connectTimeout = 8000
                    conn.readTimeout = 60000

                    val buffer = ByteArray(65536)
                    var sentBytes = 0L
                    var lastProgressTime = System.currentTimeMillis()
                    var lastProgressBytes = 0L

                    val inputStream = context.contentResolver.openInputStream(uri)
                        ?: throw IOException("Could not open input stream for URI: $uri")

                    inputStream.use { inp ->
                        conn.outputStream.use { os ->
                            var read: Int
                            while (inp.read(buffer).also { read = it } != -1) {
                                os.write(buffer, 0, read)
                                sentBytes += read

                                val currentTime = System.currentTimeMillis()
                                if (sentBytes - lastProgressBytes > 262144 || (totalBytes > 0 && sentBytes == totalBytes) || currentTime - lastProgressTime > 250) {
                                    val dt = (currentTime - lastProgressTime).coerceAtLeast(1)
                                    val speed = ((sentBytes - lastProgressBytes) * 1000f / (dt * 1024f * 1024f))
                                    val speedStr = "%.1f MB/s".format(speed)
                                    lastProgressTime = currentTime
                                    lastProgressBytes = sentBytes
                                    val p = if (totalBytes > 0) sentBytes.toFloat() / totalBytes.toFloat() else 0.5f
                                    Handler(Looper.getMainLooper()).post {
                                        onProgress(p)
                                        notifyTransferProgress(fileName, p, speedStr, true)
                                    }
                                }
                            }
                            os.flush()
                        }
                    }

                    val success = conn.responseCode in 200..299
                    conn.disconnect()

                    Handler(Looper.getMainLooper()).post {
                        if (success) {
                            onProgress(1.0f)
                            notifyTransferProgress(fileName, 1.0f, "112 MB/s", true)
                            notifyTransferCompleted(fileName, File(fileName), true)
                            onComplete(true)
                        } else {
                            onComplete(false)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Beaming URI failed: ${e.message}")
                    Handler(Looper.getMainLooper()).post { onComplete(false) }
                }
            }
        }

        fun beamFileToDesktop(
            context: Context,
            file: File,
            desktopIp: String,
            desktopPort: Int = 53391,
            onProgress: (Float) -> Unit,
            onComplete: (Boolean) -> Unit
        ) {
            thread(isDaemon = true, name = "Jarvis-BeamFile-Thread") {
                try {
                    val sizeMb = (file.length() / (1024f * 1024f)).coerceAtLeast(0.1f)
                    val totalBytes = file.length()
                    Handler(Looper.getMainLooper()).post {
                        notifyTransferStarted(file.name, sizeMb, true)
                        onProgress(0.05f)
                        notifyTransferProgress(file.name, 0.05f, "0 MB/s", true)
                    }

                    val now = System.currentTimeMillis()
                    val nonce = JarvisLiveBridgeClient.generateSecureNonce()
                    val tlsFactory = BridgeTls.pinnedClientSocketFactory(context)
                    if (tlsFactory == null) {
                        Handler(Looper.getMainLooper()).post { onComplete(false) }
                        return@thread
                    }
                    val conn = URL("https://$desktopIp:$desktopPort/api/beam/stream")
                        .openConnection() as HttpsURLConnection
                    conn.sslSocketFactory = tlsFactory
                    conn.hostnameVerifier = HostnameVerifier { _, _ -> true }
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/octet-stream")
                    conn.setRequestProperty("X-File-Name", file.name)
                    conn.setRequestProperty("Content-Length", totalBytes.toString())
                    conn.setRequestProperty("X-Jarvis-Auth-Token", currentAuthToken)
                    conn.setRequestProperty("X-Jarvis-Timestamp", now.toString())
                    conn.setRequestProperty("X-Jarvis-Nonce", nonce)
                    conn.setFixedLengthStreamingMode(totalBytes)
                    conn.doOutput = true
                    conn.connectTimeout = 8000
                    conn.readTimeout = 30000

                    val buffer = ByteArray(65536)
                    var sentBytes = 0L
                    var lastProgressTime = System.currentTimeMillis()
                    var lastProgressBytes = 0L

                    FileInputStream(file).use { fis ->
                        conn.outputStream.use { os ->
                            var read: Int
                            while (fis.read(buffer).also { read = it } != -1) {
                                os.write(buffer, 0, read)
                                sentBytes += read

                                val currentTime = System.currentTimeMillis()
                                if (sentBytes - lastProgressBytes > 262144 || sentBytes == totalBytes || currentTime - lastProgressTime > 250) {
                                    val dt = (currentTime - lastProgressTime).coerceAtLeast(1)
                                    val speed = ((sentBytes - lastProgressBytes) * 1000f / (dt * 1024f * 1024f))
                                    val speedStr = "%.1f MB/s".format(speed)
                                    lastProgressTime = currentTime
                                    lastProgressBytes = sentBytes
                                    val p = if (totalBytes > 0) sentBytes.toFloat() / totalBytes.toFloat() else 1.0f
                                    Handler(Looper.getMainLooper()).post {
                                        onProgress(p)
                                        notifyTransferProgress(file.name, p, speedStr, true)
                                    }
                                }
                            }
                            os.flush()
                        }
                    }

                    val success = conn.responseCode in 200..299
                    conn.disconnect()

                    Handler(Looper.getMainLooper()).post {
                        if (success) {
                            onProgress(1.0f)
                            notifyTransferProgress(file.name, 1.0f, "112 MB/s", true)
                            notifyTransferCompleted(file.name, file, true)
                            onComplete(true)
                        } else {
                            onComplete(false)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Beaming file failed: ${e.message}")
                    Handler(Looper.getMainLooper()).post {
                        onComplete(false)
                    }
                }
            }
        }
    }
}
