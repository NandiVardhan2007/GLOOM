package com.jarvis.island

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.island.bridge.JarvisLiveBridgeClient
import com.jarvis.island.service.JarvisIslandOverlayService
import com.jarvis.island.settings.JarvisIslandSettings
import com.jarvis.island.ui.IslandThemePalettes

class MainActivity : ComponentActivity() {

    private lateinit var bridgeClient: JarvisLiveBridgeClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bridgeClient = JarvisLiveBridgeClient(this)
        JarvisIslandSettings.instance.load(this)

        if (hasOverlayPermission() && JarvisIslandSettings.instance.isIslandEnabled) {
            startOverlayService()
        }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF07070A),
                    surface = Color(0xFF101018),
                    primary = Color(0xFF38BDF8)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF07070A)
                ) {
                    JarvisMasterApp(
                        hasOverlayPermission = hasOverlayPermission(),
                        onRequestOverlayPermission = { requestOverlayPermission() },
                        onToggleExpand = { triggerBroadcast(JarvisIslandSettings.ACTION_TOGGLE_EXPAND) },
                        onExpand = { triggerBroadcast(JarvisIslandSettings.ACTION_EXPAND) },
                        onCollapse = { triggerBroadcast(JarvisIslandSettings.ACTION_COLLAPSE) },
                        onSimulateBeam = { triggerBroadcast(JarvisIslandSettings.ACTION_TEST_BEAM) },
                        onSimulateMusic = { triggerBroadcast(JarvisIslandSettings.ACTION_TEST_MUSIC) },
                        onSyncClipboard = {
                            bridgeClient.syncClipboardToDesktop("Manual clipboard sync from JARVIS Companion")
                        },
                        onRestartService = { startOverlayService() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasOverlayPermission() && JarvisIslandSettings.instance.isIslandEnabled) {
            startOverlayService()
        }
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun triggerBroadcast(action: String) {
        val intent = Intent(action).setPackage(packageName)
        sendBroadcast(intent)
    }

    private fun startOverlayService() {
        val intent = Intent(this, JarvisIslandOverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}

/**
 * Main application scaffold featuring bottom navigation, screen routing,
 * and complete parity with MaterialYou-Dynamic-Island.
 */
@Composable
fun JarvisMasterApp(
    hasOverlayPermission: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onToggleExpand: () -> Unit,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onSimulateBeam: () -> Unit,
    onSimulateMusic: () -> Unit,
    onSyncClipboard: () -> Unit,
    onRestartService: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val settings = remember { JarvisIslandSettings.instance }
    var currentScreen by remember { mutableStateOf("home") } // "home", "plugins", "settings", "about"
    var settingsSubPage by remember { mutableStateOf<String?>(null) } // "position", "size", "themes", "behavior", "apps"

    BackHandler(enabled = settingsSubPage != null) {
        settingsSubPage = null
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0D0D14),
                contentColor = Color(0xFF38BDF8)
            ) {
                listOf(
                    NavigationItem("home", "Home", "🏠"),
                    NavigationItem("plugins", "Plugins", "⚡"),
                    NavigationItem("settings", "Settings", "⚙️"),
                    NavigationItem("about", "About", "ℹ️")
                ).forEach { item ->
                    val selected = currentScreen == item.id
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (settings.hapticFeedback) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            currentScreen = item.id
                            settingsSubPage = null
                        },
                        icon = {
                            Text(text = item.icon, fontSize = 18.sp)
                        },
                        label = {
                            Text(
                                text = item.label,
                                fontSize = 10.5.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF38BDF8),
                            selectedTextColor = Color(0xFF38BDF8),
                            unselectedIconColor = Color(0xFF94A3B8),
                            unselectedTextColor = Color(0xFF64748B),
                            indicatorColor = Color(0x2238BDF8)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF07070A))
        ) {
            AnimatedContent(
                targetState = currentScreen to settingsSubPage,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "screenTransition"
            ) { (screen, subPage) ->
                when {
                    screen == "home" -> {
                        HomeScreen(
                            hasOverlayPermission = hasOverlayPermission,
                            onRequestOverlayPermission = onRequestOverlayPermission,
                            onToggleExpand = onToggleExpand,
                            onSimulateBeam = onSimulateBeam,
                            onSimulateMusic = onSimulateMusic,
                            onSyncClipboard = onSyncClipboard,
                            onRestartService = onRestartService
                        )
                    }
                    screen == "plugins" -> {
                        PluginsScreen()
                    }
                    screen == "settings" && subPage == null -> {
                        SettingsHubScreen(
                            onNavigateSubPage = { page ->
                                if (settings.hapticFeedback) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                settingsSubPage = page
                            }
                        )
                    }
                    screen == "settings" && subPage == "position" -> {
                        PositionSettingsScreen(onBack = { settingsSubPage = null })
                    }
                    screen == "settings" && subPage == "size" -> {
                        SizeSettingsScreen(onBack = { settingsSubPage = null })
                    }
                    screen == "settings" && subPage == "themes" -> {
                        ThemesSettingsScreen(onBack = { settingsSubPage = null })
                    }
                    screen == "settings" && subPage == "behavior" -> {
                        BehaviorSettingsScreen(onBack = { settingsSubPage = null })
                    }
                    screen == "settings" && subPage == "apps" -> {
                        EnabledAppsSettingsScreen(onBack = { settingsSubPage = null })
                    }
                    screen == "about" -> {
                        AboutScreen()
                    }
                    else -> {
                        HomeScreen(
                            hasOverlayPermission = hasOverlayPermission,
                            onRequestOverlayPermission = onRequestOverlayPermission,
                            onToggleExpand = onToggleExpand,
                            onSimulateBeam = onSimulateBeam,
                            onSimulateMusic = onSimulateMusic,
                            onSyncClipboard = onSyncClipboard,
                            onRestartService = onRestartService
                        )
                    }
                }
            }
        }
    }
}

