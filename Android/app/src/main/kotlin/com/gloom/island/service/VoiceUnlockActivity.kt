package com.gloom.island.service

import android.Manifest
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.gloom.island.ai.AiRouter
import com.gloom.island.security.GloomSecurity
import com.gloom.island.ui.Apple
import com.gloom.island.ui.AppleButton
import com.gloom.island.ui.AppleType
import com.gloom.island.ui.GloomIcons
import com.gloom.island.ui.GloomIcon
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Siri-style voice shortcut. It can be launched from the island while the phone
 * is locked. Matching phrases ask the system to show its secure unlock prompt;
 * Android never lets a third-party app dismiss the keyguard on its own.
 */
class VoiceUnlockActivity : ComponentActivity() {

    private var recognizer: SpeechRecognizer? = null
    private var status by mutableStateOf("Listening…")
    private var transcript by mutableStateOf("")
    private var matched by mutableStateOf(false)
    private var failed by mutableStateOf(false)
    private var permissionDenied by mutableStateOf(false)
    private var reply by mutableStateOf<String?>(null)
    private var thinking by mutableStateOf(false)
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: String? = null

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startListening()
        } else {
            permissionDenied = true
            status = "Microphone access is required"
            failed = true
            speak("Microphone access is required.")
        }
    }

    private fun isKeyguardLocked(): Boolean {
        val km = getSystemService(KeyguardManager::class.java)
        return km?.isKeyguardLocked == true || intent?.getBooleanExtra(EXTRA_UNLOCK_ONLY, false) == true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action == Intent.ACTION_CREATE_SHORTCUT) {
            val shortcutIntent = Intent(this, VoiceUnlockActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val result = Intent().apply {
                putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent)
                putExtra(Intent.EXTRA_SHORTCUT_NAME, getString(com.gloom.island.R.string.shortcut_siri_short))
                putExtra(
                    Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                    Intent.ShortcutIconResource.fromContext(this@VoiceUnlockActivity, com.gloom.island.R.mipmap.ic_launcher)
                )
            }
            setResult(RESULT_OK, result)
            finish()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (intent?.getBooleanExtra(EXTRA_PROMPT_ONLY, false) == true) {
            promptSystemUnlock()
            return
        }
        window.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )

        initializeTts()

        setContent {
            VoiceUnlockUi(
                status = status,
                transcript = transcript,
                matched = matched,
                failed = failed,
                permissionDenied = permissionDenied,
                reply = reply,
                thinking = thinking,
                isUnlockOnly = isKeyguardLocked(),
                onAskAgain = {
                    reply = null
                    thinking = false
                    failed = false
                    matched = false
                    transcript = ""
                    status = "Listening…"
                    startListening()
                },
                onOpenSettings = {
                    startActivity(
                        Intent(this@VoiceUnlockActivity, com.gloom.island.MainActivity::class.java).apply {
                            putExtra("open_settings", true)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                    )
                    finish()
                },
                onCancel = { finish() }
            )
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startListening()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status = "Speech recognition is unavailable"
            failed = true
            speak("Speech recognition is unavailable.")
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { status = "Listening…" }
                override fun onBeginningOfSpeech() { status = "Listening…" }
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() { status = "Thinking…" }

                override fun onError(error: Int) {
                    if (!matched) {
                        status = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "I didn't catch that"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone access is required"
                            else -> "Voice is unavailable right now"
                        }
                        failed = true
                    }
                }

                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    handleTranscript(text)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val text = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (text.isNotBlank()) transcript = text
                    if (looksLikeUnlock(text)) handleTranscript(text)
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
        }
        recognizer?.startListening(intent)
    }

    private fun looksLikeUnlock(text: String): Boolean {
        val normalized = text.lowercase().trim()
        val unlock = normalized.contains("unlock") ||
            normalized.contains("open my phone") ||
            normalized.contains("open the phone") ||
            normalized.contains("open phone") ||
            normalized.contains("draw pattern") ||
            normalized.contains("draw the pattern") ||
            normalized.contains("pattern unlock") ||
            normalized.contains("replay pattern")
        val target = normalized.contains("mobile") ||
            normalized.contains("phone") ||
            normalized.contains("screen") ||
            normalized.contains("device") ||
            normalized.contains("gloom") ||
            normalized.contains("pattern") ||
            normalized.length <= 24
        return unlock && target
    }

    private fun handleTranscript(text: String) {
        transcript = text
        if (matched || thinking) return
        if (looksLikeUnlock(text)) {
            matched = true
            status = "Unlocking…"
            recognizer?.stopListening()
            unlockDevice()
            return
        }

        if (isKeyguardLocked()) {
            // Strictly enforce unlock-only policy while the device is locked
            failed = true
            status = "Unlock Only"
            val msg = "Lock screen voice is restricted to unlocking. Say “Unlock” or “Draw pattern” to unlock your phone."
            reply = msg
            speak(msg)
            recognizer?.stopListening()
            return
        }

        if (text.isNotBlank()) {
            val brain = com.gloom.island.settings.GloomIslandSettings.instance.aiBrain
            val cleaned = com.gloom.island.automation.CommandRouter.cleanSpeech(text)
            val isAction = com.gloom.island.automation.CommandRouter.isCommand(cleaned) ||
                com.gloom.island.automation.AiPlanner.looksLikeAction(cleaned)
            if (isAction) {
                matched = true
                status = "Working…"
                recognizer?.stopListening()
                val appContext = applicationContext
                Handler(Looper.getMainLooper()).postDelayed({
                    // This panel is a full-screen window. Injected taps would
                    // hit the panel instead of the app underneath, so close it
                    // first and let the target window take focus.
                    finish()
                    Handler(Looper.getMainLooper()).postDelayed({
                        Thread {
                            var outcome = com.gloom.island.automation.CommandRouter
                                .handle(appContext, cleaned)
                            if (!outcome.handled) {
                                outcome = com.gloom.island.automation.LocalBrain
                                    .answer(appContext, cleaned)
                            }
                            if (!outcome.handled) {
                                outcome = com.gloom.island.automation.AiPlanner
                                    .run(appContext, cleaned)
                            }
                            if (!outcome.handled) {
                                outcome = com.gloom.island.automation.CommandRouter.Outcome(
                                    true,
                                    false,
                                    if (com.gloom.island.settings.GloomIslandSettings.instance.aiBrain == "local") {
                                        "I couldn't do that on the phone, and the on-device brain is selected."
                                    } else {
                                        "I couldn't do that. Pair with your desktop or add a cloud key in Settings."
                                    }
                                )
                            }
                            val message = outcome.message.ifBlank {
                                if (outcome.success) "Done." else "I couldn't do that."
                            }
                            val accessibility =
                                com.gloom.island.service.GloomIslandAccessibilityService.instance
                            if (accessibility != null) {
                                accessibility.speak(message)
                            } else {
                                Handler(Looper.getMainLooper()).post {
                                    android.widget.Toast.makeText(
                                        appContext,
                                        message,
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }.start()
                    }, 600)
                }, 320)
                return
            }

            // A question. Answer from the phone first; a remote model is only
            // used when the local brain has nothing to offer.
            val local = com.gloom.island.automation.LocalBrain.answer(applicationContext, text)
            if (local.handled) {
                matched = true
                status = "GLOOM"
                reply = local.message
                speak(local.message)
                return
            }
            if (brain == "local") {
                matched = true
                status = "On-device brain"
                failed = true
                val message = "That needs the desktop model, and the on-device brain is selected."
                reply = message
                speak(message)
                return
            }
            status = "Thinking…"
            recognizer?.stopListening()
            askAi(text)
        }
    }

    private fun askAi(text: String) {
        if (!AiRouter.canReachModel(applicationContext)) {
            status = "Add a cloud key in Settings > AI & Brain"
            failed = true
            speak("Add a cloud API key in Settings, or pair with your desktop.")
            return
        }
        thinking = true
        status = "Thinking…"
        AiRouter.request(
            applicationContext,
            listOf(
                "system" to SYSTEM_PROMPT,
                "user" to text
            )
        ) { answer, error, _ ->
            thinking = false
            if (answer != null) {
                reply = answer
                status = "GLOOM"
                speak(answer)
            } else {
                status = error ?: "I couldn't reach the assistant"
                failed = true
                speak("I couldn't reach the assistant.")
            }
        }
    }

    private fun initializeTts() {
        tts = TextToSpeech(this) { status ->
            if (status != TextToSpeech.SUCCESS) {
                Log.w(TAG, "Text-to-speech initialization failed (status $status)")
            } else {
                val engine = tts
                if (engine == null) {
                    Log.w(TAG, "Text-to-speech initialized without an engine handle")
                } else {
                    val preferred = engine.setLanguage(Locale.US)
                    if (preferred == TextToSpeech.LANG_MISSING_DATA ||
                        preferred == TextToSpeech.LANG_NOT_SUPPORTED
                    ) {
                        val fallback = engine.setLanguage(Locale.getDefault())
                        Log.w(TAG, "en-US voice unavailable ($preferred); default locale result $fallback")
                        if (fallback == TextToSpeech.LANG_MISSING_DATA ||
                            fallback == TextToSpeech.LANG_NOT_SUPPORTED
                        ) {
                            Log.w(TAG, "No usable text-to-speech voice is installed")
                        }
                    }
                    com.gloom.island.voice.VoicePrefs.applyToTts(engine, this@VoiceUnlockActivity)
                    ttsReady = true
                    pendingSpeech?.let { queued ->
                        pendingSpeech = null
                        speak(queued)
                    }
                }
            }
        }
    }

    private fun speak(text: String) {
        if (text.isBlank()) return
        val engine = tts
        if (engine == null || !ttsReady) {
            // Replies usually arrive while the engine is still initializing.
            pendingSpeech = text
            return
        }
        try {
            val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "gloom-reply")
            if (result != TextToSpeech.SUCCESS) {
                Log.w(TAG, "Text-to-speech speak() returned $result")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Text-to-speech speak failed", e)
        }
    }

    private fun unlockDevice() {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard?.isKeyguardLocked != true) {
            status = "Your phone is already unlocked"
            matched = true
            speak("Your phone is already unlocked.")
            return
        }

        // Prefer replaying the saved pattern through the accessibility service.
        // The voice panel must leave the screen first, otherwise the gesture
        // would be drawn into our own overlay instead of the keyguard.
        val pattern = GloomSecurity.loadPattern(this)
        val accessibility = GloomIslandAccessibilityService.instance
        if (pattern.isNotEmpty() && accessibility != null) {
            val power = getSystemService(PowerManager::class.java)
            @Suppress("DEPRECATION")
            val wake = power?.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "gloom:pattern-unlock"
            )
            try {
                wake?.acquire(6000)
            } catch (_: Exception) {
            }
            status = "Unlocking…"
            finish()
            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed({
                accessibility.performUnlockPattern(pattern)
                // Wait for the swipe-up (250 ms), the bouncer delay (900 ms), and
                // the full pattern stroke (up to 1350 ms) before deciding it failed.
                handler.postDelayed({
                    try {
                        wake?.release()
                    } catch (_: Exception) {
                    }
                    val stillLocked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
                    if (stillLocked) {
                        try {
                            startActivity(
                                Intent(this, VoiceUnlockActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    .putExtra(EXTRA_PROMPT_ONLY, true)
                            )
                        } catch (_: Exception) {
                        }
                    }
                }, 3400)
            }, 650)
            return
        }

        promptSystemUnlock()
    }

    private fun promptSystemUnlock() {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard?.isKeyguardLocked != true) {
            finish()
            return
        }
        keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() {
                finish()
            }

            override fun onDismissError() {
                status = "Unlock was not completed"
                failed = true
            }

            override fun onDismissCancelled() {
                finish()
            }
        })
    }

    override fun onDestroy() {
        recognizer?.destroy()
        recognizer = null
        pendingSpeech = null
        ttsReady = false
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "GloomVoiceUnlock"
        private const val EXTRA_PROMPT_ONLY = "prompt_only"
        const val EXTRA_UNLOCK_ONLY = "unlock_only"
        private const val SYSTEM_PROMPT =
            "You are GLOOM, the personal AI assistant controlling this Android phone. " +
                "Keep spoken replies concise, friendly, and under two sentences. " +
                "Be direct and helpful."
    }
}

