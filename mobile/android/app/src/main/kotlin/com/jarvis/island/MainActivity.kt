package com.jarvis.island

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.island.bridge.BridgeTls
import com.jarvis.island.bridge.FileBeamReceiver
import com.jarvis.island.bridge.JarvisLiveBridgeClient
import com.jarvis.island.ui.RemoteTrackpad
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
                        onSimulateBeam = { triggerBroadcast(JarvisIslandSettings.ACTION_TEST_BEAM) },
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
    onSimulateBeam: () -> Unit,
    onSimulateMusic: () -> Unit,
    onSyncClipboard: () -> Unit,
    onRestartService: () -> Unit
) {
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

    Scaffold(
        bottomBar = {
            if (currentScreen != "trackpad") {
                NavigationBar(
                    containerColor = Color(0xFF0D0D14),
                    contentColor = Color(0xFF0A84FF)
                ) {
                    listOf(
                        NavigationItem("home", "Home", Icons.Rounded.Home),
                        NavigationItem("plugins", "Plugins", Icons.Rounded.Extension),
                        NavigationItem("settings", "Settings", Icons.Rounded.Settings),
                        NavigationItem("about", "About", Icons.Rounded.Info)
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
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = item.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF0A84FF),
                                selectedTextColor = Color(0xFF0A84FF),
                                unselectedIconColor = Color(0xFF8E8E93),
                                unselectedTextColor = Color(0xFF636366),
                                indicatorColor = Color(0x2238BDF8)
                            )
                        )
                    }
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
                            onSimulateBeam = onSimulateBeam,
                            onSimulateMusic = onSimulateMusic,
                            onSyncClipboard = onSyncClipboard,
                            onRestartService = onRestartService,
                            onOpenTrackpad = { currentScreen = "trackpad" }
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
                    screen == "settings" && subPage == "apps" -> {
                        EnabledAppsSettingsScreen(onBack = { settingsSubPage = null })
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
                            onSimulateBeam = onSimulateBeam,
                            onSimulateMusic = onSimulateMusic,
                            onSyncClipboard = onSyncClipboard,
                            onRestartService = onRestartService,
                            onOpenTrackpad = { currentScreen = "trackpad" }
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
    onSimulateBeam: () -> Unit,
    onSimulateMusic: () -> Unit,
    onSyncClipboard: () -> Unit,
    onRestartService: () -> Unit,
    onOpenTrackpad: () -> Unit = {}
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
    val bridgeAddress = bridgeClient?.desktopIp?.let { "$it:${bridgeClient.desktopPort}" }
        ?: "Searching for desktop on LAN..."

    var showPairDialog by remember { mutableStateOf(false) }
    var pairingChallengeId by remember { mutableStateOf<String?>(null) }
    var pinInput by remember { mutableStateOf("") }
    var pairStatus by remember { mutableStateOf("Waiting for the desktop to display a PIN...") }

    // Referenced so Compose re-reads the polled bridge state above.
    @Suppress("UNUSED_VARIABLE")
    val bridgePollTick = bridgeTick

    if (showPairDialog) {
        AlertDialog(
            onDismissRequest = { showPairDialog = false },
            title = { Text("Pair with JARVIS Desktop") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(pairStatus, color = Color(0xFF8E8E93), fontSize = 12.sp)
                    BridgeTls.desktopFingerprint(context)?.let { fp ->
                        Text(
                            "Desktop certificate: ${fp.take(8)}…  (must match the desktop)",
                            color = Color(0xFF636366),
                            fontSize = 10.5.sp
                        )
                    }
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it.filter(Char::isDigit).take(6) },
                        label = { Text("6-digit PIN shown on desktop") },
                        singleLine = true
                    )
                    TextButton(
                        onClick = {
                            BridgeTls.clearDesktopPin(context)
                            FileBeamReceiver.clearPairedToken(context)
                            pairingChallengeId = null
                            pairStatus = "Desktop forgotten. Waiting for a new discovery beacon..."
                        }
                    ) { Text("Forget desktop & re-pin", fontSize = 11.sp) }
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
                                pairStatus = "Paired successfully."
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
                    color = Color(0xFF8E8E93),
                    fontSize = 12.sp
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (hasOverlayPermission && settings.isIslandEnabled) Color(0x2222C55E) else Color(0x22EF4444))
                    .border(
                        1.dp,
                        if (hasOverlayPermission && settings.isIslandEnabled) Color(0xFF30D158) else Color(0xFFFF453A),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = if (hasOverlayPermission && settings.isIslandEnabled) "Running" else "Stopped",
                    color = if (hasOverlayPermission && settings.isIslandEnabled) Color(0xFF30D158) else Color(0xFFFF453A),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Hero Master Switch Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
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
                        color = Color(0xFF8E8E93),
                        fontSize = 11.5.sp
                    )
                }
                Switch(
                    checked = settings.isIslandEnabled,
                    onCheckedChange = {
                        settings.isIslandEnabled = it
                        settings.save(context)
                        if (it) {
                            onRestartService()
                        } else {
                            context.stopService(Intent(context, JarvisIslandOverlayService::class.java))
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF0A84FF),
                        uncheckedThumbColor = Color(0xFF8E8E93),
                        uncheckedTrackColor = Color(0xFF2C2C2E)
                    )
                )
            }
        }

        // Permission Card 1: Overlay
        if (!hasOverlayPermission) {
            PermissionCard(
                title = "Overlay Permission Required",
                description = "Grant 'Appear on top' permission so Dynamic Island can position over your Motorola front camera.",
                buttonText = "Grant Overlay Permission",
                buttonColor = Color(0xFFFF453A),
                onClick = onRequestOverlayPermission
            )
        }

        // Permission Card 2: Media Session / Notification Access
        if (!hasNotificationAccess) {
            PermissionCard(
                title = "Media & Now Playing Access",
                description = "Enable Notification Access so JARVIS Island can capture currently playing songs from Spotify / YouTube Music with real controls.",
                buttonText = "Enable Music Session Access",
                buttonColor = Color(0xFF0A84FF),
                onClick = onRequestNotificationAccess
            )
        }

        // Permission Card 3: Lock Screen Accessibility
        if (!hasAccessibilityPermission) {
            PermissionCard(
                title = "Lock Screen Display (Accessibility)",
                description = "Enable JARVIS Island in Accessibility to allow the dynamic island to float smoothly on your Lock Screen and Keyguard.",
                buttonText = "Enable Lock Screen Accessibility",
                buttonColor = Color(0xFF30D158),
                onClick = onRequestAccessibilityPermission
            )
        }

        // LiveBridge Windows PC Connection Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
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
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x2238BDF8)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Computer,
                                contentDescription = "Windows Desktop",
                                tint = Color(0xFF0A84FF),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(text = "JARVIS Windows Desktop", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = bridgeAddress,
                                color = if (bridgeConnected) Color(0xFF0A84FF) else Color(0xFF8E8E93),
                                fontSize = 10.5.sp
                            )
                        }
                    }
                    Button(
                        onClick = {
                            pinInput = ""
                            pairingChallengeId = null
                            pairStatus = "Waiting for the desktop to display a PIN..."
                            showPairDialog = true
                            JarvisLiveBridgeClient.activeClient?.requestPairing { id ->
                                pairingChallengeId = id
                                if (id == null) {
                                    pairStatus = "Could not reach the desktop. Ensure both devices share the same Wi-Fi and JARVIS is running."
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (bridgeConnected) Color(0xFF0A84FF) else Color(0xFF334155)
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (bridgeConnected) "Pair" else "Retry",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // PC Remote Mousepad / Trackpad Feature Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenTrackpad() }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x2238BDF8)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Mouse,
                            contentDescription = "Mousepad",
                            tint = Color(0xFF0A84FF),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "PC Remote Mousepad",
                            color = Color.White,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Control Windows cursor, clicks & scroll via phone",
                            color = Color(0xFF8E8E93),
                            fontSize = 11.5.sp
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = "Open Trackpad",
                    tint = Color(0xFF636366),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Quick Sandbox & Diagnostics Actions
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "Quick Interactive Tests", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onToggleExpand,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2E)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Toggle Expand", fontSize = 11.sp, color = Color.White)
                    }

                    Button(
                        onClick = onSimulateBeam,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A84FF)),
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
fun PluginsScreen(onOpenTrackpad: () -> Unit = {}) {
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
            Text(text = "Manage features rendered inside the Dynamic Island", color = Color(0xFF8E8E93), fontSize = 12.sp)
        }

        // Plugin 1: File Beam
        PluginItem(
            title = "High-Speed File Beam",
            description = "Animates live file transfers between Windows PC and phone with MB/s speedometer.",
            icon = Icons.Rounded.SwapHoriz,
            enabled = settings.pluginFileBeam,
            onToggle = {
                settings.pluginFileBeam = it
                settings.save(context)
            }
        )

        // Plugin 2: Media Player
        PluginItem(
            title = "Now Playing Media Session",
            description = "Displays rotating vinyl disk art, wavy seekbar, and real music playback controls.",
            icon = Icons.Rounded.MusicNote,
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
            icon = Icons.Rounded.Notifications,
            enabled = settings.pluginNotifications,
            onToggle = {
                settings.pluginNotifications = it
                settings.save(context)
            }
        )

        // Plugin 4: Battery & Charging
        PluginItem(
            title = "Charging & Battery Status",
            description = "Real-time battery level and charging badge when connected to charger.",
            icon = Icons.Rounded.BatteryChargingFull,
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
            icon = Icons.Rounded.ContentCopy,
            enabled = settings.pluginClipboard,
            onToggle = {
                settings.pluginClipboard = it
                settings.save(context)
            }
        )

        // Plugin 6: Wireless PC Trackpad & Cursor
        PluginItem(
            title = "Wireless PC Trackpad & Cursor",
            description = "KDE Connect-style trackpad controlling Windows cursor, clicks and vertical scrolling.",
            icon = Icons.Rounded.Mouse,
            enabled = true,
            onToggle = { onOpenTrackpad() }
        )
    }
}