data class NavigationItem(val id: String, val label: String, val icon: String)

// ══════════════════════════════════════════════════════════════════════
// SCREEN 1: HOME SCREEN
// ══════════════════════════════════════════════════════════════════════
@Composable
fun HomeScreen(
    hasOverlayPermission: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onToggleExpand: () -> Unit,
    onSimulateBeam: () -> Unit,
    onSimulateMusic: () -> Unit,
    onSyncClipboard: () -> Unit,
    onRestartService: () -> Unit
) {
    val context = LocalContext.current
    val settings = remember { JarvisIslandSettings.instance }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // App Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "JARVIS Island",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.SansSerif
                )
                Text(
                    text = "Connected Ecosystem Dynamic Island",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (hasOverlayPermission && settings.isIslandEnabled) Color(0x2222C55E) else Color(0x22EF4444))
                    .border(
                        1.dp,
                        if (hasOverlayPermission && settings.isIslandEnabled) Color(0xFF22C55E) else Color(0xFFEF4444),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = if (hasOverlayPermission && settings.isIslandEnabled) "Running" else "Stopped",
                    color = if (hasOverlayPermission && settings.isIslandEnabled) Color(0xFF22C55E) else Color(0xFFEF4444),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Hero Master Switch Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF13131C)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Dynamic Island Active",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (settings.isIslandEnabled) "Island overlay rendering over punch-hole camera"
                               else "Island overlay disabled",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.5.sp
                    )
                }
                Switch(
                    checked = settings.isIslandEnabled,
                    onCheckedChange = {
                        settings.isIslandEnabled = it
                        settings.save(context)
                        if (it) onRestartService()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF0284C7),
                        uncheckedThumbColor = Color(0xFF94A3B8),
                        uncheckedTrackColor = Color(0xFF1E293B)
                    )
                )
            }
        }

        // Permission Card if missing
        if (!hasOverlayPermission) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1014)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Overlay Permission Required",
                        color = Color(0xFFFCA5A5),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Grant 'Appear on top' permission so Dynamic Island can position over your Motorola front camera.",
                        color = Color(0xFFE2E8F0),
                        fontSize = 11.5.sp
                    )
                    Button(
                        onClick = onRequestOverlayPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Grant Permission", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // LiveBridge Windows PC Connection Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF13131C)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x2238BDF8)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "💻", fontSize = 15.sp)
                        }
                        Column {
                            Text(text = "JARVIS Windows Desktop", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                            Text(text = "LAN Continuity: 192.168.1.4:53390", color = Color(0xFF38BDF8), fontSize = 10.5.sp)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x2222C55E))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(text = "Connected", color = Color(0xFF22C55E), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Quick Sandbox & Diagnostics Actions
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF13131C)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "Quick Interactive Tests", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onToggleExpand,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Toggle Expand", fontSize = 11.sp, color = Color.White)
                    }

                    Button(
                        onClick = onSimulateBeam,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Test File Beam", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onSimulateMusic,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Test Music Card", fontSize = 11.sp, color = Color.White)
                    }

                    Button(
                        onClick = onSyncClipboard,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Sync Clipboard", fontSize = 11.sp, color = Color.White)
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
// SCREEN 2: PLUGINS SCREEN
// ══════════════════════════════════════════════════════════════════════
@Composable
fun PluginsScreen() {
    val context = LocalContext.current
    val settings = remember { JarvisIslandSettings.instance }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column {
            Text(text = "Island Plugins", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(text = "Manage features rendered inside the Dynamic Island", color = Color(0xFF94A3B8), fontSize = 12.sp)
        }

        // Plugin 1: File Beam
        PluginItem(
            title = "High-Speed File Beam",
            description = "Animates live file transfers between Windows PC and phone with MB/s speedometer.",
            icon = "🚀",
            enabled = settings.pluginFileBeam,
            onToggle = {
                settings.pluginFileBeam = it
                settings.save(context)
            }
        )

        // Plugin 2: Media Player
        PluginItem(
            title = "Now Playing Media Session",
            description = "Displays rotating vinyl disk art, equalizer waveform, and music playback controls.",
            icon = "🎵",
            enabled = settings.pluginMedia,
            onToggle = {
                settings.pluginMedia = it
                settings.save(context)
            }
        )

        // Plugin 3: Smart Notifications
        PluginItem(
            title = "Smart Heads-Up Notifications",
            description = "Pill alerts for incoming messaging and system notifications.",
            icon = "🔔",
            enabled = settings.pluginNotifications,
            onToggle = {
                settings.pluginNotifications = it
                settings.save(context)
            }
        )

        // Plugin 4: Battery & Charging
        PluginItem(
            title = "Charging & Battery Status",
            description = "Visual charging ring and battery percentage when charger connects.",
            icon = "⚡",
            enabled = settings.pluginBattery,
            onToggle = {
                settings.pluginBattery = it
                settings.save(context)
            }
        )

        // Plugin 5: Clipboard Continuity
        PluginItem(
            title = "Universal Clipboard Mirroring",
            description = "Live 2-way clipboard synchronization with Windows Desktop.",
            icon = "📋",
            enabled = settings.pluginClipboard,
            onToggle = {
                settings.pluginClipboard = it
                settings.save(context)
            }
        )
    }
}

@Composable
fun PluginItem(
    title: String,
    description: String,
    icon: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF13131C)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f).padding(end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1E293B)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = icon, fontSize = 20.sp)
                }
                Column {
                    Text(text = title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = description, color = Color(0xFF94A3B8), fontSize = 11.sp)
                }
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF0284C7),
                    uncheckedThumbColor = Color(0xFF94A3B8),
                    uncheckedTrackColor = Color(0xFF1E293B)
                )
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
// SCREEN 3: SETTINGS HUB & SUB-PAGES
// ══════════════════════════════════════════════════════════════════════
@Composable
fun SettingsHubScreen(onNavigateSubPage: (String) -> Unit) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column {
            Text(text = "Settings", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(text = "Material You Dynamic Island customization categories", color = Color(0xFF94A3B8), fontSize = 12.sp)
        }

        SettingsCategoryTile(
            title = "Position & Alignment",
            subtitle = "Camera punch-hole clearance, X/Y margins, Island gravity",
            icon = "📍",
            onClick = { onNavigateSubPage("position") }
        )

        SettingsCategoryTile(
            title = "Size & Geometry",
            subtitle = "Pill width, height, expanded card bounds, corner radii",
            icon = "📐",
            onClick = { onNavigateSubPage("size") }
        )

        SettingsCategoryTile(
            title = "Themes & Styling",
            subtitle = "Obsidian Black, Cyber Cyan, Emerald Matrix, border glows",
            icon = "🎨",
            onClick = { onNavigateSubPage("themes") }
        )

        SettingsCategoryTile(
            title = "Behavior & Lock Screen",
            subtitle = "Lock screen overlay, haptic feedback, gestures, auto-collapse",
            icon = "🔒",
            onClick = { onNavigateSubPage("behavior") }
        )

        SettingsCategoryTile(
            title = "Enabled Applications",
            subtitle = "Filter which apps can trigger Dynamic Island heads-up cards",
            icon = "📱",
            onClick = { onNavigateSubPage("apps") }
        )
    }
}

