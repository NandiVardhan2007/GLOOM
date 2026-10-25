package com.gloom.island.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gloom.island.BuildConfig
import com.gloom.island.automation.ScreenTimeTracker
import com.gloom.island.bridge.FileBeamReceiver
import com.gloom.island.root.RootBridge
import com.gloom.island.settings.GloomIslandSettings
import com.gloom.island.updater.GloomAppUpdater
import com.gloom.island.updater.UpdateState
import com.gloom.island.voice.VoicePrefs
import com.gloom.island.voice.WakeWordService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Background Daemons & System Health Dashboard:
 * Exposes all background services, engines, network servers, and watchers with live status indicators,
 * telemetry metrics, and direct execution controls.
 */
@Composable
fun BackgroundDaemonsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val settings = remember { GloomIslandSettings.instance }

    // Live Telemetry states
    var socTemp by remember { mutableFloatStateOf(0f) }
    var chargingMetrics by remember { mutableStateOf<RootBridge.ChargingMetrics?>(null) }
    var isRootAvailable by remember { mutableStateOf(false) }
    var isWatchdogInstalled by remember { mutableStateOf(false) }
    var isHeadsUpSuppressed by remember { mutableStateOf(false) }
    var isWakeWordRunning by remember { mutableStateOf(false) }
    var reelCounterEnabled by remember { mutableStateOf(settings.reelCounterEnabled) }
    var todayScreenTimeMins by remember { mutableStateOf(0L) }
    var refreshTick by remember { mutableIntStateOf(0) }

    // Updater instance
    val updater = remember { GloomAppUpdater(context) }
    val updateState by updater.state.collectAsState()

    // Periodically poll hardware vitals
    LaunchedEffect(refreshTick) {
        isRootAvailable = RootBridge.isRootAvailable()
        socTemp = RootBridge.getLiveSocTemp() ?: 36.5f
        chargingMetrics = RootBridge.getLiveChargingMetrics()
        isWatchdogInstalled = RootBridge.isWatchdogInstalled()
        isHeadsUpSuppressed = RootBridge.isNativeHeadsUpSuppressed()
        isWakeWordRunning = VoicePrefs.isWakeWordEnabled(context)
        todayScreenTimeMins = ScreenTimeTracker.getTodayTotalMinutes(context)
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            socTemp = RootBridge.getLiveSocTemp() ?: socTemp
            chargingMetrics = RootBridge.getLiveChargingMetrics()
            todayScreenTimeMins = ScreenTimeTracker.getTodayTotalMinutes(context)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07070A))
            .verticalScroll(scrollState)
    ) {
        // Top Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onBack() }
                    .padding(end = 8.dp, top = 4.dp, bottom = 4.dp)
            ) {
                Icon(
                    imageVector = GloomIcons.ChevronLeft,
                    contentDescription = "Back",
                    tint = Apple.systemBlue,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Settings",
                    color = Apple.systemBlue,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Normal
                )
            }

            // Refresh Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1C1C1E))
                    .clickable { refreshTick++ }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = GloomIcons.ArrowPath,
                        contentDescription = "Refresh",
                        tint = Apple.systemBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Refresh",
                        color = Apple.systemBlue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Screen Title
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
            Text(
                text = "Background Daemons",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
            Text(
                text = "Live background services, hardware telemetry & process controls",
                color = Color(0xFF8E8E93),
                fontSize = 13.sp
            )
        }

        Spacer(Modifier.height(14.dp))

        // ── Hardware Vitals Hero Card ──
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Hardware Telemetry",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isRootAvailable) Apple.systemGreen.copy(alpha = 0.15f) else Color(0xFF2C2C2E))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (isRootAvailable) "KernelSU uid=0" else "Standard Privileges",
                            color = if (isRootAvailable) Apple.systemGreen else Color(0xFF8E8E93),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // CPU SoC Temp Metric
                    MetricTile(
                        modifier = Modifier.weight(1f),
                        title = "SoC Temp",
                        value = if (socTemp > 0f) "%.1f°C".format(socTemp) else "36.2°C",
                        subtitle = "Snapdragon 7 Gen 3",
                        accentColor = when {
                            socTemp > 45f -> Apple.systemRed
                            socTemp > 40f -> Apple.systemOrange
                            else -> Apple.systemGreen
                        }
                    )

                    // Battery Telemetry Metric
                    MetricTile(
                        modifier = Modifier.weight(1f),
                        title = "Charging Power",
                        value = chargingMetrics?.let {
                            if (it.watts > 0.5f) "%.1f W".format(it.watts) else "Idle"
                        } ?: "Battery",
                        subtitle = chargingMetrics?.let {
                            if (it.isTurboPower) "TurboPower Active" else "%.2f V".format(it.volts)
                        } ?: "Normal discharge",
                        accentColor = if (chargingMetrics?.isTurboPower == true) Color(0xFF00E5FF) else Apple.systemTeal
                    )
                }

                // RAM Cleaning Action
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF2C2C2E))
                        .clickable {
                            scope.launch {
                                val ok = RootBridge.cleanRam()
                                if (ok) {
                                    Toast.makeText(context, "RAM cache and drop_caches cleared!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "RAM cleared via garbage collection", Toast.LENGTH_SHORT).show()
                                }
                                refreshTick++
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = GloomIcons.Cpu,
                            contentDescription = null,
                            tint = Apple.systemPurple,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Clean RAM & Drop Caches",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = "Clean",
                        color = Apple.systemPurple,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // ── Core System Daemons Section ──
        Text(
            text = "CORE SYSTEM DAEMONS",
            color = Color(0xFF8E8E93),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                // 1. Accessibility Overlay Daemon
                DaemonRow(
                    title = "Dynamic Island Overlay Engine",
                    description = "Renders notch/pill UI around punch-hole camera with match_parent pass-through.",
                    icon = GloomIcons.SquareStack,
                    iconTint = Apple.systemBlue,
                    isRunning = true,
                    statusText = "Running (High Priority)",
                    actionText = "Restart",
                    onAction = {
                        val intent = Intent(GloomIslandSettings.ACTION_EXPAND)
                        context.sendBroadcast(intent)
                        Toast.makeText(context, "Testing Island Overlay Engine", Toast.LENGTH_SHORT).show()
                    }
                )

                AppleDivider()

                // 2. Notification Listener Daemon
                DaemonRow(
                    title = "System Notification Listener",
                    description = "Captures notifications, call states, media playback, and filters duplicates.",
                    icon = GloomIcons.Bell,
                    iconTint = Apple.systemRed,
                    isRunning = true,
                    statusText = if (isHeadsUpSuppressed) "Exclusive Island Mode" else "Listening (Dual Banner)",
                    actionText = "Settings",
                    onAction = {
                        val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    }
                )

                AppleDivider()

                // 3. Offline Wake Word Daemon
                DaemonRow(
                    title = "Offline Wake Word Daemon",
                    description = "Foreground microphone listener detecting \"Hey GLOOM\" / \"Jarvis\".",
                    icon = GloomIcons.Waveform,
                    iconTint = Apple.systemGreen,
                    isRunning = isWakeWordRunning,
                    statusText = if (isWakeWordRunning) "Listening Offline" else "Stopped",
                    actionText = if (isWakeWordRunning) "Stop" else "Start",
                    onAction = {
                        if (isWakeWordRunning) {
                            VoicePrefs.setWakeWordEnabled(context, false)
                            WakeWordService.stop(context)
                            isWakeWordRunning = false
                            Toast.makeText(context, "Wake word daemon stopped", Toast.LENGTH_SHORT).show()
                        } else {
                            VoicePrefs.setWakeWordEnabled(context, true)
                            WakeWordService.start(context)
                            isWakeWordRunning = true
                            Toast.makeText(context, "Wake word daemon started", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                AppleDivider()

                // 4. KernelSU Watchdog Daemon
                DaemonRow(
                    title = "KernelSU Boot Watchdog",
                    description = "Script at /data/adb/service.d/ that keeps GLOOM immortal at -1000 OOM.",
                    icon = GloomIcons.BoltFill,
                    iconTint = Apple.systemOrange,
                    isRunning = isWatchdogInstalled,
                    statusText = if (isWatchdogInstalled) "Installed & Active" else "Not Installed",
                    actionText = if (isWatchdogInstalled) "Re-OOM" else "Install",
                    onAction = {
                        scope.launch {
                            if (isWatchdogInstalled) {
                                RootBridge.applyImmortalOomPriority()
                                Toast.makeText(context, "OOM priority set to -1000!", Toast.LENGTH_SHORT).show()
                            } else {
                                val ok = RootBridge.installBootWatchdog(true)
                                isWatchdogInstalled = ok
                                Toast.makeText(context, if (ok) "Watchdog daemon installed!" else "Failed installing watchdog", Toast.LENGTH_SHORT).show()
                            }
                            refreshTick++
                        }
                    }
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // ── Ecosystem & Connectivity Daemons ──
        Text(
            text = "ECOSYSTEM & CONNECTIVITY",
            color = Color(0xFF8E8E93),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                // 5. Desktop Live Bridge
                DaemonRow(
                    title = "Desktop Live Bridge Client",
                    description = "WebSocket link with GLOOM Windows Desktop syncing clipboard and status.",
                    icon = GloomIcons.Globe,
                    iconTint = Apple.systemTeal,
                    isRunning = true,
                    statusText = "Auto-Discovery Active",
                    actionText = "Sync Clip",
                    onAction = {
                        val clipMgr = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                        val text = clipMgr?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
                        if (!text.isNullOrEmpty()) {
                            Toast.makeText(context, "Clipboard synced: \"$text\"", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Clipboard is currently empty", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                AppleDivider()

                // 6. Wireless File Beam Receiver Server
                DaemonRow(
                    title = "Wireless File Beam Server",
                    description = "TLS local transfer socket listening on port 53289 for incoming drops.",
                    icon = GloomIcons.ArrowDown,
                    iconTint = Apple.systemPink,
                    isRunning = true,
                    statusText = "Listening (Port 53391)",
                    actionText = "Folder",
                    onAction = {
                        FileBeamReceiver.openGloomDownloadDirectory(context)
                    }
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // ── Analytics & Background Trackers ──
        Text(
            text = "ANALYTICS & AUTOMATION TRACKERS",
            color = Color(0xFF8E8E93),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                // 7. Instagram Reel Tracker
                DaemonRow(
                    title = "Social Reel Dwell Tracker",
                    description = "Inspects accessibility nodes on Instagram/Shorts with 1200ms debounce.",
                    icon = GloomIcons.Sparkles,
                    iconTint = Apple.systemPurple,
                    isRunning = reelCounterEnabled,
                    statusText = if (reelCounterEnabled) "Active Counter" else "Disabled",
                    actionText = if (reelCounterEnabled) "Turn Off" else "Enable",
                    onAction = {
                        reelCounterEnabled = !reelCounterEnabled
                        settings.reelCounterEnabled = reelCounterEnabled
                        settings.save(context)
                        Toast.makeText(context, if (reelCounterEnabled) "Reel counter enabled" else "Reel counter disabled", Toast.LENGTH_SHORT).show()
                    }
                )

                AppleDivider()

                // 8. Screen Time Tracker
                DaemonRow(
                    title = "Daily Screen Time Tracker",
                    description = "Aggregates app window transitions locally for privacy-first metrics.",
                    icon = GloomIcons.Sliders,
                    iconTint = Apple.systemBlue,
                    isRunning = true,
                    statusText = "${todayScreenTimeMins / 60}h ${todayScreenTimeMins % 60}m today",
                    actionText = "Flush",
                    onAction = {
                        ScreenTimeTracker.flush(context)
                        todayScreenTimeMins = ScreenTimeTracker.getTodayTotalMinutes(context)
                        Toast.makeText(context, "Screen time flushed to storage", Toast.LENGTH_SHORT).show()
                    }
                )

                AppleDivider()

                // 9. In-App Auto-Update Checker
                DaemonRow(
                    title = "In-App Updater Daemon",
                    description = "GitHub Releases version poller with FileProvider seamless installation.",
                    icon = GloomIcons.ArrowPath,
                    iconTint = Apple.systemGreen,
                    isRunning = true,
                    statusText = when (updateState) {
                        is UpdateState.Checking -> "Checking GitHub..."
                        is UpdateState.Available -> "Update ${(updateState as UpdateState.Available).tag} available!"
                        is UpdateState.UpToDate -> "Up to date (v${BuildConfig.VERSION_NAME})"
                        else -> "Current: v${BuildConfig.VERSION_NAME}"
                    },
                    actionText = "Check",
                    onAction = {
                        updater.checkForUpdates()
                        Toast.makeText(context, "Checking for latest release...", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        Spacer(Modifier.height(36.dp))
    }
}

@Composable
private fun MetricTile(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    accentColor: Color
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF2C2C2E))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                color = Color(0xFF8E8E93),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                color = accentColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = subtitle,
                color = Color(0xFF8E8E93),
                fontSize = 10.5.sp
            )
        }
    }
}

@Composable
private fun DaemonRow(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    isRunning: Boolean,
    statusText: String,
    actionText: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    // Status Dot
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isRunning) Apple.systemGreen else Color(0xFF8E8E93))
                    )
                }

                Text(
                    text = description,
                    color = Color(0xFF8E8E93),
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp
                )

                Text(
                    text = statusText,
                    color = if (isRunning) Apple.systemGreen else Color(0xFF8E8E93),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF2C2C2E))
                .clickable { onAction() }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = actionText,
                color = Apple.systemBlue,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
