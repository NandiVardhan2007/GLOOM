package com.jarvis.island.ui

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jarvis.island.security.JarvisSecurity
import com.jarvis.island.service.JarvisIslandAccessibilityService
import com.jarvis.island.settings.JarvisIslandSettings
import kotlin.math.roundToInt

@Composable
fun CalibrationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { JarvisIslandSettings.instance }
    val scrollState = rememberScrollState()

    var strategy by remember { mutableStateOf(settings.swipeStrategy) }
    var startX by remember { mutableFloatStateOf(settings.swipeStartXRatio) }
    var startY by remember { mutableFloatStateOf(settings.swipeStartYRatio) }
    var endX by remember { mutableFloatStateOf(settings.swipeEndXRatio) }
    var endY by remember { mutableFloatStateOf(settings.swipeEndYRatio) }
    var gridWidth by remember { mutableFloatStateOf(settings.patternGridWidthRatio) }
    var centerY by remember { mutableFloatStateOf(settings.patternCenterYRatio) }

    var testStatus by remember { mutableStateOf<String?>(null) }

    fun commitSettings() {
        settings.swipeStrategy = strategy
        settings.swipeStartXRatio = startX
        settings.swipeStartYRatio = startY
        settings.swipeEndXRatio = endX
        settings.swipeEndYRatio = endY
        settings.patternGridWidthRatio = gridWidth
        settings.patternCenterYRatio = centerY
        settings.save(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        AppleSubHeader(title = "Calibration", onBack = onBack)

        AppleSection(
            header = "Swipe Path (Bypass Fingerprint Sensor)",
            footer = "On phones with in-display optical fingerprint sensors (like the Edge 50 Pro), a center swipe hits the fingerprint area and vibrates or fails. Selecting 'Left' or 'Right' moves the swipe path to the side so the keyguard is dismissed cleanly."
        ) {
            val strategies = listOf("Left", "Right", "HighCenter", "Custom")
            val selectedIndex = strategies.indexOf(strategy).coerceAtLeast(0)

            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                AppleSegmentedControl(
                    options = strategies,
                    selectedIndex = selectedIndex,
                    onSelect = { idx ->
                        strategy = strategies[idx]
                        when (strategy) {
                            "Left" -> {
                                startX = 0.22f; startY = 0.82f; endX = 0.22f; endY = 0.25f
                            }
                            "Right" -> {
                                startX = 0.78f; startY = 0.82f; endX = 0.78f; endY = 0.25f
                            }
                            "HighCenter" -> {
                                startX = 0.50f; startY = 0.62f; endX = 0.50f; endY = 0.18f
                            }
                        }
                        commitSettings()
                    }
                )
            }

            if (strategy == "Custom") {
                AppleDivider()
                AppleSliderRow(
                    title = "Start X",
                    valueText = "${(startX * 100).roundToInt()}%",
                    value = startX,
                    onValueChange = { startX = it; commitSettings() },
                    valueRange = 0.05f..0.95f
                )
                AppleDivider()
                AppleSliderRow(
                    title = "Start Y",
                    valueText = "${(startY * 100).roundToInt()}%",
                    value = startY,
                    onValueChange = { startY = it; commitSettings() },
                    valueRange = 0.10f..0.95f
                )
                AppleDivider()
                AppleSliderRow(
                    title = "End X",
                    valueText = "${(endX * 100).roundToInt()}%",
                    value = endX,
                    onValueChange = { endX = it; commitSettings() },
                    valueRange = 0.05f..0.95f
                )
                AppleDivider()
                AppleSliderRow(
                    title = "End Y",
                    valueText = "${(endY * 100).roundToInt()}%",
                    value = endY,
                    onValueChange = { endY = it; commitSettings() },
                    valueRange = 0.05f..0.90f
                )
            }

            AppleDivider()
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                AppleButton(
                    text = "Test Swipe Path",
                    primary = false,
                    icon = JarvisIcons.ArrowsUpDown,
                    onClick = {
                        commitSettings()
                        val service = JarvisIslandAccessibilityService.instance
                        if (service != null) {
                            val ok = service.testSwipeGesture()
                            testStatus = if (ok) "Swipe gesture dispatched." else "Swipe rejected by system."
                        } else {
                            testStatus = "Accessibility Service not running."
                        }
                        Toast.makeText(context, testStatus, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        AppleSection(
            header = "Pattern Grid Alignment",
            footer = "Calibrate where the 9 pattern dots sit on your device screen. The preview below reflects your phone's proportion."
        ) {
            AppleSliderRow(
                title = "Grid Width",
                valueText = "${(gridWidth * 100).roundToInt()}%",
                value = gridWidth,
                onValueChange = { gridWidth = it; commitSettings() },
                valueRange = 0.45f..0.90f
            )
            AppleDivider()
            AppleSliderRow(
                title = "Center Y",
                valueText = "${(centerY * 100).roundToInt()}%",
                value = centerY,
                onValueChange = { centerY = it; commitSettings() },
                valueRange = 0.40f..0.85f
            )

            AppleDivider()

            // Visual Grid Preview Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                PatternPreviewCard(
                    gridWidthRatio = gridWidth,
                    centerYRatio = centerY,
                    swipeStartX = startX,
                    swipeStartY = startY,
                    swipeEndX = endX,
                    swipeEndY = endY
                )
            }

            AppleDivider()

            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                AppleButton(
                    text = "Test Pattern Replay",
                    primary = false,
                    icon = JarvisIcons.Sparkles,
                    onClick = {
                        commitSettings()
                        val service = JarvisIslandAccessibilityService.instance
                        val pattern = JarvisSecurity.loadPattern(context)
                        if (service == null) {
                            Toast.makeText(context, "Accessibility service not running", Toast.LENGTH_SHORT).show()
                        } else if (pattern == null || pattern.size < 4) {
                            Toast.makeText(context, "Set a pattern in Security first", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Lock the screen to test voice unlock replay", Toast.LENGTH_LONG).show()
                        }
                    }
                )
            }
        }

        AppleSection(header = "Defaults") {
            AppleRow(
                title = "Reset to Edge 50 Pro Defaults",
                subtitle = "Left swipe (22%) + 71% width grid at 65.8% height",
                icon = JarvisIcons.ArrowClockwise,
                iconTint = Apple.systemOrange,
                onClick = {
                    strategy = "Left"
                    startX = 0.22f; startY = 0.82f; endX = 0.22f; endY = 0.25f
                    gridWidth = 0.71f; centerY = 0.658f
                    commitSettings()
                    Toast.makeText(context, "Reset to defaults", Toast.LENGTH_SHORT).show()
                }
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun PatternPreviewCard(
    gridWidthRatio: Float,
    centerYRatio: Float,
    swipeStartX: Float,
    swipeStartY: Float,
    swipeEndX: Float,
    swipeEndY: Float,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.55f)
            .aspectRatio(9f / 19.5f) // Typical modern phone screen ratio
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF0A0A0C))
            .border(1.5.dp, Apple.systemBlue.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
    ) {
        // Draw swipe path & fingerprint area & 3x3 pattern dots
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Optical Fingerprint Sensor indication (center bottom)
            drawCircle(
                color = Color.Cyan.copy(alpha = 0.25f),
                radius = w * 0.08f,
                center = Offset(w * 0.5f, h * 0.85f)
            )

            // Swipe Path Line
            val sX = w * swipeStartX
            val sY = h * swipeStartY
            val eX = w * swipeEndX
            val eY = h * swipeEndY
            drawLine(
                color = Apple.systemGreen,
                start = Offset(sX, sY),
                end = Offset(eX, eY),
                strokeWidth = 3f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
            )
            // Arrowhead at swipe end
            drawCircle(
                color = Apple.systemGreen,
                radius = 4f,
                center = Offset(eX, eY)
            )

            // Pattern grid
            val gw = w * gridWidthRatio
            val left = (w - gw) / 2f
            val cell = gw / 3f
            val top = h * centerYRatio - gw / 2f

            for (r in 0..2) {
                for (c in 0..2) {
                    val cx = left + cell * c + cell / 2f
                    val cy = top + cell * r + cell / 2f
                    drawCircle(
                        color = Color.White.copy(alpha = 0.85f),
                        radius = 4.5f,
                        center = Offset(cx, cy)
                    )
                    drawCircle(
                        color = Apple.systemBlue.copy(alpha = 0.3f),
                        radius = 12f,
                        center = Offset(cx, cy)
                    )
                }
            }
        }
    }
}
