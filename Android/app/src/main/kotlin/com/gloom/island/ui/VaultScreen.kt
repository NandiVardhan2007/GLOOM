package com.gloom.island.ui

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gloom.island.security.BiometricAuth
import com.gloom.island.security.GloomSecurity
import com.gloom.island.security.Vault

@Composable
fun VaultScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()

    var isUnlocked by remember {
        mutableStateOf(!GloomSecurity.isBiometricEnabled(context) && !GloomSecurity.hasPattern(context))
    }
    var entries by remember { mutableStateOf<List<Vault.VaultEntry>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }

    var newTitle by remember { mutableStateOf("") }
    var newAccount by remember { mutableStateOf("") }
    var newSecret by remember { mutableStateOf("") }
    var newTarget by remember { mutableStateOf("") }

    fun refreshEntries() {
        entries = Vault.listEntries(context)
    }

    LaunchedEffect(Unit) {
        if (!isUnlocked && activity != null && BiometricAuth.isAvailable(context)) {
            BiometricAuth.prompt(
                activity = activity,
                onSuccess = {
                    isUnlocked = true
                    refreshEntries()
                },
                onError = {
                    Toast.makeText(context, "Authentication required to view Vault", Toast.LENGTH_SHORT).show()
                }
            )
        } else {
            isUnlocked = true
            refreshEntries()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        AppleSubHeader(title = "Password Vault", onBack = onBack)

        if (!isUnlocked) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    GloomIcon(GloomIcons.Shield, Apple.systemBlue, 48.dp)
                    Spacer(Modifier.height(16.dp))
                    Text("Vault is Locked", style = AppleType.title2, color = Apple.label)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Authenticate with your fingerprint or pattern to access your passwords.",
                        style = AppleType.footnote,
                        color = Apple.secondaryLabel
                    )
                    Spacer(Modifier.height(20.dp))
                    AppleButton(
                        text = "Unlock with Biometrics",
                        primary = true,
                        onClick = {
                            if (activity != null) {
                                BiometricAuth.prompt(
                                    activity = activity,
                                    onSuccess = {
                                        isUnlocked = true
                                        refreshEntries()
                                    },
                                    onError = {
                                        Toast.makeText(context, "Biometric unlock failed", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    )
                }
            }
            return@Column
        }

        AppleSection(
            header = "Encrypted Vault",
            footer = "Stored in local Keystore AES-256-GCM. GLOOM autonomous agent uses these to autofill login screens without exposing passwords to AI or network."
        ) {
            AppleButton(
                text = "Add Credential",
                primary = true,
                icon = GloomIcons.Plus,
                onClick = { showAddDialog = true }
            )
        }

        if (showAddDialog) {
            AppleSection(header = "New Credential") {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Service / App Name", style = AppleType.caption1, color = Apple.secondaryLabel)
                    VaultInputField(value = newTitle, onValueChange = { newTitle = it }, placeholder = "e.g. Google, GitHub, Netflix")

                    Spacer(Modifier.height(12.dp))
                    Text("Username / Email", style = AppleType.caption1, color = Apple.secondaryLabel)
                    VaultInputField(value = newAccount, onValueChange = { newAccount = it }, placeholder = "username@example.com")

                    Spacer(Modifier.height(12.dp))
                    Text("Password / Secret", style = AppleType.caption1, color = Apple.secondaryLabel)
                    VaultInputField(value = newSecret, onValueChange = { newSecret = it }, placeholder = "••••••••")

                    Spacer(Modifier.height(12.dp))
                    Text("Target App / Website (Optional)", style = AppleType.caption1, color = Apple.secondaryLabel)
                    VaultInputField(value = newTarget, onValueChange = { newTarget = it }, placeholder = "e.g. google.com or package name")

                    Spacer(Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppleButton(
                            text = "Save",
                            primary = true,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (newTitle.isBlank() || newSecret.isBlank()) {
                                    Toast.makeText(context, "Title and Password required", Toast.LENGTH_SHORT).show()
                                    return@AppleButton
                                }
                                Vault.saveEntry(
                                    context,
                                    Vault.VaultEntry(
                                        title = newTitle.trim(),
                                        account = newAccount.trim(),
                                        secret = newSecret.trim(),
                                        targetPackageOrDomain = newTarget.trim()
                                    )
                                )
                                newTitle = ""
                                newAccount = ""
                                newSecret = ""
                                newTarget = ""
                                showAddDialog = false
                                refreshEntries()
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                Toast.makeText(context, "Saved to Vault", Toast.LENGTH_SHORT).show()
                            }
                        )
                        AppleButton(
                            text = "Cancel",
                            primary = false,
                            modifier = Modifier.weight(1f),
                            onClick = { showAddDialog = false }
                        )
                    }
                }
            }
        }

        AppleSection(header = "Stored Accounts (${entries.size})") {
            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No credentials saved in Vault.", style = AppleType.footnote, color = Apple.tertiaryLabel)
                }
            } else {
                entries.forEachIndexed { index, entry ->
                    var showSecret by remember(entry.id) { mutableStateOf(false) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Apple.systemBlue.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = entry.title.take(1).uppercase(),
                                style = AppleType.headline,
                                color = Apple.systemBlue
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry.title, style = AppleType.body, color = Apple.label, fontWeight = FontWeight.SemiBold)
                            if (entry.account.isNotBlank()) {
                                Text(entry.account, style = AppleType.footnote, color = Apple.secondaryLabel)
                            }
                            Text(
                                text = if (showSecret) entry.secret else "••••••••",
                                style = AppleType.caption1,
                                color = if (showSecret) Apple.systemGreen else Apple.tertiaryLabel
                            )
                        }

                        // Actions
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Apple.fillTertiary)
                                    .clickable { showSecret = !showSecret },
                                contentAlignment = Alignment.Center
                            ) {
                                GloomIcon(
                                    if (showSecret) GloomIcons.Close else GloomIcons.Sparkles,
                                    Apple.secondaryLabel,
                                    16.dp
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Apple.fillTertiary)
                                    .clickable {
                                        clipboard.setText(AnnotatedString(entry.secret))
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        Toast.makeText(context, "Password copied", Toast.LENGTH_SHORT).show()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                GloomIcon(GloomIcons.Clipboard, Apple.secondaryLabel, 16.dp)
                            }
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Apple.fillTertiary)
                                    .clickable {
                                        Vault.deleteEntry(context, entry.id)
                                        refreshEntries()
                                        Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                GloomIcon(GloomIcons.Trash, Apple.systemRed, 16.dp)
                            }
                        }
                    }

                    if (index < entries.size - 1) {
                        AppleDivider()
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun VaultInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Apple.fillTertiary)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) {
            Text(placeholder, style = AppleType.body, color = Apple.tertiaryLabel)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = AppleType.body.copy(color = Apple.label),
            cursorBrush = SolidColor(Apple.systemBlue),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
