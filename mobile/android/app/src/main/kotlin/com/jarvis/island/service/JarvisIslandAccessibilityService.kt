package com.jarvis.island.service

import java.io.File
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.os.PowerManager
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.compositionContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.jarvis.island.bridge.FileBeamReceiver
import com.jarvis.island.bridge.JarvisLiveBridgeClient
import com.jarvis.island.cutout.CutoutDetector
import com.jarvis.island.cutout.CutoutMetrics
import com.jarvis.island.security.JarvisSecurity
import com.jarvis.island.settings.JarvisIslandSettings
import com.jarvis.island.ui.ActiveTransferInfo
import com.jarvis.island.ui.BridgeDeviceInfo
import com.jarvis.island.ui.JarvisIslandApp
import com.jarvis.island.ui.MediaTrackInfo
import com.jarvis.island.ui.NotificationIslandEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * JARVIS Accessibility Overlay Service:
 * Uses TYPE_ACCESSIBILITY_OVERLAY to render Dynamic Island on the Android Lock Screen / Keyguard
 * and across all system windows with zero cutout blockage.
 */
class JarvisIslandAccessibilityService : AccessibilityService() {

    companion object {
        var isRunning: Boolean = false
            private set
        var instance: JarvisIslandAccessibilityService? = null
            private set
    }

    private val TAG = "JarvisA11yOverlay"
    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private lateinit var layoutParams: WindowManager.LayoutParams
    private var cutoutMetrics: CutoutMetrics = CutoutMetrics()
    private lateinit var bridgeClient: JarvisLiveBridgeClient
    private lateinit var fileBeamReceiver: FileBeamReceiver
    private lateinit var audioManager: AudioManager
    private var mediaSessionManager: MediaSessionManager? = null
    private var activeMediaController: MediaController? = null

