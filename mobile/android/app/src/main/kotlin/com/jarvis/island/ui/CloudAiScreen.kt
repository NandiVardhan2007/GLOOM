package com.jarvis.island.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jarvis.island.ai.AiRouter
import com.jarvis.island.ai.SecureKeyStore
import com.jarvis.island.ai.StandaloneAiClient
import com.jarvis.island.bridge.JarvisLiveBridgeClient

/**
 * Settings > AI & Brain > Cloud API Keys.
 *
 * The keys are written to EncryptedSharedPreferences only. This screen never
 * shows a saved key in clear text and never writes one to a log or a build.
 */
@Composable
fun CloudAiScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var refreshTick by remember { mutableIntStateOf(0) }
    var keyDialogProvider by remember { mutableStateOf<String?>(null) }
    var modelDialogProvider by remember { mutableStateOf<String?>(null) }

    val bridge = JarvisLiveBridgeClient.activeClient
    val desktopConnected = bridge?.isConnected == true && bridge.desktopIp != null
    val cloudConfigured = AiRouter.isCloudConfigured(context)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        AppleSubHeader(title = "AI & Brain", onBack = onBack)

        AppleSection(
            header = "Route",
            footer = "Paired and on the same Wi-Fi: the desktop answers with its own keys. " +
                "Away from home: this phone calls Groq or OpenRouter directly. Offline: the on-device brain."
        ) {
            val (routeTitle, routeDetail, routeTint) = when {
                desktopConnected -> Triple(
                    "Desktop LiveBridge",
                    "Connected to ${bridge?.desktopIp}",
                    Apple.systemGreen
                )
                cloudConfigured -> Triple(
                    "Cloud direct",
                    "Groq / OpenRouter using this phone's keys",
                    Apple.systemBlue
                )
                else -> Triple(
                    "On-device only",
                    "Add a key below to use a cloud model away from your PC",
                    Apple.systemOrange
                )
            }
            AppleRow(
                title = routeTitle,
                subtitle = routeDetail,
                icon = JarvisIcons.Globe,
                iconTint = routeTint
            )
        }

        StandaloneAiClient.providers.forEach { provider ->
            val keys = remember(refreshTick) { SecureKeyStore.keys(context, provider.id) }
            val model = remember(refreshTick) {
                StandaloneAiClient.modelFor(context, provider)
            }
            AppleSection(
                header = provider.label,
                footer = if (provider.id == SecureKeyStore.PROVIDER_GROQ) {
                    "Fast and free-tier friendly. Paste every key you own, one per line, and JARVIS rotates when one is rate limited."
                } else {
                    "One key reaches many models. Keys are rotated the same way when a call is rejected or throttled."
                }
            ) {
                AppleRow(
                    title = "API Keys",
                    subtitle = if (keys.isEmpty()) "Not set — tap to add ${provider.keyHint}"
                    else SecureKeyStore.mask(keys.first()),
                    icon = JarvisIcons.Shield,
                    iconTint = Apple.systemBlue,
                    value = if (keys.isEmpty()) null else "${keys.size}",
                    showChevron = true,
                    onClick = { keyDialogProvider = provider.id }
                )
                AppleDivider()
                AppleRow(
                    title = "Model",
                    subtitle = "Used for direct calls when the desktop is away",
                    icon = JarvisIcons.Sparkles,
                    iconTint = Apple.systemPurple,
                    value = model,
                    showChevron = true,
                    onClick = { modelDialogProvider = provider.id }
                )
            }
        }

        AppleSection(
            header = "Privacy",
            footer = "Keys are sealed with an Android Keystore AES-256-GCM master key. " +
                "They are never compiled into the app, synced to the desktop, or written to logs."
        ) {
            AppleRow(
                title = "Stored on this device",
                subtitle = "EncryptedSharedPreferences · jarvis_secure_keys",
                icon = JarvisIcons.Shield,
                iconTint = Apple.systemGreen
            )
        }

        Spacer(Modifier.height(28.dp))
    }

    keyDialogProvider?.let { providerId ->
        KeyDialog(
            providerId = providerId,
            existingCount = SecureKeyStore.keys(context, providerId).size,
            onDismiss = { keyDialogProvider = null },
            onSaved = {
                keyDialogProvider = null
                refreshTick += 1
            }
        )
    }

    modelDialogProvider?.let { providerId ->
        ModelDialog(
            providerId = providerId,
            onDismiss = { modelDialogProvider = null },
            onSaved = {
                modelDialogProvider = null
                refreshTick += 1
            }
        )
    }
}

@Composable
private fun KeyDialog(
    providerId: String,
    existingCount: Int,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val provider = StandaloneAiClient.provider(providerId)
    var keyText by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var testMessage by remember { mutableStateOf<String?>(null) }
    var testOk by remember { mutableStateOf<Boolean?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${provider.label} API Keys") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = keyText,
                    onValueChange = {
                        keyText = it
                        testMessage = null
                        testOk = null
                    },
                    label = { Text("Key or keys (${provider.keyHint})") },
                    placeholder = { Text("Paste one per line to rotate") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = false,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
                if (testMessage != null) {
                    Text(
                        text = testMessage!!,
                        style = AppleType.footnote,
                        color = if (testOk == true) Apple.systemGreen else Apple.systemRed
                    )
                }
                if (existingCount > 0) {
                    Text(
                        text = "$existingCount key(s) already saved on this phone.",
                        style = AppleType.footnote,
                        color = Apple.secondaryLabel
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = keyText.isNotBlank(),
                onClick = {
                    SecureKeyStore.addKeys(context, providerId, keyText)
                    onSaved()
                }
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (existingCount > 0) {
                    TextButton(
                        onClick = {
                            SecureKeyStore.clearKeys(context, providerId)
                            onSaved()
                        }
                    ) { Text("Remove") }
                }
                TextButton(
                    enabled = keyText.isNotBlank() && !testing,
                    onClick = {
                        testing = true
                        testMessage = "Checking…"
                        val candidate = keyText.split('\n', ',', ';').firstOrNull()?.trim().orEmpty()
                        StandaloneAiClient.validateKey(providerId, candidate) { ok, message ->
                            testing = false
                            testOk = ok
                            testMessage = message
                        }
                    }
                ) { Text(if (testing) "Testing…" else "Test") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
private fun ModelDialog(
    providerId: String,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val provider = StandaloneAiClient.provider(providerId)
    var modelText by remember {
        mutableStateOf(SecureKeyStore.model(context, providerId) ?: provider.defaultModel)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${provider.label} Model") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = modelText,
                    onValueChange = { modelText = it },
                    label = { Text("Model id") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                provider.modelChoices.forEach { choice ->
                    Text(
                        text = choice,
                        style = AppleType.footnote,
                        color = if (choice == modelText) Apple.systemBlue else Apple.secondaryLabel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { modelText = choice }
                            .padding(vertical = 6.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = modelText.isNotBlank(),
                onClick = {
                    SecureKeyStore.setModel(context, providerId, modelText)
                    onSaved()
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
