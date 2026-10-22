package com.jarvis.island

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.island.bridge.BridgeTls
import com.jarvis.island.bridge.FileBeamReceiver
import com.jarvis.island.bridge.JarvisLiveBridgeClient
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.jarvis.island.ui.RemoteTrackpad
import com.jarvis.island.ui.Apple
import com.jarvis.island.ui.AppleButton
import com.jarvis.island.ui.AppleDivider
import com.jarvis.island.ui.AppleLargeTitle
import com.jarvis.island.ui.AppleRow
import com.jarvis.island.ui.AppleSection
import com.jarvis.island.ui.AppleSegmentedControl
import com.jarvis.island.ui.AppleSliderRow
import com.jarvis.island.ui.AppleSubHeader
import com.jarvis.island.ui.AppleSwitch
import com.jarvis.island.ui.AppleSwitchRow
import com.jarvis.island.ui.AppleTab
import com.jarvis.island.ui.AppleTabBar
import com.jarvis.island.ui.AppleType
import com.jarvis.island.ui.JarvisIcons
import com.jarvis.island.ui.JarvisIcon
import com.jarvis.island.ui.JarvisLockScreen
import com.jarvis.island.ui.SecurityScreen
import com.jarvis.island.ui.VoiceSettingsScreen
import com.jarvis.island.ui.AutomationScreen
import com.jarvis.island.ui.CloudAiScreen
import com.jarvis.island.ui.CalibrationScreen
import com.jarvis.island.ui.VaultScreen
import com.jarvis.island.ui.NotificationSettingsScreen
import com.jarvis.island.ui.ReelWeekChart
import com.jarvis.island.security.JarvisSecurity
import com.jarvis.island.service.JarvisIslandAccessibilityService
import com.jarvis.island.service.JarvisIslandOverlayService
import com.jarvis.island.updater.JarvisAppUpdater
import com.jarvis.island.updater.UpdateState
import com.jarvis.island.settings.JarvisIslandSettings
import com.jarvis.island.ui.IslandThemePalettes

class MainActivity : ComponentActivity() {

    private lateinit var bridgeClient: JarvisLiveBridgeClient
    private val activeScreenState = mutableStateOf("home")
    private val hasOverlayState = mutableStateOf(false)
    private val hasNotificationState = mutableStateOf(false)
    private val hasAccessibilityState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val startScreen = intent?.getStringExtra("open_screen")
        if (!startScreen.isNullOrEmpty()) {
            activeScreenState.value = startScreen
        }