@Composable
private fun VoiceUnlockUi(
    status: String,
    transcript: String,
    matched: Boolean,
    failed: Boolean,
    permissionDenied: Boolean,
    reply: String?,
    thinking: Boolean,
    isUnlockOnly: Boolean,
    onAskAgain: () -> Unit,
    onOpenSettings: () -> Unit,
    onCancel: () -> Unit
) {
    var autoFinish by remember { mutableStateOf(false) }
    LaunchedEffect(matched) {
        if (matched) {
            delay(1200)
            autoFinish = true
        }
    }
    LaunchedEffect(autoFinish) {
        if (autoFinish) onCancel()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onCancel() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Apple.secondaryGroupedBackground)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { /* absorb taps inside sheet */ }
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                // Apple HIG drag handle
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(width = 36.dp, height = 5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Apple.fillTertiary)
                )
                // Settings shortcut icon button (hidden when on lock screen for privacy & security)
                if (!isUnlockOnly) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Apple.fillTertiary)
                            .clickable { onOpenSettings() },
                        contentAlignment = Alignment.Center
                    ) {
                        GloomIcon(GloomIcons.Gear, Apple.secondaryLabel, 16.dp)
                    }
                }
            }

            SiriOrb(
                active = !failed,
                matched = matched,
                modifier = Modifier.size(136.dp)
            )
            Spacer(Modifier.height(16.dp))
            val displayStatus = if (isUnlockOnly && status == "Listening…") "Voice Unlock" else status
            Text(
                text = displayStatus,
                style = AppleType.title3,
                color = if (matched) Apple.systemGreen else Apple.label,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            val promptText = if (transcript.isBlank()) {
                if (isUnlockOnly) "Say “Unlock” to draw pattern" else "Try “Unlock my mobile” or ask a question"
            } else transcript
            Text(
                text = promptText,
                style = AppleType.subheadline,
                color = if (failed) Apple.systemRed else Apple.secondaryLabel,
                textAlign = TextAlign.Center
            )
            if (reply != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = reply,
                    style = AppleType.body,
                    color = Apple.label,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState())
                        .clip(RoundedCornerShape(14.dp))
                        .background(Apple.fillTertiary)
                        .padding(14.dp)
                )
            }
            if (thinking) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Thinking…",
                    style = AppleType.footnote,
                    color = Apple.secondaryLabel
                )
            }
            if (matched && status == "Your phone is already unlocked") {
                Spacer(Modifier.height(6.dp))
                GloomIcon(GloomIcons.CheckCircleFill, Apple.systemGreen, 24.dp)
            }
            Spacer(Modifier.height(18.dp))
            if (reply != null || failed) {
                AppleButton(
                    text = "Ask Again",
                    primary = false,
                    onClick = onAskAgain
                )
                Spacer(Modifier.height(10.dp))
            }
            AppleButton(
                text = "Close",
                primary = false,
                onClick = onCancel
            )
        }
    }
}