@Composable
fun SettingsCategoryTile(title: String, subtitle: String, icon: String, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF13131C)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f).padding(end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1E293B)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = icon, fontSize = 18.sp)
                }
                Column {
                    Text(text = title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = subtitle, color = Color(0xFF94A3B8), fontSize = 11.sp)
                }
            }
            Text(text = "›", color = Color(0xFF64748B), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ── Position & Clearance Sub-Page ──
@Composable
fun PositionSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { JarvisIslandSettings.instance }
    val scrollState = rememberScrollState()

    var gravity by remember { mutableStateOf(settings.gravity) }
    var cutoutClearance by remember { mutableIntStateOf(settings.cutoutClearance) }
    var positionY by remember { mutableIntStateOf(settings.positionY) }
    var positionX by remember { mutableIntStateOf(settings.positionX) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SubPageHeader(title = "Position & Clearance", onBack = onBack)

        // Visual Cutout Preview
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Visual Bezel & Camera Cutout Preview", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF030712))
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = (positionY * 0.8f).dp)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color.Black)
                            .border(1.dp, Color(0xFF334155), CircleShape)
                    )

                    Box(
                        modifier = Modifier
                            .padding(top = (positionY * 0.8f - 2).dp)
                            .offset(x = positionX.dp)
                            .width((settings.pillWidth * 0.8f).dp)
                            .height((settings.pillHeight * 0.8f).dp)
                            .clip(RoundedCornerShape((settings.cornerRadius * 0.8f).dp))
                            .background(Color.Black.copy(alpha = 0.85f))
                            .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f), RoundedCornerShape((settings.cornerRadius * 0.8f).dp))
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(Color(0xFF38BDF8)))
                            Spacer(modifier = Modifier.width((cutoutClearance * 0.8f).dp))
                            Text("65%", color = Color(0xFF38BDF8), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(text = "Clearance creates an empty dead-zone right over your center camera hole.", color = Color(0xFF64748B), fontSize = 10.5.sp)
            }
        }

        // Sliders
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF13131C)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Gravity
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Center", "Left", "Right").forEach { grav ->
                        val active = gravity == grav
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) Color(0xFF0284C7) else Color(0xFF1E293B))
                                .clickable {
                                    gravity = grav
                                    settings.gravity = grav
                                    settings.save(context)
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = grav, color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                SliderRow(
                    label = "Punch-Hole Clearance Gap",
                    value = cutoutClearance.toFloat(),
                    valueText = "${cutoutClearance} dp",
                    range = 20f..80f,
                    onValueChange = {
                        cutoutClearance = it.toInt()
                        settings.cutoutClearance = cutoutClearance
                        settings.save(context)
                    }
                )

                SliderRow(
                    label = "Top Margin (Y Position)",
                    value = positionY.toFloat(),
                    valueText = "${positionY} dp",
                    range = 0f..60f,
                    onValueChange = {
                        positionY = it.toInt()
                        settings.positionY = positionY
                        settings.save(context)
                    }
                )

                SliderRow(
                    label = "Horizontal Offset (X Position)",
                    value = positionX.toFloat(),
                    valueText = "${positionX} dp",
                    range = -50f..50f,
                    onValueChange = {
                        positionX = it.toInt()
                        settings.positionX = positionX
                        settings.save(context)
                    }
                )
            }
        }
    }
}

