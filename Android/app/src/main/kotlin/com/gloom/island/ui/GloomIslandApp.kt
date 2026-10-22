package com.gloom.island.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
    onSwipeDown: () -> Unit = {},
    settings: GloomIslandSettings = GloomIslandSettings.instance
) {
    if (!settings.isIslandEnabled) return

    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val palette = remember(settings.themeStyle) { IslandThemePalettes.getTheme(settings.themeStyle) }

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

    val islandSpring: AnimationSpec<Dp> = if (settings.reducedMotion) {
        tween(durationMillis = 90, easing = FastOutSlowInEasing)
    } else {
        spring(dampingRatio = 1f, stiffness = 340f)
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

    val isMediaActive = mediaTrack.isPlaying || mediaTrack.title.isNotBlank()
    val isNotificationActive = activeNotification != null
    val isTransferActive = activeTransfer.isActive
    val isReelCounterActive = settings.reelCounterEnabled &&
        (isInstagramForeground || System.currentTimeMillis() < showReelCounterUntil)

    val compactKind = when {
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
    val wingWidth = ((compactWidth - cameraGap) / 2).coerceAtLeast(46.dp)
    val compactInset = 8.dp

    val targetWidth by animateDpAsState(
        targetValue = if (isExpanded) settings.expandedWidth.dp else compactWidth,
        animationSpec = islandSpring,
        label = "islandWidth"
    )

    val baseExpandedHeight = settings.expandedHeight.coerceIn(140, 260)
    val expandedContentHeight = if (isTransferActive) 132 else baseExpandedHeight

    val targetHeight by animateDpAsState(
        targetValue = if (isExpanded) {
            expandedContentHeight.dp
        } else settings.pillHeight.dp,
        animationSpec = islandSpring,
        label = "islandHeight"
    )

    val collapsedCorner = settings.pillHeight.dp / 2
    val targetCorner by animateDpAsState(
        targetValue = if (isExpanded) settings.expandedCornerRadius.dp else collapsedCorner,
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
        targetValue = if (isExpanded) 1f else 0f,
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
        isExpanded && isNotificationActive -> "notification"
        isExpanded && isTransferActive -> "transfer"
        isExpanded && isMediaActive -> "media"
        isExpanded && isReelCounterActive -> "reels"
        isExpanded -> "continuity"
        else -> "compact-$compactKind"
    }

    val contentTransition = if (isExpanded) {
        (fadeIn(animationSpec = tween(durationMillis = 140, delayMillis = 60)) +
            scaleIn(initialScale = 0.96f, animationSpec = spring(1f, 340f)))
            .togetherWith(fadeOut(animationSpec = tween(durationMillis = 60)))
    } else {
        (fadeIn(animationSpec = tween(durationMillis = 120, delayMillis = 80)) +
            scaleIn(initialScale = 0.98f, animationSpec = spring(1f, 340f)))
            .togetherWith(fadeOut(animationSpec = tween(durationMillis = 60)))
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
                    .shadow(
                        elevation = targetElevation,
                        shape = shape,
                        clip = false,
                        ambientColor = Color.Black,
                        spotColor = Color.Black
                    )
                    .clip(shape)
                    .background(Color.Black)
                    .drawWithContent {
                        drawContent()
                        if (settings.showBorders) {
                            val borderColor = (if (settings.highContrast) Color(0x66FFFFFF) else Color(0x1FFFFFFF)).copy(
                                alpha = (if (settings.highContrast) 0.40f else 0.12f) * borderAlpha.toFloat()
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
                            detectVerticalDragGestures { _, dragAmount ->
                                if (dragAmount > 14f) {
                                    if (settings.hapticFeedback) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    onSwipeDown()
                                }
                            }
                        }
                    }
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            if (settings.hapticFeedback) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            if (!isExpanded) onToggleExpand()
                        },
                        onLongClick = {
                            if (settings.hapticFeedback) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            if (!isExpanded && settings.longPressToExpand) onLongPressExpand()
                        }
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = flareWidth)
                ) {
                    AnimatedContent(
                        targetState = contentKey,
                        transitionSpec = { contentTransition },
                        label = "islandContentMorph"
                    ) { key ->
                    when (key) {
                        "notification" -> AppleNotificationActivity(
                            event = activeNotification ?: NotificationIslandEvent(),
                            accentColor = palette.primaryAccent,
                            onOpen = onNotificationClick,
                            onDismiss = {
                                onNotificationDismiss()
                            }
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
                            timerText = "",
                            wingWidth = wingWidth,
                            cameraGap = cameraGap,
                            inset = compactInset,
                            reelCount = reelCount
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
