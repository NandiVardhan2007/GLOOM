package com.gloom.island.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.gloom.island.service.VoiceUnlockActivity
import com.gloom.island.voice.VoicePrefs
import com.gloom.island.voice.WakeWordModel
import com.gloom.island.voice.WakeWordService

@Composable
fun VoiceSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var enabled by remember { mutableStateOf(VoicePrefs.isWakeWordEnabled(context)) }
    var modelInstalled by remember { mutableStateOf(WakeWordModel.isInstalled(context)) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }

    // Voice Model customization state
    var voiceGender by remember { mutableStateOf(VoicePrefs.getVoiceGender(context)) }
    var voicePitch by remember { mutableFloatStateOf(VoicePrefs.getVoicePitch(context)) }
    var voiceRate by remember { mutableFloatStateOf(VoicePrefs.getVoiceRate(context)) }

    // Local TTS instance for instant testing and preview
    var testTts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        val tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Initialized
            }
        }
        testTts = tts
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    fun hasMicrophone(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun startListening() {
        if (!hasMicrophone()) {
            message = "Microphone permission is required."
            return
        }
        if (!modelInstalled) {
            message = "Download the speech model first."
            return
        }
        WakeWordService.start(context)
        message = "Say “Hey GLOOM” from anywhere."
    }

    fun stopListening() {
        WakeWordService.stop(context)
        message = "Wake word stopped."
    }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            enabled = true
            VoicePrefs.setWakeWordEnabled(context, true)
            startListening()
        } else {
            enabled = false
            VoicePrefs.setWakeWordEnabled(context, false)
            message = "Microphone permission is required for the wake word."
        }
    }

    fun downloadModel() {
        if (downloading) return
        downloading = true
        progress = 0
        message = null
        WakeWordModel.download(
            context,
            onProgress = { progress = it },
            onDone = { success, error ->
                downloading = false
                modelInstalled = success
                if (success) {
                    message = "Speech model installed."
                    if (enabled) startListening()
                } else {
                    message = error ?: "Model download failed."
                }
            }
        )
    }

    fun playVoiceTest() {
        val tts = testTts ?: return
        VoicePrefs.applyToTts(tts, context)
        val sampleText = if (voiceGender == VoicePrefs.GENDER_MALE) {
            "GLOOM voice assistant active. Ready for commands."
        } else {
            "Hi there! How can I help you today?"
        }
        tts.speak(sampleText, TextToSpeech.QUEUE_FLUSH, null, "gloom-sample")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        AppleSubHeader(title = "Voice & Speech", onBack = onBack)

        AppleSection(
            header = "Voice Assistant",
            footer = "The wake word is detected fully on this device. Questions are answered by the AI providers configured on your paired desktop."
        ) {
            AppleSwitchRow(
                title = "“Hey GLOOM” wake word",
                description = if (modelInstalled)
                    "Listens offline and opens the assistant"
                else
                    "Download the speech model below to enable it",
                checked = enabled,
                onCheckedChange = { checked ->
                    enabled = checked
                    VoicePrefs.setWakeWordEnabled(context, checked)
                    if (checked) {
                        if (!hasMicrophone()) {
                            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else if (!modelInstalled) {
                            message = "Download the speech model to start listening."
                        } else {
                            startListening()
                        }
                    } else {
                        stopListening()
                    }
                }
            )
        }

        AppleSection(
            header = "Voice Model",
            footer = "Select your preferred assistant voice profile. The female profile is tuned for high-clarity neural assistant acoustic presence. The male profile provides a deeper, resonant masculine register."
        ) {
            val genderOptions = listOf("Female (Siri-Tuned)", "Male (Deep)")
            val selectedGenderIndex = if (voiceGender == VoicePrefs.GENDER_MALE) 1 else 0

            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                AppleSegmentedControl(
                    options = genderOptions,
                    selectedIndex = selectedGenderIndex,
                    onSelect = { idx ->
                        val selected = if (idx == 1) VoicePrefs.GENDER_MALE else VoicePrefs.GENDER_FEMALE
                        voiceGender = selected
                        VoicePrefs.setVoiceGender(context, selected)
                        val newPitch = if (selected == VoicePrefs.GENDER_MALE) VoicePrefs.DEFAULT_MALE_PITCH else VoicePrefs.DEFAULT_FEMALE_PITCH
                        voicePitch = newPitch
                        VoicePrefs.setVoicePitch(context, newPitch)
                        testTts?.let { VoicePrefs.applyToTts(it, context) }
                    }
                )
            }

            AppleDivider()

            AppleSliderRow(
                title = "Pitch Tuning",
                valueText = "${(voicePitch * 100).toInt()}%",
                value = voicePitch,
                valueRange = 0.6f..1.5f,
                onValueChange = {
                    voicePitch = it
                    VoicePrefs.setVoicePitch(context, it)
                    testTts?.setPitch(it)
                }
            )

            AppleDivider()

            AppleSliderRow(
                title = "Speech Rate",
                valueText = "${(voiceRate * 100).toInt()}%",
                value = voiceRate,
                valueRange = 0.7f..1.5f,
                onValueChange = {
                    voiceRate = it
                    VoicePrefs.setVoiceRate(context, it)
                    testTts?.setSpeechRate(it)
                }
            )

            AppleDivider()

            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                AppleButton(
                    text = "Test Voice",
                    primary = false,
                    icon = GloomIcons.Waveform,
                    onClick = { playVoiceTest() }
                )
            }
        }

        AppleSection(header = "Speech Recognition Model") {
            AppleRow(
                title = "Offline English model",
                subtitle = if (modelInstalled) "Installed" else "About 40 MB, downloaded once",
                icon = GloomIcons.Waveform,
                iconTint = Apple.systemBlue,
                value = if (modelInstalled) "Ready" else "Missing"
            )
            if (downloading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "Downloading… $progress%",
                        style = AppleType.footnote,
                        color = Apple.secondaryLabel
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Apple.quaternary)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress / 100f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Apple.systemBlue)
                        )
                    }
                }
            } else {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    AppleButton(
                        text = if (modelInstalled) "Reinstall Speech Model" else "Download Speech Model",
                        primary = !modelInstalled,
                        onClick = { downloadModel() }
                    )
                }
            }
        }

        AppleSection(header = "Try Assistant") {
            AppleRow(
                title = "Open assistant",
                subtitle = "Ask a question or say “Unlock my mobile”",
                icon = GloomIcons.Sparkles,
                iconTint = Apple.systemPurple,
                showChevron = true,
                onClick = {
                    val a11y = com.gloom.island.service.GloomIslandAccessibilityService.instance
                    if (a11y != null) {
                        a11y.startVoiceAssistant()
                    } else {
                        val overlay = com.gloom.island.service.GloomIslandOverlayService.instance
                        if (overlay != null) {
                            overlay.startVoiceAssistant()
                        } else {
                            context.startActivity(
                                Intent(context, VoiceUnlockActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                }
            )
        }

        if (message != null) {
            Text(
                text = message!!,
                style = AppleType.footnote,
                color = if (downloading) Apple.secondaryLabel else Apple.systemGreen,
                modifier = Modifier.padding(horizontal = 36.dp, vertical = 10.dp)
            )
        }

        Text(
            text = "Wake word audio never leaves the phone. Speech synthesized using high-quality local neural TTS matching your chosen profile.",
            style = AppleType.footnote,
            color = Apple.secondaryLabel,
            modifier = Modifier.padding(horizontal = 36.dp, vertical = 18.dp)
        )

        Spacer(Modifier.height(24.dp))
    }
}