// ── Size & Geometry Sub-Page ──
@Composable
fun SizeSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { JarvisIslandSettings.instance }
    val scrollState = rememberScrollState()

    var pillWidth by remember { mutableIntStateOf(settings.pillWidth) }
    var pillHeight by remember { mutableIntStateOf(settings.pillHeight) }
    var expandedWidth by remember { mutableIntStateOf(settings.expandedWidth) }
    var expandedHeight by remember { mutableIntStateOf(settings.expandedHeight) }
    var cornerRadius by remember { mutableIntStateOf(settings.cornerRadius) }
    var expandedCornerRadius by remember { mutableIntStateOf(settings.expandedCornerRadius) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SubPageHeader(title = "Size & Geometry", onBack = onBack)

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF13131C)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SliderRow(
                    label = "Collapsed Pill Width",
                    value = pillWidth.toFloat(),
                    valueText = "${pillWidth} dp",
                    range = 140f..260f,
                    onValueChange = {
                        pillWidth = it.toInt()
                        settings.pillWidth = pillWidth
                        settings.save(context)
                    }
                )

                SliderRow(
                    label = "Collapsed Pill Height",
                    value = pillHeight.toFloat(),
                    valueText = "${pillHeight} dp",
                    range = 30f..54f,
                    onValueChange = {
                        pillHeight = it.toInt()
                        settings.pillHeight = pillHeight
                        settings.save(context)
                    }
                )

                SliderRow(
                    label = "Expanded Card Width",
                    value = expandedWidth.toFloat(),
                    valueText = "${expandedWidth} dp",
                    range = 300f..380f,
                    onValueChange = {
                        expandedWidth = it.toInt()
                        settings.expandedWidth = expandedWidth
                        settings.save(context)
                    }
                )

                SliderRow(
                    label = "Expanded Card Height",
                    value = expandedHeight.toFloat(),
                    valueText = "${expandedHeight} dp",
                    range = 200f..320f,
                    onValueChange = {
                        expandedHeight = it.toInt()
                        settings.expandedHeight = expandedHeight
                        settings.save(context)
                    }
                )

                SliderRow(
                    label = "Collapsed Corner Radius",
                    value = cornerRadius.toFloat(),
                    valueText = "${cornerRadius} dp",
                    range = 10f..30f,
                    onValueChange = {
                        cornerRadius = it.toInt()
                        settings.cornerRadius = cornerRadius
                        settings.save(context)
                    }
                )

                SliderRow(
                    label = "Expanded Corner Radius",
                    value = expandedCornerRadius.toFloat(),
                    valueText = "${expandedCornerRadius} dp",
                    range = 18f..36f,
                    onValueChange = {
                        expandedCornerRadius = it.toInt()
                        settings.expandedCornerRadius = expandedCornerRadius
                        settings.save(context)
                    }
                )
            }
        }
    }
}

