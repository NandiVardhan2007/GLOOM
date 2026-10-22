package com.jarvis.island.automation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.jarvis.island.ai.AiMemory
import com.jarvis.island.bridge.JarvisLiveBridgeClient
import com.jarvis.island.service.JarvisIslandAccessibilityService
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * System and Productivity Automation Tools:
 * - Drafting personalized emails
 * - Regex-based file search
 * - Universal data & clipboard synchronization with desktop
 * - System optimization (cache cleanup & duplicate file removal)
 */
object SystemTools {

    private const val TAG = "JarvisSystemTools"

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
                    ?: "$recipient@gmail.com"
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
        val client = JarvisLiveBridgeClient.activeClient
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

        JarvisIslandAccessibilityService.instance?.showLocalNotification(
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
