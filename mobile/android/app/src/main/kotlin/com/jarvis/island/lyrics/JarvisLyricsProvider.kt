package com.jarvis.island.lyrics

import android.util.Log
import com.jarvis.island.ui.LyricLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * High-performance synchronized lyrics provider powered by LRCLIB.
 * Automatically fetches synchronized time-stamped lyrics for Spotify, Apple Music,
 * YouTube Music, and system media players with multi-tiered resilient fallback.
 */
object JarvisLyricsProvider {

    private const val TAG = "JarvisLyrics"
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val cache = ConcurrentHashMap<String, List<LyricLine>>()

    // Regex matching standard LRC timestamps: [mm:ss.xx] or [mm:ss.xxx]
    private val lrcPattern = Pattern.compile("\\[(\\d{1,2}):(\\d{2})\\.(\\d{2,3})\\](.*)")

    fun fetchLyrics(
        rawTitle: String,
        rawArtist: String,
        durationMs: Long = 0L,
        onLyricsReady: (List<LyricLine>) -> Unit
    ) {
        if (rawTitle.isBlank()) {
            onLyricsReady(emptyList())
            return
        }

        val cleanTitle = cleanTrackTitle(rawTitle)
        val cleanArtist = cleanArtistName(rawArtist)
        val cacheKey = "$cleanTitle|$cleanArtist".lowercase()

        // Check cache first
        val cached = cache[cacheKey]
        if (cached != null) {
            onLyricsReady(cached)
            return
        }

        scope.launch {
            val lyrics = queryLrclibWithFallbacks(cleanTitle, cleanArtist, rawTitle, durationMs)
            if (lyrics.isNotEmpty()) {
                cache[cacheKey] = lyrics
            }
            withContext(Dispatchers.Main) {
                onLyricsReady(lyrics)
            }
        }
    }

    private fun queryLrclibWithFallbacks(
        cleanTitle: String,
        cleanArtist: String,
        rawTitle: String,
        durationMs: Long
    ): List<LyricLine> {
        // Strategy 1: Direct GET with cleanTitle and cleanArtist (NO duration filter to avoid mismatch)
        var lyrics = queryDirectGet(cleanTitle, cleanArtist, durationMs)
        if (lyrics.isNotEmpty()) return lyrics

        // Strategy 2: If clean artist is different from first token, try just first token
        val primaryArtist = cleanArtist.split(" ")[0].trim()
        if (primaryArtist.length >= 3 && primaryArtist != cleanArtist) {
            lyrics = queryDirectGet(cleanTitle, primaryArtist, durationMs)
            if (lyrics.isNotEmpty()) return lyrics
        }

        // Strategy 3: Search endpoint with track name and artist query
        lyrics = querySearch(cleanTitle, cleanArtist, durationMs)
        if (lyrics.isNotEmpty()) return lyrics

        // Strategy 4: Search endpoint with clean track name alone
        lyrics = querySearch(cleanTitle, "", durationMs)
        if (lyrics.isNotEmpty()) return lyrics

        // Strategy 5: Search with raw title
        if (rawTitle != cleanTitle) {
            lyrics = querySearch(rawTitle, "", durationMs)
            if (lyrics.isNotEmpty()) return lyrics
        }

        return emptyList()
    }

    private fun queryDirectGet(title: String, artist: String, durationMs: Long): List<LyricLine> {
        try {
            val encTitle = URLEncoder.encode(title, "UTF-8")
            val encArtist = URLEncoder.encode(artist, "UTF-8")

            // Note: Never pass duration as it is a strict filter in LRCLIB
            val urlStr = "https://lrclib.net/api/get?track_name=$encTitle&artist_name=$encArtist"
            val body = fetchHttp(urlStr) ?: return emptyList()

            val json = JSONObject(body)
            val syncedLrc = json.optString("syncedLyrics", "")
            if (syncedLrc.isNotBlank()) {
                val parsed = parseLrc(syncedLrc)
                if (parsed.isNotEmpty()) return parsed
            }

            val plainLrc = json.optString("plainLyrics", "")
            if (plainLrc.isNotBlank()) {
                return parsePlainLyrics(plainLrc, durationMs)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Direct GET lyrics error for $title: ${e.message}")
        }
        return emptyList()
    }

    private fun querySearch(title: String, artist: String, durationMs: Long): List<LyricLine> {
        try {
            val queryStr = if (artist.isNotBlank()) "$title $artist" else title
            val encQuery = URLEncoder.encode(queryStr, "UTF-8")
            val urlStr = "https://lrclib.net/api/search?q=$encQuery"
            val body = fetchHttp(urlStr) ?: return emptyList()

            val array = JSONArray(body)
            for (i in 0 until array.length().coerceAtMost(5)) {
                val item = array.getJSONObject(i)
                val syncedLrc = item.optString("syncedLyrics", "")
                if (syncedLrc.isNotBlank()) {
                    val parsed = parseLrc(syncedLrc)
                    if (parsed.isNotEmpty()) return parsed
                }
            }

            // Fallback to first item with plain lyrics
            for (i in 0 until array.length().coerceAtMost(3)) {
                val item = array.getJSONObject(i)
                val plainLrc = item.optString("plainLyrics", "")
                if (plainLrc.isNotBlank()) {
                    return parsePlainLyrics(plainLrc, durationMs)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Search lyrics error for $title: ${e.message}")
        }
        return emptyList()
    }

    private fun fetchHttp(urlStr: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "JARVIS-Dynamic-Island/1.0 (https://github.com/nandujayaram/JARVIS)")
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun parseLrc(lrcText: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val rawLines = lrcText.split("\n")

        for (line in rawLines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val matcher = lrcPattern.matcher(trimmed)
            if (matcher.find()) {
                val minStr = matcher.group(1) ?: "0"
                val secStr = matcher.group(2) ?: "0"
                val msStr = (matcher.group(3) ?: "0").padEnd(3, '0').take(3)
                val text = matcher.group(4)?.trim() ?: ""

                val timeMs = (minStr.toLong() * 60_000L) +
                        (secStr.toLong() * 1_000L) +
                        msStr.toLong()

                if (text.isNotBlank()) {
                    lines.add(LyricLine(timeMs = timeMs, text = text))
                }
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    private fun parsePlainLyrics(plainText: String, durationMs: Long): List<LyricLine> {
        val rawLines = plainText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        if (rawLines.isEmpty()) return emptyList()

        // Truthfully return plain lyric lines without synthesizing fake timestamps
        return rawLines.map { text ->
            LyricLine(timeMs = 0L, text = text)
        }
    }

    fun cleanTrackTitle(title: String): String {
        return title
            .replace(Regex("(?i)\\s*\\(from [^)]+\\)"), "")
            .replace(Regex("(?i)\\s*\\(feat\\.[^)]+\\)"), "")
            .replace(Regex("(?i)\\s*\\[[^\\]]+\\]"), "")
            .replace(Regex("(?i)\\s*-\\s*(Telugu|Hindi|Tamil|English|Remix|Cover|From).*"), "")
            .replace(Regex("(?i)\\s*-\\s*Single"), "")
            .trim()
    }

    fun cleanArtistName(artist: String): String {
        return artist
            .split(",")[0]
            .split("&")[0]
            .split("feat")[0]
            .trim()
    }
}