        hasOverlayState.value = hasOverlayPermission()
        hasNotificationState.value = hasNotificationAccess()
        hasAccessibilityState.value = hasAccessibilityPermission()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
        }

        bridgeClient = JarvisLiveBridgeClient(this)
        bridgeClient.startDiscovery { _, _ -> }
        JarvisIslandSettings.instance.load(this)
        com.jarvis.island.automation.ReminderScheduler.rescheduleAll(this)

        if (com.jarvis.island.voice.VoicePrefs.isWakeWordEnabled(this) &&
            checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
            com.jarvis.island.voice.WakeWordModel.isInstalled(this)
        ) {
            try {
                com.jarvis.island.voice.WakeWordService.start(this)
            } catch (_: Exception) {
            }
        }

        if (hasOverlayState.value && JarvisIslandSettings.instance.isIslandEnabled) {
            startOverlayService()
        }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF07070A),
                    surface = Color(0xFF101018),
                    primary = Color(0xFF0A84FF)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF07070A)
                ) {
                    val beamFilePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
                    ) { uri: android.net.Uri? ->
                        if (uri != null) {
                            val desktopIp = bridgeClient.desktopIp
                            if (!desktopIp.isNullOrBlank() && bridgeClient.isConnected) {
                                bridgeClient.beamUriToDesktop(
                                    uri = uri,
                                    onProgress = { },
                                    onComplete = { success ->
                                        val msg = if (success) "File beamed to desktop!" else "Failed to beam file to desktop"
                                        android.widget.Toast.makeText(this@MainActivity, msg, android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                )
                            } else {
                                android.widget.Toast.makeText(this@MainActivity, "Desktop bridge not connected. Ensure desktop is active on same Wi-Fi.", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    }

                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        if (intent?.getBooleanExtra("action_beam_file_picker", false) == true) {
                            beamFilePickerLauncher.launch("*/*")
                        }
                    }

                    JarvisMasterApp(
                        initialScreen = activeScreenState.value,
                        hasOverlayPermission = hasOverlayState.value,
                        hasNotificationAccess = hasNotificationState.value,
                        hasAccessibilityPermission = hasAccessibilityState.value,
                        onRequestOverlayPermission = { requestOverlayPermission() },
                        onRequestNotificationAccess = { requestNotificationAccess() },
                        onRequestAccessibilityPermission = { requestAccessibilityPermission() },
                        onToggleExpand = { triggerBroadcast(JarvisIslandSettings.ACTION_TOGGLE_EXPAND) },
                        onExpand = { triggerBroadcast(JarvisIslandSettings.ACTION_EXPAND) },
                        onCollapse = { triggerBroadcast(JarvisIslandSettings.ACTION_COLLAPSE) },
                        onBeamFile = { beamFilePickerLauncher.launch("*/*") },
                        onOpenReceivedFiles = { FileBeamReceiver.openJarvisDownloadDirectory(this@MainActivity) },
                        onSimulateMusic = { triggerBroadcast(JarvisIslandSettings.ACTION_TEST_MUSIC) },
                        onSyncClipboard = {
                            val clipMgr = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                            val clipText = clipMgr?.primaryClip
                                ?.takeIf { it.itemCount > 0 }
                                ?.getItemAt(0)?.text?.toString()
                            if (!clipText.isNullOrEmpty()) {
                                bridgeClient.syncClipboardToDesktop(clipText)
                                android.widget.Toast.makeText(this, "Clipboard synchronized with desktop", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                android.widget.Toast.makeText(this, "Clipboard is empty", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        onRestartService = { startOverlayService() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hasOverlayState.value = hasOverlayPermission()
        hasNotificationState.value = hasNotificationAccess()
        hasAccessibilityState.value = hasAccessibilityPermission()
        if (hasOverlayState.value && JarvisIslandSettings.instance.isIslandEnabled) {
            startOverlayService()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val target = intent.getStringExtra("open_screen")
        if (!target.isNullOrEmpty()) {
            activeScreenState.value = target
        }
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true
    }

    private fun hasNotificationAccess(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(packageName)
    }

    private fun hasAccessibilityPermission(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabled != null && enabled.contains(packageName)
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

    private fun requestNotificationAccess() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        startActivity(intent)
    }

    private fun requestAccessibilityPermission() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    private fun triggerBroadcast(action: String) {
        val intent = Intent(action).setPackage(packageName)
        sendBroadcast(intent)
    }

    private fun startOverlayService() {
        if (JarvisIslandAccessibilityService.isRunning) return
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
 * dedicated reset buttons for every setting, and zero emojis.
 */
@Composable
fun JarvisMasterApp(
    initialScreen: String = "home",
    hasOverlayPermission: Boolean,
    hasNotificationAccess: Boolean,
    hasAccessibilityPermission: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onRequestNotificationAccess: () -> Unit,
    onRequestAccessibilityPermission: () -> Unit,
    onToggleExpand: () -> Unit,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onBeamFile: () -> Unit,
    onOpenReceivedFiles: () -> Unit = {},
    onSimulateMusic: () -> Unit,
    onSyncClipboard: () -> Unit,
    onRestartService: () -> Unit
) {
    val lockContext = LocalContext.current
    var unlocked by remember { mutableStateOf(!JarvisSecurity.shouldLock(lockContext)) }
    if (!unlocked) {
        JarvisLockScreen(onUnlock = { unlocked = true })
        return
    }

    val haptic = LocalHapticFeedback.current
    val settings = remember { JarvisIslandSettings.instance }
    var currentScreen by remember(initialScreen) { mutableStateOf(initialScreen) } // "home", "plugins", "settings", "about", "trackpad"
    var settingsSubPage by remember { mutableStateOf<String?>(null) } // "position", "size", "themes", "behavior", "apps"

    BackHandler(enabled = settingsSubPage != null || currentScreen == "trackpad") {
        if (settingsSubPage != null) {
            settingsSubPage = null
        } else {
            currentScreen = "home"
        }
    }

    val tabs = listOf(
        AppleTab("home", "Home", JarvisIcons.House),
        AppleTab("plugins", "Features", JarvisIcons.Puzzle),
        AppleTab("settings", "Settings", JarvisIcons.Gear),
        AppleTab("about", "About", JarvisIcons.Info)
    )

    Scaffold(
        containerColor = Apple.background,
        bottomBar = {
            if (currentScreen != "trackpad") {
                AppleTabBar(
                    tabs = tabs,
                    selectedId = currentScreen,
                    onSelect = { id ->
                        currentScreen = id
                        settingsSubPage = null
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Apple.background)
        ) {
            AnimatedContent(
                targetState = currentScreen to settingsSubPage,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "screenTransition"
            ) { (screen, subPage) ->
                when {
                    screen == "trackpad" -> {
                        RemoteTrackpad(onClose = { currentScreen = "home" })
                    }
                    screen == "home" -> {
                        HomeScreen(
                            hasOverlayPermission = hasOverlayPermission,
                            hasNotificationAccess = hasNotificationAccess,
                            hasAccessibilityPermission = hasAccessibilityPermission,
                            onRequestOverlayPermission = onRequestOverlayPermission,
                            onRequestNotificationAccess = onRequestNotificationAccess,
                            onRequestAccessibilityPermission = onRequestAccessibilityPermission,
                            onToggleExpand = onToggleExpand,
                            onBeamFile = onBeamFile,
                            onOpenReceivedFiles = onOpenReceivedFiles,
                            onSimulateMusic = onSimulateMusic,
                            onSyncClipboard = onSyncClipboard,
                            onRestartService = onRestartService,
                            onOpenTrackpad = { currentScreen = "trackpad" },
                            onOpenAssistant = {
                                currentScreen = "settings"
                                settingsSubPage = "automation"
                            }
                        )
                    }
                    screen == "plugins" -> {
                        PluginsScreen(onOpenTrackpad = { currentScreen = "trackpad" })
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
                    screen == "settings" && subPage == "security" -> {
                        SecurityScreen(
                            onBack = { settingsSubPage = null },
                            onOpenCalibration = { settingsSubPage = "calibration" },
                            onOpenVault = { settingsSubPage = "vault" }
                        )
                    }
                    screen == "settings" && subPage == "calibration" -> {
                        CalibrationScreen(onBack = { settingsSubPage = "security" })
                    }
                    screen == "settings" && subPage == "vault" -> {
                        VaultScreen(onBack = { settingsSubPage = "security" })
                    }
                    screen == "settings" && subPage == "voice" -> {
                        VoiceSettingsScreen(onBack = { settingsSubPage = null })
                    }
                    screen == "settings" && subPage == "apps" -> {
                        NotificationSettingsScreen(onBack = { settingsSubPage = null })
                    }
                    screen == "settings" && subPage == "notifications" -> {
                        NotificationSettingsScreen(onBack = { settingsSubPage = null })
                    }
                    screen == "settings" && subPage == "automation" -> {
                        AutomationScreen(onBack = { settingsSubPage = null })
                    }
                    screen == "settings" && subPage == "cloudai" -> {
                        CloudAiScreen(onBack = { settingsSubPage = null })
                    }
                    screen == "about" -> {
                        AboutScreen()
                    }
                    else -> {
                        HomeScreen(
                            hasOverlayPermission = hasOverlayPermission,
                            hasNotificationAccess = hasNotificationAccess,
                            hasAccessibilityPermission = hasAccessibilityPermission,
                            onRequestOverlayPermission = onRequestOverlayPermission,
                            onRequestNotificationAccess = onRequestNotificationAccess,
                            onRequestAccessibilityPermission = onRequestAccessibilityPermission,
                            onToggleExpand = onToggleExpand,
                            onBeamFile = onBeamFile,
                            onOpenReceivedFiles = onOpenReceivedFiles,
                            onSimulateMusic = onSimulateMusic,
                            onSyncClipboard = onSyncClipboard,
                            onRestartService = onRestartService,
                            onOpenTrackpad = { currentScreen = "trackpad" },
                            onOpenAssistant = {
                                currentScreen = "settings"
                                settingsSubPage = "automation"
                            }
                        )
                    }
                }
            }
        }
    }
}

data class NavigationItem(val id: String, val label: String, val icon: ImageVector)

// ══════════════════════════════════════════════════════════════════════
// SCREEN 1: HOME SCREEN
// ══════════════════════════════════════════════════════════════════════
@Composable
fun HomeScreen(
    hasOverlayPermission: Boolean,
    hasNotificationAccess: Boolean,
    hasAccessibilityPermission: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onRequestNotificationAccess: () -> Unit,
    onRequestAccessibilityPermission: () -> Unit,
    onToggleExpand: () -> Unit,
    onBeamFile: () -> Unit,
    onOpenReceivedFiles: () -> Unit = {},
    onSimulateMusic: () -> Unit,
    onSyncClipboard: () -> Unit,
    onRestartService: () -> Unit,
    onOpenTrackpad: () -> Unit = {},
    onOpenAssistant: () -> Unit = {}
) {
    val context = LocalContext.current
    val settings = remember { JarvisIslandSettings.instance }
    val scrollState = rememberScrollState()

    var bridgeTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(2000)
            bridgeTick += 1
        }
    }
    val bridgeClient = JarvisLiveBridgeClient.activeClient
    val bridgeConnected = bridgeClient?.isConnected == true
    val desktopAddress = bridgeClient?.desktopIp
    var showPairDialog by remember { mutableStateOf(false) }
    var pairingChallengeId by remember { mutableStateOf<String?>(null) }
    var pinInput by remember { mutableStateOf("") }
    var pairStatus by remember { mutableStateOf("Waiting for the desktop to show a PIN.") }
    var scanStatus by remember { mutableStateOf<String?>(null) }
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (!contents.isNullOrBlank()) {
            pairFromQr(context, contents) { scanStatus = it }
        }
    }
    @Suppress("UNUSED_VARIABLE")
    val bridgePoll = bridgeTick

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        Spacer(Modifier.height(8.dp))
        AppleLargeTitle("JARVIS")
        Text(
            text = "Dynamic Island",
            style = AppleType.subheadline,
            color = Apple.secondaryLabel,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Apple.secondaryGroupedBackground)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IslandPreview()
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("JARVIS Island", style = AppleType.title3, color = Apple.label)
                    Text(
                        text = if (settings.isIslandEnabled) "Active" else "Off",
                        style = AppleType.footnote,
                        color = if (settings.isIslandEnabled) Apple.systemGreen else Apple.secondaryLabel,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                AppleSwitch(
                    checked = settings.isIslandEnabled,
                    onCheckedChange = { enabled ->
                        settings.isIslandEnabled = enabled
                        settings.save(context)
                        if (enabled) {
                            onRestartService()
                        } else {
                            context.stopService(Intent(context, JarvisIslandOverlayService::class.java))
                        }
                    }
                )
            }
            Spacer(Modifier.height(14.dp))
            AppleButton(text = "Preview Island", onClick = onToggleExpand)
        }

        AppleSection(header = "Permissions") {
            PermissionRow(
                title = "Overlay",
                subtitle = "Draw the island over other apps",
                granted = hasOverlayPermission,
                icon = JarvisIcons.Display,
                tint = Apple.systemBlue,
                onClick = onRequestOverlayPermission
            )
            AppleDivider()
            PermissionRow(
                title = "Media & Notifications",
                subtitle = "Show now playing and incoming alerts",
                granted = hasNotificationAccess,
                icon = JarvisIcons.Bell,
                tint = Apple.systemRed,
                onClick = onRequestNotificationAccess
            )
            AppleDivider()
            PermissionRow(
                title = "Accessibility",
                subtitle = "Enable island gestures and remote control",
                granted = hasAccessibilityPermission,
                icon = JarvisIcons.HandTap,
                tint = Apple.systemIndigo,
                onClick = onRequestAccessibilityPermission
            )
        }

        AppleSection(header = "Quick Actions") {
            AppleRow(
                title = "Open Island",
                icon = JarvisIcons.Island,
                iconTint = Apple.systemBlue,
                showChevron = true,
                onClick = onToggleExpand
            )
            AppleDivider()
            AppleRow(
                title = "Beam file to PC",
                subtitle = "Select any file from this phone to send to desktop",
                icon = JarvisIcons.Beam,
                iconTint = Apple.systemTeal,
                showChevron = true,
                onClick = onBeamFile
            )
            AppleDivider()
            AppleRow(
                title = "Open Received Files",
                subtitle = "Downloads/JARVIS folder",
                icon = JarvisIcons.DownloadDoc,
                iconTint = Apple.systemBlue,
                showChevron = true,
                onClick = onOpenReceivedFiles
            )
            AppleDivider()
            AppleRow(
                title = "Sync clipboard",
                icon = JarvisIcons.Clipboard,
                iconTint = Apple.systemGreen,
                showChevron = true,
                onClick = onSyncClipboard
            )
            AppleDivider()
            AppleRow(
                title = "Remote trackpad",
                icon = JarvisIcons.Mouse,
                iconTint = Apple.systemIndigo,
                showChevron = true,
                onClick = onOpenTrackpad
            )
            AppleDivider()
            AppleRow(
                title = "Preview music activity",
                icon = JarvisIcons.MusicNote,
                iconTint = Apple.systemPink,
                showChevron = true,
                onClick = onSimulateMusic
            )
        }

        AppleSection(
            header = "Activity & Screen Time",
            footer = "Tracks daily screen usage and Instagram Reels locally on this phone with zero cloud transmission."
        ) {
            val reelsToday = com.jarvis.island.automation.ReelCounter.today(context)
            val reelsTotal = com.jarvis.island.automation.ReelCounter.total(context)
            val screenTimeMins = com.jarvis.island.automation.ScreenTimeTracker.getTodayTotalMinutes(context)
            val topApps = remember { com.jarvis.island.automation.ScreenTimeTracker.getTodayTopApps(context) }

            AppleRow(
                title = "Screen Time Today",
                subtitle = if (screenTimeMins > 0) "Active device usage" else "Tracking active",
                value = com.jarvis.island.automation.ScreenTimeTracker.formatDuration(screenTimeMins),
                icon = JarvisIcons.Display,
                iconTint = Apple.systemBlue
            )

            if (topApps.isNotEmpty()) {
                AppleDivider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        "Top Apps Today",
                        style = AppleType.footnote,
                        color = Apple.secondaryLabel
                    )
                    Spacer(Modifier.height(8.dp))
                    val appColors = listOf(Apple.systemBlue, Apple.systemOrange, Apple.systemTeal, Apple.systemPurple)
                    topApps.forEachIndexed { idx, app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = app.appName,
                                style = AppleType.subheadline,
                                color = Apple.label,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = com.jarvis.island.automation.ScreenTimeTracker.formatDuration(app.totalMinutes),
                                style = AppleType.subheadline,
                                color = Apple.secondaryLabel
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Apple.fillTertiary)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction = app.percentage)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(appColors[idx % appColors.size])
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }

            AppleDivider()
            AppleRow(
                title = "Instagram Reels",
                subtitle = if (settings.reelCounterEnabled) "Watched today" else "Counter is off",
                value = if (settings.reelCounterEnabled) "$reelsToday" else "—",
                icon = JarvisIcons.HeartFill,
                iconTint = Apple.systemPink
            )
            AppleDivider()
            AppleRow(
                title = "Lifetime reels",
                value = if (settings.reelCounterEnabled) "$reelsTotal" else "—",
                icon = JarvisIcons.PlayFill,
                iconTint = Apple.systemPurple
            )
            if (settings.reelCounterEnabled) {
                AppleDivider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Text(
                        "Last 7 days",
                        style = AppleType.footnote,
                        color = Apple.secondaryLabel
                    )
                    Spacer(Modifier.height(10.dp))
                    ReelWeekChart(
                        days = com.jarvis.island.automation.ReelCounter.last7Days(context)
                    )
                }
            }
            AppleDivider()
            AppleRow(
                title = "Reset today's stats",
                icon = JarvisIcons.ArrowClockwise,
                iconTint = Apple.systemOrange,
                destructive = false,
                onClick = {
                    com.jarvis.island.automation.ReelCounter.resetToday(context)
                    com.jarvis.island.automation.ScreenTimeTracker.resetToday(context)
                    android.widget.Toast.makeText(
                        context,
                        "Today's stats reset",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }

        AppleSection(
            header = "Desktop",
            footer = "Pair with the JARVIS desktop app on the same Wi-Fi. The desktop shows the PIN and its certificate fingerprint."
        ) {
            AppleRow(
                title = if (bridgeConnected) "JARVIS Desktop" else "Pair with PC",
                subtitle = if (bridgeConnected) (desktopAddress ?: "Connected") else "Find and pair your desktop",
                icon = JarvisIcons.Laptop,
                iconTint = Apple.systemBlue,
                trailingIcon = if (bridgeConnected) JarvisIcons.CheckCircleFill else JarvisIcons.ChevronRight,
                trailingTint = if (bridgeConnected) Apple.systemGreen else Apple.tertiaryLabel,
                onClick = {
                    pinInput = ""
                    pairingChallengeId = null
                    pairStatus = "Waiting for the desktop to show a PIN."
                    showPairDialog = true
                    JarvisLiveBridgeClient.activeClient?.requestPairing { id ->
                        pairingChallengeId = id
                        if (id == null) {
                            pairStatus = "No desktop found. Keep both devices on the same Wi-Fi with the desktop app open."
                        }
                    }
                }
            )
            AppleDivider()
            AppleRow(
                title = "Scan QR code",
                subtitle = "Scan the code shown on the desktop to pair instantly",
                icon = JarvisIcons.QrCode,
                iconTint = Apple.systemTeal,
                showChevron = true,
                onClick = {
                    scanLauncher.launch(
                        ScanOptions().apply {
                            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            setPrompt("Scan the JARVIS pairing code")
                            setBeepEnabled(false)
                            setOrientationLocked(false)
                        }
                    )
                }
            )
            AppleDivider()
            AppleRow(
                title = "Forget desktop",
                subtitle = "Clear the pinned certificate and pairing",
                icon = JarvisIcons.Shield,
                iconTint = Apple.systemRed,
                destructive = true,
                onClick = {
                    JarvisLiveBridgeClient.activeClient?.forgetDesktop()
                        ?: run {
                            BridgeTls.clearDesktopPin(context)
                            FileBeamReceiver.clearPairedToken(context)
                        }
                    pairStatus = "Desktop forgotten."
                }
            )
        }

        if (scanStatus != null) {
            Text(
                text = scanStatus!!,
                style = AppleType.footnote,
                color = Apple.secondaryLabel,
                modifier = Modifier.padding(start = 36.dp, end = 36.dp, top = 7.dp)
            )
        }

        AppleSection(
            header = "Assistant",
            footer = "Scenes, reminders and notes are stored on this phone. The desktop model is only used when the on-device brain needs it."
        ) {
            val nextReminder = remember(bridgeTick) {
                com.jarvis.island.automation.Notebook.nextReminder(context)
            }
            val noteCount = remember(bridgeTick) {
                com.jarvis.island.automation.Notebook.notes(context).size
            }
            AppleRow(
                title = "Next reminder",
                subtitle = nextReminder?.text ?: "Nothing scheduled",
                value = nextReminder?.let {
                    com.jarvis.island.automation.TimeParser.format(it.triggerAtMs)
                } ?: "—",
                icon = JarvisIcons.Alarm,
                iconTint = Apple.systemOrange
            )
            AppleDivider()
            AppleRow(
                title = "Notes",
                subtitle = if (noteCount == 0) "No notes yet" else "$noteCount saved",
                icon = JarvisIcons.Note,
                iconTint = Apple.systemYellow
            )
            AppleDivider()
            AppleRow(
                title = "Open Assistant",
                subtitle = "Brain, readiness, scenes and safety",
                icon = JarvisIcons.Sparkles,
                iconTint = Apple.systemPurple,
                showChevron = true,
                onClick = onOpenAssistant
            )
        }

        AppleSection(header = "Device") {
            AppleRow(
                title = "Model",
                value = Build.MANUFACTURER + " " + Build.MODEL
            )
            AppleDivider()
            AppleRow(
                title = "System",
                value = "Android " + Build.VERSION.RELEASE
            )
            if (settings.isIslandEnabled) {
                AppleDivider()
                AppleRow(
                    title = "Island service",
                    icon = JarvisIcons.BoltFill,
                    iconTint = Apple.systemGreen,
                    value = "Running"
                )
            }
        }

        Spacer(Modifier.height(28.dp))
    }

    if (showPairDialog) {
        AlertDialog(
            onDismissRequest = { showPairDialog = false },
            containerColor = Apple.secondaryGroupedBackground,
            titleContentColor = Apple.label,
            textContentColor = Apple.secondaryLabel,
            title = {
                Text("Pair with JARVIS Desktop", style = AppleType.headline, color = Apple.label)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(pairStatus, style = AppleType.footnote, color = Apple.secondaryLabel)
                    BridgeTls.desktopFingerprint(context)?.let { fp ->
                        Text(
                            "Desktop certificate: " + fp.take(8) + "...",
                            style = AppleType.caption1,
                            color = Apple.secondaryLabel
                        )
                    }
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it.filter(Char::isDigit).take(6) },
                        label = { Text("6-digit PIN") },
                        singleLine = true
                    )
                    TextButton(
                        onClick = {
                            JarvisLiveBridgeClient.activeClient?.forgetDesktop()
                                ?: run {
                                    BridgeTls.clearDesktopPin(context)
                                    FileBeamReceiver.clearPairedToken(context)
                                }
                            pairingChallengeId = null
                            pairStatus = "Desktop forgotten. Searching for a new beacon..."
                        }
                    ) {
                        Text("Forget desktop and re-pin", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = pinInput.length == 6 && pairingChallengeId != null,
                    onClick = {
                        val challenge = pairingChallengeId ?: return@TextButton
                        pairStatus = "Verifying PIN..."
                        JarvisLiveBridgeClient.activeClient?.confirmPairing(challenge, pinInput) { paired ->
                            if (paired) {
                                pairStatus = "Paired."
                                showPairDialog = false
                            } else {
                                pairStatus = "Incorrect or expired PIN. Start pairing again."
                            }
                        }
                    }
                ) { Text("Pair") }
            },
            dismissButton = {
                TextButton(onClick = { showPairDialog = false }) { Text("Cancel") }
            }
        )
    }
}

private fun pairFromQr(context: Context, contents: String, onStatus: (String) -> Unit) {
    try {
        val json = JSONObject(contents)
        if (json.optString("type") != "jarvis_pair") {
            onStatus("This is not a JARVIS pairing code.")
            return
        }
        val ip = json.optString("ip").trim()
        val port = json.optInt("port", 53391)
        val fingerprint = json.optString("fp").trim()
        val challengeId = json.optString("challengeId").trim()
        val pin = json.optString("pin").trim()
        val expiresAt = json.optLong("expiresAtMs", 0L)
        if (ip.isEmpty() || fingerprint.isEmpty() || challengeId.isEmpty() || pin.isEmpty()) {
            onStatus("The QR code is incomplete.")
            return
        }
        if (expiresAt > 0 && System.currentTimeMillis() > expiresAt) {
            onStatus("This QR code has expired. Show a new one on the desktop.")
            return
        }
        val client = JarvisLiveBridgeClient.activeClient
        if (client == null) {
            onStatus("JARVIS is still starting. Try again.")
            return
        }
        onStatus("Connecting to $ip…")
        client.adoptDesktopEndpoint(ip, port, fingerprint)
        client.confirmPairing(challengeId, pin) { paired ->
            onStatus(
                if (paired) "Paired with the desktop."
                else "Pairing failed. Show a new QR code on the desktop."
            )
        }
    } catch (_: Exception) {
        onStatus("Could not read the QR code.")
    }
}

@Composable
private fun PermissionRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    AppleRow(
        title = title,
        subtitle = if (granted) subtitle else subtitle + " - required",
        icon = icon,
        iconTint = tint,
        trailingIcon = if (granted) JarvisIcons.CheckCircleFill else JarvisIcons.XCircleFill,
        trailingTint = if (granted) Apple.systemGreen else Apple.systemOrange,
        onClick = if (granted) null else onClick
    )
}

@Composable
private fun IslandPreview() {
    Box(
        modifier = Modifier
            .width(96.dp)
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(Color.Black)
            .border(0.5.dp, Apple.quaternary, RoundedCornerShape(15.dp)),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(Apple.systemGreen)
            )
            Text("9:41", style = AppleType.caption1, color = Apple.label)
            Box(
                modifier = Modifier
                    .width(12.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Apple.systemGreen)
            )
        }
    }
}

@Composable
fun PluginsScreen(onOpenTrackpad: () -> Unit = {}) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        Spacer(Modifier.height(8.dp))
        AppleLargeTitle("Features")
        Text(
            text = "Everything the island can do",
            style = AppleType.subheadline,
            color = Apple.secondaryLabel,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        AppleSection(header = "Live Activities") {
            AppleRow(
                title = "Now Playing",
                subtitle = "Album art, transport controls and progress",
                icon = JarvisIcons.MusicNote,
                iconTint = Apple.systemPink
            )
            AppleDivider()
            AppleRow(
                title = "Live Lyrics",
                subtitle = "Synced lines highlight as the song plays",
                icon = JarvisIcons.Waveform,
                iconTint = Apple.systemPurple
            )
            AppleDivider()
            AppleRow(
                title = "Notifications",
                subtitle = "Incoming alerts appear inside the island",
                icon = JarvisIcons.Bell,
                iconTint = Apple.systemRed
            )
            AppleDivider()
            AppleRow(
                title = "Focus Timer",
                subtitle = "Pomodoro and stopwatch live in the pill",
                icon = JarvisIcons.Timer,
                iconTint = Apple.systemOrange
            )
            AppleDivider()
            AppleRow(
                title = "Charging",
                subtitle = "Battery state while the device charges",
                icon = JarvisIcons.BoltFill,
                iconTint = Apple.systemGreen
            )
        }

        AppleSection(
            header = "Continuity",
            footer = "These features activate automatically when the island is running."
        ) {
            AppleRow(
                title = "Universal Clipboard",
                subtitle = "Copy on one device, paste on the other",
                icon = JarvisIcons.Clipboard,
                iconTint = Apple.systemGreen
            )
            AppleDivider()
            AppleRow(
                title = "File Beam",
                subtitle = "Send files to and from your PC over the LAN",
                icon = JarvisIcons.Beam,
                iconTint = Apple.systemTeal
            )
            AppleDivider()
            AppleRow(
                title = "Remote Trackpad",
                subtitle = "Control the Windows pointer from your phone",
                icon = JarvisIcons.Mouse,
                iconTint = Apple.systemIndigo,
                showChevron = true,
                onClick = onOpenTrackpad
            )
        }

        AppleSection(header = "Privacy") {
            AppleRow(
                title = "On-device processing",
                subtitle = "Island state and media data stay on this device",
                icon = JarvisIcons.Shield,
                iconTint = Apple.systemBlue
            )
            AppleDivider()
            AppleRow(
                title = "Pinned connections",
                subtitle = "The PC link is encrypted and certificate-pinned",
                icon = JarvisIcons.Globe,
                iconTint = Apple.systemGreen
            )
        }

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
fun SettingsHubScreen(onNavigateSubPage: (String) -> Unit) {
    val settings = remember { JarvisIslandSettings.instance }
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        Spacer(Modifier.height(8.dp))
        AppleLargeTitle("Settings")
        Text(
            text = "Appearance and behavior",
            style = AppleType.subheadline,
            color = Apple.secondaryLabel,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        AppleSection(header = "Island") {
            AppleRow(
                title = "Position & Clearance",
                subtitle = "Gravity, camera clearance and offsets",
                icon = JarvisIcons.Move,
                iconTint = Apple.systemIndigo,
                value = settings.gravity,
                showChevron = true,
                onClick = { onNavigateSubPage("position") }
            )
            AppleDivider()
            AppleRow(
                title = "Size & Shape",
                subtitle = "Pill dimensions in density-independent pixels",
                icon = JarvisIcons.ArrowsUpDown,
                iconTint = Apple.systemBlue,
                value = settings.pillWidth.toString() + " x " + settings.pillHeight.toString(),
                showChevron = true,
                onClick = { onNavigateSubPage("size") }
            )
            AppleDivider()
            AppleRow(
                title = "Theme",
                subtitle = "Palette and accent color",
                icon = JarvisIcons.Palette,
                iconTint = Apple.systemPink,
                value = settings.themeStyle,
                showChevron = true,
                onClick = { onNavigateSubPage("themes") }
            )
        }

        AppleSection(header = "Behavior") {
            AppleRow(
                title = "Gestures & Behavior",
                subtitle = "Haptics, expansion and auto-collapse",
                icon = JarvisIcons.Sliders,
                iconTint = Apple.systemOrange,
                showChevron = true,
                onClick = { onNavigateSubPage("behavior") }
            )
        }

        AppleSection(header = "Intelligence") {
            AppleRow(
                title = "Assistant",
                subtitle = "Brain, readiness, scenes, reminders and notes",
                icon = JarvisIcons.Sparkles,
                iconTint = Apple.systemPurple,
                value = when (settings.aiBrain) {
                    "local" -> "On-device"
                    "remote" -> "Desktop"
                    else -> "Auto"
                },
                showChevron = true,
                onClick = { onNavigateSubPage("automation") }
            )
            AppleDivider()
            AppleRow(
                title = "Cloud API Keys",
                subtitle = "Groq and OpenRouter directly from this phone",
                icon = JarvisIcons.Globe,
                iconTint = Apple.systemTeal,
                value = if (com.jarvis.island.ai.SecureKeyStore.hasCloudKeys(context)) "On"
                else "Off",
                showChevron = true,
                onClick = { onNavigateSubPage("cloudai") }
            )
            AppleDivider()
            AppleRow(
                title = "Notifications",
                subtitle = "Per-app alerts, triage and code copy",
                icon = JarvisIcons.Bell,
                iconTint = Apple.systemRed,
                value = if (settings.blockedPackages.isEmpty()) "All"
                else "${settings.blockedPackages.size} muted",
                showChevron = true,
                onClick = { onNavigateSubPage("notifications") }
            )
        }

        AppleSection(header = "Voice") {
            AppleRow(
                title = "Hey JARVIS & AI",
                subtitle = "Offline wake word with desktop AI replies",
                icon = JarvisIcons.Waveform,
                iconTint = Apple.systemPurple,
                value = if (com.jarvis.island.voice.VoicePrefs.isWakeWordEnabled(context)) "On" else "Off",
                showChevron = true,
                onClick = { onNavigateSubPage("voice") }
            )
        }

        AppleSection(header = "Security") {
            AppleRow(
                title = "App Lock",
                subtitle = "Pattern, fingerprint and voice unlock",
                icon = JarvisIcons.Shield,
                iconTint = Apple.systemBlue,
                value = if (JarvisSecurity.isAppLockEnabled(context)) "On" else "Off",
                showChevron = true,
                onClick = { onNavigateSubPage("security") }
            )
        }

        AppleSection(
            header = "About",
            footer = "JARVIS " + BuildConfig.VERSION_NAME
        ) {
            AppleRow(
                title = "Privacy",
                subtitle = "No account, no cloud, no tracking",
                icon = JarvisIcons.Shield,
                iconTint = Apple.systemBlue
            )
        }

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
fun PositionSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
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
        SubPageHeader(
            title = "Position & Clearance",
            onBack = onBack,
            onResetSection = {
                if (settings.hapticFeedback) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                settings.resetPosition(context)
                gravity = settings.gravity
                cutoutClearance = settings.cutoutClearance
                positionY = settings.positionY
                positionX = settings.positionX
            }
        )

        // Motorola Edge 50 Pro Hardware Profile Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Motorola Edge 50 Pro Calibration", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                    Text(text = "1220×2712 • 446 PPI", color = Color(0xFF0A84FF), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    text = "Punch-hole centered at X: 610 px, Y: 52 px (48-52 px diameter). Pixel-perfect 2 px top margin clearance.",
                    color = Color(0xFF8E8E93),
                    fontSize = 11.sp
                )
                Button(
                    onClick = {
                        if (settings.hapticFeedback) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        settings.applyMotoEdge50ProPreset(context)
                        gravity = settings.gravity
                        cutoutClearance = settings.cutoutClearance
                        positionY = settings.positionY
                        positionX = settings.positionX
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A84FF))
                ) {
                    Text(text = "Apply Motorola Edge 50 Pro Preset", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

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
                        .border(1.dp, Color(0xFF2C2C2E), RoundedCornerShape(12.dp)),
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
                            .padding(top = (positionY * 0.8f).coerceAtLeast(0f).dp)
                            .offset(x = positionX.dp)
                            .width((settings.pillWidth * 0.8f).dp)
                            .height((settings.pillHeight * 0.8f).dp)
                            .clip(RoundedCornerShape((settings.pillHeight * 0.8f / 2).dp))
                            .background(Color.Black.copy(alpha = 0.85f))
                            .border(1.dp, Color(0xFF0A84FF).copy(alpha = 0.5f), RoundedCornerShape((settings.pillHeight * 0.8f / 2).dp))
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(Color(0xFF0A84FF)))
                            Spacer(modifier = Modifier.width((cutoutClearance * 0.8f).dp))
                            Text("JARVIS", color = Color(0xFF0A84FF), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(text = "The camera band mirrors Apple's 125 dp TrueDepth gap; content lives in the 52 dp wings on either side.", color = Color(0xFF636366), fontSize = 10.5.sp)
            }
        }

        // Sliders with Individual Reset Buttons
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
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
                                .background(if (active) Color(0xFF0A84FF) else Color(0xFF2C2C2E))
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
                    label = "Camera Band (TrueDepth Gap)",
                    value = cutoutClearance.toFloat(),
                    valueText = "${cutoutClearance} dp",
                    range = 40f..160f,
                    onReset = {
                        cutoutClearance = JarvisIslandSettings.DEFAULT_CUTOUT_CLEARANCE
                        settings.cutoutClearance = cutoutClearance
                        settings.save(context)
                    },
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
                    onReset = {
                        positionY = JarvisIslandSettings.DEFAULT_POSITION_Y
                        settings.positionY = positionY
                        settings.save(context)
                    },
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
                    onReset = {
                        positionX = JarvisIslandSettings.DEFAULT_POSITION_X
                        settings.positionX = positionX
                        settings.save(context)
                    },
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
    val haptic = LocalHapticFeedback.current
    val settings = remember { JarvisIslandSettings.instance }
    val scrollState = rememberScrollState()

    var pillWidth by remember { mutableIntStateOf(settings.pillWidth) }
    var pillHeight by remember { mutableIntStateOf(settings.pillHeight) }
    var expandedWidth by remember { mutableIntStateOf(settings.expandedWidth) }
    var expandedHeight by remember { mutableIntStateOf(settings.expandedHeight) }
    var expandedCornerRadius by remember { mutableIntStateOf(settings.expandedCornerRadius) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SubPageHeader(
            title = "Size & Geometry",
            onBack = onBack,
            onResetSection = {
                if (settings.hapticFeedback) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                settings.resetSize(context)
                pillWidth = settings.pillWidth
                pillHeight = settings.pillHeight
                expandedWidth = settings.expandedWidth
                expandedHeight = settings.expandedHeight
                expandedCornerRadius = settings.expandedCornerRadius
            }
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SliderRow(
                    label = "Collapsed Pill Width",
                    value = pillWidth.toFloat(),
                    valueText = "${pillWidth} dp",
                    range = 140f..320f,
                    onReset = {
                        pillWidth = JarvisIslandSettings.DEFAULT_PILL_WIDTH
                        settings.pillWidth = pillWidth
                        settings.save(context)
                    },
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
                    onReset = {
                        pillHeight = JarvisIslandSettings.DEFAULT_PILL_HEIGHT
                        settings.pillHeight = pillHeight
                        settings.save(context)
                    },
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
                    range = 300f..420f,
                    onReset = {
                        expandedWidth = JarvisIslandSettings.DEFAULT_EXPANDED_WIDTH
                        settings.expandedWidth = expandedWidth
                        settings.save(context)
                    },
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
                    range = 160f..220f,
                    onReset = {
                        expandedHeight = JarvisIslandSettings.DEFAULT_EXPANDED_HEIGHT
                        settings.expandedHeight = expandedHeight
                        settings.save(context)
                    },
                    onValueChange = {
                        expandedHeight = it.toInt()
                        settings.expandedHeight = expandedHeight
                        settings.save(context)
                    }
                )

                SliderRow(
                    label = "Expanded Corner Radius",
                    value = expandedCornerRadius.toFloat(),
                    valueText = "${expandedCornerRadius} dp",
                    range = 30f..56f,
                    onReset = {
                        expandedCornerRadius = JarvisIslandSettings.DEFAULT_EXPANDED_CORNER_RADIUS
                        settings.expandedCornerRadius = expandedCornerRadius
                        settings.save(context)
                    },
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
    val haptic = LocalHapticFeedback.current
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
        SubPageHeader(
            title = "Themes & Styling",
            onBack = onBack,
            onResetSection = {
                if (settings.hapticFeedback) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                settings.resetThemes(context)
                themeStyle = settings.themeStyle
                showBorders = settings.showBorders
                borderGlow = settings.borderGlow
            }
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
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
                            .background(if (active) Color(0xFF2C2C2E) else Color(0xFF0F172A))
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
    val haptic = LocalHapticFeedback.current
    val settings = remember { JarvisIslandSettings.instance }
    val scrollState = rememberScrollState()

    var showOnLockScreen by remember { mutableStateOf(settings.showOnLockScreen) }
    var showInLandscape by remember { mutableStateOf(settings.showInLandscape) }
    var autoExpandOnTransfer by remember { mutableStateOf(settings.autoExpandOnTransfer) }
    var autoCollapseDelaySec by remember { mutableFloatStateOf(settings.autoCollapseDelaySec) }
    var hapticFeedback by remember { mutableStateOf(settings.hapticFeedback) }
    var longPressToExpand by remember { mutableStateOf(settings.longPressToExpand) }
    var filterSilentNotifications by remember { mutableStateOf(settings.filterSilentNotifications) }
    var reelCounterEnabled by remember { mutableStateOf(settings.reelCounterEnabled) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SubPageHeader(
            title = "Behavior & Lock Screen",
            onBack = onBack,
            onResetSection = {
                if (settings.hapticFeedback) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                settings.resetBehavior(context)
                showOnLockScreen = settings.showOnLockScreen
                showInLandscape = settings.showInLandscape
                autoExpandOnTransfer = settings.autoExpandOnTransfer
                autoCollapseDelaySec = settings.autoCollapseDelaySec
                hapticFeedback = settings.hapticFeedback
                longPressToExpand = settings.longPressToExpand
                filterSilentNotifications = settings.filterSilentNotifications
                reelCounterEnabled = settings.reelCounterEnabled
            }
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
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
                    title = "Filter Silent Notifications",
                    description = "Quiet channel updates stay in the shade instead of popping the island.",
                    checked = filterSilentNotifications,
                    onCheckedChange = {
                        filterSilentNotifications = it
                        settings.filterSilentNotifications = it
                        settings.save(context)
                    }
                )

                SettingsSwitch(
                    title = "Count Instagram Reels",
                    description = "Adds a reel to today's counter when the reels viewer advances.",
                    checked = reelCounterEnabled,
                    onCheckedChange = {
                        reelCounterEnabled = it
                        settings.reelCounterEnabled = it
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
                    onReset = {
                        autoCollapseDelaySec = JarvisIslandSettings.DEFAULT_AUTO_COLLAPSE_DELAY
                        settings.autoCollapseDelaySec = autoCollapseDelaySec
                        settings.save(context)
                    },
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

// ══════════════════════════════════════════════════════════════════════
// SCREEN 4: ABOUT SCREEN
// ══════════════════════════════════════════════════════════════════════
@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val updater = remember { JarvisAppUpdater(context) }
    val updateState by updater.state.collectAsState()
    val version = BuildConfig.VERSION_NAME

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(26.dp))
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Apple.systemBlue),
            contentAlignment = Alignment.Center
        ) {
            JarvisIcon(JarvisIcons.Sparkles, Color.White, 40.dp)
        }
        Spacer(Modifier.height(12.dp))
        Text("JARVIS", style = AppleType.title1, color = Apple.label)
        Text(
            text = "Dynamic Island · " + version,
            style = AppleType.subheadline,
            color = Apple.secondaryLabel
        )

        AppleSection(header = "Software Update") {
            when (val state = updateState) {
                is UpdateState.Checking -> {
                    AppleRow(
                        title = "Checking for updates",
                        subtitle = "Contacting GitHub Releases",
                        icon = JarvisIcons.ArrowClockwise,
                        iconTint = Apple.systemBlue
                    )
                }
                is UpdateState.UpToDate -> {
                    AppleRow(
                        title = "You're up to date",
                        subtitle = "JARVIS " + version,
                        icon = JarvisIcons.CheckCircleFill,
                        iconTint = Apple.systemGreen
                    )
                    AppleDivider()
                    AppleRow(
                        title = "Check Again",
                        icon = JarvisIcons.ArrowClockwise,
                        iconTint = Apple.systemGray,
                        showChevron = true,
                        onClick = { updater.checkForUpdates() }
                    )
                }
                is UpdateState.Available -> {
                    AppleRow(
                        title = state.tag + " is available",
                        subtitle = "%.1f MB · verified download".format(state.sizeMb),
                        icon = JarvisIcons.ArrowClockwise,
                        iconTint = Apple.systemBlue
                    )
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        AppleButton(
                            text = "Download & Install",
                            icon = JarvisIcons.Beam,
                            onClick = { updater.startDownload(state.downloadUrl, state.checksumUrl) }
                        )
                    }
                }
                is UpdateState.Downloading -> {
                    val progress = state.progress
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Downloading", style = AppleType.body, color = Apple.label)
                            Text(
                                progress.percent.toString() + "%",
                                style = AppleType.body,
                                color = Apple.secondaryLabel
                            )
                        }
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
                                    .fillMaxWidth(progress.percent / 100f)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Apple.systemBlue)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "%.1f of %.1f MB".format(progress.downloadedMb, progress.totalMb),
                                style = AppleType.caption1,
                                color = Apple.secondaryLabel
                            )
                            Text(progress.speedMbps, style = AppleType.caption1, color = Apple.secondaryLabel)
                        }
                    }
                }
                is UpdateState.ReadyToInstall -> {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            JarvisIcon(JarvisIcons.CheckCircleFill, Apple.systemGreen, 20.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Download verified", style = AppleType.body, color = Apple.label)
                        }
                        Spacer(Modifier.height(10.dp))
                        AppleButton(
                            text = "Install Update",
                            icon = JarvisIcons.Sparkles,
                            onClick = { updater.installApk(state.apkFile) }
                        )
                    }
                }
                is UpdateState.Error -> {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(
                            text = state.message,
                            style = AppleType.footnote,
                            color = Apple.systemRed
                        )
                        Spacer(Modifier.height(10.dp))
                        AppleButton(
                            text = "Try Again",
                            primary = false,
                            onClick = { updater.checkForUpdates() }
                        )
                    }
                }
                else -> {
                    AppleRow(
                        title = "Check for Updates",
                        subtitle = "You are running JARVIS " + version,
                        icon = JarvisIcons.ArrowClockwise,
                        iconTint = Apple.systemBlue,
                        showChevron = true,
                        onClick = { updater.checkForUpdates() }
                    )
                }
            }
        }

        AppleSection(header = "About") {
            AppleRow(
                title = "Platform",
                subtitle = "Android companion with a Windows desktop island",
                icon = JarvisIcons.Laptop,
                iconTint = Apple.systemBlue
            )
            AppleDivider()
            AppleRow(
                title = "Privacy",
                subtitle = "No account, no cloud, no tracking",
                icon = JarvisIcons.Shield,
                iconTint = Apple.systemGreen
            )
            AppleDivider()
            AppleRow(
                title = "GitHub",
                subtitle = "Source, releases and issue tracker",
                icon = JarvisIcons.Globe,
                iconTint = Apple.systemGray,
                showChevron = true,
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/NandiVardhan2007/JARVIS"))
                    )
                }
            )
        }

        Text(
            text = "JARVIS uses Android overlay and notification access to draw Live Activities around the camera cutout. No personal data, messages or passwords are collected or uploaded.",
            style = AppleType.footnote,
            color = Apple.secondaryLabel,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 36.dp, vertical = 18.dp)
        )

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun SubPageHeader(title: String, onBack: () -> Unit, onResetSection: (() -> Unit)? = null) {
    AppleSubHeader(
        title = title,
        onBack = onBack,
        actionLabel = if (onResetSection != null) "Reset" else null,
        onAction = onResetSection
    )
}

@Composable
fun SliderRow(
    label: String,
    value: Float,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    onReset: (() -> Unit)? = null,
    onValueChange: (Float) -> Unit
) {
    AppleSliderRow(
        title = label,
        valueText = valueText,
        value = value.coerceIn(range.start, range.endInclusive),
        onValueChange = onValueChange,
        valueRange = range
    )
}

@Composable
fun SettingsSwitch(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    AppleSwitchRow(
        title = title,
        description = description,
        checked = checked,
        onCheckedChange = onCheckedChange
    )
}

@Composable
fun PermissionCard(
    title: String,
    description: String,
    buttonText: String,
    buttonColor: Color,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF181824)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Text(
                text = description,
                color = Color(0xFF8E8E93),
                fontSize = 11.5.sp
            )
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(buttonText, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}