// ── Themes Sub-Page ──
@Composable
fun ThemesSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { JarvisIslandSettings.instance }
    val scrollState = rememberScrollState()

    var themeStyle by remember { mutableStateOf(settings.themeStyle) }
    var showBorders by remember { mutableStateOf(settings.showBorders) }
    var borderGlow by remember { mutableStateOf(settings.borderGlow) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SubPageHeader(title = "Themes & Styling", onBack = onBack)

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF13131C)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf("Obsidian Black", "Cyber Cyan", "Emerald Matrix", "Material Purple").forEach { themeName ->
                    val active = themeStyle == themeName
                    val palette = IslandThemePalettes.getTheme(themeName)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) Color(0xFF1E293B) else Color(0xFF0F172A))
                            .border(
                                width = if (active) 1.5.dp else 1.dp,
                                color = if (active) palette.primaryAccent else Color(0x22FFFFFF),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                themeStyle = themeName
                                settings.themeStyle = themeName
                                settings.save(context)
                            }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(modifier = Modifier.size(22.dp).clip(CircleShape).background(palette.primaryAccent))
                            Text(text = themeName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        if (active) {
                            Text(text = "Active", color = palette.primaryAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                SettingsSwitch(
                    title = "Show Outer Borders",
                    description = "Displays sleek accent border around island.",
                    checked = showBorders,
                    onCheckedChange = {
                        showBorders = it
                        settings.showBorders = it
                        settings.save(context)
                    }
                )

                SettingsSwitch(
                    title = "Glowing Shimmer Effect",
                    description = "Pulses subtle radiance during transfers.",
                    checked = borderGlow,
                    onCheckedChange = {
                        borderGlow = it
                        settings.borderGlow = it
                        settings.save(context)
                    }
                )
            }
        }
    }
}

