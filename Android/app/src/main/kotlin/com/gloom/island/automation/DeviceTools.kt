package com.gloom.island.automation

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The phone's own tools, reached through public Android intents.
 *
 * Calls, texts, alarms and calendar entries open in the user's own apps with
 * the fields filled in. Nothing is sent or saved without the user pressing the
 * final button in that app, which keeps a misheard command from becoming a real
 * message or a real appointment.
 */
object DeviceTools {

    data class Result(val success: Boolean, val message: String)

    fun call(context: Context, query: String): Result {
        val cleanQuery = query.trim()
            .removePrefix("to ")
            .removePrefix("the ")
            .removePrefix("my ")
            .removePrefix("contact ")
            .trim()
        val number = resolveNumber(context, cleanQuery)
            ?: return Result(false, missingNumberMessage(context, cleanQuery))
        if (context.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(number)}"))
            val res = launch(context, callIntent, "Calling $cleanQuery...")
            if (res.success) return res
        }
        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}"))
        return launch(context, dialIntent, "Calling $cleanQuery...")
    }

    fun text(context: Context, query: String, message: String): Result {
        val number = resolveNumber(context, query)
            ?: return Result(false, missingNumberMessage(context, query))
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}")).apply {
            putExtra("sms_body", message)
        }
        return launch(
            context,
            intent,
            "Ready to text $query. Press Send in your messaging app."
        )
    }

    fun email(context: Context, address: String, subject: String, body: String): Result {
        val target = if (address.contains('@')) address.trim()
        else com.gloom.island.ai.AiMemory.resolveEntity(address)?.takeIf { it.contains('@') } ?: address.trim()
        val mailUri = if (target.isNotBlank()) "mailto:${Uri.encode(target)}" else "mailto:"
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(mailUri)).apply {
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        return launch(context, intent, if (target.isNotBlank()) "Ready to email $target." else "Opening email client.")
    }

    fun openCamera(context: Context): Result {
        val still = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (tryStart(context, still)) return Result(true, "Opening the camera.")
        val capture = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return launch(context, capture, "Opening the camera.")
    }

    fun setAlarm(context: Context, hour: Int, minute: Int, label: String): Result {
        if (hour !in 0..23 || minute !in 0..59) return Result(false, "That is not a valid time.")
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            if (label.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
        val display = formatTime(hour, minute)
        return launch(context, intent, "Opening the clock to set an alarm for $display.")
    }

    fun addCalendarEvent(
        context: Context,
        title: String,
        beginMs: Long,
        durationMinutes: Int = 60,
    ): Result {
        val endMs = beginMs + durationMinutes.coerceIn(5, 24 * 60) * 60_000L
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginMs)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMs)
        }
        return launch(context, intent, "Opening the calendar to add $title.")
    }

    fun openContactPicker(context: Context): Result {
        val intent = Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI)
        return launch(context, intent, "Opening contacts.")
    }

    fun resolveNumber(context: Context, query: String): String? {
        val trimmed = query.trim()
            .removePrefix("to ")
            .removePrefix("the ")
            .removePrefix("my ")
            .removePrefix("contact ")
            .trim()
        if (trimmed.isEmpty()) return null
        val digits = trimmed.filter { it.isDigit() || it == '+' }
        if (digits.count { it.isDigit() } >= 7) return digits

        // Candidate names to look up in device contacts
        val candidates = mutableListOf<String>()
        candidates.add(trimmed)

        // Relationship alias from AiMemory (e.g. user taught "mom is Jane")
        val alias = com.gloom.island.ai.AiMemory.resolveEntity(trimmed)
        if (alias != null && !alias.equals(trimmed, ignoreCase = true) && !alias.any { it.isDigit() }) {
            candidates.add(alias)
        }

        // 1. PRIMARY SOURCE OF TRUTH: Query Android Device Contacts directly
        if (hasContactsPermission(context)) {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            )

            // 1a. Exact case-insensitive match on candidate names
            for (cand in candidates.distinct()) {
                try {
                    context.contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        projection,
                        "LOWER(${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME}) = ?",
                        arrayOf(cand.lowercase().trim()),
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val num = cursor.getString(0)?.trim()
                            if (!num.isNullOrBlank()) return num
                        }
                    }
                } catch (_: Exception) {}
            }

            // 1b. Prefix or word-boundary match (e.g. "Jane Work", "Jane Mobile")
            for (cand in candidates.distinct()) {
                try {
                    context.contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        projection,
                        "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                        arrayOf("$cand%", "% $cand%"),
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val num = cursor.getString(0)?.trim()
                            if (!num.isNullOrBlank()) return num
                        }
                    }
                } catch (_: Exception) {}
            }

            // 1c. Full-scan across all device contacts for substring matching
            try {
                context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    projection,
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameIdx) ?: continue
                        val nameLower = name.lowercase().trim()
                        for (cand in candidates) {
                            val candLower = cand.lowercase().trim()
                            if (nameLower == candLower ||
                                nameLower.startsWith(candLower) ||
                                nameLower.contains(candLower) ||
                                candLower.contains(nameLower)
                            ) {
                                val num = cursor.getString(numIdx)?.trim()
                                if (!num.isNullOrBlank()) return num
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. FALLBACK ONLY: Check AiMemory only if the contact is NOT in device contacts
        val memResolved = com.gloom.island.ai.AiMemory.resolveEntity("$trimmed number")
            ?: com.gloom.island.ai.AiMemory.resolveEntity("$trimmed phone")
            ?: com.gloom.island.ai.AiMemory.resolveEntity(trimmed)?.filter { it.isDigit() || it == '+' }?.takeIf { it.length >= 7 }
        if (memResolved != null) return memResolved

        return null
    }

    fun takeScreenshot(context: Context): Result {
        val a11ySuccess = com.gloom.island.service.GloomIslandAccessibilityService.instance
            ?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT) == true
        return if (a11ySuccess) {
            Result(true, "Screenshot captured.")
        } else {
            Result(false, "Accessibility service required to capture screenshot.")
        }
    }

    fun playMedia(context: Context, query: String, platform: String? = null): Result {
        val cleanQuery = query.trim()
        val defaultMusicApp = com.gloom.island.ai.AiMemory.resolveEntity("music")?.lowercase()?.trim() ?: "harmonix"
        val plat = platform?.lowercase()?.trim() ?: when {
            cleanQuery.contains("harmonix", ignoreCase = true) -> "harmonix"
            cleanQuery.contains("youtube", ignoreCase = true) -> "youtube"
            cleanQuery.contains("spotify", ignoreCase = true) -> "spotify"
            else -> defaultMusicApp
        }
        val target = cleanQuery
            .replace("(?i)on harmonix".toRegex(), "")
            .replace("(?i)in harmonix".toRegex(), "")
            .replace("(?i)on youtube".toRegex(), "")
            .replace("(?i)on spotify".toRegex(), "")
            .replace("(?i)in youtube".toRegex(), "")
            .replace("(?i)in spotify".toRegex(), "")
            .replace("(?i)on music".toRegex(), "")
            .replace("(?i)in music".toRegex(), "")
            .replace("(?i)^play\\s+".toRegex(), "")
            .trim()

        if (plat == "harmonix" || plat == "music") {
            val harmonixPkg = com.gloom.island.voice.AppLauncher.resolvePackage(context, "harmonix")?.first
                ?: "com.aniruddh.harmonix"
            val hxIntent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                setPackage(harmonixPkg)
                putExtra(android.app.SearchManager.QUERY, target)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (tryStart(context, hxIntent)) {
                return Result(true, "Playing \"$target\" on Harmonix.")
            }
            val launchIntent = context.packageManager.getLaunchIntentForPackage(harmonixPkg)?.apply {
                putExtra("query", target)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (launchIntent != null && tryStart(context, launchIntent)) {
                return Result(true, "Opening Harmonix to play \"$target\".")
            }
            if (platform != null && platform.equals("harmonix", ignoreCase = true)) {
                return Result(false, "Harmonix music player is not installed or could not be launched.")
            }
        }

        if (plat == "youtube" || (plat != "spotify" && plat != "harmonix")) {
            val ytIntent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage("com.google.android.youtube")
                putExtra("query", target)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (tryStart(context, ytIntent)) {
                return Result(true, "Playing \"$target\" on YouTube.")
            }
            val webYt = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(target)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (tryStart(context, webYt)) {
                return Result(true, "Searching \"$target\" on YouTube.")
            }
        }
        if (plat == "spotify") {
            val spotIntent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:${Uri.encode(target)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (tryStart(context, spotIntent)) {
                return Result(true, "Playing \"$target\" on Spotify.")
            }
        }

        val mediaIntent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            putExtra(android.app.SearchManager.QUERY, target)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return launch(context, mediaIntent, "Playing \"$target\".")
    }

    fun searchFiles(context: Context, query: String): Result {
        val clean = query.trim().removePrefix("for ").removePrefix("file ").trim()
        if (clean.isBlank()) {
            return openFileManager(context)
        }
        val results = mutableListOf<java.io.File>()
        val searchDirs = listOf(
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS),
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES),
            android.os.Environment.getExternalStorageDirectory()
        )
        for (dir in searchDirs) {
            if (dir.exists() && dir.canRead()) {
                dir.listFiles()?.forEach { f ->
                    if (f.name.contains(clean, ignoreCase = true)) {
                        results.add(f)
                        if (results.size >= 5) return@forEach
                    }
                }
            }
            if (results.size >= 5) break
        }
        if (results.isEmpty()) {
            return Result(false, "No files found matching \"$clean\".")
        }
        val names = results.joinToString(", ") { it.name }
        return Result(true, "Found ${results.size} file(s): $names")
    }

    fun openFileManager(context: Context): Result {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse("content://com.android.externalstorage.documents/root/primary"), "resource/folder")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (tryStart(context, intent)) return Result(true, "Opening Files.")
        val fallback = Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return launch(context, fallback, "Opening Downloads.")
    }

    fun openFile(context: Context, fileName: String): Result {
        val clean = fileName.trim()
        val searchDirs = listOf(
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS),
            android.os.Environment.getExternalStorageDirectory()
        )
        var match: java.io.File? = null
        for (dir in searchDirs) {
            if (dir.exists()) {
                val found = dir.listFiles()?.firstOrNull { it.name.contains(clean, ignoreCase = true) }
                if (found != null) {
                    match = found
                    break
                }
            }
        }
        if (match != null) {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                match
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return launch(context, intent, "Opening ${match.name}.")
        }
        return Result(false, "Could not find file \"$clean\" to open.")
    }

    fun openBrowser(context: Context, urlOrQuery: String? = null): Result {
        val target = urlOrQuery?.trim()
        val uri = when {
            target.isNullOrBlank() -> Uri.parse("https://www.google.com")
            target.startsWith("http://") || target.startsWith("https://") -> Uri.parse(target)
            target.contains(".") && !target.contains(" ") -> Uri.parse("https://$target")
            else -> Uri.parse("https://www.google.com/search?q=${Uri.encode(target)}")
        }
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return launch(context, intent, if (target.isNullOrBlank()) "Opening browser." else "Searching for \"$target\".")
    }

    suspend fun fetchTechNews(): String = withContext(Dispatchers.IO) {
        try {
            val url = java.net.URL("https://news.google.com/rss/headlines/section/topic/TECHNOLOGY?hl=en-IN&gl=IN&ceid=IN:en")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            val xml = conn.inputStream.bufferedReader().use { it.readText() }
            val titles = mutableListOf<String>()
            val itemRegex = Regex("""<item>(.*?)</item>""", RegexOption.DOT_MATCHES_ALL)
            val titleRegex = Regex("""<title>(.*?)</title>""")
            for (itemMatch in itemRegex.findAll(xml)) {
                val titleMatch = titleRegex.find(itemMatch.groupValues[1])
                if (titleMatch != null) {
                    var title = titleMatch.groupValues[1]
                        .replace("&amp;", "&")
                        .replace("&quot;", "\"")
                        .replace("&#39;", "'")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
                        .trim()
                    val dashIdx = title.lastIndexOf(" - ")
                    if (dashIdx > 0) {
                        title = title.substring(0, dashIdx).trim()
                    }
                    if (title.isNotBlank()) {
                        titles.add(title)
                    }
                }
                if (titles.size >= 3) break
            }
            if (titles.isNotEmpty()) {
                val formatted = titles.mapIndexed { idx, t -> "${idx + 1}. $t" }.joinToString(". ")
                "Here is the latest tech news: $formatted."
            } else {
                "I couldn't load the tech headlines right now."
            }
        } catch (e: Exception) {
            "Failed to fetch tech news: ${e.localizedMessage ?: "Network error"}"
        }
    }

    fun hasContactsPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    private fun missingNumberMessage(context: Context, query: String): String =
        if (hasContactsPermission(context)) {
            "I couldn't find $query in your contacts."
        } else {
            "I couldn't read contacts, so I need a phone number for $query."
        }

    private fun launch(context: Context, intent: Intent, successMessage: String): Result {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (tryStart(context, intent)) Result(true, successMessage)
        else Result(false, "No app on this phone can handle that.")
    }

    private fun tryStart(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    private fun formatTime(hour: Int, minute: Int): String {
        val suffix = if (hour < 12) "AM" else "PM"
        val displayHour = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return if (minute == 0) "$displayHour $suffix" else "$displayHour:${minute.toString().padStart(2, '0')} $suffix"
    }
}
