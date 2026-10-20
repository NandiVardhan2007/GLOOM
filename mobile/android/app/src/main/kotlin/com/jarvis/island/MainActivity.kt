package com.jarvis.island

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.island.bridge.JarvisLiveBridgeClient
import com.jarvis.island.service.JarvisIslandOverlayService
import com.jarvis.island.settings.JarvisIslandSettings

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
                    background = Color(0xFF09090B),
                    surface = Color(0xFF13131A),
                    primary = Color(0xFF38BDF8)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF09090B)
                ) {
                    JarvisSettingsScreen(
                        hasOverlayPermission = hasOverlayPermission(),
                        onRequestOverlayPermission = { requestOverlayPermission() },
                        onToggleExpand = { triggerBroadcast(JarvisIslandSettings.ACTION_TOGGLE_EXPAND) },
                        onSimulateBeam = { triggerBroadcast(JarvisIslandSettings.ACTION_TEST_BEAM) },
                        onSyncClipboard = {
                            bridgeClient.syncClipboardToDesktop("Manual clipboard sync from Android Settings")
                        },
                        onRestartService = { startOverlayService() }
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
fun JarvisSettingsScreen(
    hasOverlayPermission: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onToggleExpand: () -> Unit,
    onSimulateBeam: () -> Unit,
    onSyncClipboard: () -> Unit,
    onRestartService: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings = remember { JarvisIslandSettings.instance }
    val scrollState = rememberScrollState()

    var cutoutClearance by remember { mutableIntStateOf(settings.cutoutClearance) }
    var positionY by remember { mutableIntStateOf(settings.positionY) }
    var positionX by remember { mutableIntStateOf(settings.positionX) }
    var pillWidth by remember { mutableIntStateOf(settings.pillWidth) }
    var pillHeight by remember { mutableIntStateOf(settings.pillHeight) }
    var cornerRadius by remember { mutableIntStateOf(settings.cornerRadius) }
    var showOnLockScreen by remember { mutableStateOf(settings.showOnLockScreen) }
    var autoExpandOnTransfer by remember { mutableStateOf(settings.autoExpandOnTransfer) }
    var autoCollapseDelaySec by remember { mutableFloatStateOf(settings.autoCollapseDelaySec) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 18.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
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
                    text = "Camera Cutout Clearance & Ecosystem Customization",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
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
                    .padding(horizontal = 10.dp, vertical = 5.dp)
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
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2E1015)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Overlay Permission Required",
                        color = Color(0xFFFCA5A5),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Enable 'Appear on top' to display the Dynamic Island over system apps and the lock screen.",
                        color = Color(0xFFE2E8F0),
                        fontSize = 12.sp
                    )
                    Button(
                        onClick = onRequestOverlayPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                    ) {
                        Text("Grant Overlay Permission", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Section 1: Live Interactive Testing & Quick Actions
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF13131A)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Live Island Actions",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Test island expansion, lock-screen visibility, and file beam animations in real time.",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.5.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onToggleExpand,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Expand/Collapse", fontSize = 11.sp, color = Color.White)
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

                OutlinedButton(
                    onClick = onSyncClipboard,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Sync Clipboard to Windows PC", color = Color(0xFF38BDF8), fontSize = 12.sp)
                }
            }
        }

        // Section 2: Physical Camera Cutout & Geometry Customization
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF13131A)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Camera Cutout Clearance",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Live Update",
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = "Calibrated for Motorola Edge 50 Pro & center punch-hole cameras so text never collides with the lens.",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.5.sp
                )

                // 1. Center Punch Hole Clearance Gap
                SliderItem(
                    label = "Punch-Hole Clearance Gap",
                    value = cutoutClearance.toFloat(),
                    valueText = "${cutoutClearance} dp",
                    range = 20f..70f,
                    onValueChange = {
                        cutoutClearance = it.toInt()
                        settings.cutoutClearance = cutoutClearance
                        settings.save(context)
                    }
                )

                // 2. Top Margin / Y-Offset
                SliderItem(
                    label = "Top Margin (Y Position)",
                    value = positionY.toFloat(),
                    valueText = "${positionY} dp",
                    range = 0f..50f,
                    onValueChange = {
                        positionY = it.toInt()
                        settings.positionY = positionY
                        settings.save(context)
                    }
                )

                // 3. Pill Width (Collapsed)
                SliderItem(
                    label = "Pill Width",
                    value = pillWidth.toFloat(),
                    valueText = "${pillWidth} dp",
                    range = 140f..260f,
                    onValueChange = {
                        pillWidth = it.toInt()
                        settings.pillWidth = pillWidth
                        settings.save(context)
                    }
                )

                // 4. Pill Height (Collapsed)
                SliderItem(
                    label = "Pill Height",
                    value = pillHeight.toFloat(),
                    valueText = "${pillHeight} dp",
                    range = 30f..54f,
                    onValueChange = {
                        pillHeight = it.toInt()
                        settings.pillHeight = pillHeight
                        settings.save(context)
                    }
                )

                // 5. Horizontal Offset (X-Position)
                SliderItem(
                    label = "Horizontal Offset (X Position)",
                    value = positionX.toFloat(),
                    valueText = "${positionX} dp",
                    range = -40f..40f,
                    onValueChange = {
                        positionX = it.toInt()
                        settings.positionX = positionX
                        settings.save(context)
                    }
                )

                // 6. Corner Curvature
                SliderItem(
                    label = "Corner Curvature Radius",
                    value = cornerRadius.toFloat(),
                    valueText = "${cornerRadius} dp",
                    range = 10f..30f,
                    onValueChange = {
                        cornerRadius = it.toInt()
                        settings.cornerRadius = cornerRadius
                        settings.save(context)
                    }
                )
            }
        }

        // Section 3: Lock Screen & Behavior Toggles
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF13131A)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Behavior & Lock Screen",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                // Lock Screen Toggle
                ToggleItem(
                    title = "Show on Lock Screen",
                    subtitle = "Dynamic Island stays visible and functional above the device keyguard.",
                    checked = showOnLockScreen,
                    onCheckedChange = {
                        showOnLockScreen = it
                        settings.showOnLockScreen = it
                        settings.save(context)
                    }
                )

                // Auto-Expand Toggle
                ToggleItem(
                    title = "Auto-Expand on File Beam",
                    subtitle = "Automatically expand into animated transfer card when sharing files.",
                    checked = autoExpandOnTransfer,
                    onCheckedChange = {
                        autoExpandOnTransfer = it
                        settings.autoExpandOnTransfer = it
                        settings.save(context)
                    }
                )

                // Auto-Collapse Delay Slider
                SliderItem(
                    label = "Auto-Collapse Delay",
                    value = autoCollapseDelaySec,
                    valueText = String.format("%.1fs", autoCollapseDelaySec),
                    range = 1.5f..8.0f,
                    onValueChange = {
                        autoCollapseDelaySec = it
                        settings.autoCollapseDelaySec = it
                        settings.save(context)
                    }
                )
            }
        }

        // Section 4: Defaults & Reset
        OutlinedButton(
            onClick = {
                settings.resetDefaults(context)
                cutoutClearance = settings.cutoutClearance
                positionY = settings.positionY
                positionX = settings.positionX
                pillWidth = settings.pillWidth
                pillHeight = settings.pillHeight
                cornerRadius = settings.cornerRadius
                showOnLockScreen = settings.showOnLockScreen
                autoExpandOnTransfer = settings.autoExpandOnTransfer
                autoCollapseDelaySec = settings.autoCollapseDelaySec
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Reset to Calibrated Defaults", color = Color(0xFF94A3B8), fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun SliderItem(
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
            Text(text = label, color = Color(0xFFE2E8F0), fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
            Text(text = valueText, color = Color(0xFF38BDF8), fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
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
fun ToggleItem(
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
            Text(text = title, color = Color(0xFFE2E8F0), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, color = Color(0xFF64748B), fontSize = 11.sp)
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