@Composable
private fun SiriOrb(active: Boolean, matched: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "siri")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "rotation"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * 0.42f * (if (active) pulse else 0.94f)

        val blue = Color(0xFF0A84FF)
        val pink = Color(0xFFFF375F)
        val purple = Color(0xFFBF5AF2)
        val orange = Color(0xFFFF9F0A)

        fun blob(color: Color, angleDeg: Float, distance: Float, blobRadius: Float, alpha: Float) {
            val angle = Math.toRadians((angleDeg + rotation).toDouble())
            val offset = Offset(
                center.x + (Math.cos(angle) * distance).toFloat(),
                center.y + (Math.sin(angle) * distance).toFloat()
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                    center = offset,
                    radius = blobRadius
                ),
                radius = blobRadius,
                center = offset
            )
        }

        val glow = if (matched) Apple.systemGreen else blue
        blob(glow, 0f, radius * 0.22f, radius * 0.85f, 0.75f)
        blob(purple, 130f, radius * 0.26f, radius * 0.8f, 0.6f)
        blob(if (matched) Color(0xFF64D2FF) else pink, 250f, radius * 0.24f, radius * 0.8f, 0.55f)
        if (!matched) {
            blob(orange, 310f, radius * 0.18f, radius * 0.55f, 0.35f)
        }

        drawCircle(
            color = Color.Transparent,
            radius = radius,
            center = center,
            style = Stroke(width = 1.5.dp.toPx())
        )
    }
}
