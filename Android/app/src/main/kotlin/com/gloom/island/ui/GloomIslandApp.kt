package com.gloom.island.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gloom.island.settings.GloomIslandSettings

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GloomIslandApp(
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onLongPressExpand: () -> Unit = {},
    onBeamSampleFile: () -> Unit = {},
    onSyncClipboard: () -> Unit = {},
    onMediaPlayPause: () -> Unit = {},
    onMediaNext: () -> Unit = {},
    onMediaPrev: () -> Unit = {},
    onMediaSeek: (Float) -> Unit = {},
    activeTransfer: ActiveTransferInfo = ActiveTransferInfo(),
    mediaTrack: MediaTrackInfo = MediaTrackInfo(),
    activeNotification: NotificationIslandEvent? = null,
    onNotificationClick: () -> Unit = {},
    onNotificationDismiss: () -> Unit = {},
    bridgeDevice: BridgeDeviceInfo = BridgeDeviceInfo(),
    batteryLevel: Int = 100,
    isCharging: Boolean = false,
    reelCount: Int = 0,
    isInstagramForeground: Boolean = false,
    showReelCounterUntil: Long = 0L,
    lockEvent: LockIslandEvent? = null,
    activeTimer: TimerIslandEvent? = null,
    onTimerPauseResume: () -> Unit = {},
    onTimerCancel: () -> Unit = {},
    voiceAssistant: VoiceAssistantIslandEvent? = null,
    onVoiceAssistantAskAgain: () -> Unit = {},
    onVoiceAssistantClose: () -> Unit = {},
    systemAlert: SystemAlertIslandEvent? = null,
    onSystemAlertDismiss: () -> Unit = {},
    onSwipeDown: (Boolean) -> Unit = {},
    onInputFocusChanged: (Boolean) -> Unit = {},
    settings: GloomIslandSettings = GloomIslandSettings.instance
) {
    if (!settings.isIslandEnabled) return

    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val palette = remember(settings.themeStyle) { IslandThemePalettes.getTheme(settings.themeStyle) }

    val islandBrush = remember(palette) {
        when (palette.fillType) {
            ThemeFillType.SOLID -> SolidColor(palette.backgroundColors.firstOrNull() ?: Color.Black)
            ThemeFillType.HORIZONTAL_GRADIENT -> Brush.horizontalGradient(palette.backgroundColors)
            ThemeFillType.LINEAR_GRADIENT -> Brush.linearGradient(palette.backgroundColors)
            ThemeFillType.RADIAL_GRADIENT -> Brush.radialGradient(palette.backgroundColors)
            ThemeFillType.SWEEP_GRADIENT -> Brush.sweepGradient(palette.backgroundColors)
        }
    }

    // Interactive Apple fluid touch scale
    val islandInteractionSource = remember { MutableInteractionSource() }
    val isIslandPressed by islandInteractionSource.collectIsPressedAsState()
    val islandScale by animateFloatAsState(
        targetValue = if (isIslandPressed) 0.97f else 1.0f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
        label = "islandScale"
    )

    // The collapsed pill hugs the punch-hole; the expanded card slides below
    // the status bar so the clock and system icons stay legible.
    val statusBarInset = remember(context) {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        val px = if (id > 0) {
            context.resources.getDimensionPixelSize(id)
        } else {
            (24f * context.resources.displayMetrics.density).toInt()
        }
        (px / context.resources.displayMetrics.density).dp
    }

    // Apple HIG critically damped fluid spring (dampingRatio = 0.82f, stiffness = 380f)
    val islandSpring: AnimationSpec<Dp> = if (settings.reducedMotion) {
        tween(durationMillis = 90, easing = FastOutSlowInEasing)
    } else {
        spring(dampingRatio = 0.82f, stiffness = 380f)
    }

    val isNotchStyle = settings.islandStyle == "Notch"
    val flareWidth = if (isNotchStyle) 14.dp else 0.dp
    val flareHeight = if (isNotchStyle) 13.dp else 0.dp

    val targetTop = if (isNotchStyle) {
        0.dp
    } else {
        (if (isExpanded) statusBarInset else 0.dp) + settings.positionY.dp
    }

    val topPadding by animateDpAsState(
        targetValue = targetTop,
        animationSpec = islandSpring,
        label = "islandTop"
    )

    val isLockActive = lockEvent != null && (System.currentTimeMillis() - lockEvent.timestampMs < 2500L)
    val isTimerActive = activeTimer?.isActive == true
    val isSystemAlertActive = systemAlert != null && systemAlert.type != SystemAlertIslandEvent.AlertType.NONE
    val isVoiceActive = voiceAssistant?.isActive == true
    val isMediaActive = mediaTrack.isPlaying || mediaTrack.title.isNotBlank()
    val isNotificationActive = activeNotification != null
    val isTransferActive = activeTransfer.isActive
    val isReelCounterActive = settings.reelCounterEnabled &&
        (isInstagramForeground || System.currentTimeMillis() < showReelCounterUntil)

    val compactKind = when {
        isVoiceActive -> IslandCompactKind.VOICE_ASSISTANT
        isLockActive -> IslandCompactKind.LOCK
        isTimerActive -> IslandCompactKind.TIMER
        isNotificationActive -> IslandCompactKind.NOTIFICATION
        isTransferActive -> IslandCompactKind.TRANSFER
        isReelCounterActive -> IslandCompactKind.REEL_COUNTER
        isMediaActive -> IslandCompactKind.MEDIA
        isCharging -> IslandCompactKind.CHARGING
        else -> IslandCompactKind.IDLE
    }

    val isIdle = compactKind == IslandCompactKind.IDLE

    // Default pill width cleanly balances camera band and wings (no collapse to camera circle on idle)
    val compactWidth = settings.pillWidth.dp

    val maxCameraGap = (compactWidth - 96.dp).coerceAtLeast(36.dp)
    val cameraGap = settings.cutoutClearance.dp.coerceIn(36.dp, maxCameraGap)
    val wingWidth = ((compactWidth - cameraGap) / 2).coerceAtLeast(42.dp)
    val compactInset = 8.dp

    val targetWidth by animateDpAsState(
        targetValue = when {
            isSystemAlertActive -> 232.dp
            isExpanded -> settings.expandedWidth.dp
            else -> compactWidth
        },
        animationSpec = islandSpring,
        label = "islandWidth"
    )

    val baseExpandedHeight = settings.expandedHeight.coerceIn(140, 260)
    val expandedContentHeight = when {
        isVoiceActive -> 210
        isTimerActive -> 150
        isNotificationActive && activeNotification?.replyAction != null -> 180
        isTransferActive -> 132
        else -> baseExpandedHeight
    }

    val targetHeight by animateDpAsState(
        targetValue = when {
            isSystemAlertActive -> 48.dp
            isExpanded -> expandedContentHeight.dp
            else -> settings.pillHeight.dp
        },
        animationSpec = islandSpring,
        label = "islandHeight"
    )

    val collapsedCorner = settings.pillHeight.dp / 2
    val targetCorner by animateDpAsState(
        targetValue = when {
            isSystemAlertActive -> 24.dp
            isExpanded -> settings.expandedCornerRadius.dp
            else -> collapsedCorner
        },
        animationSpec = islandSpring,
        label = "islandCorner"
    )

    val targetElevation by animateDpAsState(
        targetValue = if (isExpanded) 18.dp else 0.dp,
        animationSpec = if (settings.reducedMotion) {
            tween(durationMillis = 90)
        } else {
            tween(durationMillis = 180, easing = FastOutSlowInEasing)
        },
        label = "islandElevation"
    )

    val borderAlpha by animateFloatAsState(
        targetValue = if (isExpanded || palette.borderWidthDp > 0f) 1f else 0f,
        animationSpec = if (settings.reducedMotion) {
            tween(durationMillis = 90)
        } else {
            tween(durationMillis = 160, easing = FastOutSlowInEasing)
        },
        label = "islandBorderAlpha"
    )

    val totalWidth = targetWidth + (flareWidth * 2)

    val shape = GloomNotchShape(
        flareWidth = flareWidth,
        flareHeight = flareHeight,
        bottomRadius = targetCorner,
        isNotch = isNotchStyle
    )
    val boxAlignment = when (settings.gravity) {
        "Left" -> Alignment.TopStart
        "Right" -> Alignment.TopEnd
        else -> Alignment.TopCenter
    }

    val contentKey = when {
        isVoiceActive -> "voice_assistant"
        isSystemAlertActive -> "system_alert"
        isExpanded && isTimerActive -> "timer"
        isExpanded && isNotificationActive -> "notification"
        isExpanded && isTransferActive -> "transfer"
        isExpanded && isMediaActive -> "media"
        isExpanded && isReelCounterActive -> "reels"
        isExpanded -> "continuity"
        else -> "compact-$compactKind"
    }

    val contentTransition = if (isExpanded) {
        ContentTransform(
            targetContentEnter = fadeIn(animationSpec = tween(durationMillis = 140, delayMillis = 60)) +
                scaleIn(
                    initialScale = 0.96f,
                    transformOrigin = TransformOrigin(0.5f, 0f),
                    animationSpec = spring(1f, 340f)
                ),
            initialContentExit = fadeOut(animationSpec = tween(durationMillis = 60)),
            sizeTransform = null
        )
    } else {
        ContentTransform(
            targetContentEnter = fadeIn(animationSpec = tween(durationMillis = 120, delayMillis = 80)) +
                scaleIn(
                    initialScale = 0.98f,
                    transformOrigin = TransformOrigin(0.5f, 0f),
                    animationSpec = spring(1f, 340f)
                ),
            initialContentExit = fadeOut(animationSpec = tween(durationMillis = 60)),
            sizeTransform = null
        )
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = boxAlignment) {
        // Expanded cards dismiss on a tap outside the island. Apple does not
        // dim the screen behind the Dynamic Island, so this layer stays clear.
        if (isExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures {
                            if (settings.hapticFeedback) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            onInputFocusChanged(false)
                            onToggleExpand()
                        }
                    }
            )
        }

        Box(
            modifier = Modifier
                .padding(top = topPadding)
                .offset(
                    x = settings.positionX.dp,
                    y = if (isNotchStyle) (-2).dp else 0.dp
                )
        ) {
            Box(
                modifier = Modifier
                    .width(totalWidth)
                    .height(targetHeight + (if (isNotchStyle) 2.dp else 0.dp))
                    .graphicsLayer {
                        scaleX = islandScale
                        scaleY = islandScale
                    }
                    .shadow(
                        elevation = targetElevation,
                        shape = shape,
                        clip = false,
                        ambientColor = Color.Black,
                        spotColor = Color.Black
                    )
                    .clip(shape)
                    .background(islandBrush)
                    .drawWithContent {
                        drawContent()
                        if (palette.borderWidthDp > 0f) {
                            val strokeColor = palette.borderColor
                            val hairlinePath = shape.createHairlineBorderPath(size, this)
                            if (settings.borderGlow) {
                                drawPath(
                                    path = hairlinePath,
                                    color = strokeColor.copy(alpha = 0.35f * borderAlpha),
                                    style = Stroke(
                                        width = (palette.borderWidthDp + 2f).dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                )
                            }
                            drawPath(
                                path = hairlinePath,
                                color = strokeColor.copy(alpha = borderAlpha.coerceAtLeast(0.85f)),
                                style = Stroke(
                                    width = palette.borderWidthDp.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            )
                        } else if (settings.showBorders) {
                            val borderColor = (if (settings.highContrast) Color(0x66FFFFFF) else Color(0x1FFFFFFF)).copy(
                                alpha = (if (settings.highContrast) 0.40f else 0.12f) * borderAlpha
                            )
                            val hairlinePath = shape.createHairlineBorderPath(size, this)
                            drawPath(
                                path = hairlinePath,
                                color = borderColor,
                                style = Stroke(
                                    width = 1f,
                                    cap = StrokeCap.Round
                                )
                            )
                        }
                    }
                    .semantics {
                        contentDescription = if (isExpanded) {
                            "GLOOM Dynamic Island, expanded activity. Tap outside to collapse."
                        } else {
                            "GLOOM Dynamic Island. Tap to expand, swipe down for notifications."
                        }
                    }
                    .pointerInput(isExpanded) {
                        if (!isExpanded) {
                            var totalDragY = 0f
                            detectVerticalDragGestures(
                                onDragStart = { totalDragY = 0f },
                                onDragEnd = { totalDragY = 0f },
                                onDragCancel = { totalDragY = 0f },
                                onVerticalDrag = { change, dragAmount ->
                                    totalDragY += dragAmount
                                    if (totalDragY > 20f) {
                                        change.consume()
                                        totalDragY = -1000f
                                        val isRight = change.position.x > (totalWidth.toPx() / 2f)
                                        if (settings.hapticFeedback) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        onSwipeDown(isRight)
                                    }
                                }
                            )
                        }
                    }
                    .pointerInput(isExpanded) {
                        if (!isExpanded) {
                            detectTapGestures(
                                onTap = {
                                    if (settings.hapticFeedback) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    onToggleExpand()
                                },
                                onLongPress = {
                                    if (settings.hapticFeedback) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                    if (settings.longPressToExpand) onLongPressExpand()
                                }
                            )
                        }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = flareWidth),
                    contentAlignment = Alignment.TopCenter
                ) {
                    AnimatedContent(
                        targetState = contentKey,
                        transitionSpec = { contentTransition },
                        contentAlignment = Alignment.TopCenter,
                        modifier = Modifier.fillMaxSize(),
                        label = "islandContentMorph"
                    ) { key ->
                    when (key) {
                        "voice_assistant" -> AppleVoiceAssistantActivity(
                            event = voiceAssistant ?: VoiceAssistantIslandEvent(),
                            onAskAgain = onVoiceAssistantAskAgain,
                            onClose = onVoiceAssistantClose
                        )
                        "system_alert" -> AppleSystemAlertActivity(
                            event = systemAlert ?: SystemAlertIslandEvent(),
                            onDismiss = onSystemAlertDismiss,
                            cameraGap = cameraGap
                        )
                        "timer" -> AppleTimerActivity(
                            timer = activeTimer ?: TimerIslandEvent(),
                            onPauseResume = onTimerPauseResume,
                            onCancel = onTimerCancel
                        )
                        "notification" -> AppleNotificationActivity(
                            event = activeNotification ?: NotificationIslandEvent(),
                            accentColor = palette.primaryAccent,
                            onOpen = {
                                onInputFocusChanged(false)
                                onNotificationClick()
                            },
                            onDismiss = {
                                onInputFocusChanged(false)
                                onNotificationDismiss()
                            },
                            onInputFocusChanged = onInputFocusChanged
                        )
                        "transfer" -> AppleTransferActivity(
                            transfer = activeTransfer,
                            accentColor = palette.primaryAccent,
                            onClose = onToggleExpand
                        )
                        "media" -> AppleMusicActivity(
                            track = mediaTrack,
                            onPlayPause = onMediaPlayPause,
                            onNext = onMediaNext,
                            onPrev = onMediaPrev,
                            onSeek = onMediaSeek,
                            onClose = onToggleExpand
                        )
                        "reels" -> AppleReelActivity(
                            reelCount = reelCount,
                            onClose = onToggleExpand
                        )
                        "continuity" -> AppleContinuityActivity(
                            device = bridgeDevice,
                            batteryLevel = batteryLevel,
                            isCharging = isCharging,
                            accentColor = palette.primaryAccent,
                            secondaryColor = palette.secondaryAccent,
                            onBeamFile = onBeamSampleFile,
                            onSyncClipboard = onSyncClipboard,
                            onClose = onToggleExpand
                        )
                        else -> AppleCompactContent(
                            kind = compactKind,
                            track = mediaTrack,
                            transfer = activeTransfer,
                            notification = activeNotification,
                            batteryLevel = batteryLevel,
                            isCharging = isCharging,
                            timerText = activeTimer?.formattedTime ?: "",
                            wingWidth = wingWidth,
                            cameraGap = cameraGap,
                            inset = compactInset,
                            reelCount = reelCount,
                            lockEvent = lockEvent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(settings.pillHeight.dp)
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
fun AppleReelActivity(
    reelCount: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = AppleIslandSpec.contentMargin,
                end = AppleIslandSpec.contentMargin,
                top = AppleIslandSpec.contentTop,
                bottom = 14.dp
            ),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CompactGlyphCircle(
                    icon = GloomIcons.Instagram,
                    tint = Color.White,
                    background = Color(0xFFE1306C)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Instagram Reels", style = IslandType.title, color = Color.White)
                    Text("Live Mindfulness Tracker", style = IslandType.caption, color = Apple.secondaryLabel)
                }
            }
            Text(
                text = "$reelCount",
                style = IslandType.heroNumber,
                color = Color(0xFFE1306C)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppleActivityButton(
                text = "Close",
                background = Color(0xFFE1306C),
                foreground = Color.White,
                modifier = Modifier.weight(1f),
                onClick = onClose
            )
        }
    }
}

@Composable
fun AppleVoiceAssistantActivity(
    event: VoiceAssistantIslandEvent,
    onAskAgain: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "voiceOrb")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "voicePulse"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = AppleIslandSpec.contentMargin,
                end = AppleIslandSpec.contentMargin,
                top = AppleIslandSpec.contentTop,
                bottom = 12.dp
            ),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    if (event.isSuccess) Apple.systemGreen else if (event.isFailed) Apple.systemRed else Apple.systemPurple,
                                    Apple.systemBlue,
                                    Color.Transparent
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    GloomIcon(
                        icon = if (event.isThinking) GloomIcons.Sparkles else GloomIcons.Waveform,
                        tint = Color.White,
                        size = (18f * if (event.isThinking) pulse else 1f).dp
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (event.isThinking) "Thinking…" else if (event.reply != null) "GLOOM" else event.status,
                        style = IslandType.title,
                        color = Color.White
                    )
                    Text(
                        text = if (event.transcript.isNotBlank()) event.transcript else "Speak a command or question…",
                        style = IslandType.caption,
                        color = Apple.secondaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (event.reply != null) {
            Text(
                text = event.reply,
                style = AppleType.subheadline,
                color = Color.White,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x22FFFFFF))
                    .padding(8.dp)
            )
        } else if (event.isThinking) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Processing…",
                    style = IslandType.subtitle,
                    color = Apple.secondaryLabel
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (event.reply != null || event.isFailed) {
                AppleActivityButton(
                    text = "Ask Again",
                    background = Apple.fillSecondary,
                    foreground = Color.White,
                    modifier = Modifier.weight(1f),
                    onClick = onAskAgain
                )
            }
            AppleActivityButton(
                text = "Done",
                background = Apple.systemBlue,
                foreground = Color.White,
                modifier = Modifier.weight(1f),
                onClick = onClose
            )
        }
    }
}
