package com.gloom.island.ui

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
import com.gloom.island.security.GloomSecurity
import com.gloom.island.service.GloomIslandAccessibilityService
import com.gloom.island.settings.GloomIslandSettings
import kotlin.math.roundToInt

@Composable
fun CalibrationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { GloomIslandSettings.instance }
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
            header = "Auto-Calibration on Lock Screen",
            footer = "Automatically locks your phone, wakes the display, and swipes up to the pattern screen to capture the exact physical pattern bounds."
        ) {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                AppleButton(
                    text = "Auto-Calibrate on Lock Screen",
                    primary = true,
                    icon = GloomIcons.Grid,
                    onClick = {
                        commitSettings()
                        val service = GloomIslandAccessibilityService.instance
                        if (service != null) {
                            service.startLivePatternCalibration { status ->
                                testStatus = status
                                gridWidth = settings.patternGridWidthRatio
                                centerY = settings.patternCenterYRatio
                            }
                        } else {
                            testStatus = "Accessibility service is not running."
                            Toast.makeText(context, testStatus, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
            if (testStatus != null) {
                AppleDivider()
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(
                        text = testStatus!!,
                        style = AppleType.footnote,
                        color = Apple.systemBlue
                    )
                }
            }
        }

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
                    icon = GloomIcons.ArrowsUpDown,
                    onClick = {
                        commitSettings()
                        val service = GloomIslandAccessibilityService.instance
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
                    icon = GloomIcons.Sparkles,
                    onClick = {
                        commitSettings()
                        val service = GloomIslandAccessibilityService.instance
                        val pattern = GloomSecurity.loadPattern(context)
                        if (service == null) {
                            Toast.makeText(context, "Accessibility service not running", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Locking in 1s to test unlock pattern replay...", Toast.LENGTH_SHORT).show()
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                service.lockScreen()
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    service.performUnlockPattern(pattern)
                                }, 1400)
                            }, 1000)
                        }
                    }
                )
            }
        }

        AppleSection(
            header = "In-App Pattern Recording & Verification",
            footer = "Draw your 9-dot unlock pattern directly below or load the preset sequence (2,5,7,3,6,8,4,1,9). Connecting at least 4 dots saves it directly to your device Keystore for autonomous unlocking."
        ) {
            var drawnPattern by remember { mutableStateOf<List<Int>>(emptyList()) }
            var patternStatus by remember { mutableStateOf<String?>(null) }
            val existingPattern = remember { GloomSecurity.loadPattern(context) }
            var hasPattern by remember { mutableStateOf(existingPattern.size >= 4) }

            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                AppleButton(
                    text = "Load Sequence (2,5,7,3,6,8,4,1,9)",
                    primary = false,
                    icon = GloomIcons.CheckCircleFill,
                    onClick = {
                        GloomSecurity.savePattern(context, GloomSecurity.TEST_PATTERN_1_BASED)
                        hasPattern = true
                        patternStatus = "Saved test sequence (2,5,7,3,6,8,4,1,9) into Keystore."
                        Toast.makeText(context, "Saved (2,5,7,3,6,8,4,1,9) to Keystore", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                PatternLockView(
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .padding(horizontal = 16.dp),
                    onPatternComplete = { pattern ->
                        if (pattern.size >= 4) {
                            drawnPattern = pattern
                            GloomSecurity.savePattern(context, pattern)
                            hasPattern = true
                            patternStatus = "Pattern recorded & saved successfully (${pattern.size} dots connected)."
                            Toast.makeText(context, "Pattern saved to Keystore", Toast.LENGTH_SHORT).show()
                        } else {
                            patternStatus = "Connect at least 4 dots to form a valid pattern."
                        }
                    }
                )
            }
            if (patternStatus != null || hasPattern) {
                AppleDivider()
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(
                        text = patternStatus ?: "Active unlock pattern configured (${existingPattern.size} dots).",
                        style = AppleType.footnote,
                        color = if (patternStatus?.contains("at least") == true) Apple.systemRed else Apple.systemGreen
                    )
                }
            }
        }

        AppleSection(header = "Defaults") {
            AppleRow(
                title = "Reset to Edge 50 Pro Defaults",
                subtitle = "Left swipe (22%) + 68% width grid at 56% height (above FOD sensor)",
                icon = GloomIcons.ArrowClockwise,
                iconTint = Apple.systemOrange,
                onClick = {
                    strategy = "Left"
                    startX = 0.22f; startY = 0.82f; endX = 0.22f; endY = 0.25f
                    gridWidth = 0.68f; centerY = 0.56f
                    commitSettings()
                    Toast.makeText(context, "Reset to Edge 50 Pro defaults", Toast.LENGTH_SHORT).show()
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
