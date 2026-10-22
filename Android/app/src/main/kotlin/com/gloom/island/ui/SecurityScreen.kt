package com.gloom.island.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.app.Activity
import com.gloom.island.security.BiometricAuth
import com.gloom.island.security.GloomSecurity

@Composable
fun SecurityScreen(onBack: () -> Unit, onOpenCalibration: (() -> Unit)? = null, onOpenVault: (() -> Unit)? = null) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scrollState = rememberScrollState()

    var hasPattern by remember { mutableStateOf(GloomSecurity.hasPattern(context)) }
    var biometricEnabled by remember { mutableStateOf(GloomSecurity.isBiometricEnabled(context)) }
    var appLockEnabled by remember { mutableStateOf(GloomSecurity.isAppLockEnabled(context)) }
    var step by remember { mutableStateOf(0) }
    var firstPattern by remember { mutableStateOf<List<Int>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(false) }

    val biometricAvailable = remember { BiometricAuth.isAvailable(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        AppleSubHeader(title = "Security", onBack = onBack)

        if (step == 0) {
            AppleSection(header = "App Lock") {
                AppleRow(
                    title = "Pattern Lock",
                    subtitle = if (hasPattern) "A pattern is saved on this device" else "No pattern saved",
                    icon = GloomIcons.Apps,
                    iconTint = Apple.systemBlue,
                    value = if (hasPattern) "On" else "Off"
                )
                AppleDivider()
                AppleSwitchRow(
                    title = "Fingerprint Unlock",
                    description = if (biometricAvailable)
                        "Unlock GLOOM with your enrolled fingerprint"
                    else
                        "No fingerprint is enrolled on this device",
                    checked = biometricEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            if (!biometricAvailable) {
                                message = "Enroll a fingerprint in system settings first."
                                return@AppleSwitchRow
                            }
                            activity?.let { act ->
                                BiometricAuth.prompt(
                                    activity = act,
                                    onSuccess = {
                                        biometricEnabled = true
                                        GloomSecurity.setBiometricEnabled(context, true)
                                        message = "Fingerprint unlock enabled."
                                    },
                                    onError = { message = it }
                                )
                            } ?: run { message = "Fingerprint is unavailable." }
                        } else {
                            biometricEnabled = false
                            GloomSecurity.setBiometricEnabled(context, false)
                        }
                    }
                )
                AppleDivider()
                AppleSwitchRow(
                    title = "Lock GLOOM on Launch",
                    description = "Require your pattern or fingerprint when the app opens",
                    checked = appLockEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled && !hasPattern && !biometricEnabled) {
                            message = "Set a pattern or fingerprint first."
                        } else {
                            appLockEnabled = enabled
                            GloomSecurity.setAppLockEnabled(context, enabled)
                            message = if (enabled) "GLOOM will lock on launch." else "Launch lock disabled."
                        }
                    }
                )
                if (onOpenVault != null) {
                    AppleDivider()
                    AppleRow(
                        title = "Password Vault",
                        subtitle = "Encrypted credentials for agent autofill",
                        icon = GloomIcons.Shield,
                        iconTint = Apple.systemBlue,
                        showChevron = true,
                        onClick = onOpenVault
                    )
                }
            }

            AppleSection(header = "Pattern") {
                AppleButton(
                    text = if (hasPattern) "Change Pattern" else "Set Pattern",
                    primary = !hasPattern,
                    icon = GloomIcons.Sparkles,
                    onClick = {
                        step = 1
                        firstPattern = emptyList()
                        message = null
                        error = false
                        if (!hasPattern) appLockEnabled = appLockEnabled
                    }
                )
                if (hasPattern) {
                    AppleDivider()
                    AppleRow(
                        title = "Remove Pattern",
                        icon = GloomIcons.Close,
                        iconTint = Apple.systemRed,
                        destructive = true,
                        onClick = {
                            GloomSecurity.clearPattern(context)
                            GloomSecurity.setAppLockEnabled(context, false)
                            hasPattern = false
                            appLockEnabled = false
                            message = "Pattern removed."
                        }
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = if (step == 1) "Draw a new pattern" else "Draw it again to confirm",
                    style = AppleType.title2,
                    color = Apple.label,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (step == 1) "Connect at least four dots." else "Repeat the same pattern.",
                    style = AppleType.footnote,
                    color = Apple.secondaryLabel,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(Modifier.height(12.dp))
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                PatternLockView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp),
                    error = error,
                    onPatternComplete = { pattern ->
                        if (pattern.size < 4) {
                            error = true
                            message = "Connect at least four dots."
                            return@PatternLockView
                        }
                        if (step == 1) {
                            firstPattern = pattern
                            step = 2
                            error = false
                            message = null
                        } else {
                            if (pattern == firstPattern) {
                                GloomSecurity.savePattern(context, pattern)
                                hasPattern = true
                                step = 0
                                error = false
                                message = "Pattern saved."
                            } else {
                                error = true
                                message = "The patterns do not match. Try again."
                                step = 1
                                firstPattern = emptyList()
                            }
                        }
                    }
                )
                }
                Spacer(Modifier.height(10.dp))
                if (message != null) {
                    Text(
                        text = message!!,
                        style = AppleType.footnote,
                        color = if (error) Apple.systemRed else Apple.systemGreen,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                AppleButton(
                    text = "Cancel",
                    primary = false,
                    onClick = {
                        step = 0
                        firstPattern = emptyList()
                        error = false
                        message = null
                    }
                )
            }
        }

        if (step == 0 && message != null) {
            Text(
                text = message!!,
                style = AppleType.footnote,
                color = if (error) Apple.systemRed else Apple.systemGreen,
                modifier = Modifier.padding(horizontal = 36.dp, vertical = 8.dp)
            )
        }

        if (step == 0) {
        AppleSection(
            header = "Device Unlock",
            footer = "Saying “Unlock my mobile” makes GLOOM draw your saved pattern on the lock screen where Android permits it; if the system blocks the gesture, the secure unlock prompt appears instead. The pattern is stored encrypted with Android Keystore — re-save it once after this update so the encrypted copy exists."
        ) {
            AppleRow(
                title = "Voice Unlock",
                subtitle = "Long-press the island, then say “Unlock my mobile”",
                icon = GloomIcons.Waveform,
                iconTint = Apple.systemPurple
            )
            AppleDivider()
            AppleRow(
                title = "Storage",
                subtitle = "Your pattern is salted and hashed on this device",
                icon = GloomIcons.Shield,
                iconTint = Apple.systemGreen
            )
            if (onOpenCalibration != null) {
                AppleDivider()
                AppleRow(
                    title = "Pattern & Swipe Calibration",
                    subtitle = "Bypass fingerprint sensor & align 9 pattern dots",
                    icon = GloomIcons.Sliders,
                    iconTint = Apple.systemOrange,
                    showChevron = true,
                    onClick = onOpenCalibration
                )
            }
        }
        }

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
fun GloomLockScreen(onUnlock: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    var error by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Apple.background)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(56.dp))
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Apple.systemBlue),
            contentAlignment = Alignment.Center
        ) {
            GloomIcon(GloomIcons.Shield, androidx.compose.ui.graphics.Color.White, 32.dp)
        }
        Spacer(Modifier.height(14.dp))
        Text("GLOOM", style = AppleType.largeTitle, color = Apple.label)
        Text(
            "Locked",
            style = AppleType.subheadline,
            color = Apple.secondaryLabel
        )
        Spacer(Modifier.height(26.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            PatternLockView(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                error = error,
                onPatternComplete = { pattern ->
                    if (pattern.size < 4) {
                        error = true
                        message = "Connect at least four dots."
                    } else if (GloomSecurity.verifyPattern(context, pattern)) {
                        onUnlock()
                    } else {
                        error = true
                        message = "Wrong pattern. Try again."
                    }
                }
            )
        }
        if (message != null) {
            Text(
                text = message!!,
                style = AppleType.footnote,
                color = Apple.systemRed,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        if (GloomSecurity.isBiometricEnabled(context) && BiometricAuth.isAvailable(context)) {
            AppleButton(
                text = "Use Fingerprint",
                primary = false,
                icon = GloomIcons.HandTap,
                onClick = {
                    activity?.let { act ->
                        BiometricAuth.prompt(
                            activity = act,
                            onSuccess = onUnlock,
                            onError = { message = it }
                        )
                    }
                }
            )
            Spacer(Modifier.height(14.dp))
        }
        Spacer(Modifier.height(24.dp))
    }
}
