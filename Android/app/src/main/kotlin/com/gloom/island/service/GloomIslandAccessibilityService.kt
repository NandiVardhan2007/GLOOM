package com.gloom.island.service

import java.io.File
import android.app.KeyguardManager
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.os.PowerManager
import androidx.core.content.ContextCompat
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.graphics.Region
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import java.lang.reflect.Proxy
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
import com.gloom.island.ui.LockIslandEvent
import com.gloom.island.ui.NotificationIslandEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * GLOOM Accessibility Overlay Service:
 * Uses TYPE_ACCESSIBILITY_OVERLAY to render Dynamic Island on the Android Lock Screen / Keyguard
 * and across all system windows with zero cutout blockage.
 */
class GloomIslandAccessibilityService : AccessibilityService() {

    companion object {
        var isRunning: Boolean = false
            private set
        var instance: GloomIslandAccessibilityService? = null
            private set

        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val enabled = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.contains(context.packageName)
        }
    }

    private val TAG = "GloomA11yOverlay"
    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private lateinit var layoutParams: WindowManager.LayoutParams
    private var cutoutMetrics: CutoutMetrics = CutoutMetrics()
    private lateinit var bridgeClient: GloomLiveBridgeClient
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
    private var windowResizeJob: Job? = null
    private var notificationJob: Job? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: String? = null

    private var activeLockEvent by mutableStateOf<LockIslandEvent?>(null)
    private var lockEventJob: Job? = null

    fun triggerLockEvent(isUnlocked: Boolean) {
        serviceScope.launch {
            activeLockEvent = LockIslandEvent(isUnlocked = isUnlocked, timestampMs = System.currentTimeMillis())
            lockEventJob?.cancel()
            lockEventJob = launch {
                delay(2200)
                activeLockEvent = null
            }
        }
    }

    private val lockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> handleScreenOn()
                Intent.ACTION_SCREEN_OFF -> handleScreenOff()
                Intent.ACTION_USER_PRESENT -> handleUserPresent()
            }
        }
    }

    private fun handleScreenOff() {
        collapseIsland(immediate = true)
        notificationJob?.cancel()
        activeNotification = null

        if (!GloomIslandSettings.instance.showOnLockScreen) {
            composeView?.visibility = View.GONE
            layoutParams.flags = layoutParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            try {
                composeView?.let { windowManager.updateViewLayout(it, layoutParams) }
            } catch (_: Exception) {}
        }
        triggerLockEvent(isUnlocked = false)
    }

    private fun handleScreenOn() {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val locked = km?.isKeyguardLocked ?: true
        if (locked) {
            if (GloomIslandSettings.instance.showOnLockScreen) {
                applyCollapsedDimensions()
                if (GloomIslandSettings.instance.isIslandEnabled) {
                    composeView?.visibility = View.VISIBLE
                }
                triggerLockEvent(isUnlocked = false)
            } else {
                composeView?.visibility = View.GONE
                layoutParams.flags = layoutParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                try {
                    composeView?.let { windowManager.updateViewLayout(it, layoutParams) }
                } catch (_: Exception) {}
            }
        } else {
            handleUserPresent()
        }
    }

    private fun handleUserPresent() {
        collapseIsland(immediate = true)
        applyCollapsedDimensions()
        if (GloomIslandSettings.instance.isIslandEnabled) {
            composeView?.visibility = View.VISIBLE
        }
        triggerLockEvent(isUnlocked = true)
    }

    private val internalLockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.gloom.island.ACTION_LOCK_EVENT") {
                val isUnlocked = intent.getBooleanExtra("is_unlocked", false)
                triggerLockEvent(isUnlocked = isUnlocked)
            }
        }
    }

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
                GloomIslandSettings.ACTION_SETTINGS_CHANGED -> updateOverlayParameters()
                GloomIslandSettings.ACTION_TOGGLE_EXPAND -> toggleIslandExpansion()
                GloomIslandSettings.ACTION_EXPAND -> expandIsland()
                GloomIslandSettings.ACTION_COLLAPSE -> collapseIsland()
                GloomIslandSettings.ACTION_TEST_BEAM -> simulateTestTransfer()
                GloomIslandSettings.ACTION_TEST_MUSIC -> refreshActiveMediaSession()
                GloomIslandSettings.ACTION_MEDIA_PLAY_PAUSE -> toggleMediaPlayback()
                GloomIslandSettings.ACTION_MEDIA_NEXT -> skipToNextMedia()
                GloomIslandSettings.ACTION_MEDIA_PREV -> skipToPrevMedia()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isRunning = true
        Log.i(TAG, "Accessibility Service Connected - Initializing Lock Screen Overlay")

        // This service owns the island whenever accessibility is enabled: it can
        // draw over the keyguard, where the plain overlay cannot. Synchronously
        // remove any overlay view owned by GloomIslandOverlayService before adding ours.
        GloomIslandOverlayService.instance?.let { overlayService ->
            overlayService.removeOverlaySafely()
            overlayService.stopSelf()
        }
        runCatching { stopService(Intent(this, GloomIslandOverlayService::class.java)) }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        bridgeClient = GloomLiveBridgeClient(this)
        GloomIslandSettings.instance.load(this)

        serviceScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            com.gloom.island.ai.NeedleBridge.initialize(this@GloomIslandAccessibilityService)
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

            override fun onTransferFailed(fileName: String, error: String) {
                serviceScope.launch {
                    activeTransfer = activeTransfer.copy(
                        fileName = fileName,
                        speedMbps = "Failed",
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

        try {
            com.gloom.island.automation.InstagramReelTracker.onEvent(this, current)
            com.gloom.island.automation.ScreenTimeTracker.onEvent(this, current)
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
        ContextCompat.registerReceiver(this, settingsReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        // System Screen & Keyguard Lock/Unlock Broadcasts (system-only filter requires no export flag)
        val lockFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(lockReceiver, lockFilter)

        // Internal app lock event broadcast (not exported to external apps)
        val internalLockFilter = IntentFilter("com.gloom.island.ACTION_LOCK_EVENT")
        ContextCompat.registerReceiver(this, internalLockReceiver, internalLockFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun setupRealMediaSessions() {
        GloomNotificationListener.onMediaUpdate = { liveTrack ->
            serviceScope.launch {
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

    private fun refreshActiveMediaSession() {
        GloomNotificationListener.instance?.checkActiveMedia()
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

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        ).toInt()
    }

    private fun getCollapsedWidthPx(): Int {
        val density = resources.displayMetrics.density
        return ((GloomIslandSettings.instance.pillWidth + 24) * density).toInt()
    }

    private fun getCollapsedHeightPx(): Int {
        val density = resources.displayMetrics.density
        return ((GloomIslandSettings.instance.positionY + GloomIslandSettings.instance.pillHeight + 16) * density).toInt()
    }

    private fun getOverlayGravity(): Int {
        return when (GloomIslandSettings.instance.gravity) {
            "Left" -> Gravity.TOP or Gravity.START
            "Right" -> Gravity.TOP or Gravity.END
            else -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }
    }

    private fun getOverlayXOffsetPx(): Int {
        val density = resources.displayMetrics.density
        return (GloomIslandSettings.instance.positionX * density).toInt()
    }

    @Suppress("DEPRECATION")
    private fun getOverlayFlags(): Int {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        if (GloomIslandSettings.instance.showOnLockScreen) {
            flags = flags or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        }
        return flags
    }

    private var isViewAttached = false

    private fun addOverlayView() {
        val view = composeView ?: return
        if (isViewAttached || view.isAttachedToWindow) return
        try {
            windowManager.addView(view, layoutParams)
            isViewAttached = true
            updateVisibilityForOrientation()
        } catch (e: Exception) {
            Log.e(TAG, "Error adding accessibility overlay view", e)
        }
    }

    fun removeOverlaySafely() {
        val view = composeView ?: return
        if (!isViewAttached && !view.isAttachedToWindow) return
        try {
            if (view.isAttachedToWindow) {
                windowManager.removeViewImmediate(view)
            } else {
                windowManager.removeView(view)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error removing accessibility overlay view", e)
        } finally {
            isViewAttached = false
            composeView = null
        }
    }

    private fun setupComposeOverlay() {
        cutoutMetrics = CutoutDetector.detectCutout(this, null)

        val initialHeight = if (isExpanded) {
            dpToPx((GloomIslandSettings.instance.expandedHeight + GloomIslandSettings.instance.positionY + 44).toFloat())
        } else {
            getCollapsedHeightPx()
        }
        val initialWidth = if (isExpanded) {
            WindowManager.LayoutParams.MATCH_PARENT
        } else {
            getCollapsedWidthPx()
        }

        // TYPE_ACCESSIBILITY_OVERLAY renders above Lock Screen / Keyguard
        layoutParams = WindowManager.LayoutParams(
            initialWidth,
            initialHeight,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            getOverlayFlags(),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = if (isExpanded) Gravity.TOP or Gravity.CENTER_HORIZONTAL else getOverlayGravity()
            x = if (isExpanded) 0 else getOverlayXOffsetPx()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
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
                if (isLandscape && !GloomIslandSettings.instance.showInLandscape) {
                    return@setContent
                }

                GloomIslandApp(
                    isExpanded = isExpanded,
                    onToggleExpand = { toggleIslandExpansion() },
                                        onLongPressExpand = {
                        try {
                            startActivity(
                                Intent(this@GloomIslandAccessibilityService, VoiceUnlockActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (_: Exception) {
                            expandIsland()
                        }
                    },
                    onBeamSampleFile = {
                        val itn = Intent(this@GloomIslandAccessibilityService, com.gloom.island.bridge.ShareToPcActivity::class.java).apply {
                            action = "com.gloom.island.ACTION_PICK_AND_SHARE"
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(itn)
                        collapseIsland()
                    },
                    onSyncClipboard = {
                        bridgeClient.syncClipboardToDesktop("Manual clipboard sync triggered")
                    },
                    onMediaPlayPause = { toggleMediaPlayback() },
                    onMediaNext = { skipToNextMedia() },
                    onMediaPrev = { skipToPrevMedia() },
                    onMediaSeek = { ratio -> GloomNotificationListener.seekTo(ratio) },
                    activeTransfer = activeTransfer,
                    mediaTrack = mediaTrack,
                    activeNotification = activeNotification,
                    onNotificationClick = {
                        activeNotification?.openApp(applicationContext)
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
                    lockEvent = activeLockEvent,
                    onSwipeDown = {
                        performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
                    },
                    settings = GloomIslandSettings.instance
                )
            }
        }

        view.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_OUTSIDE) {
                if (isExpanded) {
                    collapseIsland()
                }
                true
            } else {
                false
            }
        }

        composeView = view
        setupTouchableRegion(view)
        addOverlayView()
    }

    private fun setupTouchableRegion(view: View) {
        try {
            val listenerClass = Class.forName("android.view.ViewTreeObserver\$OnComputeInternalInsetsListener")
            val insetsInfoClass = Class.forName("android.view.ViewTreeObserver\$InternalInsetsInfo")
            val setTouchableInsetsMethod = insetsInfoClass.getMethod("setTouchableInsets", Int::class.javaPrimitiveType)
            val touchableRegionField = insetsInfoClass.getField("touchableRegion")
            val regionSetMethod = Region::class.java.getMethod("set", Rect::class.java)

            val proxy = Proxy.newProxyInstance(
                listenerClass.classLoader,
                arrayOf(listenerClass)
            ) { _, method, args ->
                if (method.name == "onComputeInternalInsets" && args != null && args.isNotEmpty()) {
                    val inoutInfo = args[0]
                    if (!isExpanded) {
                        val density = resources.displayMetrics.density
                        val screenWidth = resources.displayMetrics.widthPixels
                        val pillWidthPx = ((GloomIslandSettings.instance.pillWidth + 24) * density).toInt()
                        val pillHeightPx = ((GloomIslandSettings.instance.pillHeight + 16) * density).toInt()
                        val posYPx = (GloomIslandSettings.instance.positionY * density).toInt()
                        val posXOffsetPx = (GloomIslandSettings.instance.positionX * density).toInt()
                        val left = when (GloomIslandSettings.instance.gravity) {
                            "Left" -> posXOffsetPx
                            "Right" -> screenWidth - pillWidthPx + posXOffsetPx
                            else -> ((screenWidth - pillWidthPx) / 2) + posXOffsetPx
                        }
                        val right = left + pillWidthPx
                        val top = posYPx
                        val bottom = top + pillHeightPx

                        setTouchableInsetsMethod.invoke(inoutInfo, 3) // TOUCHABLE_INSETS_REGION
                        val region = touchableRegionField.get(inoutInfo) as Region
                        regionSetMethod.invoke(region, Rect(left, top, right, bottom))
                    } else {
                        setTouchableInsetsMethod.invoke(inoutInfo, 0) // TOUCHABLE_INSETS_FRAME
                    }
                }
                null
            }

            val addMethod = ViewTreeObserver::class.java.getMethod("addOnComputeInternalInsetsListener", listenerClass)
            addMethod.invoke(view.viewTreeObserver, proxy)
        } catch (_: Throwable) {
            // Graceful fallback: window layout bounds themselves constrain touches
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateVisibilityForOrientation(newConfig)
    }

    private fun updateVisibilityForOrientation(config: Configuration = resources.configuration) {
        val view = composeView ?: return
        val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
        val shouldHide = (isLandscape && !GloomIslandSettings.instance.showInLandscape) ||
                !GloomIslandSettings.instance.isIslandEnabled
        if (shouldHide) {
            if (isExpanded) collapseIsland(immediate = true)
            view.visibility = View.GONE
            layoutParams.flags = layoutParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            try {
                windowManager.updateViewLayout(view, layoutParams)
            } catch (_: Exception) {}
        } else {
            view.visibility = View.VISIBLE
            layoutParams.flags = getOverlayFlags()
            try {
                windowManager.updateViewLayout(view, layoutParams)
            } catch (_: Exception) {}
        }
    }

    private fun updateOverlayParameters() {
        val view = composeView ?: return
        if (!isViewAttached && !view.isAttachedToWindow) return

        if (!GloomIslandSettings.instance.isIslandEnabled) {
            if (isExpanded) collapseIsland(immediate = true)
            view.visibility = View.GONE
            layoutParams.flags = getOverlayFlags() or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            try {
                windowManager.updateViewLayout(view, layoutParams)
            } catch (_: Exception) {}
            return
        }

        updateVisibilityForOrientation()
        layoutParams.flags = getOverlayFlags()
        if (isExpanded) {
            layoutParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            layoutParams.x = 0
            layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT
            layoutParams.height = dpToPx((GloomIslandSettings.instance.expandedHeight + GloomIslandSettings.instance.positionY + 44).toFloat())
        } else {
            layoutParams.gravity = getOverlayGravity()
            layoutParams.x = getOverlayXOffsetPx()
            layoutParams.width = getCollapsedWidthPx()
            layoutParams.height = getCollapsedHeightPx()
        }
        try {
            windowManager.updateViewLayout(view, layoutParams)
        } catch (e: Exception) {
            Log.w(TAG, "Error updating overlay layout", e)
        }
    }

    private fun expandIsland() {
        if (!GloomIslandSettings.instance.isIslandEnabled) return
        isExpanded = true
        windowResizeJob?.cancel()
        val view = composeView ?: return
        if (!isViewAttached && !view.isAttachedToWindow) return
        layoutParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        layoutParams.x = 0
        layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT
        layoutParams.height = dpToPx((GloomIslandSettings.instance.expandedHeight + GloomIslandSettings.instance.positionY + 44).toFloat())
        layoutParams.flags = getOverlayFlags()
        try {
            windowManager.updateViewLayout(view, layoutParams)
        } catch (e: Exception) {
            Log.w(TAG, "Error expanding overlay view", e)
        }
    }

    private fun collapseIsland(immediate: Boolean = false) {
        if (!isExpanded && !immediate) return
        isExpanded = false
        windowResizeJob?.cancel()
        if (immediate) {
            applyCollapsedDimensions()
        } else {
            windowResizeJob = serviceScope.launch {
                delay(380)
                if (isExpanded) return@launch
                applyCollapsedDimensions()
            }
        }
    }

    private fun applyCollapsedDimensions() {
        val view = composeView ?: return
        if (!isViewAttached && !view.isAttachedToWindow) return
        layoutParams.gravity = getOverlayGravity()
        layoutParams.x = getOverlayXOffsetPx()
        layoutParams.width = getCollapsedWidthPx()
        layoutParams.height = getCollapsedHeightPx()
        layoutParams.flags = getOverlayFlags()
        try {
            windowManager.updateViewLayout(view, layoutParams)
        } catch (e: Exception) {
            Log.w(TAG, "Error applying collapsed dimensions", e)
        }
    }

    private fun toggleIslandExpansion() {
        if (isExpanded) collapseIsland() else expandIsland()
    }

    private fun scheduleAutoCollapse() {
        collapseJob?.cancel()
        collapseJob = serviceScope.launch {
            delay((GloomIslandSettings.instance.autoCollapseDelaySec * 1000).toLong())
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
        val normPattern = com.gloom.island.security.GloomSecurity.normalizePattern(pattern)
        if (normPattern.size < 4) return false
        val km = getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
        if (km != null && !km.isKeyguardLocked) {
            Log.i(TAG, "Device is already unlocked, skipping pattern replay")
            return true
        }
        return try {
            wakeScreen()
            serviceScope.launch {
                // Give display hardware time to wake from doze/sleep before accepting touch gestures
                delay(220)

                val metrics = resources.displayMetrics
                val width = metrics.widthPixels.toFloat()
                val height = metrics.heightPixels.toFloat()
                val settings = com.gloom.island.settings.GloomIslandSettings.instance

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

                var bouncerReady = isPatternBouncerVisible()
                if (!bouncerReady) {
                    dispatchGesture(revealGesture, null, null)
                    bouncerReady = waitForPatternBouncer(2600)
                    if (!bouncerReady) {
                        Log.w(TAG, "Pattern page did not appear after the first swipe; swiping up again")
                        dispatchGesture(revealGesture, null, null)
                        bouncerReady = waitForPatternBouncer(2200)
                    }
                }

                if (!bouncerReady) {
                    logKeyguardWindows()
                } else {
                    // Let the bouncer finish its entry animation fully (350ms)
                    delay(350)
                }

                // Calibrated keyguard pattern grid geometry
                val grid = width * settings.patternGridWidthRatio
                val left = (width - grid) / 2f
                val cell = grid / 3f
                val top = height * settings.patternCenterYRatio - grid / 2f
                val fallbackPoints = normPattern.map { index ->
                    PointF(
                        left + cell * (index % 3) + cell / 2f,
                        top + cell * (index / 3) + cell / 2f
                    )
                }

                // If live pattern view is detected, use its exact physical pixel coordinates!
                val liveBounds = capturePatternGridBounds()
                val livePoints = if (liveBounds != null && liveBounds.width() > 100) {
                    val cW = liveBounds.width().toFloat() / 3f
                    val cH = liveBounds.height().toFloat() / 3f
                    normPattern.map { index ->
                        PointF(
                            liveBounds.left + cW * (index % 3) + cW / 2f,
                            liveBounds.top + cH * (index / 3) + cH / 2f
                        )
                    }
                } else {
                    fallbackPoints
                }

                val strokePath = Path().apply {
                    val first = livePoints.first()
                    moveTo(first.x, first.y)
                    // Micro-dwell on initial dot to guarantee initial touch DOWN is registered inside dot hitbox
                    lineTo(first.x + 0.5f, first.y + 0.5f)
                    lineTo(first.x, first.y)
                    for (i in 1 until livePoints.size) {
                        val pt = livePoints[i]
                        lineTo(pt.x, pt.y)
                        lineTo(pt.x + 0.5f, pt.y + 0.5f)
                        lineTo(pt.x, pt.y)
                    }
                }
                val strokeDuration = (normPattern.size * 220L).coerceIn(1000L, 2800L)
                val strokeGesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(strokePath, 0L, strokeDuration))
                    .build()

                Log.i(TAG, "Replaying unlock gesture across ${livePoints.size} points over ${strokeDuration}ms")
                val dispatched = dispatchGesture(strokeGesture, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        Log.i(TAG, "Pattern unlock gesture completed successfully!")
                    }
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        Log.w(TAG, "Pattern unlock gesture was cancelled by system")
                    }
                }, null)
                if (!dispatched) {
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
                "gloom:wake"
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
        val desc = node.contentDescription?.toString().orEmpty()

        // 1. Prioritize direct hit on the actual lockPatternView widget (not full-screen bouncer container)
        val isPatternWidget = cls.contains("LockPatternView", ignoreCase = true) ||
            id.endsWith("lockPatternView", ignoreCase = true) ||
            id.endsWith("lock_pattern_view", ignoreCase = true) ||
            (id.contains("lockPattern", ignoreCase = true) && !id.contains("container", ignoreCase = true))

        if (isPatternWidget) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            val screenH = resources.displayMetrics.heightPixels.toFloat()
            if (rect.width() > 100 && rect.height() > 100 && rect.height() < screenH * 0.70f) {
                return node
            }
        }

        // Search children first so deepest specific widgets take precedence over parent containers
        for (i in 0 until node.childCount) {
            val child = try { node.getChild(i) } catch (_: Exception) { null } ?: continue
            val res = searchPatternNode(child, depth + 1)
            if (res != null) return res
        }

        // 2. Fallback: match any view mentioning pattern, strictly rejecting container/flipper nodes
        val isContainer = id.contains("container", ignoreCase = true) ||
            id.contains("flipper", ignoreCase = true) ||
            id.contains("security", ignoreCase = true) ||
            cls.contains("Layout", ignoreCase = true)

        if (!isContainer) {
            if (cls.contains("LockPattern", ignoreCase = true) ||
                cls.contains("PatternView", ignoreCase = true) ||
                (id.contains("pattern", ignoreCase = true) && cls.contains("View", ignoreCase = true)) ||
                desc.contains("pattern", ignoreCase = true)
            ) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                val screenH = resources.displayMetrics.heightPixels.toFloat()
                if (rect.width() > 100 && rect.height() > 100 && rect.height() < screenH * 0.70f) {
                    return node
                }
            }
        }
        return null
    }

    fun capturePatternGridBounds(): Rect? {
        val node = findPatternViewNode() ?: return null
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val metrics = resources.displayMetrics
        val screenW = metrics.widthPixels.toFloat()
        val screenH = metrics.heightPixels.toFloat()
        // Must be a valid pattern grid widget (not a full screen container)
        if (rect.width() > 100 && rect.height() > 100 && rect.height() < screenH * 0.70f && rect.width() < screenW * 0.95f) {
            val settings = com.gloom.island.settings.GloomIslandSettings.instance
            settings.patternGridWidthRatio = (rect.width().toFloat() / screenW).coerceIn(0.40f, 0.90f)
            settings.patternCenterYRatio = (rect.exactCenterY() / screenH).coerceIn(0.40f, 0.85f)
            settings.save(this)
            Log.i(TAG, "Captured real lock pattern bounds: $rect (gridRatio=${settings.patternGridWidthRatio}, centerYRatio=${settings.patternCenterYRatio})")
            return rect
        }
        Log.w(TAG, "Ignoring non-pattern bounds: $rect")
        return null
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
            val settings = com.gloom.island.settings.GloomIslandSettings.instance
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
        try {
            val activeRoot = rootInActiveWindow
            if (activeRoot != null && containsPatternView(activeRoot, 0)) return true
        } catch (_: Exception) {}

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
        val desc = node.contentDescription?.toString().orEmpty()
        if (className.contains("Pattern", ignoreCase = true) ||
            className.contains("Bouncer", ignoreCase = true) ||
            viewId.contains("lock_pattern", ignoreCase = true) ||
            viewId.contains("pattern", ignoreCase = true) ||
            viewId.contains("bouncer", ignoreCase = true) ||
            desc.contains("pattern", ignoreCase = true)
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
            if (isExpanded) collapseIsland()
        }
    }

    /**
     * Shows an alert GLOOM produced itself (a reminder, a finished transfer,
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
            appName = "GLOOM",
            title = title,
            text = text,
            contentIntent = contentIntent,
            postTimeMs = System.currentTimeMillis(),
            otpCode = otp,
        )
        mainHandler.post {
            serviceScope.launch {
                activeNotification = event
                if (GloomIslandSettings.instance.autoExpandOnNotification && !isExpanded) {
                    expandIsland()
                }
                notificationJob?.cancel()
                notificationJob = launch {
                    delay((GloomIslandSettings.instance.autoCollapseDelaySec * 1000).toLong())
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
                        if (created != null) {
                            com.gloom.island.voice.VoicePrefs.applyToTts(created, this@GloomIslandAccessibilityService)
                        }
                        ttsReady = true
                        pendingSpeech?.let { queued ->
                            pendingSpeech = null
                            created?.speak(queued, TextToSpeech.QUEUE_FLUSH, null, "gloom-result")
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
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "gloom-result")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            // Do not leave the listener pointing at a dead renderer: it would
            // keep consuming and cancelling alerts that nobody displays.
            GloomNotificationListener.onNotificationEvent = null
            GloomNotificationListener.onNotificationDismissed = null
            GloomNotificationListener.onMediaUpdate = null
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
            unregisterReceiver(lockReceiver)
            unregisterReceiver(internalLockReceiver)
        } catch (e: Exception) {}
        removeOverlaySafely()
    }
}
