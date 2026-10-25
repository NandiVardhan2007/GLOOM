package com.gloom.island.service

import java.io.File
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale
import com.gloom.island.ui.VoiceAssistantIslandEvent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Region
import android.view.View
import android.view.ViewTreeObserver
import java.lang.reflect.Proxy
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
import android.view.inputmethod.InputMethodManager
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
import com.gloom.island.settings.GloomIslandSettings
import com.gloom.island.ui.ActiveTransferInfo
import com.gloom.island.ui.BridgeDeviceInfo
import com.gloom.island.ui.GloomIslandApp
import com.gloom.island.ui.MediaTrackInfo
import com.gloom.island.ui.LockIslandEvent
import com.gloom.island.ui.NotificationIslandEvent
import com.gloom.island.ui.SystemAlertIslandEvent
import android.bluetooth.BluetoothDevice
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

    private var activeLockEvent by mutableStateOf<LockIslandEvent?>(null)
    private var lockEventJob: Job? = null
    private var voiceAssistantEvent by mutableStateOf<VoiceAssistantIslandEvent?>(null)
    private var speechRecognizer: SpeechRecognizer? = null
    private var voiceCollapseJob: Job? = null

    private var activeSystemAlert by mutableStateOf<SystemAlertIslandEvent?>(null)
    private var systemAlertJob: Job? = null

    private var activeTimer by mutableStateOf<com.gloom.island.ui.TimerIslandEvent?>(null)
    private var timerTickerJob: Job? = null

    fun showSystemAlert(alert: SystemAlertIslandEvent) {
        serviceScope.launch {
            systemAlertJob?.cancel()
            activeSystemAlert = alert
            updateOverlayDimensions(isExpanded = false)
            systemAlertJob = launch {
                val duration = if (alert.type == SystemAlertIslandEvent.AlertType.CHARGING) 3000L else 2500L
                delay(duration)
                if (activeSystemAlert?.timestampMs == alert.timestampMs) {
                    activeSystemAlert = null
                    updateOverlayDimensions(isExpanded = false)
                }
            }
        }
    }
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: String? = null

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
                            com.gloom.island.voice.VoicePrefs.applyToTts(created, this@GloomIslandOverlayService)
                        }
                        ttsReady = true
                        pendingSpeech?.let { queued ->
                            pendingSpeech = null
                            created?.speak(queued, TextToSpeech.QUEUE_FLUSH, null, "gloom-result")
                        }
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

    fun startVoiceAssistant() {
        mainHandler.post {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                voiceAssistantEvent = VoiceAssistantIslandEvent(
                    isActive = true,
                    status = "Microphone access required",
                    isFailed = true
                )
                expandIsland()
                speak("Microphone access is required.")
                return@post
            }

            voiceAssistantEvent = VoiceAssistantIslandEvent(
                isActive = true,
                status = "Listening…",
                transcript = "",
                reply = null,
                isThinking = false
            )
            expandIsland()

            try {
                speechRecognizer?.destroy()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: android.os.Bundle?) {
                            voiceAssistantEvent = voiceAssistantEvent?.copy(status = "Listening…")
                        }
                        override fun onBeginningOfSpeech() {
                            voiceAssistantEvent = voiceAssistantEvent?.copy(status = "Listening…")
                        }
                        override fun onRmsChanged(rmsdB: Float) = Unit
                        override fun onBufferReceived(buffer: ByteArray?) = Unit
                        override fun onEndOfSpeech() {
                            voiceAssistantEvent = voiceAssistantEvent?.copy(status = "Thinking…", isThinking = true)
                        }
                        override fun onError(error: Int) {
                            val msg = when (error) {
                                SpeechRecognizer.ERROR_NO_MATCH -> "I didn't catch that"
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone access required"
                                else -> "Voice unavailable right now"
                            }
                            voiceAssistantEvent = voiceAssistantEvent?.copy(
                                status = msg,
                                isThinking = false,
                                isFailed = true
                            )
                        }
                        override fun onResults(results: android.os.Bundle?) {
                            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull().orEmpty()
                            handleVoiceAssistantTranscript(text)
                        }
                        override fun onPartialResults(partialResults: android.os.Bundle?) {
                            val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull().orEmpty()
                            if (text.isNotBlank()) {
                                voiceAssistantEvent = voiceAssistantEvent?.copy(transcript = text)
                            }
                        }
                        override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
                    })
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
                }
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                voiceAssistantEvent = voiceAssistantEvent?.copy(
                    status = "Speech recognition unavailable",
                    isFailed = true
                )
            }
        }
    }

    private fun handleVoiceAssistantTranscript(text: String) {
        if (text.isBlank()) {
            voiceAssistantEvent = voiceAssistantEvent?.copy(
                status = "I didn't catch that",
                isThinking = false,
                isFailed = true
            )
            return
        }
        voiceAssistantEvent = voiceAssistantEvent?.copy(transcript = text, status = "Thinking…", isThinking = true)

        val cleaned = com.gloom.island.automation.CommandRouter.cleanSpeech(text)
        val isAction = com.gloom.island.automation.CommandRouter.isCommand(cleaned) ||
            com.gloom.island.automation.AiPlanner.looksLikeAction(cleaned)

        if (isAction) {
            Thread {
                var outcome = com.gloom.island.automation.CommandRouter.handle(applicationContext, cleaned)
                if (!outcome.handled) {
                    outcome = com.gloom.island.automation.LocalBrain.answer(applicationContext, cleaned)
                }
                if (!outcome.handled) {
                    outcome = com.gloom.island.automation.AiPlanner.run(applicationContext, cleaned)
                }
                val msg = outcome.message.ifBlank { if (outcome.success) "Done." else "I couldn't do that." }
                mainHandler.post {
                    voiceAssistantEvent = voiceAssistantEvent?.copy(
                        status = if (outcome.success) "Done" else "Failed",
                        reply = msg,
                        isThinking = false,
                        isSuccess = outcome.success,
                        isFailed = !outcome.success
                    )
                    speak(msg)
                    scheduleVoiceAssistantCollapse(4000)
                }
            }.start()
            return
        }

        val local = com.gloom.island.automation.LocalBrain.answer(applicationContext, text)
        if (local.handled) {
            voiceAssistantEvent = voiceAssistantEvent?.copy(
                status = "GLOOM",
                reply = local.message,
                isThinking = false,
                isSuccess = true
            )
            speak(local.message)
            scheduleVoiceAssistantCollapse(5000)
            return
        }

        if (GloomIslandSettings.instance.aiBrain == "local") {
            val msg = "That needs the desktop model, and the on-device brain is selected."
            voiceAssistantEvent = voiceAssistantEvent?.copy(
                status = "On-device brain",
                reply = msg,
                isThinking = false,
                isFailed = true
            )
            speak(msg)
            return
        }

        if (!com.gloom.island.ai.AiRouter.canReachModel(applicationContext)) {
            val msg = "Add a cloud key in Settings > AI & Brain, or pair with desktop."
            voiceAssistantEvent = voiceAssistantEvent?.copy(
                status = "AI Model unreachable",
                reply = msg,
                isThinking = false,
                isFailed = true
            )
            speak(msg)
            return
        }

        val timeContext = com.gloom.island.automation.LocalBrain.liveTimeAndDate()
        val batteryContext = com.gloom.island.automation.LocalBrain.battery(applicationContext)
        val wifiContext = com.gloom.island.automation.SystemTools.getWifiStatus(applicationContext)
        val volContext = com.gloom.island.automation.SystemTools.getVolumeStatus(applicationContext)
        val systemPrompt = "You are GLOOM, the personal AI assistant living inside the Dynamic Island of this Android phone. " +
            "Keep answers concise, natural, and under two sentences. " +
            "Current live phone status: $timeContext, $batteryContext, $wifiContext, $volContext."

        com.gloom.island.ai.AiRouter.request(
            applicationContext,
            listOf(
                "system" to systemPrompt,
                "user" to text
            )
        ) { answer, error, _ ->
            mainHandler.post {
                if (answer != null) {
                    voiceAssistantEvent = voiceAssistantEvent?.copy(
                        status = "GLOOM",
                        reply = answer,
                        isThinking = false,
                        isSuccess = true
                    )
                    speak(answer)
                    scheduleVoiceAssistantCollapse(7000)
                } else {
                    val errMsg = error ?: "I couldn't reach the assistant"
                    voiceAssistantEvent = voiceAssistantEvent?.copy(
                        status = errMsg,
                        reply = errMsg,
                        isThinking = false,
                        isFailed = true
                    )
                    speak(errMsg)
                }
            }
        }
    }

    fun closeVoiceAssistant() {
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        voiceAssistantEvent = null
        collapseIsland()
    }

    private fun scheduleVoiceAssistantCollapse(delayMs: Long) {
        voiceCollapseJob?.cancel()
        voiceCollapseJob = serviceScope.launch {
            delay(delayMs)
            if (voiceAssistantEvent?.isThinking != true) {
                closeVoiceAssistant()
            }
        }
    }

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
            composeView?.visibility = android.view.View.GONE
            layoutParams?.let { params ->
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                try {
                    composeView?.let { windowManager.updateViewLayout(it, params) }
                } catch (_: Exception) {}
            }
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
                    composeView?.visibility = android.view.View.VISIBLE
                }
                triggerLockEvent(isUnlocked = false)
            } else {
                composeView?.visibility = android.view.View.GONE
                layoutParams?.let { params ->
                    params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    try {
                        composeView?.let { windowManager.updateViewLayout(it, params) }
                    } catch (_: Exception) {}
                }
            }
        } else {
            handleUserPresent()
        }
    }

    private fun handleUserPresent() {
        collapseIsland(immediate = true)
        applyCollapsedDimensions()
        if (GloomIslandSettings.instance.isIslandEnabled) {
            composeView?.visibility = android.view.View.VISIBLE
        }
        triggerLockEvent(isUnlocked = true)
        composeView?.requestLayout()
    }

    private val internalLockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.gloom.island.ACTION_LOCK_EVENT") {
                val isUnlocked = intent.getBooleanExtra("is_unlocked", false)
                triggerLockEvent(isUnlocked = isUnlocked)
            }
        }
    }

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

    private val hardwareAlertReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                AudioManager.RINGER_MODE_CHANGED_ACTION -> {
                    val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    val mode = intent.getIntExtra(
                        AudioManager.EXTRA_RINGER_MODE,
                        am?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
                    )
                    val isSilent = (mode == AudioManager.RINGER_MODE_SILENT || mode == AudioManager.RINGER_MODE_VIBRATE)
                    showSystemAlert(
                        SystemAlertIslandEvent(
                            type = SystemAlertIslandEvent.AlertType.RINGER,
                            isSilent = isSilent,
                            title = if (isSilent) "Silent" else "Ring"
                        )
                    )
                }
                Intent.ACTION_POWER_CONNECTED -> {
                    showSystemAlert(
                        SystemAlertIslandEvent(
                            type = SystemAlertIslandEvent.AlertType.CHARGING,
                            level = batteryLevel,
                            subtitle = "Charging"
                        )
                    )
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    showSystemAlert(
                        SystemAlertIslandEvent(
                            type = SystemAlertIslandEvent.AlertType.CHARGING,
                            level = batteryLevel,
                            subtitle = "Unplugged"
                        )
                    )
                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    val name = try { device?.name } catch (_: SecurityException) { null } ?: "Headphones"
                    showSystemAlert(
                        SystemAlertIslandEvent(
                            type = SystemAlertIslandEvent.AlertType.BLUETOOTH,
                            title = name,
                            isConnected = true
                        )
                    )
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    val name = try { device?.name } catch (_: SecurityException) { null } ?: "Headphones"
                    showSystemAlert(
                        SystemAlertIslandEvent(
                            type = SystemAlertIslandEvent.AlertType.BLUETOOTH,
                            title = name,
                            isConnected = false
                        )
                    )
                }
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
        if (GloomIslandAccessibilityService.isRunning ||
            GloomIslandAccessibilityService.isAccessibilityServiceEnabled(this)
        ) {
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

        bridgeClient.startDiscovery { desktopName, desktopIp ->
            bridgeDevice = BridgeDeviceInfo(
                deviceName = desktopName,
                ipAddress = desktopIp,
                pingMs = 12,
                isConnected = true
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (GloomIslandAccessibilityService.isRunning ||
            GloomIslandAccessibilityService.isAccessibilityServiceEnabled(this)
        ) {
            Log.i("GloomOverlay", "Accessibility island active; stopping duplicate overlay service")
            removeOverlaySafely()
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
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

        // Real Battery Changed Broadcast
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        // System Screen & Keyguard Lock/Unlock Broadcasts (system-only filter requires no export flag)
        val lockFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(lockReceiver, lockFilter)

        // Hardware & System Alert HUD Broadcasts (Ringer, Charging, Bluetooth)
        val hardwareFilter = IntentFilter().apply {
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        registerReceiver(hardwareAlertReceiver, hardwareFilter)

        // Internal app lock event broadcast (not exported to external apps)
        val internalLockFilter = IntentFilter("com.gloom.island.ACTION_LOCK_EVENT")
        ContextCompat.registerReceiver(this, internalLockReceiver, internalLockFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
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
                        val delayMs = (GloomIslandSettings.instance.autoCollapseDelaySec * 1000).toLong().coerceAtLeast(3500L)
                        delay(delayMs)
                        if (!isInputFocusEnabled) {
                            if (isExpanded) {
                                collapseIsland()
                            }
                            activeNotification = null
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
        GloomNotificationListener.onTimerUpdate = { timer ->
            serviceScope.launch {
                timerTickerJob?.cancel()
                activeTimer = timer
                if (timer != null && timer.remainingSeconds > 0) {
                    timerTickerJob = launch {
                        while (activeTimer?.isActive == true && (activeTimer?.remainingSeconds ?: 0) > 0) {
                            delay(1000)
                            if (activeTimer?.isPaused == false) {
                                val rem = (activeTimer?.remainingSeconds ?: 1) - 1
                                if (rem <= 0) {
                                    activeTimer = activeTimer?.copy(remainingSeconds = 0, isCompleted = true)
                                    delay(4000)
                                    activeTimer = null
                                    break
                                } else {
                                    activeTimer = activeTimer?.copy(remainingSeconds = rem)
                                }
                            }
                        }
                    }
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

    private fun getCollapsedHeightPx(): Int {
        val density = resources.displayMetrics.density
        val heightDp = if (activeSystemAlert != null && activeSystemAlert?.type != SystemAlertIslandEvent.AlertType.NONE) {
            GloomIslandSettings.instance.positionY + 48 + 16
        } else {
            GloomIslandSettings.instance.positionY + GloomIslandSettings.instance.pillHeight + 16
        }
        return (heightDp * density).toInt()
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
        val params = layoutParams ?: return
        if (isViewAttached || view.isAttachedToWindow) return
        try {
            windowManager.addView(view, params)
            isViewAttached = true
            updateVisibilityForOrientation()
        } catch (e: Exception) {
            Log.e("GloomOverlay", "Error adding overlay view", e)
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
            Log.w("GloomOverlay", "Error removing overlay view", e)
        } finally {
            isViewAttached = false
            composeView = null
        }
    }

    private fun setupComposeOverlay() {
        if (GloomIslandAccessibilityService.isRunning ||
            GloomIslandAccessibilityService.isAccessibilityServiceEnabled(this)
        ) {
            return
        }
        cutoutMetrics = CutoutDetector.detectCutout(this, null)

        val params = buildLayoutParams()
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
                    onLongPressExpand = { startVoiceAssistant() },
                    onBeamSampleFile = {
                        val itn = Intent(this@GloomIslandOverlayService, com.gloom.island.bridge.ShareToPcActivity::class.java).apply {
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
                    activeTimer = activeTimer,
                    onTimerPauseResume = {
                        activeTimer = activeTimer?.copy(isPaused = !(activeTimer?.isPaused ?: false))
                    },
                    onTimerCancel = {
                        timerTickerJob?.cancel()
                        activeTimer = null
                    },
                    voiceAssistant = voiceAssistantEvent,
                    onVoiceAssistantAskAgain = { startVoiceAssistant() },
                    onVoiceAssistantClose = { closeVoiceAssistant() },
                    systemAlert = activeSystemAlert,
                    onSystemAlertDismiss = {
                        activeSystemAlert = null
                        updateOverlayDimensions(isExpanded = false)
                    },
                    onSwipeDown = { isRightSide ->
                        try {
                            val sbservice = getSystemService("statusbar")
                            val statusbarManager = Class.forName("android.app.StatusBarManager")
                            val methodName = if (isRightSide) "expandSettingsPanel" else "expandNotificationsPanel"
                            val method = statusbarManager.getMethod(methodName)
                            method.invoke(sbservice)
                        } catch (_: Exception) {}
                    },
                    onInputFocusChanged = { enabled ->
                        setInputFocusEnabled(enabled)
                    },
                    settings = GloomIslandSettings.instance
                )
            }
        }

        view.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_OUTSIDE) {
                if (isInputFocusEnabled) {
                    setInputFocusEnabled(false)
                }
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

    @SuppressLint("PrivateApi")
    private fun setupTouchableRegion(view: View) {
        val register = { targetView: View ->
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
                            val currentPillWidth = if (activeSystemAlert != null && activeSystemAlert?.type != SystemAlertIslandEvent.AlertType.NONE) {
                                232 + 16
                            } else {
                                GloomIslandSettings.instance.pillWidth + 16
                            }
                            val currentPillHeight = if (activeSystemAlert != null && activeSystemAlert?.type != SystemAlertIslandEvent.AlertType.NONE) {
                                48 + 12
                            } else {
                                GloomIslandSettings.instance.pillHeight + 12
                            }
                            val pillWidthPx = (currentPillWidth * density).toInt()
                            val pillHeightPx = (currentPillHeight * density).toInt()
                            val posYPx = (GloomIslandSettings.instance.positionY * density).toInt()
                            val posXOffsetPx = (GloomIslandSettings.instance.positionX * density).toInt()
                            val left = when (GloomIslandSettings.instance.gravity) {
                                "Left" -> posXOffsetPx
                                "Right" -> screenWidth - pillWidthPx + posXOffsetPx
                                else -> ((screenWidth - pillWidthPx) / 2) + posXOffsetPx
                            }
                            val right = left + pillWidthPx
                            val top = posYPx.coerceAtLeast(0)
                            val bottom = posYPx + pillHeightPx

                            setTouchableInsetsMethod.invoke(inoutInfo, 3) // TOUCHABLE_INSETS_REGION
                            val region = touchableRegionField.get(inoutInfo) as Region
                            regionSetMethod.invoke(region, Rect(left, top, right, bottom))
                        } else {
                            setTouchableInsetsMethod.invoke(inoutInfo, 0) // TOUCHABLE_INSETS_FRAME
                        }
                    }
                    null
                }

                val observer = targetView.viewTreeObserver
                if (observer.isAlive) {
                    val addMethod = ViewTreeObserver::class.java.getMethod("addOnComputeInternalInsetsListener", listenerClass)
                    addMethod.invoke(observer, proxy)
                    Log.i("GloomOverlay", "OnComputeInternalInsetsListener registered on active ViewTreeObserver")
                }
            } catch (e: Throwable) {
                Log.w("GloomOverlay", "Could not register OnComputeInternalInsetsListener: ${e.message}", e)
            }
        }

        if (view.isAttachedToWindow) {
            register(view)
        } else {
            view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    register(v)
                }
                override fun onViewDetachedFromWindow(v: View) {}
            })
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        updateVisibilityForOrientation(newConfig)
    }

    private fun updateVisibilityForOrientation(config: android.content.res.Configuration = resources.configuration) {
        val view = composeView ?: return
        val params = layoutParams ?: return
        val isLandscape = config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val shouldHide = (isLandscape && !GloomIslandSettings.instance.showInLandscape) ||
                !GloomIslandSettings.instance.isIslandEnabled
        if (shouldHide) {
            if (isExpanded) collapseIsland(immediate = true)
            view.visibility = android.view.View.GONE
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            try {
                windowManager.updateViewLayout(view, params)
            } catch (_: Exception) {}
        } else {
            view.visibility = android.view.View.VISIBLE
            params.flags = getOverlayFlags()
            try {
                windowManager.updateViewLayout(view, params)
            } catch (_: Exception) {}
        }
    }

    private fun buildLayoutParams(): WindowManager.LayoutParams {
        val initialHeight = if (isExpanded) {
            dpToPx((GloomIslandSettings.instance.expandedHeight + GloomIslandSettings.instance.positionY + 44).toFloat())
        } else {
            dpToPx((GloomIslandSettings.instance.pillHeight + 12).toFloat())
        }
        val initialWidth = if (isExpanded) {
            WindowManager.LayoutParams.MATCH_PARENT
        } else {
            dpToPx((GloomIslandSettings.instance.pillWidth + 12).toFloat())
        }
        val density = resources.displayMetrics.density
        val initialX = if (isExpanded) 0 else (GloomIslandSettings.instance.positionX * density).toInt()
        val initialY = if (isExpanded) 0 else (GloomIslandSettings.instance.positionY * density).toInt()
        val initialGravity = if (isExpanded) {
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        } else {
            when (GloomIslandSettings.instance.gravity) {
                "Left" -> Gravity.TOP or Gravity.START
                "Right" -> Gravity.TOP or Gravity.END
                else -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
            }
        }
        return WindowManager.LayoutParams(
            initialWidth,
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
            gravity = initialGravity
            x = initialX
            y = initialY
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun updateOverlayDimensions(isExpanded: Boolean = this.isExpanded) {
        updateOverlayParameters()
    }

    private fun updateOverlayParameters() {
        val view = composeView ?: return
        val params = layoutParams ?: return
        if (!isViewAttached && !view.isAttachedToWindow) return

        if (!GloomIslandSettings.instance.isIslandEnabled) {
            if (isExpanded) collapseIsland(immediate = true)
            view.visibility = android.view.View.GONE
            params.flags = getOverlayFlags() or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            try {
                windowManager.updateViewLayout(view, params)
            } catch (_: Exception) {}
            return
        }

        updateVisibilityForOrientation()
        params.flags = getOverlayFlags()
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.x = 0
        params.y = 0
        params.width = WindowManager.LayoutParams.MATCH_PARENT
        params.height = if (isExpanded) {
            dpToPx((GloomIslandSettings.instance.expandedHeight + GloomIslandSettings.instance.positionY + 44).toFloat())
        } else {
            getCollapsedHeightPx()
        }
        try {
            windowManager.updateViewLayout(view, params)
            view.requestLayout()
        } catch (e: Exception) {
            Log.w("GloomOverlay", "Error updating overlay layout", e)
        }
    }

    private fun expandIsland() {
        if (!GloomIslandSettings.instance.isIslandEnabled) return
        isExpanded = true
        notificationJob?.cancel()
        windowResizeJob?.cancel()
        val view = composeView ?: return
        val params = layoutParams ?: return
        if (!isViewAttached && !view.isAttachedToWindow) return
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.x = 0
        params.y = 0
        params.width = WindowManager.LayoutParams.MATCH_PARENT
        params.height = dpToPx((GloomIslandSettings.instance.expandedHeight + GloomIslandSettings.instance.positionY + 44).toFloat())
        params.flags = getOverlayFlags()
        try {
            windowManager.updateViewLayout(view, params)
            view.requestLayout()
        } catch (e: Exception) {
            Log.w("GloomOverlay", "Error expanding overlay view", e)
        }
    }

    private fun collapseIsland(immediate: Boolean = false) {
        if (!isExpanded && !immediate && windowResizeJob == null) return
        isExpanded = false
        if (isInputFocusEnabled) {
            setInputFocusEnabled(false)
        }
        windowResizeJob?.cancel()
        if (immediate) {
            applyCollapsedDimensions()
        } else {
            windowResizeJob = serviceScope.launch {
                try {
                    delay(380)
                } finally {
                    if (!isExpanded) {
                        applyCollapsedDimensions()
                    }
                }
            }
        }
    }

    private var isInputFocusEnabled = false

    private fun setInputFocusEnabled(enabled: Boolean) {
        if (isInputFocusEnabled == enabled) return
        isInputFocusEnabled = enabled
        val view = composeView ?: return
        val params = layoutParams ?: return
        if (!isViewAttached && !view.isAttachedToWindow) return

        if (enabled) {
            notificationJob?.cancel()
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
            params.height = WindowManager.LayoutParams.MATCH_PARENT
            try {
                windowManager.updateViewLayout(view, params)
                view.isFocusable = true
                view.isFocusableInTouchMode = true
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
            } catch (e: Exception) {
                Log.w("GloomOverlay", "Error enabling input focus", e)
            }
        } else {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
            if (isExpanded) {
                params.height = dpToPx((GloomIslandSettings.instance.expandedHeight + GloomIslandSettings.instance.positionY + 44).toFloat())
            } else {
                params.height = dpToPx((GloomIslandSettings.instance.pillHeight + 12).toFloat())
            }
            try {
                windowManager.updateViewLayout(view, params)
            } catch (e: Exception) {
                Log.w("GloomOverlay", "Error disabling input focus", e)
            }
        }
    }

    private fun applyCollapsedDimensions() {
        val view = composeView ?: return
        val params = layoutParams ?: return
        if (!isViewAttached && !view.isAttachedToWindow) return

        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.x = 0
        params.y = 0
        params.width = WindowManager.LayoutParams.MATCH_PARENT
        params.height = getCollapsedHeightPx()
        params.flags = getOverlayFlags()
        try {
            windowManager.updateViewLayout(view, params)
            view.requestLayout()
        } catch (e: Exception) {
            Log.w("GloomOverlay", "Error applying collapsed dimensions", e)
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
        speechRecognizer?.destroy()
        speechRecognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        try {
            unregisterReceiver(settingsReceiver)
            unregisterReceiver(batteryReceiver)
            unregisterReceiver(lockReceiver)
            unregisterReceiver(internalLockReceiver)
            unregisterReceiver(hardwareAlertReceiver)
        } catch (e: Exception) {}
        removeOverlaySafely()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
