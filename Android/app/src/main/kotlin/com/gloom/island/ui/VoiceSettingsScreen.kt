package com.gloom.island.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.getValue
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        AppleSubHeader(title = "Hey GLOOM", onBack = onBack)

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

        AppleSection(header = "Speech Model") {
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
                        text = if (modelInstalled) "Reinstall Model" else "Download Model",
                        primary = !modelInstalled,
                        onClick = { downloadModel() }
                    )
                }
            }
        }

        AppleSection(header = "Try It") {
            AppleRow(
                title = "Open assistant",
                subtitle = "Ask a question or say “Unlock my mobile”",
                icon = GloomIcons.Sparkles,
                iconTint = Apple.systemPurple,
                showChevron = true,
                onClick = {
                    context.startActivity(
                        Intent(context, VoiceUnlockActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
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
            text = "Wake word audio never leaves the phone. Only the text of a question is sent to your paired desktop, which calls the AI provider you configured.",
            style = AppleType.footnote,
            color = Apple.secondaryLabel,
            modifier = Modifier.padding(horizontal = 36.dp, vertical = 18.dp)
        )

        Spacer(Modifier.height(24.dp))
    }
}
