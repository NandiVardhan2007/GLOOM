package com.gloom.island.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gloom.island.security.GloomSecurity
import kotlinx.coroutines.delay

/**
 * Apple-style animated lock/unlock icon.
 * The shackle lifts vertically and rotates smoothly into an unlatched open state
 * with a subtle spring bounce (350-450 ms), and clicks back into place when locking.
 */
@Composable
fun AppleAnimatedLockIcon(
    isUnlocked: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    tint: Color = Color.White
) {
    var animated by remember { mutableStateOf(!isUnlocked) }
    LaunchedEffect(isUnlocked) {
        animated = isUnlocked
    }

    // Smooth, subtle 380ms spring transition matching iOS lock screen lock/unlock icon
    val progress by animateFloatAsState(
        targetValue = if (animated) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.68f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "lock_shackle_anim"
    )

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Shackle stroke and proportions
        val strokeWidth = w * 0.12f
        val bodyWidth = w * 0.72f
        val bodyHeight = h * 0.52f
        val bodyLeft = (w - bodyWidth) / 2f
        val bodyTop = h * 0.44f
        val cornerRad = bodyWidth * 0.22f

        // Shackle dimensions
        val shackleWidth = w * 0.42f
        val shackleHeight = h * 0.40f
        val shackleLeft = (w - shackleWidth) / 2f
        val shackleClosedTop = bodyTop - shackleHeight + (strokeWidth * 0.5f)

        // Animation offsets: vertical lift of ~16% height, and unlatched rotation around left leg
        val liftY = -h * 0.16f * progress
        val openAngle = -24f * progress

        // Draw Shackle (Loop)
        val shackleTop = shackleClosedTop + liftY
        val hingeX = shackleLeft + (strokeWidth / 2f)
        val hingeY = bodyTop

        rotate(degrees = openAngle, pivot = Offset(hingeX, hingeY)) {
            val path = Path().apply {
                // Left vertical leg extending slightly into body
                val startY = bodyTop + (strokeWidth * 0.5f)
                val archRadius = shackleWidth / 2f
                moveTo(shackleLeft, startY)
                lineTo(shackleLeft, shackleTop + archRadius)
                // Semicircle arch
                arcTo(
                    rect = Rect(
                        left = shackleLeft,
                        top = shackleTop,
                        right = shackleLeft + shackleWidth,
                        bottom = shackleTop + (archRadius * 2f)
                    ),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = 180f,
                    forceMoveTo = false
                )
                // Right vertical leg (unlatches when open)
                val rightEndY = if (progress > 0.05f) {
                    shackleTop + archRadius + (shackleHeight - archRadius) * (1f - progress * 0.45f)
                } else {
                    startY
                }
                lineTo(shackleLeft + shackleWidth, rightEndY)
            }

            drawPath(
                path = path,
                color = tint,
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round
                )
            )
        }

        // Draw Lock Body
        drawRoundRect(
            color = tint,
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyWidth, bodyHeight),
            cornerRadius = CornerRadius(cornerRad, cornerRad)
        )

        // Draw Center Keyhole / Notch
        val holeRadius = bodyWidth * 0.11f
        val holeCenter = Offset(w / 2f, bodyTop + bodyHeight * 0.40f)
        val holeColor = Color(0xFF0F172A)

        drawCircle(
            color = holeColor,
            radius = holeRadius,
            center = holeCenter
        )

        val slotWidth = holeRadius * 1.0f
        val slotHeight = bodyHeight * 0.24f
        drawRoundRect(
            color = holeColor,
            topLeft = Offset(holeCenter.x - (slotWidth / 2f), holeCenter.y),
            size = Size(slotWidth, slotHeight),
            cornerRadius = CornerRadius(slotWidth / 2f, slotWidth / 2f)
        )
    }
}

