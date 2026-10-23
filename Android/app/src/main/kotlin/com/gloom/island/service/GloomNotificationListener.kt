package com.gloom.island.service

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.gloom.island.lyrics.GloomLyricsProvider
import com.gloom.island.settings.GloomIslandSettings
import com.gloom.island.ui.MediaTrackInfo

/**
 * GLOOM Notification & Media Session Service:
 * Extracts real-time system media playback (Spotify, YouTube, Apple Music, System Players)
 * using MediaSessionManager and StatusBarNotification MediaStyle tokens.
 * Provides live transport controls, accurate track titles, artist names, duration, and album art.
 */
class GloomNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "GloomNotification"

        var instance: GloomNotificationListener? = null
            private set

        var currentTrackInfo: MediaTrackInfo = MediaTrackInfo()
            private set

        var activeController: MediaController? = null
            private set

        var onMediaUpdate: ((MediaTrackInfo) -> Unit)? = null
        var onMediaChanged: (() -> Unit)? = null
        var onNotificationEvent: ((com.gloom.island.ui.NotificationIslandEvent) -> Unit)? = null
        var onNotificationDismissed: ((String) -> Unit)? = null

        fun play() {
            try {
                activeController?.transportControls?.play()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to call play: ${e.message}")
            }
        }

        fun pause() {
            try {
                activeController?.transportControls?.pause()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to call pause: ${e.message}")
            }
        }

        fun togglePlayPause() {
            try {
                val state = activeController?.playbackState?.state
                if (state == PlaybackState.STATE_PLAYING) {
                    activeController?.transportControls?.pause()
                } else {
                    activeController?.transportControls?.play()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to toggle play/pause: ${e.message}")
            }
        }

        fun skipNext() {
            try {
                activeController?.transportControls?.skipToNext()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to skip next: ${e.message}")
            }
        }

        fun skipPrevious() {
            try {
                activeController?.transportControls?.skipToPrevious()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to skip previous: ${e.message}")
            }
        }

        fun seekTo(progressPercent: Float) {
            try {
                val duration = currentTrackInfo.durationMs
                if (duration > 0) {
                    val targetMs = (duration * progressPercent.coerceIn(0f, 1f)).toLong()
                    activeController?.transportControls?.seekTo(targetMs)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to seek: ${e.message}")
            }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var progressTicker: Runnable? = null
    private val lastRenderedAt = HashMap<String, Long>()
    private val lastRenderedSignature = HashMap<String, String>()
    private val lastContentAt = HashMap<String, Long>()
    // Keys we cancelled ourselves. Cancelling the shade copy emits
    // onNotificationRemoved, and treating that as a user dismissal would erase
    // the island card the moment it appeared.
    private val selfCancelled = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private val mediaCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updateFromController(activeController)
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            updateFromController(activeController)
        }

        override fun onSessionDestroyed() {
            activeController = null
            checkActiveMedia()
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        // The listener can be the first component to start after a reboot, so
        // it must load the persisted filters itself.
        GloomIslandSettings.instance.load(this)
        Log.i(TAG, "Notification listener connected!")
        startProgressTicker()
        checkActiveMedia()
        onMediaChanged?.invoke()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
        stopProgressTicker()
        activeController?.unregisterCallback(mediaCallback)
        activeController = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        Log.d(
            TAG,
            "Posted ${sbn.packageName} channel=${sbn.notification.channelId} " +
                "flags=${sbn.notification.flags}"
        )
        if (sbn.packageName == packageName) return

        val extras = sbn.notification.extras

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()
            ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""

        // 2. Check for MediaStyle token directly in notification
        val token = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION) as? MediaSession.Token
        }

        if (token != null) {
            attachController(MediaController(this, token))
            return
        }

        // 3. Fallback: Check if notification has Media template or audio category
        val category = sbn.notification.category
        if (category == Notification.CATEGORY_TRANSPORT || sbn.notification.isMediaNotification()) {
            checkActiveMedia()
            return
        }

        // 4. Regular App Heads-up Notification (WhatsApp, Telegram, Messages, Phone, etc.)
        // The island is meant to replace the heads-up copy, not repeat it, so
        // grouped summaries, ongoing alerts and silent channels are dropped
        // before anything is rendered.
        if (isGroupSummary(sbn)) return
        if (GloomIslandSettings.instance.filterSilentNotifications && isSilent(sbn)) {
            Log.d(TAG, "Skipping silent notification from ${sbn.packageName} (${sbn.notification.channelId})")
            return
        }
        val isCall = sbn.notification.category == Notification.CATEGORY_CALL
        val isAlarm = sbn.notification.category == Notification.CATEGORY_ALARM
        val isProgress = sbn.notification.category == Notification.CATEGORY_PROGRESS
        val isSystemForeground = (sbn.packageName == "android" || sbn.packageName == "com.android.systemui") && !isCall

        // Route calls, messages, OTPs, reminders to Dynamic Island; only drop background system spinners
        if (isProgress || isSystemForeground) return
        if (!isCall && !isAlarm && sbn.isOngoing && (sbn.notification.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0) return

        if (title.isBlank() && text.isBlank()) return

        val settings = GloomIslandSettings.instance
        if (!settings.isPackageAllowed(sbn.packageName)) {
            Log.d(TAG, "Skipping ${sbn.packageName}: app is muted in the island")
            return
        }

        // Triage decides whether an alert is shown at all and extracts a
        // one-time code so the island can offer a single copy action.
        var otpCode: String? = null
        if (settings.notificationTriageEnabled) {
            val channelName = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val ranking = Ranking()
                    if (currentRanking?.getRanking(sbn.key, ranking) == true) {
                        ranking.channel?.name?.toString().orEmpty()
                    } else ""
                } else ""
            }.getOrDefault("")
            val verdict = com.gloom.island.automation.NotificationTriage.classify(
                packageName = sbn.packageName,
                title = title,
                text = text,
                channelName = channelName,
            )
            if (!verdict.show) {
                Log.d(TAG, "Triage ignored ${sbn.packageName} (${verdict.kind})")
                return
            }
            otpCode = verdict.otp
        }

        val shouldRender = onNotificationEvent != null &&
            GloomIslandSettings.instance.pluginNotifications &&
            rememberAndDedupe(sbn, title, text)
        if (!shouldRender) {
            if (onNotificationEvent == null) {
                Log.d(TAG, "No island renderer attached; keeping ${sbn.packageName} in the shade")
            }
            return
        }
        Log.i(TAG, "Rendering ${sbn.packageName} in the island")

        val appLabel = try {
            val pm = packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (e: Exception) {
            sbn.packageName
        }

        val largeIconBitmap = try {
            val icon = sbn.notification.getLargeIcon()
            val drawable = icon?.loadDrawable(this)
            if (drawable is BitmapDrawable) {
                drawable.bitmap
            } else if (drawable != null) {
                val bmp = Bitmap.createBitmap(
                    drawable.intrinsicWidth.coerceAtLeast(48),
                    drawable.intrinsicHeight.coerceAtLeast(48),
                    Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bmp
            } else null
        } catch (e: Exception) {
            null
        }

        var replyAction: com.gloom.island.ui.NotificationReplyAction? = null
        val notifActions = sbn.notification.actions
        if (notifActions != null) {
            for (action in notifActions) {
                val remoteInputs = action.remoteInputs
                if (remoteInputs != null && remoteInputs.isNotEmpty()) {
                    val input = remoteInputs.first()
                    replyAction = com.gloom.island.ui.NotificationReplyAction(
                        label = action.title?.toString() ?: "Reply",
                        pendingIntent = action.actionIntent,
                        remoteInput = input,
                        resultKey = input.resultKey
                    )
                    break
                }
            }
        }

        val event = com.gloom.island.ui.NotificationIslandEvent(
            id = sbn.key ?: sbn.id.toString(),
            packageName = sbn.packageName,
            appName = appLabel,
            title = title,
            text = text,
            subText = subText,
            largeIcon = largeIconBitmap,
            contentIntent = sbn.notification.contentIntent,
            postTimeMs = sbn.postTime,
            otpCode = otpCode,
            replyAction = replyAction,
        )

        // Dismiss the status-bar copy as early as possible so the alert is
        // delivered once, by the island. This is best-effort: Android shows
        // the heads-up before the listener callback arrives, and OEMs may
        // refuse the cancel. Never touch non-clearable notifications.
        if (sbn.isClearable) {
            try {
                if (selfCancelled.size > 200) selfCancelled.clear()
                sbn.key?.let { selfCancelled.add(it) }
                cancelNotification(sbn.key)
            } catch (_: Exception) {
                // Some OEMs restrict cancellation; the island still shows the alert.
            }
        }

        mainHandler.post {
            onNotificationEvent?.invoke(event)
        }
    }

    private fun isGroupSummary(sbn: StatusBarNotification): Boolean {
        return (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
    }

    /**
     * A silent notification is one whose channel is below IMPORTANCE_DEFAULT:
     * no sound, no heads-up, no vibration. Rendering those turns every quiet
     * background update into an island alert.
     */
    @Suppress("DEPRECATION")
    private fun isSilent(sbn: StatusBarNotification): Boolean {
        val notification = sbn.notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ranking = Ranking()
            if (currentRanking?.getRanking(sbn.key, ranking) == true) {
                val channel = ranking.channel
                if (channel != null) {
                    return channel.importance < NotificationManager.IMPORTANCE_DEFAULT
                }
                return ranking.importance < NotificationManager.IMPORTANCE_DEFAULT
            }
        }
        return notification.sound == null &&
            notification.vibrate == null &&
            (notification.defaults and (Notification.DEFAULT_SOUND or Notification.DEFAULT_VIBRATE)) == 0 &&
            notification.fullScreenIntent == null
    }

    /**
     * Apps repost the same notification for each update (a typing indicator, a
     * badge, a progress step). Rendering every repost is what makes one message
     * appear several times. Identical content is dropped for 20 s and any
     * repost of the same key within 600 ms is coalesced.
     */
    private fun rememberAndDedupe(sbn: StatusBarNotification, title: String, text: String): Boolean {
        val key = sbn.key ?: sbn.id.toString()
        val now = SystemClock.elapsedRealtime()
        val signature = "$title\u0000$text"
        // Some apps repost under a new key each time. Identical content from the
        // same package inside three seconds is one alert, whichever key it uses.
        val contentKey = "${sbn.packageName}\u0000$signature"
        val contentAt = lastContentAt[contentKey] ?: 0L
        if (now - contentAt < 3_000L) {
            Log.d(TAG, "Dropping duplicate alert from ${sbn.packageName} (same content)")
            return false
        }
        val previousAt = lastRenderedAt[key] ?: 0L
        val previousSignature = lastRenderedSignature[key]
        if (previousSignature == signature && now - previousAt < 20_000L) {
            Log.d(TAG, "Dropping duplicate notification from ${sbn.packageName}")
            return false
        }
        if (now - previousAt < 600L) {
            Log.d(TAG, "Coalescing rapid repost from ${sbn.packageName}")
            return false
        }
        lastContentAt[contentKey] = now
        lastRenderedAt[key] = now
        lastRenderedSignature[key] = signature
        if (lastRenderedAt.size > 200) {
            val cutoff = now - 60_000L
            lastRenderedAt.entries.removeAll { it.value < cutoff }
            lastRenderedSignature.keys.retainAll(lastRenderedAt.keys)
        }
        if (lastContentAt.size > 200) {
            val cutoff = now - 60_000L
            lastContentAt.entries.removeAll { it.value < cutoff }
        }
        return true
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        checkActiveMedia()
        onMediaChanged?.invoke()
        if (sbn != null) {
            val key = sbn.key ?: sbn.id.toString()
            // A dismissal lets the same alert show again if the app reposts it.
            lastRenderedAt.remove(key)
            lastRenderedSignature.remove(key)
            if (selfCancelled.remove(key)) {
                // This removal is our own shade-copy cancellation, not a
                // dismissal. The island card must stay until its own timer ends.
                return
            }
            mainHandler.post {
                onNotificationDismissed?.invoke(key)
            }
        }
    }

    private fun Notification.isMediaNotification(): Boolean {
        return extras.containsKey(Notification.EXTRA_MEDIA_SESSION) ||
                category == Notification.CATEGORY_TRANSPORT ||
                category == Notification.CATEGORY_SERVICE
    }

    fun checkActiveMedia() {
        mainHandler.post {
            try {
                val mm = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
                val component = ComponentName(this, GloomNotificationListener::class.java)
                val controllers = mm?.getActiveSessions(component) ?: emptyList()

                // Only an actively playing session may drive the island. A paused or
                // metadata-less session must not look like a song is playing.
                val target = controllers.firstOrNull {
                    it.playbackState?.state == PlaybackState.STATE_PLAYING
                }

                if (target != null) {
                    attachController(target)
                } else {
                    activeController?.unregisterCallback(mediaCallback)
                    activeController = null
                    val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    val isMusicActive = am?.isMusicActive == true
                    if (isMusicActive && currentTrackInfo.title.isNotBlank()) {
                        currentTrackInfo = currentTrackInfo.copy(isPlaying = true)
                    } else {
                        currentTrackInfo = MediaTrackInfo()
                    }
                    onMediaUpdate?.invoke(currentTrackInfo)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error checking active media: ${e.message}")
            }
        }
    }

    private fun attachController(controller: MediaController) {
        if (activeController?.sessionToken == controller.sessionToken) {
            updateFromController(controller)
            return
        }

        activeController?.unregisterCallback(mediaCallback)
        activeController = controller
        controller.registerCallback(mediaCallback, mainHandler)
        updateFromController(controller)
    }

    private fun updateFromController(controller: MediaController?) {
        if (controller == null) return

        val metadata = controller.metadata
        val state = controller.playbackState

        val rawTitle = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        val rawArtist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
        val rawAlbum = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)

        if (rawTitle.isNullOrBlank() && rawArtist.isNullOrBlank()) {
            // No identifiable track yet; keep the island empty rather than inventing one.
            currentTrackInfo = MediaTrackInfo()
            onMediaUpdate?.invoke(currentTrackInfo)
            return
        }

        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val position = state?.position ?: 0L
        val isPlaying = state?.state == PlaybackState.STATE_PLAYING

        val progress = if (duration > 0) {
            (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        } else 0f

        val rawArt = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

        // Determine user-friendly app name
        val pkg = controller.packageName ?: ""
        val appName = try {
            val pm = packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            when {
                pkg.contains("spotify") -> "Spotify"
                pkg.contains("youtube") -> "YouTube Music"
                pkg.contains("music") -> "Music Player"
                else -> "Live Audio"
            }
        }

        val hasLyricsAlready = currentTrackInfo.title == rawTitle && currentTrackInfo.lyrics.isNotEmpty()

        val track = MediaTrackInfo(
            title = if (!rawTitle.isNullOrBlank()) rawTitle else "Playing Audio",
            artist = if (!rawArtist.isNullOrBlank()) rawArtist else appName,
            album = rawAlbum ?: "",
            albumArt = rawArt,
            isPlaying = isPlaying,
            durationMs = duration,
            positionMs = position,
            progress = progress,
            appName = appName,
            packageName = pkg,
            lyrics = if (hasLyricsAlready) currentTrackInfo.lyrics else emptyList()
        )

        currentTrackInfo = track
        onMediaUpdate?.invoke(track)
        onMediaChanged?.invoke()

        // Asynchronously fetch real synchronized lyrics if track changed or lyrics not cached
        if (!rawTitle.isNullOrBlank() && !hasLyricsAlready) {
            GloomLyricsProvider.fetchLyrics(rawTitle, rawArtist ?: "", duration) { fetchedLyrics ->
                if (currentTrackInfo.title == rawTitle && fetchedLyrics.isNotEmpty()) {
                    currentTrackInfo = currentTrackInfo.copy(lyrics = fetchedLyrics)
                    onMediaUpdate?.invoke(currentTrackInfo)
                }
            }
        }
    }

    private fun startProgressTicker() {
        stopProgressTicker()
        val ticker = object : Runnable {
            override fun run() {
                if (currentTrackInfo.isPlaying && currentTrackInfo.durationMs > 0) {
                    val nextPos = (currentTrackInfo.positionMs + 500).coerceAtMost(currentTrackInfo.durationMs)
                    val nextProgress = (nextPos.toFloat() / currentTrackInfo.durationMs.toFloat()).coerceIn(0f, 1f)
                    currentTrackInfo = currentTrackInfo.copy(
                        positionMs = nextPos,
                        progress = nextProgress
                    )
                    onMediaUpdate?.invoke(currentTrackInfo)
                }
                mainHandler.postDelayed(this, 500)
            }
        }
        progressTicker = ticker
        mainHandler.postDelayed(ticker, 500)
    }

    private fun stopProgressTicker() {
        progressTicker?.let { mainHandler.removeCallbacks(it) }
        progressTicker = null
    }
}
