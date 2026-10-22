package com.gloom.island.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Dedicated Privacy Policy screen detailing zero-data-collection,
 * local-first processing, and explicit Android permission rationales.
 */
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Apple.background)
            .verticalScroll(scrollState)
    ) {
        AppleSubHeader(title = "Privacy Policy", onBack = onBack)

        Text(
            text = "Your privacy is fundamental to GLOOM. We believe your device data belongs exclusively to you.",
            style = AppleType.subheadline,
            color = Apple.secondaryLabel,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        AppleSection(header = "Core Principles") {
            AppleRow(
                title = "Zero Data Collection",
                subtitle = "We do not collect, store, track, or sell any personal data, usage habits, or device telemetry.",
                icon = GloomIcons.Shield,
                iconTint = Apple.systemGreen
            )
            AppleDivider()
            AppleRow(
                title = "100% On-Device Processing",
                subtitle = "Live Activities, media progress, lyrics, timers, and notifications are processed strictly in phone RAM.",
                icon = GloomIcons.Display,
                iconTint = Apple.systemBlue
            )
            AppleDivider()
            AppleRow(
                title = "No Accounts or Sign-In",
                subtitle = "GLOOM does not require an account, email address, cloud login, or remote profile.",
                icon = GloomIcons.CheckCircleFill,
                iconTint = Apple.systemTeal
            )
        }

        AppleSection(
            header = "Permissions We Request",
            footer = "All permissions are optional and only activated when corresponding features are enabled."
        ) {
            AppleRow(
                title = "Display Over Other Apps (Overlay)",
                subtitle = "Required to render the Dynamic Island and Notch view above apps and the status bar.",
                icon = GloomIcons.Display,
                iconTint = Apple.systemBlue
            )
            AppleDivider()
            AppleRow(
                title = "Notification Listener",
                subtitle = "Used solely to detect active media (album art, artist, title) and show incoming heads-up alerts. Message text is never saved.",
                icon = GloomIcons.Bell,
                iconTint = Apple.systemPink
            )
            AppleDivider()
            AppleRow(
                title = "Accessibility Service",
                subtitle = "Used to detect notch tap/swipe gestures and ensure smooth positioning below the physical camera cutout.",
                icon = GloomIcons.HandTap,
                iconTint = Apple.systemPurple
            )
            AppleDivider()
            AppleRow(
                title = "Nearby Devices & Local Network",
                subtitle = "Used exclusively to pair with your Windows desktop running GLOOM over your private local Wi-Fi.",
                icon = GloomIcons.Laptop,
                iconTint = Apple.systemIndigo
            )
        }

        AppleSection(header = "Security & Encryption") {
            AppleRow(
                title = "TLS Pinned Desktop Bridge",
                subtitle = "LAN communication between this phone and your desktop is encrypted with TLS and pinned certificate keys.",
                icon = GloomIcons.Shield,
                iconTint = Apple.systemGreen
            )
            AppleDivider()
            AppleRow(
                title = "Protected Local Storage",
                subtitle = "Configuration settings and custom color themes are saved only in Android private SharedPreferences.",
                icon = GloomIcons.Shield,
                iconTint = Apple.systemOrange
            )
        }

        AppleSection(
            header = "Contact & Open Source",
            footer = "Last updated: October 2026 · GLOOM Dynamic Island v1.5.0"
        ) {
            AppleRow(
                title = "Open Source Transparency",
                subtitle = "Full source code is openly available for inspection and audit on GitHub.",
                icon = GloomIcons.Globe,
                iconTint = Apple.systemTeal
            )
        }

        Spacer(Modifier.height(36.dp))
    }
}

/**
 * Setup Guide and FAQ for notch calibration and desktop pairing.
 */