// ── Behavior Sub-Page ──
@Composable
fun BehaviorSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { JarvisIslandSettings.instance }
    val scrollState = rememberScrollState()

    var showOnLockScreen by remember { mutableStateOf(settings.showOnLockScreen) }
    var showInLandscape by remember { mutableStateOf(settings.showInLandscape) }
    var autoExpandOnTransfer by remember { mutableStateOf(settings.autoExpandOnTransfer) }
    var autoCollapseDelaySec by remember { mutableFloatStateOf(settings.autoCollapseDelaySec) }
    var hapticFeedback by remember { mutableStateOf(settings.hapticFeedback) }
    var longPressToExpand by remember { mutableStateOf(settings.longPressToExpand) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SubPageHeader(title = "Behavior & Lock Screen", onBack = onBack)

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF13131C)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SettingsSwitch(
                    title = "Show on Lock Screen",
                    description = "Overlay stays visible and active on keyguard / lock screen.",
                    checked = showOnLockScreen,
                    onCheckedChange = {
                        showOnLockScreen = it
                        settings.showOnLockScreen = it
                        settings.save(context)
                    }
                )

                SettingsSwitch(
                    title = "Long Press to Expand",
                    description = "Holding down on the pill springs open the card.",
                    checked = longPressToExpand,
                    onCheckedChange = {
                        longPressToExpand = it
                        settings.longPressToExpand = it
                        settings.save(context)
                    }
                )

                SettingsSwitch(
                    title = "Haptic Tactile Feedback",
                    description = "Gentle vibration on tap and long-press.",
                    checked = hapticFeedback,
                    onCheckedChange = {
                        hapticFeedback = it
                        settings.hapticFeedback = it
                        settings.save(context)
                    }
                )

                SettingsSwitch(
                    title = "Auto-Expand on File Beam",
                    description = "Automatically opens card during PC file transfer.",
                    checked = autoExpandOnTransfer,
                    onCheckedChange = {
                        autoExpandOnTransfer = it
                        settings.autoExpandOnTransfer = it
                        settings.save(context)
                    }
                )

                SettingsSwitch(
                    title = "Show in Landscape",
                    description = "Show island when phone is rotated horizontally.",
                    checked = showInLandscape,
                    onCheckedChange = {
                        showInLandscape = it
                        settings.showInLandscape = it
                        settings.save(context)
                    }
                )

                SliderRow(
                    label = "Auto-Collapse Delay",
                    value = autoCollapseDelaySec,
                    valueText = String.format("%.1fs", autoCollapseDelaySec),
                    range = 1.5f..10.0f,
                    onValueChange = {
                        autoCollapseDelaySec = it
                        settings.autoCollapseDelaySec = it
                        settings.save(context)
                    }
                )
            }
        }
    }
}

// ── Enabled Apps Sub-Page ──
@Composable
fun EnabledAppsSettingsScreen(onBack: () -> Unit) {
    val scrollState = rememberScrollState()
    val apps = remember {
        listOf(
            "WhatsApp" to true,
            "Telegram" to true,
            "Spotify" to true,
            "YouTube Music" to true,
            "Google Chrome" to false,
            "Slack" to true,
            "Discord" to true,
            "Phone & Dialer" to true
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SubPageHeader(title = "Enabled Applications", onBack = onBack)

        Text(text = "Choose which apps can present cards inside the Dynamic Island.", color = Color(0xFF94A3B8), fontSize = 12.sp)

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF13131C)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                apps.forEach { (appName, defaultEnabled) ->
                    var isEnabled by remember { mutableStateOf(defaultEnabled) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = appName, color = Color.White, fontSize = 13.5.sp)
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { isEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF0284C7),
                                uncheckedThumbColor = Color(0xFF94A3B8),
                                uncheckedTrackColor = Color(0xFF1E293B)
                            )
                        )
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
// SCREEN 4: ABOUT SCREEN
// ══════════════════════════════════════════════════════════════════════
@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF0284C7)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "⚡", fontSize = 36.sp)
        }

        Text(text = "JARVIS Dynamic Island", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(text = "Version 1.0.0 • Connected Ecosystem", color = Color(0xFF94A3B8), fontSize = 12.sp)

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF13131C)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "Platform Architecture", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "JARVIS Ecosystem connects Windows Desktop and Android companion devices seamlessly. " +
                            "Features real-time punch-hole cutout clearance, LiveBridge clipboard sync, and 5G WiFi Direct file beaming.",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.5.sp
                )

                OutlinedButton(
                    onClick = {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/NandiVardhan2007/JARVIS.git"))
                        context.startActivity(browserIntent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("View GitHub Repository", color = Color(0xFF38BDF8), fontSize = 12.sp)
                }
            }
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF13131C)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "Accessibility & Overlay Disclosure", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "JARVIS Island uses Android System Alert Window (Overlay) APIs to render dynamic activity cards around your camera cutout. " +
                            "No personal data or passwords are ever accessed or collected.",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
            }
        }
    }
}

// ── Shared UI Helpers ──
@Composable
fun SubPageHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1E293B))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "←", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Text(text = title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SliderRow(label: String, value: Float, valueText: String, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, color = Color(0xFFE2E8F0), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(text = valueText, color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF38BDF8),
                activeTrackColor = Color(0xFF38BDF8),
                inactiveTrackColor = Color(0xFF1E293B)
            )
        )
    }
}

@Composable
fun SettingsSwitch(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, color = Color(0xFFE2E8F0), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            Text(text = description, color = Color(0xFF64748B), fontSize = 10.5.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF0284C7),
                uncheckedThumbColor = Color(0xFF94A3B8),
                uncheckedTrackColor = Color(0xFF1E293B)
            )
        )
    }
}
