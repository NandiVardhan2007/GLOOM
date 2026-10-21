package com.jarvis.island.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jarvis.island.settings.JarvisIslandSettings

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun JarvisIslandApp(
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onLongPressExpand: () -> Unit = {},
    onBeamSampleFile: () -> Unit = {},
    onSyncClipboard: () -> Unit = {},
    onOpenTrackpad: () -> Unit = {},
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
    settings: JarvisIslandSettings = JarvisIslandSettings.instance
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

    val topPadding by animateDpAsState(
        targetValue = (if (isExpanded) statusBarInset else 0.dp) + settings.positionY.dp,
        animationSpec = if (settings.reducedMotion) {
            tween(durationMillis = 90, easing = FastOutSlowInEasing)
        } else {
            spring(dampingRatio = 1f, stiffness = 320f)
        },
        label = "islandTop"
    )

    val isMediaActive = mediaTrack.isPlaying || mediaTrack.title.isNotBlank()
    val isNotificationActive = activeNotification != null
    val isTransferActive = activeTransfer.isActive

    val compactKind = when {
        isNotificationActive -> IslandCompactKind.NOTIFICATION
        isTransferActive -> IslandCompactKind.TRANSFER
        isMediaActive -> IslandCompactKind.MEDIA
        isCharging -> IslandCompactKind.CHARGING
        else -> IslandCompactKind.IDLE
    }

    val hasActivity = compactKind != IslandCompactKind.IDLE

    // Apple's canvas: 126 at rest, 230 with one Live Activity, 371 expanded.
    val compactWidth = if (hasActivity) settings.pillWidth.dp else AppleIslandSpec.restWidth
    val cameraGap = settings.cutoutClearance.dp.coerceIn(40.dp, compactWidth - 60.dp)
    val wingWidth = ((compactWidth - cameraGap) / 2).coerceAtLeast(30.dp)

    val targetWidth by animateDpAsState(
        targetValue = if (isExpanded) settings.expandedWidth.dp else compactWidth,
        animationSpec = if (settings.reducedMotion) {
            tween(durationMillis = 90, easing = FastOutSlowInEasing)
        } else {
            spring(dampingRatio = 1f, stiffness = 320f)
        },
        label = "islandWidth"
    )

    val baseExpandedHeight = settings.expandedHeight.coerceIn(160, 220)
    val expandedContentHeight = if (isTransferActive) 132 else baseExpandedHeight

    val targetHeight by animateDpAsState(
        targetValue = if (isExpanded) {
            expandedContentHeight.dp
        } else settings.pillHeight.dp,
        animationSpec = if (settings.reducedMotion) {
            tween(durationMillis = 90, easing = FastOutSlowInEasing)
        } else {
            spring(dampingRatio = 1f, stiffness = 320f)
        },
        label = "islandHeight"
    )

    val collapsedCorner = settings.pillHeight.dp / 2
    val targetCorner by animateDpAsState(
        targetValue = if (isExpanded) settings.expandedCornerRadius.dp else collapsedCorner,
        animationSpec = if (settings.reducedMotion) {
            tween(durationMillis = 90, easing = FastOutSlowInEasing)
        } else {
            spring(dampingRatio = 1f, stiffness = 320f)
        },
        label = "islandCorner"
    )

    val shape = RoundedCornerShape(targetCorner)
    val boxAlignment = when (settings.gravity) {
        "Left" -> Alignment.TopStart
        "Right" -> Alignment.TopEnd
        else -> Alignment.TopCenter
    }

    val contentKey = when {
        isExpanded && isNotificationActive -> "notification"
        isExpanded && isTransferActive -> "transfer"
        isExpanded && isMediaActive -> "media"
        isExpanded -> "continuity"
        else -> "compact-$compactKind"
    }

    val contentTransition = (
        fadeIn(animationSpec = tween(160)) +
            scaleIn(initialScale = 0.97f, animationSpec = spring(1f, 320f))
        ).togetherWith(fadeOut(animationSpec = tween(110)))

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
                .offset(x = settings.positionX.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(targetWidth)
                    .height(targetHeight)
                    .shadow(
                        elevation = if (isExpanded) 18.dp else 0.dp,
                        shape = shape,
                        clip = false,
                        ambientColor = Color.Black,
                        spotColor = Color.Black
                    )
                    .clip(shape)
                    .background(Color.Black)
                    .then(
                        if (isExpanded) {
                            Modifier.border(
                                width = 0.5.dp,
                                color = if (settings.highContrast) Color(0x66FFFFFF) else Color(0x1FFFFFFF),
                                shape = shape
                            )
                        } else Modifier
                    )
                    .semantics {
                        contentDescription = if (isExpanded) {
                            "JARVIS Dynamic Island, expanded activity. Tap outside to collapse."
                        } else {
                            "JARVIS Dynamic Island. Tap to expand, touch and hold to speak."
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
                        "continuity" -> AppleContinuityActivity(
                            device = bridgeDevice,
                            batteryLevel = batteryLevel,
                            isCharging = isCharging,
                            accentColor = palette.primaryAccent,
                            secondaryColor = palette.secondaryAccent,
                            onBeamFile = onBeamSampleFile,
                            onSyncClipboard = onSyncClipboard,
                            onOpenTrackpad = onOpenTrackpad,
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
                            inset = AppleIslandSpec.compactInset
                        )
                    }
                }
            }
        }
    }
}