    @Volatile
    private var lastForwardedClipboard: String = ""
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        forwardClipboardChange()
    }

    private var isExpanded by mutableStateOf(false)
    private var bridgeDevice by mutableStateOf(BridgeDeviceInfo())
    private var activeTransfer by mutableStateOf(ActiveTransferInfo())
    private var mediaTrack by mutableStateOf(MediaTrackInfo())
    private var activeNotification by mutableStateOf<NotificationIslandEvent?>(null)
    private var batteryLevel by mutableIntStateOf(100)
    private var isCharging by mutableStateOf(false)
    private var reelCount by mutableIntStateOf(0)
    private var isInstagramForeground by mutableStateOf(false)
    private var showReelCounterUntil by mutableStateOf(0L)

    fun updateReelCount(count: Int) {
        serviceScope.launch {
            reelCount = count
            showReelCounterUntil = System.currentTimeMillis() + 8000L
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var collapseJob: Job? = null
    private var windowResizeJob: Job? = null
    private var notificationJob: Job? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: String? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
                batteryLevel = if (level >= 0 && scale > 0) (level * 100) / scale else 100
            }
        }
    }

    private val settingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                JarvisIslandSettings.ACTION_SETTINGS_CHANGED -> updateOverlayParameters()
                JarvisIslandSettings.ACTION_TOGGLE_EXPAND -> toggleIslandExpansion()
                JarvisIslandSettings.ACTION_EXPAND -> expandIsland()
                JarvisIslandSettings.ACTION_COLLAPSE -> collapseIsland()
                JarvisIslandSettings.ACTION_TEST_BEAM -> simulateTestTransfer()
                JarvisIslandSettings.ACTION_TEST_MUSIC -> refreshActiveMediaSession()
                JarvisIslandSettings.ACTION_MEDIA_PLAY_PAUSE -> toggleMediaPlayback()
                JarvisIslandSettings.ACTION_MEDIA_NEXT -> skipToNextMedia()
                JarvisIslandSettings.ACTION_MEDIA_PREV -> skipToPrevMedia()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isRunning = true
        Log.i(TAG, "Accessibility Service Connected - Initializing Lock Screen Overlay")

        // This service owns the island whenever accessibility is enabled: it can
        // draw over the keyguard, where the plain overlay cannot. Leaving the
        // other service alive produces two overlapping islands and two copies of
        // every alert.
        runCatching { stopService(Intent(this, JarvisIslandOverlayService::class.java)) }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        bridgeClient = JarvisLiveBridgeClient(this)
        JarvisIslandSettings.instance.load(this)

        setupComposeOverlay()
        registerReceivers()
        setupRealMediaSessions()

        FileBeamReceiver.addListener(object : FileBeamReceiver.FileTransferListener {
            override fun onTransferStarted(fileName: String, fileSizeMb: Float, isUpload: Boolean) {
                serviceScope.launch {
                    activeTransfer = ActiveTransferInfo(
                        fileName = fileName,
                        fileSizeMb = fileSizeMb,
                        progress = 0.08f,
                        speedMbps = "112 MB/s",
                        isUpload = isUpload,
                        isActive = true
                    )
                    if (JarvisIslandSettings.instance.autoExpandOnTransfer && !isExpanded) {
                        expandIsland()
                    }
                }
            }

            override fun onTransferProgress(fileName: String, progress: Float, speedMbps: String, isUpload: Boolean) {
                serviceScope.launch {
                    activeTransfer = activeTransfer.copy(
                        progress = progress,
                        speedMbps = speedMbps,
                        isActive = true
                    )
                }
            }

            override fun onTransferCompleted(fileName: String, file: File?, isUpload: Boolean) {
                serviceScope.launch {
                    activeTransfer = activeTransfer.copy(
                        progress = 1.0f,
                        speedMbps = "Completed",
                        isActive = true
                    )
                    scheduleAutoCollapse()
                    delay(3500)
                    activeTransfer = ActiveTransferInfo()
                }
            }
        })

        fileBeamReceiver = FileBeamReceiver(this)
        fileBeamReceiver.startServer(53391)

        startClipboardSync()

        bridgeClient.startDiscovery { desktopName, desktopIp ->
            bridgeDevice = BridgeDeviceInfo(
                deviceName = desktopName,
                ipAddress = desktopIp,
                pingMs = 12,
                isConnected = true
            )
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val current = event ?: return
        val currentPkg = current.packageName?.toString().orEmpty()
        if (currentPkg == packageName) return

        if (currentPkg == "com.instagram.android" || currentPkg == "com.facebook.katana") {
            if (!isInstagramForeground) {
                isInstagramForeground = true
                reelCount = com.jarvis.island.automation.ReelCounter.today(this)
                updateReelCount(reelCount)
            }
        } else if (current.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && currentPkg.isNotEmpty()) {
            isInstagramForeground = false
        }

        try {
            com.jarvis.island.automation.InstagramReelTracker.onEvent(this, current)
            com.jarvis.island.automation.ScreenTimeTracker.onEvent(this, current)
        } catch (e: Exception) {
            Log.w(TAG, "Could not inspect accessibility event: ${e.message}")
        }
    }

    private fun startClipboardSync() {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.addPrimaryClipChangedListener(clipboardListener)
            Log.i(TAG, "Universal Clipboard listener attached")
        } catch (e: Exception) {
            Log.w(TAG, "Could not attach the clipboard listener: ${e.message}")
        }
    }

    private fun forwardClipboardChange() {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = cm.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)?.text?.toString()
                .orEmpty()
            if (text.isEmpty()) {
                Log.d(TAG, "Clipboard changed but no text was readable (background access may be restricted)")
                return
            }
            // Text pushed from the desktop is written to this same clipboard; don't echo it back.
            if (text == lastForwardedClipboard || !FileBeamReceiver.shouldForwardClipboard(text)) return
            lastForwardedClipboard = text
            Log.i(TAG, "Forwarding clipboard change to desktop (${text.length} chars)")
            bridgeClient.syncClipboardToDesktop(text)
        } catch (e: Exception) {
            Log.w(TAG, "Clipboard sync failed: ${e.message}")
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(JarvisIslandSettings.ACTION_SETTINGS_CHANGED)
            addAction(JarvisIslandSettings.ACTION_TOGGLE_EXPAND)
            addAction(JarvisIslandSettings.ACTION_EXPAND)
            addAction(JarvisIslandSettings.ACTION_COLLAPSE)
            addAction(JarvisIslandSettings.ACTION_TEST_BEAM)
            addAction(JarvisIslandSettings.ACTION_TEST_MUSIC)
            addAction(JarvisIslandSettings.ACTION_MEDIA_PLAY_PAUSE)
            addAction(JarvisIslandSettings.ACTION_MEDIA_NEXT)
            addAction(JarvisIslandSettings.ACTION_MEDIA_PREV)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(settingsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(settingsReceiver, filter)
        }
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    private fun setupRealMediaSessions() {
        JarvisNotificationListener.onMediaUpdate = { liveTrack ->
            serviceScope.launch {
                mediaTrack = liveTrack
            }
        }
        JarvisNotificationListener.onNotificationEvent = { event ->
            if (JarvisIslandSettings.instance.pluginNotifications) {
                serviceScope.launch {
                    activeNotification = event
                    if (JarvisIslandSettings.instance.autoExpandOnNotification && !isExpanded) {
                        expandIsland()
                    }
                    notificationJob?.cancel()
                    notificationJob = launch {
                        delay((JarvisIslandSettings.instance.autoCollapseDelaySec * 1000).toLong())
                        activeNotification = null
                        if (isExpanded) {
                            collapseIsland()
                        }
                    }
                }
            }
        }
        JarvisNotificationListener.onNotificationDismissed = { key ->
            serviceScope.launch {
                if (activeNotification?.id == key) {
                    activeNotification = null
                }
            }
        }
        JarvisNotificationListener.instance?.checkActiveMedia()
    }

    private fun refreshActiveMediaSession() {
        JarvisNotificationListener.instance?.checkActiveMedia()
    }

    private fun toggleMediaPlayback() {
        JarvisNotificationListener.togglePlayPause()
    }

    private fun skipToNextMedia() {
        JarvisNotificationListener.skipNext()
    }

    private fun skipToPrevMedia() {
        JarvisNotificationListener.skipPrevious()
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        ).toInt()
    }

    @Suppress("DEPRECATION")
    private fun getOverlayFlags(): Int {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        if (JarvisIslandSettings.instance.showOnLockScreen) {
            flags = flags or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        }
        return flags
    }

    private fun setupComposeOverlay() {
        cutoutMetrics = CutoutDetector.detectCutout(this, null)

        val initialHeight = if (isExpanded) {
            dpToPx((JarvisIslandSettings.instance.expandedHeight + JarvisIslandSettings.instance.positionY + 44).toFloat())
        } else {
            dpToPx((JarvisIslandSettings.instance.positionY + JarvisIslandSettings.instance.pillHeight + 20).toFloat())
        }
        val initialWidth = if (isExpanded) {
            WindowManager.LayoutParams.MATCH_PARENT
        } else {
            dpToPx((JarvisIslandSettings.instance.pillWidth + 24).toFloat())
        }

        // TYPE_ACCESSIBILITY_OVERLAY renders above Lock Screen / Keyguard
        layoutParams = WindowManager.LayoutParams(
            initialWidth,
            initialHeight,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            getOverlayFlags(),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }

        val view = ComposeView(this).apply {
            val viewModelStore = ViewModelStore()
            val viewModelStoreOwner = object : ViewModelStoreOwner {
                override val viewModelStore: ViewModelStore
                    get() = viewModelStore
            }

            val lifecycleOwner = MyLifecycleOwner()
            lifecycleOwner.performRestore(null)
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(viewModelStoreOwner)

            val coroutineContext = AndroidUiDispatcher.CurrentThread
            val runRecomposeScope = CoroutineScope(coroutineContext)
            val recomposer = Recomposer(coroutineContext)
            compositionContext = recomposer
            runRecomposeScope.launch {
                recomposer.runRecomposeAndApplyChanges()
            }

            setContent {
                val currentConfig = androidx.compose.ui.platform.LocalConfiguration.current
                val isLandscape = currentConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
                if (isLandscape && !JarvisIslandSettings.instance.showInLandscape) {
                    return@setContent
                }

                JarvisIslandApp(
                    isExpanded = isExpanded,
                    onToggleExpand = { toggleIslandExpansion() },
                                        onLongPressExpand = {
                        try {
                            startActivity(
                                Intent(this@JarvisIslandAccessibilityService, VoiceUnlockActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (_: Exception) {
                            expandIsland()
                        }
                    },
                    onBeamSampleFile = {
                        val itn = Intent(this@JarvisIslandAccessibilityService, com.jarvis.island.MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            putExtra("action_beam_file_picker", true)
                        }
                        startActivity(itn)
                        collapseIsland()
                    },
                    onSyncClipboard = {
                        bridgeClient.syncClipboardToDesktop("Manual clipboard sync triggered")
                    },
                    onOpenTrackpad = {
                        collapseIsland()
                        val itn = Intent(this@JarvisIslandAccessibilityService, com.jarvis.island.MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            putExtra("open_screen", "trackpad")
                        }
                        startActivity(itn)
                    },
                    onMediaPlayPause = { toggleMediaPlayback() },
                    onMediaNext = { skipToNextMedia() },
                    onMediaPrev = { skipToPrevMedia() },
                    onMediaSeek = { ratio -> JarvisNotificationListener.seekTo(ratio) },
                    activeTransfer = activeTransfer,
                    mediaTrack = mediaTrack,
                    activeNotification = activeNotification,
                    onNotificationClick = {
                        try {
                            activeNotification?.contentIntent?.send()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        activeNotification = null
                        collapseIsland()
                    },
                    onNotificationDismiss = {
                        activeNotification = null
                        collapseIsland()
                    },
                    bridgeDevice = bridgeDevice,
                    batteryLevel = batteryLevel,
                    isCharging = isCharging,
                    reelCount = reelCount,
                    isInstagramForeground = isInstagramForeground,
                    showReelCounterUntil = showReelCounterUntil,
                    onSwipeDown = {
                        performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
                    },
                    settings = JarvisIslandSettings.instance
                )
            }
        }

        composeView = view
        try {
            windowManager.addView(composeView, layoutParams)
            updateVisibilityForOrientation()
        } catch (e: Exception) {
            Log.e(TAG, "Error adding accessibility overlay view", e)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateVisibilityForOrientation(newConfig)
    }

    private fun updateVisibilityForOrientation(config: Configuration = resources.configuration) {
        val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
        val shouldHide = isLandscape && !JarvisIslandSettings.instance.showInLandscape
        if (shouldHide) {
            if (isExpanded) collapseIsland()
            composeView?.visibility = View.GONE
        } else {
            if (JarvisIslandSettings.instance.isIslandEnabled) {
                composeView?.visibility = View.VISIBLE
            }
        }
    }

    private fun updateOverlayParameters() {
        updateVisibilityForOrientation()
        layoutParams.flags = getOverlayFlags()
        layoutParams.width = if (isExpanded) {
            WindowManager.LayoutParams.MATCH_PARENT
        } else {
            dpToPx((JarvisIslandSettings.instance.pillWidth + 24).toFloat())
        }
        layoutParams.height = if (isExpanded) {
            dpToPx((JarvisIslandSettings.instance.expandedHeight + JarvisIslandSettings.instance.positionY + 44).toFloat())
        } else {
            dpToPx((JarvisIslandSettings.instance.positionY + JarvisIslandSettings.instance.pillHeight + 20).toFloat())
        }
        try {
            composeView?.let { windowManager.updateViewLayout(it, layoutParams) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun expandIsland() {
        isExpanded = true
        windowResizeJob?.cancel()
        layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT
        layoutParams.height = dpToPx((JarvisIslandSettings.instance.expandedHeight + JarvisIslandSettings.instance.positionY + 44).toFloat())
        layoutParams.flags = getOverlayFlags() and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL.inv()
        try {
            composeView?.let { windowManager.updateViewLayout(it, layoutParams) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun collapseIsland() {
        isExpanded = false
        windowResizeJob?.cancel()
        windowResizeJob = serviceScope.launch {
            delay(380)
            if (isExpanded) return@launch
            layoutParams.width = dpToPx((JarvisIslandSettings.instance.pillWidth + 24).toFloat())
            layoutParams.height = dpToPx((JarvisIslandSettings.instance.positionY + JarvisIslandSettings.instance.pillHeight + 20).toFloat())
            layoutParams.flags = getOverlayFlags()
            try {
                composeView?.let { windowManager.updateViewLayout(it, layoutParams) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun toggleIslandExpansion() {
        if (isExpanded) collapseIsland() else expandIsland()
    }

    private fun scheduleAutoCollapse() {
        collapseJob?.cancel()
        collapseJob = serviceScope.launch {
            delay((JarvisIslandSettings.instance.autoCollapseDelaySec * 1000).toLong())
            activeTransfer = activeTransfer.copy(isActive = false)
            if (isExpanded) collapseIsland()
        }
    }

    /**
     * Draws the user's saved pattern on the lock screen. This only works on
     * devices/ROMs that allow accessibility gestures over the secure keyguard;
     * otherwise the caller falls back to the system unlock prompt.
     */
    fun performUnlockPattern(pattern: List<Int>): Boolean {
        if (pattern.size < 4) return false
        return try {
            val metrics = resources.displayMetrics
            val width = metrics.widthPixels.toFloat()
            val height = metrics.heightPixels.toFloat()
            val settings = com.jarvis.island.settings.JarvisIslandSettings.instance
            // Calibrated keyguard pattern grid geometry
            val grid = width * settings.patternGridWidthRatio
            val left = (width - grid) / 2f
            val cell = grid / 3f
            val top = height * settings.patternCenterYRatio - grid / 2f
            val points = pattern.map { index ->
                PointF(
                    left + cell * (index % 3) + cell / 2f,
                    top + cell * (index / 3) + cell / 2f
                )
            }
            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            // A human draws this in about 6 s; 150 ms per dot stays well inside
            // the keyguard's sampling window without feeling sluggish.
            val duration = (pattern.size * 150L).coerceIn(600L, 2200L)
            val patternGesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0L, duration))
                .build()

            // The pattern bouncer only exists after the keyguard is swiped up.
            // Bypasses the optical in-display fingerprint sensor (FOD) which sits
            // in the lower center (0.50w, 0.82-0.88h).
            val (startX, startY, endX, endY) = when (settings.swipeStrategy) {
                "Right" -> floatArrayOf(width * 0.78f, height * 0.82f, width * 0.78f, height * 0.25f)
                "HighCenter" -> floatArrayOf(width * 0.50f, height * 0.62f, width * 0.50f, height * 0.18f)
                "Custom" -> floatArrayOf(
                    width * settings.swipeStartXRatio,
                    height * settings.swipeStartYRatio,
                    width * settings.swipeEndXRatio,
                    height * settings.swipeEndYRatio
                )
                else -> floatArrayOf(width * 0.22f, height * 0.82f, width * 0.22f, height * 0.25f) // "Left" default
            }
            val reveal = Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }
            val revealGesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(reveal, 0L, 260L))
                .build()
            if (!dispatchGesture(revealGesture, null, null)) {
                Log.w(TAG, "The system rejected the keyguard swipe-up gesture")
                return false
            }

            serviceScope.launch {
                var bouncerReady = waitForPatternBouncer(2600)
                if (!bouncerReady) {
                    Log.w(TAG, "Pattern page did not appear after the first swipe; swiping up again")
                    dispatchGesture(revealGesture, null, null)
                    bouncerReady = waitForPatternBouncer(2200)
                }
                if (!bouncerReady) {
                    logKeyguardWindows()
                } else {
                    // Let the bouncer finish its entry animation, otherwise the
                    // first dot of the stroke is dropped.
                    delay(300)
                }

                // If live pattern view is detected, use its exact physical pixel coordinates!
                val liveBounds = capturePatternGridBounds()
                val livePoints = if (liveBounds != null && liveBounds.width() > 100) {
                    val cW = liveBounds.width().toFloat() / 3f
                    val cH = liveBounds.height().toFloat() / 3f
                    pattern.map { index ->
                        PointF(
                            liveBounds.left + cW * (index % 3) + cW / 2f,
                            liveBounds.top + cH * (index / 3) + cH / 2f
                        )
                    }
                } else {
                    points
                }

                val strokePath = Path().apply {
                    moveTo(livePoints.first().x, livePoints.first().y)
                    livePoints.drop(1).forEach { lineTo(it.x, it.y) }
                }
                val strokeDuration = (pattern.size * 180L).coerceIn(800L, 2400L)
                val strokeGesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(strokePath, 0L, strokeDuration))
                    .build()

                Log.i(TAG, "Replaying unlock gesture across ${livePoints.size} points over ${strokeDuration}ms")
                if (!dispatchGesture(strokeGesture, null, null)) {
                    Log.w(TAG, "The system rejected the pattern replay gesture")
                }
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not replay the unlock pattern: ${e.message}")
            false
        }
    }

    fun lockScreen(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    }

    fun wakeScreen() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            val wakeLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "jarvis:wake"
            )
            wakeLock.acquire(4000)
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire wake lock: ${e.message}")
        }
    }

    fun findPatternViewNode(): android.view.accessibility.AccessibilityNodeInfo? {
        val candidates = mutableListOf<android.view.accessibility.AccessibilityNodeInfo>()
        rootInActiveWindow?.let { candidates.add(it) }
        try {
            windows?.forEach { w -> w.root?.let { candidates.add(it) } }
        } catch (_: Exception) {}

        for (root in candidates) {
            val found = searchPatternNode(root, 0)
            if (found != null) return found
        }
        return null
    }

    private fun searchPatternNode(node: android.view.accessibility.AccessibilityNodeInfo?, depth: Int): android.view.accessibility.AccessibilityNodeInfo? {
        if (node == null || depth > 25) return null
        val cls = node.className?.toString().orEmpty()
        val id = node.viewIdResourceName.orEmpty()
        if (cls.contains("LockPatternView", ignoreCase = true) ||
            cls.contains("PatternView", ignoreCase = true) ||
            id.contains("lockPatternView", ignoreCase = true) ||
            id.contains("lock_pattern", ignoreCase = true)
        ) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = try { node.getChild(i) } catch (_: Exception) { null } ?: continue
            val res = searchPatternNode(child, depth + 1)
            if (res != null) return res
        }
        return null
    }

    fun capturePatternGridBounds(): Rect? {
        val node = findPatternViewNode() ?: return null
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.width() > 100 && rect.height() > 100) {
            val metrics = resources.displayMetrics
            val screenW = metrics.widthPixels.toFloat()
            val screenH = metrics.heightPixels.toFloat()
            val settings = com.jarvis.island.settings.JarvisIslandSettings.instance
            settings.patternGridWidthRatio = (rect.width().toFloat() / screenW).coerceIn(0.40f, 0.95f)
            settings.patternCenterYRatio = (rect.exactCenterY() / screenH).coerceIn(0.35f, 0.90f)
            settings.save(this)
            Log.i(TAG, "Captured real lock pattern bounds: $rect (gridRatio=${settings.patternGridWidthRatio}, centerYRatio=${settings.patternCenterYRatio})")
            return rect
        }
        return null
    }

    fun startLivePatternCalibration(onStatus: (String) -> Unit) {
        serviceScope.launch {
            val km = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager

            onStatus("Locking device in 2 seconds…")
            speak("Locking screen. We will swipe to the pattern screen.")
            showLocalNotification("Calibration", "Locking screen in 2 seconds…")
            delay(2000)

            lockScreen()
            onStatus("Device locked. Verifying secure keyguard state…")
            
            var wasLocked = false
            val lockWaitDeadline = System.currentTimeMillis() + 4500L
            while (System.currentTimeMillis() < lockWaitDeadline) {
                if (km.isKeyguardLocked) {
                    wasLocked = true
                    break
                }
                delay(300)
            }

            delay(1000)
            wakeScreen()
            onStatus("Waking display…")
            delay(1200)

            onStatus("Swiping up to reveal pattern…")
            testSwipeGesture()
            delay(800)

            if (km.isKeyguardLocked) {
                testSwipeGesture()
                delay(700)
            }

            // Capture real pattern grid bounds if visible
            var bounds: Rect? = null
            val findDeadline = System.currentTimeMillis() + 8000L
            while (System.currentTimeMillis() < findDeadline) {
                bounds = capturePatternGridBounds()
                if (bounds != null) break
                delay(400)
            }

            if (wasLocked) {
                onStatus("Pattern screen ready! Please draw your pattern to unlock and verify.")
                speak("Please draw your pattern now to unlock and verify.")

                var unlocked = false
                val startWait = System.currentTimeMillis()
                val deadline = startWait + 35000L
                while (System.currentTimeMillis() < deadline) {
                    if (!km.isKeyguardLocked) {
                        // Ensure it wasn't an instant unlock (like Face Unlock under 1.5s)
                        if (System.currentTimeMillis() - startWait > 1500L) {
                            unlocked = true
                            break
                        }
                    }
                    delay(350)
                }

                if (unlocked) {
                    val settings = com.jarvis.island.settings.JarvisIslandSettings.instance
                    settings.save(this@JarvisIslandAccessibilityService)
                    val msg = "Pattern unlocked successfully! Grid calibrated at ${((settings.patternCenterYRatio) * 100).toInt()}% height."
                    onStatus(msg)
                    showLocalNotification("JARVIS Calibrated", msg)
                    speak("Pattern unlocked successfully. Calibration complete.")
                } else {
                    onStatus("Calibration incomplete or timed out. Please try again or draw pattern in Calibration settings.")
                    speak("Calibration incomplete.")
                }
            } else {
                onStatus("Screen was not locked (check lock timeout or Smart Lock). You can draw and save your pattern directly below in Calibration settings.")
                speak("Screen was not locked. You can draw your pattern directly in Calibration settings.")
            }
        }
    }

    /**
     * Injects the configured swipe gesture to test dismissing the lock screen without
     * triggering the in-display fingerprint sensor.
     */
    fun testSwipeGesture(): Boolean {
        return try {
            val metrics = resources.displayMetrics
            val width = metrics.widthPixels.toFloat()
            val height = metrics.heightPixels.toFloat()
            val settings = com.jarvis.island.settings.JarvisIslandSettings.instance
            val (startX, startY, endX, endY) = when (settings.swipeStrategy) {
                "Right" -> floatArrayOf(width * 0.78f, height * 0.82f, width * 0.78f, height * 0.25f)
                "HighCenter" -> floatArrayOf(width * 0.50f, height * 0.62f, width * 0.50f, height * 0.18f)
                "Custom" -> floatArrayOf(
                    width * settings.swipeStartXRatio,
                    height * settings.swipeStartYRatio,
                    width * settings.swipeEndXRatio,
                    height * settings.swipeEndYRatio
                )
                else -> floatArrayOf(width * 0.22f, height * 0.82f, width * 0.22f, height * 0.25f)
            }
            val reveal = Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(reveal, 0L, 260L))
                .build()
            dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            Log.w(TAG, "Test swipe gesture failed: ${e.message}")
            false
        }
    }

    /** Polls the accessibility window tree until the lock-screen pattern view exists. */
    private suspend fun waitForPatternBouncer(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (isPatternBouncerVisible()) return true
            delay(120)
        }
        return isPatternBouncerVisible()
    }

    private fun isPatternBouncerVisible(): Boolean {
        val windowList = try {
            windows
        } catch (e: Exception) {
            null
        } ?: return false
        return windowList.any { window ->
            val root = try {
                window.root
            } catch (e: Exception) {
                null
            }
            root != null && containsPatternView(root, 0)
        }
    }

    private fun containsPatternView(node: android.view.accessibility.AccessibilityNodeInfo?, depth: Int): Boolean {
        if (node == null || depth > 25) return false
        val className = node.className?.toString().orEmpty()
        val viewId = node.viewIdResourceName.orEmpty()
        if (className.contains("Pattern", ignoreCase = true) ||
            viewId.contains("lock_pattern", ignoreCase = true)
        ) {
            return true
        }
        for (index in 0 until node.childCount) {
            val child = try {
                node.getChild(index)
            } catch (e: Exception) {
                null
            } ?: continue
            if (containsPatternView(child, depth + 1)) return true
        }
        return false
    }

    private fun logKeyguardWindows() {
        try {
            val descriptions = windows.map { window ->
                val root = try {
                    window.root
                } catch (e: Exception) {
                    null
                }
                val cls = root?.className?.toString().orEmpty()
                "type=${window.type} pkg=${root?.packageName} cls=$cls"
            }
            Log.w(TAG, "Pattern page not detected. Windows: ${descriptions.joinToString(" | ")}")
        } catch (e: Exception) {
            Log.w(TAG, "Could not inspect keyguard windows: ${e.message}")
        }
    }

    fun handleRemoteControl(action: String, x: Float, y: Float, deltaX: Float, deltaY: Float) {
        val displayMetrics = resources.displayMetrics
        val screenW = displayMetrics.widthPixels.toFloat()
        val screenH = displayMetrics.heightPixels.toFloat()

        when (action) {
            "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "notifications" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            "lock" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            "tap" -> {
                val tapX = (x * screenW).coerceIn(10f, screenW - 10f)
                val tapY = (y * screenH).coerceIn(10f, screenH - 10f)
                injectTap(tapX, tapY)
            }
            "swipe_up" -> injectSwipe(screenW * 0.5f, screenH * 0.75f, screenW * 0.5f, screenH * 0.25f)
            "swipe_down" -> injectSwipe(screenW * 0.5f, screenH * 0.25f, screenW * 0.5f, screenH * 0.75f)
            "swipe_left" -> injectSwipe(screenW * 0.8f, screenH * 0.5f, screenW * 0.2f, screenH * 0.5f)
            "swipe_right" -> injectSwipe(screenW * 0.2f, screenH * 0.5f, screenW * 0.8f, screenH * 0.5f)
            "vol_up" -> audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            "vol_down" -> audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            "media_toggle" -> toggleMediaPlayback()
            "media_next" -> skipToNextMedia()
            "media_prev" -> skipToPrevMedia()
        }
    }

    private fun injectTap(tapX: Float, tapY: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = android.graphics.Path().apply { moveTo(tapX, tapY) }
            val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 50)
            val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build()
            dispatchGesture(gesture, null, null)
        }
    }

    private fun injectSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 250) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = android.graphics.Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }
            val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, durationMs)
            val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build()
            dispatchGesture(gesture, null, null)
        }
    }

    private fun simulateTestTransfer() {
        collapseJob?.cancel()
        serviceScope.launch {
            activeTransfer = ActiveTransferInfo(
                fileName = "Matrix_Holocron_UltraHD.mov",
                fileSizeMb = 142.4f,
                progress = 0.04f,
                speedMbps = "124 MB/s",
                isUpload = false,
                isActive = true
            )
            if (JarvisIslandSettings.instance.autoExpandOnTransfer && !isExpanded) {
                expandIsland()
            }

            val totalSteps = 30
            for (step in 1..totalSteps) {
                delay(80)
                val p = step.toFloat() / totalSteps.toFloat()
                val speed = (116 + (step % 5) * 4).toString() + " MB/s"
                activeTransfer = activeTransfer.copy(
                    progress = p,
                    speedMbps = speed
                )
            }

            delay((JarvisIslandSettings.instance.autoCollapseDelaySec * 1000).toLong())
            activeTransfer = activeTransfer.copy(isActive = false)
            if (isExpanded) collapseIsland()
        }
    }

    /**
     * Shows an alert JARVIS produced itself (a reminder, a finished transfer,
     * a desktop notification). These never pass through the system notification
     * listener, which deliberately skips our own package.
     */
    fun showLocalNotification(
        title: String,
        text: String,
        contentIntent: android.app.PendingIntent? = null,
        otp: String? = null,
    ) {
        val event = NotificationIslandEvent(
            id = "local-${System.currentTimeMillis()}",
            packageName = packageName,
            appName = "JARVIS",
            title = title,
            text = text,
            contentIntent = contentIntent,
            postTimeMs = System.currentTimeMillis(),
            otpCode = otp,
        )
        mainHandler.post {
            serviceScope.launch {
                activeNotification = event
                if (JarvisIslandSettings.instance.autoExpandOnNotification && !isExpanded) {
                    expandIsland()
                }
                notificationJob?.cancel()
                notificationJob = launch {
                    delay((JarvisIslandSettings.instance.autoCollapseDelaySec * 1000).toLong())
                    if (activeNotification?.id == event.id) {
                        activeNotification = null
                        if (isExpanded) collapseIsland()
                    }
                }
            }
        }
    }

    /**
     * Speaks a result after the voice panel has closed. Automation finishes
     * the panel first so injected touches reach the app underneath, which
     * means the panel's own text-to-speech engine is gone by then.
     */
    fun speak(text: String) {
        if (text.isBlank()) return
        mainHandler.post {
            val engine = tts
            if (engine == null) {
                pendingSpeech = text
                tts = TextToSpeech(this) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        val created = tts
                        val language = created?.setLanguage(Locale.US)
                        if (language == TextToSpeech.LANG_MISSING_DATA ||
                            language == TextToSpeech.LANG_NOT_SUPPORTED
                        ) {
                            created?.setLanguage(Locale.getDefault())
                        }
                        ttsReady = true
                        pendingSpeech?.let { queued ->
                            pendingSpeech = null
                            created?.speak(queued, TextToSpeech.QUEUE_FLUSH, null, "jarvis-result")
                        }
                    } else {
                        Log.w(TAG, "Result speech initialization failed ($status)")
                    }
                }
                return@post
            }
            if (!ttsReady) {
                pendingSpeech = text
                return@post
            }
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis-result")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            // Do not leave the listener pointing at a dead renderer: it would
            // keep consuming and cancelling alerts that nobody displays.
            JarvisNotificationListener.onNotificationEvent = null
            JarvisNotificationListener.onNotificationDismissed = null
            JarvisNotificationListener.onMediaUpdate = null
        }
        instance = null
        isRunning = false
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
        pendingSpeech = null
        fileBeamReceiver.stopServer()
        try {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .removePrimaryClipChangedListener(clipboardListener)
        } catch (e: Exception) {}
        try {
            unregisterReceiver(settingsReceiver)
            unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {}
        composeView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {}
        }
    }
}