@Composable
fun SetupGuideScreen(onBack: () -> Unit) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Apple.background)
            .verticalScroll(scrollState)
    ) {
        AppleSubHeader(title = "Setup Guide & FAQ", onBack = onBack)

        Text(
            text = "Everything you need to set up GLOOM on your Android device.",
            style = AppleType.subheadline,
            color = Apple.secondaryLabel,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        AppleSection(header = "1. Island vs Notch Style") {
            AppleRow(
                title = "Notch Style (Desktop Parity)",
                subtitle = "Sits flush against the top bezel with concave shoulder flares, matching the Windows desktop GLOOM notch.",
                icon = GloomIcons.ArrowsUpDown,
                iconTint = Apple.systemPurple
            )
            AppleDivider()
            AppleRow(
                title = "Pill Style (Floating)",
                subtitle = "Floats independently as an elliptical pill around your camera punch-hole cutout with customizable clearance.",
                icon = GloomIcons.Island,
                iconTint = Apple.systemBlue
            )
        }

        AppleSection(header = "2. Calibrating Fit") {
            AppleRow(
                title = "Position Sliders",
                subtitle = "Go to Settings > Position to align the island with your camera punch hole (Center, Left, or Right).",
                icon = GloomIcons.Move,
                iconTint = Apple.systemIndigo
            )
            AppleDivider()
            AppleRow(
                title = "Clearance Top",
                subtitle = "Adjust clearance in dp to avoid overlapping status bar clocks or hardware bezels.",
                icon = GloomIcons.Sliders,
                iconTint = Apple.systemOrange
            )
        }

        AppleSection(header = "3. Continuity with Desktop") {
            AppleRow(
                title = "Connect to Same Wi-Fi",
                subtitle = "Ensure your phone and PC running GLOOM are on the same local network.",
                icon = GloomIcons.Laptop,
                iconTint = Apple.systemTeal
            )
            AppleDivider()
            AppleRow(
                title = "Scan QR or Enter PIN",
                subtitle = "Tap 'Pair with PC' on the Home screen or scan the QR code displayed in the desktop app.",
                icon = GloomIcons.QrCode,
                iconTint = Apple.systemGreen
            )
            AppleDivider()
            AppleRow(
                title = "Clipboard & File Beam",
                subtitle = "Once paired, clipboard copies and file transfers work instantly across both devices without Internet.",
                icon = GloomIcons.Beam,
                iconTint = Apple.systemBlue
            )
        }

        AppleSection(header = "4. Gestures") {
            AppleRow(
                title = "Tap Island",
                subtitle = "Expands into full panel with media controls, lyrics, or detailed notification actions.",
                icon = GloomIcons.HandTap,
                iconTint = Apple.systemPink
            )
            AppleDivider()
            AppleRow(
                title = "Swipe to Collapse",
                subtitle = "Swipe upward on the expanded panel to immediately collapse back into compact pill or notch.",
                icon = GloomIcons.ArrowsUpDown,
                iconTint = Apple.systemGray
            )
        }

        Spacer(Modifier.height(36.dp))
    }
}

/**
 * Open Source Licenses & Acknowledgements.
 */
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Apple.background)
            .verticalScroll(scrollState)
    ) {
        AppleSubHeader(title = "Open Source Licenses", onBack = onBack)

        AppleSection(header = "GLOOM") {
            AppleRow(
                title = "GLOOM Ecosystem",
                subtitle = "MIT License · Copyright © 2026 Nandu",
                icon = GloomIcons.Sparkles,
                iconTint = Apple.systemBlue
            )
        }

        AppleSection(header = "Third-Party Libraries") {
            AppleRow(
                title = "Jetpack Compose & Material 3",
                subtitle = "Apache License 2.0 · Google LLC",
                icon = GloomIcons.Display,
                iconTint = Apple.systemGreen
            )
            AppleDivider()
            AppleRow(
                title = "Kotlin & Coroutines",
                subtitle = "Apache License 2.0 · JetBrains s.r.o.",
                icon = GloomIcons.Puzzle,
                iconTint = Apple.systemPurple
            )
            AppleDivider()
            AppleRow(
                title = "ZXing Barcode & QR Scanner",
                subtitle = "Apache License 2.0 · ZXing Authors",
                icon = GloomIcons.QrCode,
                iconTint = Apple.systemTeal
            )
            AppleDivider()
            AppleRow(
                title = "Tauri v2 Desktop Framework",
                subtitle = "MIT / Apache 2.0 · The Tauri Programme",
                icon = GloomIcons.Laptop,
                iconTint = Apple.systemOrange
            )
        }

        Spacer(Modifier.height(36.dp))
    }
}
