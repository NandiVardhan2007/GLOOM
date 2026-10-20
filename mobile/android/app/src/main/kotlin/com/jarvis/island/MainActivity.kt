package com.jarvis.island

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.island.bridge.JarvisLiveBridgeClient
import com.jarvis.island.service.JarvisIslandOverlayService
import com.jarvis.island.settings.JarvisIslandSettings
import com.jarvis.island.ui.CyberBeamIcon
import com.jarvis.island.ui.IslandThemePalettes
import com.jarvis.island.ui.PulseConnectionDot
import com.jarvis.island.ui.SpinningVinylRecord

class MainActivity : ComponentActivity() {

    private lateinit var bridgeClient: JarvisLiveBridgeClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bridgeClient = JarvisLiveBridgeClient(this)
        JarvisIslandSettings.instance.load(this)

        if (hasOverlayPermission()) {
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
                    JarvisMasterCustomizationScreen(
                        hasOverlayPermission = hasOverlayPermission(),
                        onRequestOverlayPermission = { requestOverlayPermission() },
                        onToggleExpand = { triggerBroadcast(JarvisIslandSettings.ACTION_TOGGLE_EXPAND) },
                        onExpand = { triggerBroadcast(JarvisIslandSettings.ACTION_EXPAND) },
                        onCollapse = { triggerBroadcast(JarvisIslandSettings.ACTION_COLLAPSE) },
                        onSimulateBeam = { triggerBroadcast(JarvisIslandSettings.ACTION_TEST_BEAM) },
                        onSimulateMusic = { triggerBroadcast(JarvisIslandSettings.ACTION_TEST_MUSIC) },
                        onSyncClipboard = {
                            bridgeClient.syncClipboardToDesktop("Manual clipboard sync from Android Settings")
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasOverlayPermission()) {
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

@Composable
fun JarvisMasterCustomizationScreen(
    hasOverlayPermission: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onToggleExpand: () -> Unit,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onSimulateBeam: () -> Unit,
    onSimulateMusic: () -> Unit,
    onSyncClipboard: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val haptic = LocalHapticFeedback.current
    val settings = remember { JarvisIslandSettings.instance }
    val scrollState = rememberScrollState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Position", "Size", "Themes", "Behavior", "Sandbox")

    var gravity by remember { mutableStateOf(settings.gravity) }
    var cutoutClearance by remember { mutableIntStateOf(settings.cutoutClearance) }
    var positionY by remember { mutableIntStateOf(settings.positionY) }
    var positionX by remember { mutableIntStateOf(settings.positionX) }

    var pillWidth by remember { mutableIntStateOf(settings.pillWidth) }
    var pillHeight by remember { mutableIntStateOf(settings.pillHeight) }
    var expandedWidth by remember { mutableIntStateOf(settings.expandedWidth) }
    var expandedHeight by remember { mutableIntStateOf(settings.expandedHeight) }
    var cornerRadius by remember { mutableIntStateOf(settings.cornerRadius) }
    var expandedCornerRadius by remember { mutableIntStateOf(settings.expandedCornerRadius) }

    var themeStyle by remember { mutableStateOf(settings.themeStyle) }
    var showBorders by remember { mutableStateOf(settings.showBorders) }
    var borderGlow by remember { mutableStateOf(settings.borderGlow) }

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
            .padding(horizontal = 16.dp, vertical = 20.dp),
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
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.SansSerif
                )
                Text(
                    text = "Advanced Material You Customization Suite",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.5.sp
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (hasOverlayPermission) Color(0x2222C55E) else Color(0x22EF4444))
                    .border(
                        1.dp,
                        if (hasOverlayPermission) Color(0xFF22C55E) else Color(0xFFEF4444),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (hasOverlayPermission) "Active" else "Permission Req",
                    color = if (hasOverlayPermission) Color(0xFF22C55E) else Color(0xFFEF4444),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Overlay Permission Banner if needed
        if (!hasOverlayPermission) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2E1015)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Overlay Permission Required",
                        color = Color(0xFFFCA5A5),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Enable 'Appear on top' so the Dynamic Island renders above apps and the lock screen.",
                        color = Color(0xFFE2E8F0),
                        fontSize = 11.5.sp
                    )
                    Button(
                        onClick = onRequestOverlayPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                    ) {
                        Text("Grant Permission", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // ══════════════════════════════════════════════════════════
        // VISUAL CAMERA CUTOUT PREVIEW (Simulated Top Bezel)
        // ══════════════════════════════════════════════════════════
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Visual Bezel & Camera Preview",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${cutoutClearance}dp Clearance",
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Simulated Phone Top Edge with Punch Hole Camera & Dynamic Island Pill
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF030712))
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.TopCenter
                ) {
                    // Physical Camera Punch Hole (Simulated)
                    Box(
                        modifier = Modifier
                            .padding(top = (positionY * 0.8f).dp)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF000000))
                            .border(1.dp, Color(0xFF334155), CircleShape)
                    )

