package com.gloom.island.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.gloom.island.ai.AiRouter
import com.gloom.island.ai.KeyBatchImporter
import com.gloom.island.ai.SecureKeyStore
import com.gloom.island.ai.StandaloneAiClient
import com.gloom.island.bridge.GloomLiveBridgeClient
import kotlinx.coroutines.launch

/**
 * Settings > AI & Brain > Cloud API Keys.
 *
 * The keys are written to EncryptedSharedPreferences only. This screen never
 * shows a saved key in clear text and never writes one to a log or a build.
 */
@Composable
fun CloudAiScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshTick by remember { mutableIntStateOf(0) }
    var keyDialogProvider by remember { mutableStateOf<String?>(null) }
    var modelDialogProvider by remember { mutableStateOf<String?>(null) }
    var importProgress by remember { mutableStateOf<KeyBatchImporter.ImportProgress?>(null) }
    var showPasteJsonDialog by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val content = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (!content.isNullOrBlank()) {
                scope.launch {
                    KeyBatchImporter.importJson(context, content) { progress ->
                        importProgress = progress
                        if (progress.isFinished) refreshTick += 1
                    }
                }
            } else {
                Toast.makeText(context, "Could not read the selected file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val bridge = GloomLiveBridgeClient.activeClient
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
                icon = GloomIcons.Globe,
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
                    "Fast and free-tier friendly. Paste every key you own, one per line, and GLOOM rotates when one is rate limited."
                } else {
                    "One key reaches many models. Keys are rotated the same way when a call is rejected or throttled."
                }
            ) {
                AppleRow(
                    title = "API Keys",
                    subtitle = if (keys.isEmpty()) "Not set — tap to add ${provider.keyHint}"
                    else SecureKeyStore.mask(keys.first()),
                    icon = GloomIcons.Shield,
                    iconTint = Apple.systemBlue,
                    value = if (keys.isEmpty()) null else "${keys.size}",
                    showChevron = true,
                    onClick = { keyDialogProvider = provider.id }
                )
                AppleDivider()
                AppleRow(
                    title = "Model",
                    subtitle = "Used for direct calls when the desktop is away",
                    icon = GloomIcons.Sparkles,
                    iconTint = Apple.systemPurple,
                    value = model,
                    showChevron = true,
                    onClick = { modelDialogProvider = provider.id }
                )
            }
        }

        AppleSection(
            header = "Batch Import",
            footer = "Upload a keys.json file or paste JSON keys from your computer. Every key is validated individually and only verified working keys are saved."
        ) {
            AppleRow(
                title = "Upload keys.json",
                subtitle = "Select JSON file to test and import",
                icon = GloomIcons.DownloadDoc,
                iconTint = Apple.systemBlue,
                showChevron = true,
                onClick = { filePickerLauncher.launch("*/*") }
            )
            AppleDivider()
            AppleRow(
                title = "Paste JSON Keys",
                subtitle = "Paste raw JSON with keys",
                icon = GloomIcons.Clipboard,
                iconTint = Apple.systemPurple,
                showChevron = true,
                onClick = { showPasteJsonDialog = true }
            )
        }

        AppleSection(
            header = "Privacy",
            footer = "Keys are sealed with an Android Keystore AES-256-GCM master key. " +
                "They are never compiled into the app, synced to the desktop, or written to logs."
        ) {
            AppleRow(
                title = "Stored on this device",
                subtitle = "EncryptedSharedPreferences · gloom_secure_keys",
                icon = GloomIcons.Shield,
                iconTint = Apple.systemGreen
            )
        }

        Spacer(Modifier.height(28.dp))
    }

    importProgress?.let { progress ->
        AlertDialog(
            onDismissRequest = {
                if (progress.isFinished) importProgress = null
            },
            title = {
                Text(if (progress.isFinished) "Import Finished" else "Testing Keys…")
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!progress.isFinished) {
                        CircularProgressIndicator(color = Apple.systemBlue, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Testing key ${progress.testedCount + 1} of ${progress.totalFound}",
                            style = AppleType.body,
                            color = Apple.label
                        )
                        Text(
                            text = "${progress.currentProvider} · ${progress.currentKeyMasked}",
                            style = AppleType.footnote,
                            color = Apple.secondaryLabel
                        )
                    } else {
                        Text(
                            text = progress.summary ?: "Validation complete.",
                            style = AppleType.body,
                            color = Apple.label
                        )
                    }
                }
            },
            confirmButton = {
                if (progress.isFinished) {
                    TextButton(onClick = { importProgress = null }) {
                        Text("Done", color = Apple.systemBlue)
                    }
                }
            }
        )
    }

    if (showPasteJsonDialog) {
        var pastedText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPasteJsonDialog = false },
            title = { Text("Paste JSON Keys") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Paste any JSON format with Groq or OpenRouter keys (e.g. {\"groq\": [\"gsk_...\"]}).",
                        style = AppleType.footnote,
                        color = Apple.secondaryLabel
                    )
                    OutlinedTextField(
                        value = pastedText,
                        onValueChange = { pastedText = it },
                        placeholder = { Text("{\"groq\": [\"gsk_...\"], \"openrouter\": [\"sk-or-...\"]}") },
                        visualTransformation = PasswordVisualTransformation(),
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val input = pastedText.trim()
                        showPasteJsonDialog = false
                        if (input.isNotEmpty()) {
                            scope.launch {
                                KeyBatchImporter.importJson(context, input) { progress ->
                                    importProgress = progress
                                    if (progress.isFinished) refreshTick += 1
                                }
                            }
                        }
                    }
                ) {
                    Text("Test & Import", color = Apple.systemBlue)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasteJsonDialog = false }) {
                    Text("Cancel", color = Apple.secondaryLabel)
                }
            }
        )
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
