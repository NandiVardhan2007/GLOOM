package com.jarvis.island.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.compositionContext
import androidx.compose.runtime.Recomposer
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
import com.jarvis.island.ui.ActiveTransferInfo
import com.jarvis.island.ui.BridgeDeviceInfo
import com.jarvis.island.ui.JarvisIslandApp
import com.jarvis.island.ui.MediaTrackInfo
import kotlinx.coroutines.CoroutineScope
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

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        bridgeClient = JarvisLiveBridgeClient(this)

        startForegroundServiceNotification()
        setupComposeOverlay()

        fileBeamReceiver = FileBeamReceiver(this)
        fileBeamReceiver.startServer(53391) { receivedFile ->
            activeTransfer = ActiveTransferInfo(
                fileName = receivedFile.name,
                fileSizeMb = (receivedFile.length() / (1024f * 1024f)),
                progress = 1.0f,
                speedMbps = "112 MB/s",
                isUpload = false,
                isActive = true
            )
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

    private fun setupComposeOverlay() {
        cutoutMetrics = CutoutDetector.detectCutout(this, null)

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            dpToPx(56f),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
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
                    topMarginDp = if (cutoutMetrics.hasCutout) 10 else 6
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

    private fun toggleIslandExpansion() {
        isExpanded = !isExpanded
        // When expanded, expand layoutParams height to 248dp; when collapsed, keep at 56dp
        layoutParams.height = if (isExpanded) dpToPx(248f) else dpToPx(56f)
        try {
            composeView?.let { windowManager.updateViewLayout(it, layoutParams) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fileBeamReceiver.stopServer()
        composeView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {}
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
