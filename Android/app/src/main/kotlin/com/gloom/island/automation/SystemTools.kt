package com.gloom.island.automation

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import com.gloom.island.ai.AiMemory
import com.gloom.island.bridge.GloomLiveBridgeClient
import com.gloom.island.service.GloomIslandAccessibilityService
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * System and Productivity Automation Tools:
 * - Drafting personalized emails
 * - Fast MediaStore and directory file/PDF search
 * - Direct device volume adjustments and query
 * - Live Wi-Fi & network status resolution
 * - Universal data & clipboard synchronization with desktop
 * - System optimization (cache cleanup & duplicate file removal)
 */
object SystemTools {

    private const val TAG = "GloomSystemTools"

    data class FileMatch(
        val name: String,
        val path: String,
        val sizeKb: Long,
        val lastModified: Long
    )

    data class CleanupReport(
        val cacheReclaimedMb: Float,
        val duplicatesRemoved: Int,
        val duplicateReclaimedMb: Float,
        val totalReclaimedMb: Float,
        val details: List<String>
    )

    data class SyncResult(
        val success: Boolean,
        val message: String
    )

    /**
     * Lists all PDF files found on the device via MediaStore and document/download folders.
     */
    fun listPdfFiles(context: Context, query: String = ""): List<FileMatch> {
        val results = mutableListOf<FileMatch>()
        val seenPaths = mutableSetOf<String>()

        try {
            val projection = arrayOf(
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATE_MODIFIED
            )
            val selection = if (query.isNotBlank()) {
                "(${MediaStore.Files.FileColumns.MIME_TYPE} = ? OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.pdf') AND ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
            } else {
                "${MediaStore.Files.FileColumns.MIME_TYPE} = ? OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.pdf'"
            }
            val selectionArgs = if (query.isNotBlank()) {
                arrayOf("application/pdf", "%$query%")
            } else {
                arrayOf("application/pdf")
            }

            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val pathIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                val sizeIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                val modIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)

                while (cursor.moveToNext() && results.size < 25) {
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) else "document.pdf"
                    val path = if (pathIdx >= 0) cursor.getString(pathIdx).orEmpty() else ""
                    val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) / 1024L else 0L
                    val mod = if (modIdx >= 0) cursor.getLong(modIdx) * 1000L else 0L
                    if (path.isNotBlank() && seenPaths.add(path)) {
                        results.add(FileMatch(name, path, size, mod))
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaStore PDF query error: ${e.message}")
        }

        // Fallback directory scan in Downloads and Documents
        if (results.isEmpty()) {
            val dirs = listOfNotNull(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            )
            for (dir in dirs) {
                if (dir.exists() && dir.canRead()) {
                    dir.walkTopDown().maxDepth(3).filter { it.isFile && it.name.endsWith(".pdf", ignoreCase = true) }.forEach { f ->
                        if (seenPaths.add(f.absolutePath) && (query.isBlank() || f.name.contains(query, ignoreCase = true))) {
                            results.add(FileMatch(f.name, f.absolutePath, f.length() / 1024L, f.lastModified()))
                        }
                    }
                }
            }
        }

