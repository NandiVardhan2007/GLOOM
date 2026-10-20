package com.jarvis.island.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class JarvisIslandOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private lateinit var layoutParams: WindowManager.LayoutParams
    private var cutoutMetrics: CutoutMetrics = CutoutMetrics()
    private lateinit var bridgeClient: JarvisLiveBridgeClient
    private lateinit var fileBeamReceiver: FileBeamReceiver

    private var isExpanded by mutableStateOf(false)
    private var bridgeDevice by mutableStateOf(BridgeDeviceInfo())
    private var activeTransfer by mutableStateOf(ActiveTransferInfo())
    private var mediaTrack by mutableStateOf(MediaTrackInfo())

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var collapseJob: Job? = null

    private val settingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                JarvisIslandSettings.ACTION_SETTINGS_CHANGED -> {
                    updateOverlayParameters()
                }
                JarvisIslandSettings.ACTION_TOGGLE_EXPAND -> {
                    toggleIslandExpansion()
                }
                JarvisIslandSettings.ACTION_TEST_BEAM -> {
                    simulateTestTransfer()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        bridgeClient = JarvisLiveBridgeClient(this)
        JarvisIslandSettings.instance.load(this)

        startForegroundServiceNotification()
        setupComposeOverlay()
        registerSettingsReceiver()

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

    private fun registerSettingsReceiver() {
        val filter = IntentFilter().apply {
            addAction(JarvisIslandSettings.ACTION_SETTINGS_CHANGED)
            addAction(JarvisIslandSettings.ACTION_TOGGLE_EXPAND)
            addAction(JarvisIslandSettings.ACTION_TEST_BEAM)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(settingsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(settingsReceiver, filter)
        }
    }

    private fun startForegroundServiceNotification() {
        val channelId = "jarvis_island_overlay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "JARVIS Island Overlay",
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
                .setContentTitle("JARVIS Island Active")
                .setContentText("Camera cutout overlay & ecosystem sync running")
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("JARVIS Island Active")
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

    private fun getOverlayFlags(): Int {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (JarvisIslandSettings.instance.showOnLockScreen) {
            flags = flags or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        }
        return flags
    }

    private fun setupComposeOverlay() {
        cutoutMetrics = CutoutDetector.detectCutout(this, null)

        val initialHeight = if (isExpanded) {
            dpToPx(248f)
        } else {
            dpToPx((JarvisIslandSettings.instance.positionY + JarvisIslandSettings.instance.pillHeight + 18).toFloat())
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            initialHeight,
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
                    onBeamSampleFile = {
                        bridgeClient.syncClipboardToDesktop("Sample text beam from Motorola Edge 50 Pro")
                    },
                    onSyncClipboard = {
                        bridgeClient.syncClipboardToDesktop("Manual clipboard sync triggered")
                    },
                    activeTransfer = activeTransfer,
                    mediaTrack = mediaTrack,
                    bridgeDevice = bridgeDevice,
                    settings = JarvisIslandSettings.instance
                )
            }
        }

        composeView = view
        try {
            windowManager.addView(composeView, layoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateOverlayParameters() {
        layoutParams.flags = getOverlayFlags()
        layoutParams.height = if (isExpanded) {
            dpToPx(248f)
        } else {
            dpToPx((JarvisIslandSettings.instance.positionY + JarvisIslandSettings.instance.pillHeight + 18).toFloat())
        }
        try {
            composeView?.let { windowManager.updateViewLayout(it, layoutParams) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun expandIsland() {
        isExpanded = true
        layoutParams.height = dpToPx(248f)
        try {
            composeView?.let { windowManager.updateViewLayout(it, layoutParams) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun collapseIsland() {
        isExpanded = false
        layoutParams.height = dpToPx((JarvisIslandSettings.instance.positionY + JarvisIslandSettings.instance.pillHeight + 18).toFloat())
        try {
            composeView?.let { windowManager.updateViewLayout(it, layoutParams) }
        } catch (e: Exception) {
            e.printStackTrace()
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
            delay((JarvisIslandSettings.instance.autoCollapseDelaySec * 1000).toLong())
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
                fileName = "Ecosystem_Sync_Payload.zip",
                fileSizeMb = 48.6f,
                progress = 0.05f,
                speedMbps = "118 MB/s",
                isUpload = true,
                isActive = true
            )
            if (JarvisIslandSettings.instance.autoExpandOnTransfer && !isExpanded) {
                expandIsland()
            }

            // Smooth progress simulation
            val totalSteps = 25
            for (step in 1..totalSteps) {
                delay(90)
                val p = step.toFloat() / totalSteps.toFloat()
                val speed = (108 + (step % 4) * 6).toString() + " MB/s"
                activeTransfer = activeTransfer.copy(
                    progress = p,
                    speedMbps = speed
                )
            }

            // Keep card visible for configured auto-collapse delay
            delay((JarvisIslandSettings.instance.autoCollapseDelaySec * 1000).toLong())
            activeTransfer = activeTransfer.copy(isActive = false)
            if (isExpanded) {
                collapseIsland()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fileBeamReceiver.stopServer()
        try {
            unregisterReceiver(settingsReceiver)
        } catch (e: Exception) {}
        composeView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {}
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