@Composable
fun SecurityScreen(onBack: () -> Unit, onOpenVault: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var hasPattern by remember { mutableStateOf(GloomSecurity.hasPattern(context)) }
    var step by remember { mutableStateOf(0) }
    var firstPattern by remember { mutableStateOf<List<Int>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        AppleSubHeader(title = "Security", onBack = onBack)

        if (step == 0) {
            AppleSection(header = "App Lock") {
                AppleRow(
                    title = "Pattern Lock",
                    subtitle = if (hasPattern) "A pattern is saved on this device" else "No pattern saved",
                    icon = GloomIcons.Apps,
                    iconTint = Apple.systemBlue,
                    value = if (hasPattern) "On" else "Off"
                )
                if (onOpenVault != null) {
                    AppleDivider()
                    AppleRow(
                        title = "Password Vault",
                        subtitle = "Encrypted credentials for agent autofill",
                        icon = GloomIcons.Shield,
                        iconTint = Apple.systemBlue,
                        showChevron = true,
                        onClick = onOpenVault
                    )
                }
            }

            AppleSection(header = "Pattern") {
                AppleButton(
                    text = if (hasPattern) "Change Pattern" else "Set Pattern",
                    primary = !hasPattern,
                    icon = GloomIcons.Sparkles,
                    onClick = {
                        step = 1
                        firstPattern = emptyList()
                        message = null
                        error = false
                    }
                )
                if (hasPattern) {
                    AppleDivider()
                    AppleRow(
                        title = "Remove Pattern",
                        icon = GloomIcons.Close,
                        iconTint = Apple.systemRed,
                        destructive = true,
                        onClick = {
                            GloomSecurity.clearPattern(context)
                            GloomSecurity.setAppLockEnabled(context, false)
                            hasPattern = false
                            message = "Pattern removed."
                        }
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = if (step == 1) "Draw a new pattern" else "Draw it again to confirm",
                    style = AppleType.title2,
                    color = Apple.label,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (step == 1) "Connect at least four dots." else "Repeat the same pattern.",
                    style = AppleType.footnote,
                    color = Apple.secondaryLabel,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    PatternLockView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 40.dp),
                        error = error,
                        onPatternComplete = { pattern ->
                            if (pattern.size < 4) {
                                error = true
                                message = "Connect at least four dots."
                                return@PatternLockView
                            }
                            if (step == 1) {
                                firstPattern = pattern
                                step = 2
                                error = false
                                message = null
                            } else {
                                if (pattern == firstPattern) {
                                    GloomSecurity.savePattern(context, pattern)
                                    hasPattern = true
                                    step = 0
                                    error = false
                                    message = "Pattern saved."
                                } else {
                                    error = true
                                    message = "The patterns do not match. Try again."
                                    step = 1
                                    firstPattern = emptyList()
                                }
                            }
                        }
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (message != null) {
                    Text(
                        text = message!!,
                        style = AppleType.footnote,
                        color = if (error) Apple.systemRed else Apple.systemGreen,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                AppleButton(
                    text = "Cancel",
                    primary = false,
                    onClick = {
                        step = 0
                        firstPattern = emptyList()
                        error = false
                        message = null
                    }
                )
            }
        }

        if (step == 0 && message != null) {
            Text(
                text = message!!,
                style = AppleType.footnote,
                color = if (error) Apple.systemRed else Apple.systemGreen,
                modifier = Modifier.padding(horizontal = 36.dp, vertical = 8.dp)
            )
        }

        if (step == 0) {
            AppleSection(
                header = "Device Unlock",
                footer = "Saying “Unlock my mobile” makes GLOOM draw your saved pattern on the lock screen. Even when Dynamic Island is turned off, the Siri pop-up is available on the lock screen strictly for unlocking purposes."
            ) {
                val settings = com.gloom.island.settings.GloomIslandSettings.instance
                AppleRow(
                    title = "Voice Unlock",
                    subtitle = "Say “Unlock” in the Siri pop-up or Dynamic Island",
                    icon = GloomIcons.Waveform,
                    iconTint = Apple.systemPurple
                )
                AppleDivider()
                AppleSwitchRow(
                    title = "Lock Screen Siri Unlock",
                    description = "Allow Siri pop-up on lock screen when Dynamic Island is off",
                    checked = settings.lockScreenSiriUnlock,
                    onCheckedChange = { enabled ->
                        settings.lockScreenSiriUnlock = enabled
                        settings.save(context)
                    }
                )
                if (settings.lockScreenSiriUnlock) {
                    AppleDivider()
                    AppleSwitchRow(
                        title = "Auto-Pop Siri on Lock Screen",
                        description = "Automatically pop up Siri unlock card when waking lock screen",
                        checked = settings.lockScreenAutoPopSiri,
                        onCheckedChange = { enabled ->
                            settings.lockScreenAutoPopSiri = enabled
                            settings.save(context)
                        }
                    )
                }
                AppleDivider()
                AppleRow(
                    title = "Storage",
                    subtitle = "Your pattern is encrypted with Android Keystore",
                    icon = GloomIcons.Shield,
                    iconTint = Apple.systemGreen
                )
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
fun GloomLockScreen(onUnlock: () -> Unit) {
    val context = LocalContext.current
    var isUnlocked by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isUnlocked) {
        if (isUnlocked) {
            // Give 400ms for Apple-style lock icon shackle animation to complete smoothly
            delay(420)
            onUnlock()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Apple.background)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(56.dp))
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(if (isUnlocked) Apple.systemGreen else Apple.systemBlue),
            contentAlignment = Alignment.Center
        ) {
            AppleAnimatedLockIcon(
                isUnlocked = isUnlocked,
                size = 38.dp,
                tint = Color.White
            )
        }
        Spacer(Modifier.height(14.dp))
        Text("GLOOM", style = AppleType.largeTitle, color = Apple.label)
        Text(
            if (isUnlocked) "Unlocked" else "Locked",
            style = AppleType.subheadline,
            color = if (isUnlocked) Apple.systemGreen else Apple.secondaryLabel
        )
        Spacer(Modifier.height(26.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            PatternLockView(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                error = error,
                onPatternComplete = { pattern ->
                    if (isUnlocked) return@PatternLockView
                    if (pattern.size < 4) {
                        error = true
                        message = "Connect at least four dots."
                    } else if (GloomSecurity.verifyPattern(context, pattern)) {
                        error = false
                        message = null
                        isUnlocked = true
                    } else {
                        error = true
                        message = "Wrong pattern. Try again."
                    }
                }
            )
        }
        if (message != null) {
            Text(
                text = message!!,
                style = AppleType.footnote,
                color = Apple.systemRed,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