@Composable
fun PluginItem(
    title: String,
    description: String,
    icon: ImageVector,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
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
                        .background(Color(0xFF2C2C2E)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = Color(0xFF0A84FF),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(text = title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = description, color = Color(0xFF8E8E93), fontSize = 11.sp)
                }
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF0A84FF),
                    uncheckedThumbColor = Color(0xFF8E8E93),
                    uncheckedTrackColor = Color(0xFF2C2C2E)
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
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
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
            Text(text = "Settings", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(text = "Material You Dynamic Island customization categories", color = Color(0xFF8E8E93), fontSize = 12.sp)
        }

        SettingsCategoryTile(
            title = "Position & Alignment",
            subtitle = "Camera punch-hole clearance, X/Y margins (top 2px default)",
            icon = Icons.Rounded.Place,
            onClick = { onNavigateSubPage("position") }
        )

        SettingsCategoryTile(
            title = "Size & Geometry",
            subtitle = "Pill width, height, expanded card bounds, corner radii",
            icon = Icons.Rounded.AspectRatio,
            onClick = { onNavigateSubPage("size") }
        )

        SettingsCategoryTile(
            title = "Themes & Styling",
            subtitle = "Obsidian Black, Cyber Cyan, Emerald Matrix, border glows",
            icon = Icons.Rounded.Palette,
            onClick = { onNavigateSubPage("themes") }
        )

        SettingsCategoryTile(
            title = "Behavior & Lock Screen",
            subtitle = "Lock screen overlay, haptic feedback, gestures, auto-collapse",
            icon = Icons.Rounded.Lock,
            onClick = { onNavigateSubPage("behavior") }
        )

        SettingsCategoryTile(
            title = "Enabled Applications",
            subtitle = "Filter which apps can trigger Dynamic Island heads-up cards",
            icon = Icons.Rounded.Apps,
            onClick = { onNavigateSubPage("apps") }
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Reset All Settings Button
        OutlinedButton(
            onClick = {
                if (settings.hapticFeedback) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                settings.resetDefaults(context)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color(0xFFFF453A)
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.RestartAlt,
                    contentDescription = "Reset All",
                    modifier = Modifier.size(18.dp)
                )
                Text(text = "Reset All Settings to Defaults", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SettingsCategoryTile(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
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
                        .background(Color(0xFF2C2C2E)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = Color(0xFF0A84FF),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(text = title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = subtitle, color = Color(0xFF8E8E93), fontSize = 11.sp)
                }
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = "Open",
                tint = Color(0xFF636366),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ── Position & Clearance Sub-Page ──
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
                            .clip(RoundedCornerShape((settings.cornerRadius * 0.8f).dp))
                            .background(Color.Black.copy(alpha = 0.85f))
                            .border(1.dp, Color(0xFF0A84FF).copy(alpha = 0.5f), RoundedCornerShape((settings.cornerRadius * 0.8f).dp))
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
                Text(text = "Clearance creates an empty dead-zone right over your center camera hole.", color = Color(0xFF636366), fontSize = 10.5.sp)
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
                    label = "Punch-Hole Clearance Gap",
                    value = cutoutClearance.toFloat(),
                    valueText = "${cutoutClearance} dp",
                    range = 20f..80f,
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
    var cornerRadius by remember { mutableIntStateOf(settings.cornerRadius) }
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
                cornerRadius = settings.cornerRadius
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
                    range = 140f..260f,
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
                    range = 300f..400f,
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
                    range = 200f..340f,
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
                    label = "Collapsed Corner Radius",
                    value = cornerRadius.toFloat(),
                    valueText = "${cornerRadius} dp",
                    range = 10f..30f,
                    onReset = {
                        cornerRadius = JarvisIslandSettings.DEFAULT_CORNER_RADIUS
                        settings.cornerRadius = cornerRadius
                        settings.save(context)
                    },
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

        Text(text = "Choose which apps can present cards inside the Dynamic Island.", color = Color(0xFF8E8E93), fontSize = 12.sp)

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
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
                                checkedTrackColor = Color(0xFF0A84FF),
                                uncheckedThumbColor = Color(0xFF8E8E93),
                                uncheckedTrackColor = Color(0xFF2C2C2E)
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
    val updater = remember { JarvisAppUpdater(context) }
    val updateState by updater.state.collectAsState()

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
                .background(Color(0xFF0A84FF)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = "JARVIS",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }

        Text(text = "JARVIS Dynamic Island", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(text = "Version 1.0.0 • Connected Ecosystem", color = Color(0xFF8E8E93), fontSize = 12.sp)

        // ── In-App Seamless Auto Updater Card ──
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x2238BDF8)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = "Update",
                            tint = Color(0xFF0A84FF),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(text = "Software Updates", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(text = "v1.0.0", color = Color(0xFF8E8E93), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }

                when (val state = updateState) {
                    is UpdateState.Idle -> {
                        Text(
                            text = "Keep JARVIS Dynamic Island up-to-date with latest Apple HIG features & Live Activities.",
                            color = Color(0xFF8E8E93),
                            fontSize = 11.5.sp
                        )
                        Button(
                            onClick = { updater.checkForUpdates() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2E)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                            Spacer(Modifier.width(6.dp))
                            Text("Check for Updates", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    is UpdateState.Checking -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(vertical = 6.dp)
                        ) {
                            Text("Checking GitHub Releases for latest build...", color = Color(0xFF0A84FF), fontSize = 12.sp)
                        }
                    }
                    is UpdateState.UpToDate -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFF30D158), modifier = Modifier.size(16.dp))
                            Text("You are running the latest version (v1.0.0)", color = Color(0xFF30D158), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = { updater.checkForUpdates() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2E)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Check Again", color = Color.White, fontSize = 11.5.sp)
                        }
                    }
                    is UpdateState.Available -> {
                        Text("New release ${state.tag} is ready for installation (${"%.1f".format(state.sizeMb)} MB).", color = Color.White, fontSize = 12.sp)
                        Button(
                            onClick = { updater.startDownload(state.downloadUrl, state.checksumUrl) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A84FF)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Rounded.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                            Spacer(Modifier.width(6.dp))
                            Text("Download & Install Update", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    is UpdateState.Downloading -> {
                        val progress = state.progress
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Downloading Update...", color = Color(0xFF0A84FF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text("${progress.percent}%", color = Color(0xFF0A84FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            // Glowing animated gradient progress bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF2C2C2E))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(progress.percent / 100f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(Color(0xFF0A84FF), Color(0xFF0A84FF))
                                            )
                                        )
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${"%.1f".format(progress.downloadedMb)} MB / ${"%.1f".format(progress.totalMb)} MB", color = Color(0xFF8E8E93), fontSize = 10.5.sp)
                                Text(progress.speedMbps, color = Color(0xFF8E8E93), fontSize = 10.5.sp)
                            }
                        }
                    }
                    is UpdateState.ReadyToInstall -> {
                        Text("Download complete! Ready to install update.", color = Color(0xFF30D158), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Button(
                            onClick = { updater.installApk(state.apkFile) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF30D158)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                            Spacer(Modifier.width(6.dp))
                            Text("Install Update Now", color = Color.Black, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    is UpdateState.Error -> {
                        Text("Update error: ${state.message}", color = Color(0xFFFF453A), fontSize = 11.5.sp)
                        Button(
                            onClick = { updater.checkForUpdates() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2E)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Retry", color = Color.White, fontSize = 11.5.sp)
                        }
                    }
                }
            }
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "Platform Architecture", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "JARVIS Ecosystem connects Windows Desktop and Android companion devices seamlessly. " +
                            "Features real-time punch-hole cutout clearance, LiveBridge clipboard sync, and 5G WiFi Direct file beaming.",
                    color = Color(0xFF8E8E93),
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
                    Text("View GitHub Repository", color = Color(0xFF0A84FF), fontSize = 12.sp)
                }
            }
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "Accessibility & Overlay Disclosure", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "JARVIS Island uses Android System Alert Window (Overlay) APIs to render dynamic activity cards around your camera cutout. " +
                            "No personal data or passwords are ever accessed or collected.",
                    color = Color(0xFF8E8E93),
                    fontSize = 11.sp
                )
            }
        }
    }
}

// ── Shared UI Helpers ──
@Composable
fun SubPageHeader(title: String, onBack: () -> Unit, onResetSection: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2C2C2E))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(text = title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        if (onResetSection != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2C2C2E))
                    .clickable(onClick = onResetSection)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Reset Section",
                        tint = Color(0xFF8E8E93),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Reset",
                        color = Color(0xFF8E8E93),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
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
    val haptic = LocalHapticFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, color = Color(0xFFE2E8F0), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(text = valueText, color = Color(0xFF0A84FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (onReset != null) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2C2C2E))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onReset()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Reset $label",
                            tint = Color(0xFF8E8E93),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF0A84FF),
                activeTrackColor = Color(0xFF0A84FF),
                inactiveTrackColor = Color(0xFF2C2C2E)
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
            Text(text = description, color = Color(0xFF636366), fontSize = 10.5.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF0A84FF),
                uncheckedThumbColor = Color(0xFF8E8E93),
                uncheckedTrackColor = Color(0xFF2C2C2E)
            )
        )
    }
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