                    // Dynamic Island Collapsed Pill Preview
                    Box(
                        modifier = Modifier
                            .padding(top = (positionY * 0.8f - 2).dp)
                            .offset(x = positionX.dp)
                            .width((pillWidth * 0.8f).dp)
                            .height((pillHeight * 0.8f).dp)
                            .clip(RoundedCornerShape((cornerRadius * 0.8f).dp))
                            .background(Color(0xFF000000).copy(alpha = 0.85f))
                            .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f), RoundedCornerShape((cornerRadius * 0.8f).dp))
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left Wing
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF38BDF8))
                            )
                            // Clearance Gap
                            Spacer(modifier = Modifier.width((cutoutClearance * 0.8f).dp))
                            // Right Wing
                            Text("65%", color = Color(0xFF38BDF8), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Text(
                    text = "Text and icons remain strictly in the left and right wings. Zero obstruction with camera lens.",
                    color = Color(0xFF64748B),
                    fontSize = 10.5.sp
                )
            }
        }

        // ══════════════════════════════════════════════════════════
        // TAB ROW (Position / Size / Themes / Behavior / Sandbox)
        // ══════════════════════════════════════════════════════════
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color(0xFF101018),
            contentColor = Color(0xFF38BDF8),
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = Color(0xFF38BDF8)
                )
            }
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = {
                        if (settings.hapticFeedback) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        selectedTab = index
                    },
                    text = {
                        Text(
                            text = title,
                            fontSize = 11.5.sp,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == index) Color(0xFF38BDF8) else Color(0xFF94A3B8)
                        )
                    }
                )
            }
        }

        // ══════════════════════════════════════════════════════════
        // TAB CONTENT
        // ══════════════════════════════════════════════════════════
        when (selectedTab) {
            0 -> {
                // ── TAB 1: POSITION & GRAVITY ──
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF101018)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(text = "Island Alignment & Gravity", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)

                        // Gravity Chips
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

                        // 1. Center Punch Hole Clearance Gap
                        SliderControl(
                            label = "Camera Cutout Clearance Gap",
                            value = cutoutClearance.toFloat(),
                            valueText = "${cutoutClearance} dp",
                            range = 20f..80f,
                            onValueChange = {
                                cutoutClearance = it.toInt()
                                settings.cutoutClearance = cutoutClearance
                                settings.save(context)
                            }
                        )

                        // 2. Top Margin / Y-Position
                        SliderControl(
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

                        // 3. Horizontal Offset / X-Position
                        SliderControl(
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

            1 -> {
                // ── TAB 2: SIZE & GEOMETRY ──
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF101018)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(text = "Pill & Expanded Dimensions", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)

                        SliderControl(
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

                        SliderControl(
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

                        SliderControl(
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

                        SliderControl(
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

                        SliderControl(
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

                        SliderControl(
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

            2 -> {
                // ── TAB 3: THEMES & VISUALS ──
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF101018)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(text = "Theme & Visual Styling", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)

                        // Theme Options
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(palette.primaryAccent)
                                    )
                                    Text(text = themeName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                                if (active) {
                                    Text(text = "Selected", color = palette.primaryAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        ToggleOption(
                            title = "Show Outer Borders",
                            subtitle = "Displays sleek outline border matching selected theme.",
                            checked = showBorders,
                            onCheckedChange = {
                                showBorders = it
                                settings.showBorders = it
                                settings.save(context)
                            }
                        )

                        ToggleOption(
                            title = "Glowing Shimmer Effect",
                            subtitle = "Pulses subtle accent radiance during active transfers and playback.",
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

            3 -> {
                // ── TAB 4: BEHAVIOR & GESTURES ──
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF101018)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(text = "Interaction & Lock Screen", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)

                        ToggleOption(
                            title = "Show on Lock Screen",
                            subtitle = "Overlay stays visible and active on keyguard / lock screen.",
                            checked = showOnLockScreen,
                            onCheckedChange = {
                                showOnLockScreen = it
                                settings.showOnLockScreen = it
                                settings.save(context)
                            }
                        )

                        ToggleOption(
                            title = "Long Press to Expand",
                            subtitle = "Holding down on the collapsed pill springs open the expanded card.",
                            checked = longPressToExpand,
                            onCheckedChange = {
                                longPressToExpand = it
                                settings.longPressToExpand = it
                                settings.save(context)
                            }
                        )

                        ToggleOption(
                            title = "Haptic Tactile Feedback",
                            subtitle = "Vibrates gently on tap and long-press interactions.",
                            checked = hapticFeedback,
                            onCheckedChange = {
                                hapticFeedback = it
                                settings.hapticFeedback = it
                                settings.save(context)
                            }
                        )

                        ToggleOption(
                            title = "Auto-Expand on File Beam",
                            subtitle = "Automatically open card when transferring files with PC.",
                            checked = autoExpandOnTransfer,
                            onCheckedChange = {
                                autoExpandOnTransfer = it
                                settings.autoExpandOnTransfer = it
                                settings.save(context)
                            }
                        )

                        ToggleOption(
                            title = "Show in Landscape",
                            subtitle = "Render Dynamic Island when device is rotated horizontally.",
                            checked = showInLandscape,
                            onCheckedChange = {
                                showInLandscape = it
                                settings.showInLandscape = it
                                settings.save(context)
                            }
                        )

                        SliderControl(
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

            4 -> {
                // ── TAB 5: LIVE SANDBOX & DIAGNOSTICS ──
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF101018)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(text = "Interactive Test Sandbox", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Trigger animations and verify gesture responsiveness right now.",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.5.sp
                        )

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
                                Text("Simulate Beam", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onSimulateMusic,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Simulate Music", fontSize = 11.sp, color = Color.White)
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

                        OutlinedButton(
                            onClick = {
                                settings.resetDefaults(context)
                                gravity = settings.gravity
                                cutoutClearance = settings.cutoutClearance
                                positionY = settings.positionY
                                positionX = settings.positionX
                                pillWidth = settings.pillWidth
                                pillHeight = settings.pillHeight
                                expandedWidth = settings.expandedWidth
                                expandedHeight = settings.expandedHeight
                                cornerRadius = settings.cornerRadius
                                expandedCornerRadius = settings.expandedCornerRadius
                                themeStyle = settings.themeStyle
                                showBorders = settings.showBorders
                                borderGlow = settings.borderGlow
                                showOnLockScreen = settings.showOnLockScreen
                                showInLandscape = settings.showInLandscape
                                autoExpandOnTransfer = settings.autoExpandOnTransfer
                                autoCollapseDelaySec = settings.autoCollapseDelaySec
                                hapticFeedback = settings.hapticFeedback
                                longPressToExpand = settings.longPressToExpand
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Reset All to Calibrated Defaults", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun SliderControl(
    label: String,
    value: Float,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
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
fun ToggleOption(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, color = Color(0xFFE2E8F0), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, color = Color(0xFF64748B), fontSize = 10.5.sp)
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
