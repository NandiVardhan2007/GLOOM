package com.gloom.island.service

import java.io.File
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
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
import com.gloom.island.bridge.FileBeamReceiver
import com.gloom.island.bridge.GloomLiveBridgeClient
import com.gloom.island.cutout.CutoutDetector
import com.gloom.island.cutout.CutoutMetrics
import com.gloom.island.security.GloomSecurity
import com.gloom.island.settings.GloomIslandSettings
import com.gloom.island.ui.ActiveTransferInfo
import com.gloom.island.ui.BridgeDeviceInfo
import com.gloom.island.ui.GloomIslandApp
import com.gloom.island.ui.MediaTrackInfo
import com.gloom.island.ui.NotificationIslandEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class GloomIslandOverlayService : Service() {

    companion object {
        var instance: GloomIslandOverlayService? = null
            private set
    }

    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var cutoutMetrics: CutoutMetrics = CutoutMetrics()
    private lateinit var bridgeClient: GloomLiveBridgeClient
    private lateinit var fileBeamReceiver: FileBeamReceiver
    private lateinit var audioManager: AudioManager
    private var mediaSessionManager: MediaSessionManager? = null
    private var activeMediaController: MediaController? = null

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
            showReelCounterUntil = System.currentTimeMillis() + 6000L
        }
    }

    fun updateReelViewerActive(inViewer: Boolean) {
        serviceScope.launch {
            if (isInstagramForeground != inViewer) {
                isInstagramForeground = inViewer
                if (!inViewer) {
                    showReelCounterUntil = System.currentTimeMillis() + 3000L
                }
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var collapseJob: Job? = null
    private var notificationJob: Job? = null
    private var demoMediaJob: Job? = null
    private var windowResizeJob: Job? = null
    private var demoMediaActive = false

    // Real System Battery & Power Receiver
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
                GloomIslandSettings.ACTION_SETTINGS_CHANGED -> {
                    updateOverlayParameters()
                }
                GloomIslandSettings.ACTION_TOGGLE_EXPAND -> {
                    toggleIslandExpansion()
                }
                GloomIslandSettings.ACTION_EXPAND -> {
                    expandIsland()
                }
                GloomIslandSettings.ACTION_COLLAPSE -> {
                    collapseIsland()
                }
                GloomIslandSettings.ACTION_TEST_BEAM -> {
                    simulateTestTransfer()
                }
                GloomIslandSettings.ACTION_TEST_MUSIC -> {
                    startDemoMedia()
                }
                GloomIslandSettings.ACTION_MEDIA_PLAY_PAUSE -> {
                    toggleMediaPlayback()
                }
                GloomIslandSettings.ACTION_MEDIA_NEXT -> {
                    skipToNextMedia()
                }
                GloomIslandSettings.ACTION_MEDIA_PREV -> {
                    skipToPrevMedia()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        bridgeClient = GloomLiveBridgeClient(this)
        GloomIslandSettings.instance.load(this)

        startForegroundServiceNotification()
        // The accessibility service draws the same island over every window,
        // including the keyguard. Running both leaves two islands on screen and
        // two copies of each notification. Accessibility wins once it is bound;
        // until then this service keeps the island alive.
        if (GloomIslandAccessibilityService.isRunning) {
            Log.i("GloomOverlay", "Accessibility island owns the overlay; stopping this duplicate")
            stopSelf()
            return
        }
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
                    if (GloomIslandSettings.instance.autoExpandOnTransfer && !isExpanded) {
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

        bridgeClient.startDiscovery { desktopName, desktopIp ->
            bridgeDevice = BridgeDeviceInfo(
                deviceName = desktopName,
                ipAddress = desktopIp,
                pingMs = 12,
                isConnected = true
            )
        }
    }

    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(GloomIslandSettings.ACTION_SETTINGS_CHANGED)
            addAction(GloomIslandSettings.ACTION_TOGGLE_EXPAND)
            addAction(GloomIslandSettings.ACTION_EXPAND)
            addAction(GloomIslandSettings.ACTION_COLLAPSE)
            addAction(GloomIslandSettings.ACTION_TEST_BEAM)
            addAction(GloomIslandSettings.ACTION_TEST_MUSIC)
            addAction(GloomIslandSettings.ACTION_MEDIA_PLAY_PAUSE)
            addAction(GloomIslandSettings.ACTION_MEDIA_NEXT)
            addAction(GloomIslandSettings.ACTION_MEDIA_PREV)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(settingsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(settingsReceiver, filter)
        }

        // Real Battery Changed Broadcast
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    private fun setupRealMediaSessions() {
        GloomNotificationListener.onMediaUpdate = { liveTrack ->
            serviceScope.launch {
                // Keep the sample track until a real session starts playing.
                if (demoMediaActive && !(liveTrack.isPlaying && liveTrack.title.isNotBlank())) {
                    return@launch
                }
                demoMediaActive = false
                mediaTrack = liveTrack
            }
        }
        GloomNotificationListener.onNotificationEvent = { event ->
            if (GloomIslandSettings.instance.pluginNotifications) {
                serviceScope.launch {
                    activeNotification = event
                    if (GloomIslandSettings.instance.autoExpandOnNotification && !isExpanded) {
                        expandIsland()
                    }
                    notificationJob?.cancel()
                    notificationJob = launch {
                        delay((GloomIslandSettings.instance.autoCollapseDelaySec * 1000).toLong())
                        activeNotification = null
                        if (isExpanded) {
                            collapseIsland()
                        }
                    }
                }
            }
        }
        GloomNotificationListener.onNotificationDismissed = { key ->
            serviceScope.launch {
                if (activeNotification?.id == key) {
                    activeNotification = null
                }
            }
        }
        GloomNotificationListener.instance?.checkActiveMedia()
    }

    /**
     * "Preview music activity" in the app: shows the real Apple music card with
     * a sample track so the layout can be reviewed without another app playing.
     * Clears itself after 12 seconds or as soon as a real media session appears.
     */
    private fun startDemoMedia() {
        demoMediaJob?.cancel()
        demoMediaActive = true
        mediaTrack = MediaTrackInfo(
            title = "Midnight City",
            artist = "M83",
            album = "Hurry Up, We're Dreaming",
            isPlaying = true,
            durationMs = 244000L,
            positionMs = 82000L,
            progress = 0.34f,
            appName = "GLOOM Preview"
        )
        if (!isExpanded) {
            expandIsland()
        }
        demoMediaJob = serviceScope.launch {
            delay(12000)
            if (demoMediaActive) {
                demoMediaActive = false
                mediaTrack = MediaTrackInfo()
            }
        }
    }

    private fun refreshActiveMediaSession() {
        try {
            val component = ComponentName(this, GloomNotificationListener::class.java)
            val controllers = mediaSessionManager?.getActiveSessions(component) ?: emptyList()
            if (controllers.isNotEmpty()) {
                val playingController = controllers.firstOrNull {
                    it.playbackState?.state == PlaybackState.STATE_PLAYING
                }
                if (playingController != null) {
                    bindMediaController(playingController)
                } else {
                    // A paused/empty session must not be presented as an active song.
                    activeMediaController = null
                    mediaTrack = MediaTrackInfo(title = "", artist = "", isPlaying = false, progress = 0f)
                }
            } else {
                val isMusicActive = audioManager.isMusicActive
                if (!isMusicActive) {
                    mediaTrack = MediaTrackInfo(title = "", artist = "", isPlaying = false, progress = 0f)
                }
            }
        } catch (e: SecurityException) {
            val isMusicActive = audioManager.isMusicActive
            if (isMusicActive) {
                // Notification access is missing, so the track cannot be identified.
                mediaTrack = MediaTrackInfo(title = "Audio Playing", artist = "System Media", isPlaying = true, progress = 0f)
            } else {
                mediaTrack = MediaTrackInfo(title = "", artist = "", isPlaying = false, progress = 0f)
            }
        } catch (e: Exception) {
            val isMusicActive = audioManager.isMusicActive
            mediaTrack = mediaTrack.copy(isPlaying = isMusicActive)
        }
    }

    private fun bindMediaController(controller: MediaController) {
        activeMediaController = controller
        val metadata = controller.metadata
        val state = controller.playbackState

        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val isPlaying = state?.state == PlaybackState.STATE_PLAYING

        if (title.isBlank() && artist.isBlank() && !isPlaying) {
            // Nothing identifiable is playing; never invent a track.
            mediaTrack = MediaTrackInfo(title = "", artist = "", isPlaying = false, progress = 0f)
            return
        }

        mediaTrack = MediaTrackInfo(
            title = if (title.isNotBlank()) title else "Audio Playing",
            artist = if (artist.isNotBlank()) artist else "Media Session",
            isPlaying = isPlaying,
            progress = 0.35f
        )

        controller.registerCallback(object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                val playing = state?.state == PlaybackState.STATE_PLAYING
                mediaTrack = mediaTrack.copy(isPlaying = playing)
            }

            override fun onMetadataChanged(metadata: MediaMetadata?) {
                val newTitle = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
                val newArtist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                mediaTrack = mediaTrack.copy(
                    title = if (newTitle.isNotBlank()) newTitle else mediaTrack.title,
                    artist = if (newArtist.isNotBlank()) newArtist else mediaTrack.artist
                )
            }
        })
    }

    private fun toggleMediaPlayback() {
        GloomNotificationListener.togglePlayPause()
    }

    private fun skipToNextMedia() {
        GloomNotificationListener.skipNext()
    }

    private fun skipToPrevMedia() {
        GloomNotificationListener.skipPrevious()
    }

    private fun startForegroundServiceNotification() {
        val channelId = "gloom_island_overlay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "GLOOM Island Overlay",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Active camera cutout dynamic island and live bridge"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
                .setContentTitle("GLOOM Island Active")
                .setContentText("Camera cutout overlay & ecosystem sync running")
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("GLOOM Island Active")
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .build()
        }

        startForeground(1001, notification)
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
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (GloomIslandSettings.instance.showOnLockScreen) {
            flags = flags or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        }
        return flags
    }

    private fun setupComposeOverlay() {
        if (GloomIslandAccessibilityService.isRunning) {
            return
        }
        cutoutMetrics = CutoutDetector.detectCutout(this, null)

        val initialHeight = if (isExpanded) {
            dpToPx((GloomIslandSettings.instance.expandedHeight + GloomIslandSettings.instance.positionY + 44).toFloat())
        } else {
            dpToPx((GloomIslandSettings.instance.positionY + GloomIslandSettings.instance.pillHeight + 20).toFloat())
        }

        val params = buildLayoutParams(initialHeight)
        layoutParams = params

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
                val isLandscape = currentConfig.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                if (isLandscape && !GloomIslandSettings.instance.showInLandscape) {
                    return@setContent
                }

                GloomIslandApp(
                    isExpanded = isExpanded,
                    onToggleExpand = { toggleIslandExpansion() },
                                        onLongPressExpand = {
                        try {
                            startActivity(
                                Intent(this@GloomIslandOverlayService, VoiceUnlockActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (_: Exception) {
                            expandIsland()
                        }
                    },
                    onBeamSampleFile = {
                        val itn = Intent(this@GloomIslandOverlayService, com.gloom.island.MainActivity::class.java).apply {
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
                        val itn = Intent(this@GloomIslandOverlayService, com.gloom.island.MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            putExtra("open_screen", "trackpad")
                        }
                        startActivity(itn)
                    },
                    onMediaPlayPause = { toggleMediaPlayback() },
                    onMediaNext = { skipToNextMedia() },
                    onMediaPrev = { skipToPrevMedia() },
                    onMediaSeek = { ratio -> GloomNotificationListener.seekTo(ratio) },
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
                    onSwipeDown = {},
                    settings = GloomIslandSettings.instance
                )
            }
        }

        composeView = view
        try {
            windowManager.addView(composeView, params)
            updateVisibilityForOrientation()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        updateVisibilityForOrientation(newConfig)
    }

    private fun updateVisibilityForOrientation(config: android.content.res.Configuration = resources.configuration) {
        val isLandscape = config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val shouldHide = isLandscape && !GloomIslandSettings.instance.showInLandscape
        if (shouldHide) {
            if (isExpanded) collapseIsland()
            composeView?.visibility = android.view.View.GONE
        } else {
            if (GloomIslandSettings.instance.isIslandEnabled) {
                composeView?.visibility = android.view.View.VISIBLE
            }
        }
    }

    private fun buildLayoutParams(height: Int): WindowManager.LayoutParams {
        val initialWidth = if (isExpanded) {
            WindowManager.LayoutParams.MATCH_PARENT
        } else {
            dpToPx((GloomIslandSettings.instance.pillWidth + 40).toFloat())
        }
        return WindowManager.LayoutParams(
            initialWidth,
            height,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            getOverlayFlags(),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun updateOverlayParameters() {
        val params = layoutParams ?: return
        params.flags = getOverlayFlags()
        params.width = if (isExpanded) {
            WindowManager.LayoutParams.MATCH_PARENT
        } else {
            dpToPx((GloomIslandSettings.instance.pillWidth + 40).toFloat())
        }
        params.height = if (isExpanded) {
            dpToPx((GloomIslandSettings.instance.expandedHeight + GloomIslandSettings.instance.positionY + 44).toFloat())
        } else {
            dpToPx((GloomIslandSettings.instance.positionY + GloomIslandSettings.instance.pillHeight + 20).toFloat())
        }
        try {
            composeView?.let { windowManager.updateViewLayout(it, params) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun expandIsland() {
        isExpanded = true
        windowResizeJob?.cancel()
        val params = layoutParams ?: return
        params.width = WindowManager.LayoutParams.MATCH_PARENT
        params.height = dpToPx((GloomIslandSettings.instance.expandedHeight + GloomIslandSettings.instance.positionY + 44).toFloat())
        params.flags = getOverlayFlags() and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL.inv()
        try {
            composeView?.let { windowManager.updateViewLayout(it, params) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun collapseIsland() {
        isExpanded = false
        windowResizeJob?.cancel()
        // Let the Compose collapse spring finish before shrinking the window,
        // otherwise the card is clipped mid-animation.
        windowResizeJob = serviceScope.launch {
            delay(420)
            if (isExpanded) return@launch
            val params = layoutParams ?: return@launch
            params.width = dpToPx((GloomIslandSettings.instance.pillWidth + 40).toFloat())
            params.height = dpToPx((GloomIslandSettings.instance.positionY + GloomIslandSettings.instance.pillHeight + 20).toFloat())
            params.flags = getOverlayFlags()
            try {
                composeView?.let { windowManager.updateViewLayout(it, params) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun toggleIslandExpansion() {
        if (isExpanded) {
            collapseIsland()
        } else {
            expandIsland()
        }
    }

    private fun scheduleAutoCollapse() {
        collapseJob?.cancel()
        collapseJob = serviceScope.launch {
            delay((GloomIslandSettings.instance.autoCollapseDelaySec * 1000).toLong())
            activeTransfer = activeTransfer.copy(isActive = false)
            if (isExpanded) {
                collapseIsland()
            }
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
            if (GloomIslandSettings.instance.autoExpandOnTransfer && !isExpanded) {
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

            delay((GloomIslandSettings.instance.autoCollapseDelaySec * 1000).toLong())
            activeTransfer = activeTransfer.copy(isActive = false)
            if (isExpanded) {
                collapseIsland()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Only release the shared callbacks when this service was the one
        // rendering. The early-stop path (accessibility owns the island) must
        // not clear the accessibility service's live callbacks.
        if (composeView != null && !GloomIslandAccessibilityService.isRunning) {
            GloomNotificationListener.onNotificationEvent = null
            GloomNotificationListener.onNotificationDismissed = null
            GloomNotificationListener.onMediaUpdate = null
        }
        if (::fileBeamReceiver.isInitialized) {
            fileBeamReceiver.stopServer()
        }
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

    override fun onBind(intent: Intent?): IBinder? = null
}
