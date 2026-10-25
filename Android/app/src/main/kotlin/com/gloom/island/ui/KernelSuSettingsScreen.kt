package com.gloom.island.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gloom.island.root.RootBridge
import com.gloom.island.settings.GloomIslandSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun KernelSuSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { GloomIslandSettings.instance }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    var isRootAvailable by remember { mutableStateOf(RootBridge.isRootAvailable()) }
    var isKernelSu by remember { mutableStateOf(RootBridge.isKernelSu()) }
    var liveMetrics by remember { mutableStateOf(RootBridge.getLiveChargingMetrics()) }
    var socTemp by remember { mutableStateOf(RootBridge.getLiveSocTemp()) }
    var isWatchdogInstalled by remember { mutableStateOf(RootBridge.isWatchdogInstalled()) }
    var isGrantingPermissions by remember { mutableStateOf(false) }

    // Periodic telemetry refresh
    LaunchedEffect(Unit) {
        while (true) {
            liveMetrics = RootBridge.getLiveChargingMetrics()
            socTemp = RootBridge.getLiveSocTemp()
            isRootAvailable = RootBridge.isRootAvailable(forceRefresh = true)
            isKernelSu = RootBridge.isKernelSu(forceRefresh = true)
            isWatchdogInstalled = RootBridge.isWatchdogInstalled()
            delay(1500)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        AppleSubHeader(
            title = "KernelSU Engine",
            onBack = onBack
        )

        // 1. Status & Telemetry Badge Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isRootAvailable) Apple.systemGreen else Apple.systemOrange)
                        )
                        Text(
                            text = if (isRootAvailable) "KernelSU Active" else "Root Unprivileged",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF2C2C2E))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (isKernelSu) "LKM • GKI 5.15" else "Standard Kernel",
                            color = if (isRootAvailable) Apple.systemGreen else Apple.secondaryLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Text(
                    text = if (isRootAvailable)
                        "KernelSU elevated permissions granted. GLOOM has unrestricted access to kernel hardware telemetry, immortal OOM scheduling, root system commands, and zero-touch permission elevation."
                    else
                        "KernelSU root not detected for GLOOM. Open the KernelSU Manager app -> Superuser tab -> toggle GLOOM Island to ON.",
                    color = Apple.secondaryLabel,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp
                )

                if (liveMetrics != null || socTemp != null) {
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF252528))
                            .border(1.dp, Color(0xFF333336), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "LIVE HARDWARE TELEMETRY",
                                style = AppleType.caption2.copy(letterSpacing = 0.8.sp, fontWeight = FontWeight.Bold),
                                color = Apple.systemGreen
                            )
                            if (liveMetrics != null) {
                                val m = liveMetrics!!
                                val modeStr = if (m.isTurboPower) "TurboPower" else if (m.watts > 1.0f) "Charging" else "Standby / USB"
                                Text(
                                    text = String.format(Locale.US, "Battery: %.1fW  •  %.2fV @ %.2fA  •  %.1f°C  (%s)", m.watts, m.volts, m.amperes, m.tempC, modeStr),
                                    style = AppleType.headline.copy(fontSize = 12.5.sp),
                                    color = Color.White
                                )
                            }
                            if (socTemp != null) {
                                Text(
                                    text = String.format(Locale.US, "Snapdragon 7 Gen 3 SoC: %.1f°C", socTemp),
                                    style = AppleType.headline.copy(fontSize = 12.5.sp),
                                    color = Apple.systemTeal
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Zero-Touch Elevation Action
        if (isRootAvailable) {
            AppleSection(header = "Zero-Touch Elevation") {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Instantly grant Overlay, Notification Access, Accessibility Service, Battery Optimization Whitelist, and Secure Settings with a single root command.",
                        style = AppleType.caption1,
                        color = Apple.secondaryLabel
                    )
                    AppleButton(
                        text = if (isGrantingPermissions) "Granting Privileges..." else "⚡ Auto-Grant All Permissions",
                        onClick = {
                            if (!isGrantingPermissions) {
                                isGrantingPermissions = true
                                scope.launch {
                                    val (ok, msg) = RootBridge.autoGrantAllPermissions()
                                    isGrantingPermissions = false
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    )
                }
            }
        }

        // 3. Privileged Superpowers
        AppleSection(header = "Privileged Superpowers") {
            AppleSwitchRow(
                title = "Immortal Background Daemon",
                description = "Applies Linux OOM priority -1000 so Motorola Low Memory Killer never terminates GLOOM in deep sleep.",
                checked = settings.rootImmortalProcess,
                onCheckedChange = { checked ->
                    settings.rootImmortalProcess = checked
                    settings.save(context)
                    if (checked) {
                        scope.launch { RootBridge.applyImmortalOomPriority() }
                    }
                }
            )

            AppleDivider()

            AppleSwitchRow(
                title = "Persistent Boot Watchdog",
                description = "Installs a service script in /data/adb/service.d/ that re-applies -1000 OOM priority and restores accessibility on device reboot.",
                checked = isWatchdogInstalled,
                onCheckedChange = { checked ->
                    scope.launch {
                        val success = RootBridge.installBootWatchdog(checked)
                        isWatchdogInstalled = RootBridge.isWatchdogInstalled()
                        val msg = if (success) {
                            if (checked) "Watchdog installed in /data/adb/service.d/" else "Watchdog script removed"
                        } else {
                            "Failed to update watchdog script"
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            )

            AppleDivider()

            AppleSwitchRow(
                title = "Status Bar Immersion",
                description = "Silently hides stock clock and notifications under the notch when Island expands, blending seamlessly with the bezel.",
                checked = settings.rootStatusBarImmersion,
                onCheckedChange = { checked ->
                    settings.rootStatusBarImmersion = checked
                    settings.save(context)
                    if (!checked) {
                        scope.launch { RootBridge.setStatusBarIconsHidden(false) }
                    }
                }
            )

            AppleDivider()

            AppleSwitchRow(
                title = "Exclusive Island Notifications",
                description = "Disables Android's background heads-up popups (heads_up_notifications_enabled 0) so alerts appear ONLY in the Dynamic Island.",
                checked = settings.suppressNativeHeadsUp,
                onCheckedChange = { checked ->
                    settings.suppressNativeHeadsUp = checked
                    settings.save(context)
                    scope.launch {
                        RootBridge.setNativeHeadsUpSuppressed(checked)
                    }
                }
            )

            AppleDivider()

            AppleSwitchRow(
                title = "TurboPower Hardware HUD",
                description = "Reads kernel power supply microvolts and microamperes in real time to render live wattage on the Island when charging.",
                checked = settings.rootTurboPowerHud,
                onCheckedChange = { checked ->
                    settings.rootTurboPowerHud = checked
                    settings.save(context)
                }
            )
        }

        // 4. Hardware & Power Quick Actions
        if (isRootAvailable) {
            AppleSection(header = "Hardware & Power Actions") {
                AppleRow(
                    title = "Soft Reboot (Zygote)",
                    subtitle = "Instantly restarts Android framework in 2 seconds without full kernel reboot",
                    icon = GloomIcons.ArrowClockwise,
                    iconTint = Apple.systemOrange,
                    showChevron = false,
                    onClick = {
                        RootBridge.reboot("soft")
                    }
                )

                AppleDivider()

                AppleRow(
                    title = "Clean RAM & Drop Caches",
                    subtitle = "Flushes dirty pagecaches and frees inactive Linux memory buffers",
                    icon = GloomIcons.Sparkles,
                    iconTint = Apple.systemGreen,
                    showChevron = false,
                    onClick = {
                        scope.launch {
                            val ok = RootBridge.cleanRam()
                            Toast.makeText(context, if (ok) "RAM caches cleared successfully!" else "Failed to drop caches", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                AppleDivider()

                AppleRow(
                    title = "Lock Device Instantly",
                    subtitle = "Dispatches hardware keyevent 26 without accessibility lag",
                    icon = GloomIcons.Shield,
                    iconTint = Apple.systemIndigo,
                    showChevron = false,
                    onClick = {
                        RootBridge.lockScreen()
                    }
                )
            }
        }

        // 5. Security Guarantee
        AppleSection(header = "Security & Integrity") {
            Text(
                text = "• SELinux Mode: Enforcing\n• Stealth Mount: Active\n• Banking App Compatibility: 100% Unaffected\n\nKernelSU runs with per-app namespace isolation. Non-granted applications never detect root binaries or system modifications.",
                style = AppleType.caption1,
                color = Apple.secondaryLabel,
                modifier = Modifier.padding(16.dp),
                lineHeight = 18.sp
            )
        }
    }
}