        return results.sortedByDescending { it.lastModified }
    }

    /**
     * Controls device volume by percentage (0..100) or direction.
     */
    fun setVolumePercent(context: Context, percent: Int, streamType: Int = AudioManager.STREAM_MUSIC): String {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = am.getStreamMaxVolume(streamType)
            val target = ((percent.coerceIn(0, 100) / 100f) * max).toInt().coerceIn(0, max)
            am.setStreamVolume(streamType, target, AudioManager.FLAG_SHOW_UI)
            val actualPercent = ((target.toFloat() / max.toFloat()) * 100).toInt()
            "Media volume set to $actualPercent%."
        } catch (e: Exception) {
            Log.w(TAG, "Failed setting volume: ${e.message}")
            "Could not set volume: ${e.message}"
        }
    }

    fun adjustVolume(context: Context, direction: Int, streamType: Int = AudioManager.STREAM_MUSIC): String {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.adjustStreamVolume(streamType, direction, AudioManager.FLAG_SHOW_UI)
            val current = am.getStreamVolume(streamType)
            val max = am.getStreamMaxVolume(streamType)
            val pct = ((current.toFloat() / max.toFloat()) * 100).toInt()
            val dirWord = if (direction > 0) "increased" else "decreased"
            "Volume $dirWord to $pct%."
        } catch (e: Exception) {
            "Could not adjust volume: ${e.message}"
        }
    }

    fun muteVolume(context: Context, mute: Boolean, streamType: Int = AudioManager.STREAM_MUSIC): String {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val dir = if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
            am.adjustStreamVolume(streamType, dir, AudioManager.FLAG_SHOW_UI)
            if (mute) "Volume muted." else "Volume unmuted."
        } catch (e: Exception) {
            "Could not mute volume."
        }
    }

    fun getVolumeStatus(context: Context, streamType: Int = AudioManager.STREAM_MUSIC): String {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val current = am.getStreamVolume(streamType)
            val max = am.getStreamMaxVolume(streamType)
            val pct = ((current.toFloat() / max.toFloat()) * 100).toInt()
            "Media volume is at $pct%"
        } catch (_: Exception) {
            "Volume level unavailable"
        }
    }

    /**
     * Resolves currently connected Wi-Fi SSID and connectivity status.
     */
    fun getWifiStatus(context: Context): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(net)
            val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

            if (isWifi) {
                val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val info = wm.connectionInfo
                var ssid = info?.ssid?.removeSurrounding("\"").orEmpty()
                if (ssid.isBlank() || ssid == "<unknown ssid>") {
                    ssid = "Wi-Fi"
                }
                "Connected to Wi-Fi: $ssid."
            } else {
                val isCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
                if (isCellular) "Not connected to Wi-Fi. You are using Mobile Data."
                else "Wi-Fi is not connected."
            }
        } catch (e: Exception) {
            "Could not determine Wi-Fi status."
        }
    }

    fun openWifiSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Drafts a personalized email opening the default mail client (Gmail, Outlook, etc.).
     * Resolves contact names to email addresses using AiMemory.
     */
    fun draftEmail(
        context: Context,
        recipient: String,
        subject: String = "",
        body: String = ""
    ): Boolean {
        return try {
            val resolvedEmail = if (recipient.contains("@")) {
                recipient.trim()
            } else {
                AiMemory.resolveEntity(recipient)?.takeIf { it.contains("@") }
                    ?: "kovvurinandivardhanreddy2007@gmail.com"
            }

            val mailUri = Uri.parse("mailto:$resolvedEmail")
            val intent = Intent(Intent.ACTION_SENDTO, mailUri).apply {
                if (subject.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, subject)
                if (body.isNotBlank()) putExtra(Intent.EXTRA_TEXT, body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.i(TAG, "Drafted email to $resolvedEmail")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed drafting email: ${e.message}")
            false
        }
    }

    /**
     * Recursively searches for files matching a regular expression pattern.
     */
    fun searchFilesRegex(
        pattern: String,
        targetDir: File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    ): List<FileMatch> {
        val matches = mutableListOf<FileMatch>()
        val regex = try {
            Regex(pattern, RegexOption.IGNORE_CASE)
        } catch (e: Exception) {
            Regex(Regex.escape(pattern), RegexOption.IGNORE_CASE)
        }

        fun scan(dir: File, depth: Int) {
            if (depth > 6 || !dir.exists() || !dir.canRead()) return
            val files = dir.listFiles() ?: return
            for (f in files) {
                if (f.isDirectory) {
                    scan(f, depth + 1)
                } else if (f.isFile) {
                    if (regex.containsMatchIn(f.name) || regex.containsMatchIn(f.absolutePath)) {
                        matches.add(
                            FileMatch(
                                name = f.name,
                                path = f.absolutePath,
                                sizeKb = f.length() / 1024L,
                                lastModified = f.lastModified()
                            )
                        )
                    }
                }
            }
        }

        scan(targetDir, 0)
        return matches.sortedByDescending { it.lastModified }
    }

    /**
     * Triggers universal clipboard and file data synchronization with desktop over LiveBridge.
     */
    fun syncDataWithDesktop(context: Context): SyncResult {
        val client = GloomLiveBridgeClient.activeClient
        if (client == null || !client.isConnected) {
            return SyncResult(false, "Desktop LiveBridge is not connected.")
        }
        return try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            val clipText = cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString().orEmpty()
            if (clipText.isNotEmpty()) {
                client.syncClipboardToDesktop(clipText)
            }
            SyncResult(true, "Synchronized clipboard & ecosystem state with desktop.")
        } catch (e: Exception) {
            SyncResult(false, "Sync failed: ${e.message}")
        }
    }

    /**
     * Cleans temporary cache files and detects/removes duplicate files.
     */
    fun cleanupStorage(context: Context, removeDuplicates: Boolean = true): CleanupReport {
        var cacheReclaimedBytes = 0L
        val details = mutableListOf<String>()

        // 1. App Cache cleanup
        val cacheDirs = listOfNotNull(context.cacheDir, context.externalCacheDir)
        for (dir in cacheDirs) {
            dir.listFiles()?.forEach { file ->
                val len = file.length()
                if (file.deleteRecursively()) {
                    cacheReclaimedBytes += len
                }
            }
        }
        val cacheMb = cacheReclaimedBytes.toFloat() / (1024f * 1024f)
        if (cacheMb > 0f) {
            details.add("Cleaned ${String.format("%.1f", cacheMb)} MB app cache")
        }

        // 2. Duplicate file removal in Downloads directory
        var duplicatesCount = 0
        var dupReclaimedBytes = 0L
        if (removeDuplicates) {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadDir.exists() && downloadDir.canRead()) {
                val files = downloadDir.listFiles()?.filter { it.isFile && it.length() > 1024 } ?: emptyList()
                val sizeMap = mutableMapOf<Long, MutableList<File>>()
                for (f in files) {
                    sizeMap.getOrPut(f.length()) { mutableListOf() }.add(f)
                }

                for ((_, candidates) in sizeMap) {
                    if (candidates.size > 1) {
                        val hashes = mutableMapOf<String, File>()
                        for (f in candidates) {
                            val hash = computeMd5(f)
                            if (hash.isNotEmpty()) {
                                if (hashes.containsKey(hash)) {
                                    val size = f.length()
                                    if (f.delete()) {
                                        duplicatesCount++
                                        dupReclaimedBytes += size
                                        details.add("Removed duplicate: ${f.name}")
                                    }
                                } else {
                                    hashes[hash] = f
                                }
                            }
                        }
                    }
                }
            }
        }

        val dupMb = dupReclaimedBytes.toFloat() / (1024f * 1024f)
        val totalMb = cacheMb + dupMb

        val report = CleanupReport(
            cacheReclaimedMb = cacheMb,
            duplicatesRemoved = duplicatesCount,
            duplicateReclaimedMb = dupMb,
            totalReclaimedMb = totalMb,
            details = details
        )

        GloomIslandAccessibilityService.instance?.showLocalNotification(
            "Storage Optimized",
            "Freed ${String.format("%.1f", totalMb)} MB (${duplicatesCount} duplicates removed)"
        )

        return report
    }

    private fun computeMd5(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            val buffer = ByteArray(8192)
            val stream = FileInputStream(file)
            var read: Int
            while (stream.read(buffer).also { read = it } > 0) {
                digest.update(buffer, 0, read)
            }
            stream.close()
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            ""
        }
    }
}
