package com.jarvis.island.service

import android.accessibilityservice.AccessibilityService
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
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
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

    private var isExpanded by mutableStateOf(false)
    private var bridgeDevice by mutableStateOf(BridgeDeviceInfo())
    private var activeTransfer by mutableStateOf(ActiveTransferInfo())
    private var mediaTrack by mutableStateOf(MediaTrackInfo())
    private var activeNotification by mutableStateOf<NotificationIslandEvent?>(null)
    private var batteryLevel by mutableIntStateOf(100)
    private var isCharging by mutableStateOf(false)

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var collapseJob: Job? = null
    private var notificationJob: Job? = null

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

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        bridgeClient = JarvisLiveBridgeClient(this)
        JarvisIslandSettings.instance.load(this)

        setupComposeOverlay()
        registerReceivers()
        setupRealMediaSessions()

        fileBeamReceiver = FileBeamReceiver(this)
        fileBeamReceiver.startServer(53391) { receivedFile ->
            serviceScope.launch {
                activeTransfer = ActiveTransferInfo(
                    fileName = receivedFile.name,
                    fileSizeMb = (receivedFile.length() / (1024f * 1024f)),
                    progress = 1.0f,
                    speedMbps = "112 MB/s",
                    isUpload = false,
                    isActive = true
                )
                if (JarvisIslandSettings.instance.autoExpandOnTransfer && !isExpanded) {
                    expandIsland()
                }
                scheduleAutoCollapse()
            }
        }

        bridgeClient.startDiscovery { desktopName ->
            bridgeDevice = BridgeDeviceInfo(
                deviceName = desktopName,
                ipAddress = "192.168.1.4",
                pingMs = 12,
                isConnected = true
            )
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Monitors window state and screen transitions
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
            dpToPx((JarvisIslandSettings.instance.expandedHeight + JarvisIslandSettings.instance.positionY + 20).toFloat())
        } else {
            dpToPx((JarvisIslandSettings.instance.positionY + JarvisIslandSettings.instance.pillHeight + 20).toFloat())
        }

        // TYPE_ACCESSIBILITY_OVERLAY renders above Lock Screen / Keyguard
        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
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
                JarvisIslandApp(
                    isExpanded = isExpanded,
                    onToggleExpand = { toggleIslandExpansion() },
                    onLongPressExpand = { expandIsland() },
                    onBeamSampleFile = {
                        bridgeClient.syncClipboardToDesktop("Sample text beam from Motorola Edge 50 Pro")
                    },
                    onSyncClipboard = {
                        bridgeClient.syncClipboardToDesktop("Manual clipboard sync triggered")
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
                    settings = JarvisIslandSettings.instance
                )
            }
        }

        composeView = view
        try {
            windowManager.addView(composeView, layoutParams)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding accessibility overlay view", e)
        }
    }

    private fun updateOverlayParameters() {
        layoutParams.flags = getOverlayFlags()
        layoutParams.height = if (isExpanded) {
            WindowManager.LayoutParams.MATCH_PARENT
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
        layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT
        try {
            composeView?.let { windowManager.updateViewLayout(it, layoutParams) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun collapseIsland() {
        isExpanded = false
        layoutParams.height = dpToPx((JarvisIslandSettings.instance.positionY + JarvisIslandSettings.instance.pillHeight + 20).toFloat())
        try {
            composeView?.let { windowManager.updateViewLayout(it, layoutParams) }
        } catch (e: Exception) {
            e.printStackTrace()
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

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        isRunning = false
        fileBeamReceiver.stopServer()
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
