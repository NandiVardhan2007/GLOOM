package com.jarvis.island.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.jarvis.island.ui.MediaTrackInfo
import java.util.regex.Pattern

/**
 * JARVIS Notification & Media Session Service:
 * Extracts real-time system media playback (Spotify, YouTube, Apple Music, System Players)
 * using MediaSessionManager and StatusBarNotification MediaStyle tokens.
 * Provides live transport controls, accurate track titles, artist names, duration, and album art.
 */
class JarvisNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "JarvisNotification"

        var instance: JarvisNotificationListener? = null
            private set

        var currentTrackInfo: MediaTrackInfo = MediaTrackInfo()
            private set

        var activeController: MediaController? = null
            private set

        var onMediaUpdate: ((MediaTrackInfo) -> Unit)? = null
        var onMediaChanged: (() -> Unit)? = null
        var onNotificationEvent: ((com.jarvis.island.ui.NotificationIslandEvent) -> Unit)? = null
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
    private val otpPattern = Pattern.compile("(?i)\\b(\\d{3}[- ]?\\d{3}|\\d{4,8})\\b")
    private var progressTicker: Runnable? = null

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
        if (sbn == null || sbn.packageName == packageName) return

        val extras = sbn.notification.extras

        // 1. Inspect for OTP verification codes
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()
            ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""

        val combined = "$title $text"
        val matcher = otpPattern.matcher(combined)
        if (matcher.find()) {
            val code = matcher.group(1)?.replace("-", "")?.replace(" ", "") ?: ""
            if (code.length in 4..8) {
                Log.i(TAG, "Detected OTP code: $code from ${sbn.packageName}")
            }
        }

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
        if (title.isNotBlank() || text.isNotBlank()) {
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

            val event = com.jarvis.island.ui.NotificationIslandEvent(
                id = sbn.key ?: sbn.id.toString(),
                packageName = sbn.packageName,
                appName = appLabel,
                title = title,
                text = text,
                subText = subText,
                largeIcon = largeIconBitmap,
                contentIntent = sbn.notification.contentIntent,
                postTimeMs = sbn.postTime
            )

            mainHandler.post {
                onNotificationEvent?.invoke(event)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        checkActiveMedia()
        onMediaChanged?.invoke()
        if (sbn != null) {
            val key = sbn.key ?: sbn.id.toString()
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
                val component = ComponentName(this, JarvisNotificationListener::class.java)
                val controllers = mm?.getActiveSessions(component) ?: emptyList()

                // Find currently playing session first
                val target = controllers.firstOrNull {
                    it.playbackState?.state == PlaybackState.STATE_PLAYING
                } ?: controllers.firstOrNull()

                if (target != null) {
                    attachController(target)
                } else {
                    // Check if system audio manager reports music is playing
                    val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    val isMusicActive = am?.isMusicActive == true
                    if (isMusicActive && currentTrackInfo.title.isNotBlank()) {
                        currentTrackInfo = currentTrackInfo.copy(isPlaying = true)
                    } else if (!isMusicActive) {
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
            packageName = pkg
        )

        currentTrackInfo = track
        onMediaUpdate?.invoke(track)
        onMediaChanged?.invoke()
    }

    private fun startProgressTicker() {
        stopProgressTicker()
        progressTicker = object : Runnable {
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
        mainHandler.postDelayed(progressTicker!!, 500)
    }

    private fun stopProgressTicker() {
        progressTicker?.let { mainHandler.removeCallbacks(it) }
        progressTicker = null
    }
}
