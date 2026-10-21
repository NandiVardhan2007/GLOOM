package com.jarvis.island.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val palette = remember(settings.themeStyle) { IslandThemePalettes.getTheme(settings.themeStyle) }

    val isMediaActive = mediaTrack.isPlaying || mediaTrack.title.isNotBlank()
    val isNotificationActive = activeNotification != null
    val isTransferActive = activeTransfer.isActive

    // Motion specification: critically damped spring or reduced motion tween
    val animationSpecDp = if (settings.reducedMotion) {
        tween<androidx.compose.ui.unit.Dp>(durationMillis = 90, easing = FastOutSlowInEasing)
    } else {
        spring<androidx.compose.ui.unit.Dp>(
            dampingRatio = 0.9f, // Near-critical: settles cleanly, no visible bounce
            stiffness = 320f
        )
    }

    // Dynamic Width:
    // Expanded -> 380dp
    // Notification -> ~230-360dp
    // Transfer or Music -> 184dp
    // Charging -> 140dp
    // Idle -> 104dp (pure compact punch-hole hugger, zero battery display)
    val targetWidth by animateDpAsState(
        targetValue = when {
            isExpanded -> settings.expandedWidth.dp
            isNotificationActive -> (settings.pillWidth + 48).coerceAtMost(360).dp
            isTransferActive -> 184.dp
            isMediaActive -> 184.dp
            isCharging -> 140.dp
            else -> 104.dp
        },
        animationSpec = animationSpecDp,
        label = "islandWidth"
    )

    val targetHeight by animateDpAsState(
        targetValue = if (isExpanded) {
            if (isMediaActive) 250.dp else settings.expandedHeight.dp.coerceIn(176.dp, 220.dp)
        } else {
            settings.pillHeight.dp
        },
        animationSpec = animationSpecDp,
        label = "islandHeight"
    )

    val targetCorner by animateDpAsState(
        targetValue = if (isExpanded) settings.expandedCornerRadius.dp else settings.cornerRadius.dp,
        animationSpec = animationSpecDp,
        label = "islandCorner"
    )

    val boxAlignment = when (settings.gravity) {
        "Left" -> Alignment.TopStart
        "Right" -> Alignment.TopEnd
        else -> Alignment.TopCenter
    }

    // Outer Screen Container:
    // When expanded, tapping anywhere outside the island card collapses it immediately.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (isExpanded) {
                    Modifier
                        .background(Color(0x28000000))
                        .pointerInput(Unit) {
                            detectTapGestures {
                                if (settings.hapticFeedback) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                onToggleExpand()
                            }
                        }
                } else Modifier
            ),
        contentAlignment = boxAlignment
    ) {
        Box(
            modifier = Modifier
                .padding(top = settings.positionY.dp) // Calibrated default 2px top
                .offset(x = settings.positionX.dp)
        ) {
            Card(
                shape = RoundedCornerShape(targetCorner),
                colors = CardDefaults.cardColors(containerColor = if (settings.highContrast) Color.Black else palette.background),
                modifier = Modifier
                    .width(targetWidth)
                    .height(targetHeight)
                    .defaultMinSize(minWidth = 100.dp, minHeight = 36.dp)
                    .semantics {
                        contentDescription = if (isExpanded) {
                            "JARVIS Dynamic Island overlay, expanded activity card. Tap outside to collapse."
                        } else {
                            "JARVIS Dynamic Island overlay pill. Tap to expand."
                        }
                    }
                    .shadow(
                        elevation = if (isExpanded) 16.dp else 6.dp,
                        shape = RoundedCornerShape(targetCorner),
                        ambientColor = Color(0xFF000000),
                        spotColor = Color(0x40000000)
                    )
                    .border(
                        width = if (settings.showBorders) 0.5.dp else 0.dp,
                        brush = palette.borderBrush,
                        shape = RoundedCornerShape(targetCorner)
                    )
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            if (settings.hapticFeedback) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            if (!isExpanded) {
                                onToggleExpand()
                            }
                        },
                        onLongClick = {
                            if (settings.hapticFeedback) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            if (!isExpanded && settings.longPressToExpand) {
                                onLongPressExpand()
                            }
                        }
                    )
            ) {
                AnimatedContent(
                    targetState = isExpanded,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(180, delayMillis = 60)) +
                                scaleIn(initialScale = 0.90f, animationSpec = spring(0.72f, 380f)))
                            .togetherWith(
                                fadeOut(animationSpec = tween(120)) +
                                        scaleOut(targetScale = 0.90f, animationSpec = tween(120))
                            )
                    },
                    label = "islandContentMorph"
                ) { expandedState ->
                    if (!expandedState) {
                        // ══════════════════════════════════════════════════════════
                        // 1. COLLAPSED PILL (Strict Center Punch-Hole Clearance)
                        // ══════════════════════════════════════════════════════════
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // ── Left Wing ──
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Start
                            ) {
                                if (activeNotification != null) {
                                    if (activeNotification.largeIcon != null) {
                                        Image(
                                            bitmap = activeNotification.largeIcon.asImageBitmap(),
                                            contentDescription = "Avatar",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .border(0.5.dp, Color(0x44FFFFFF), CircleShape)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.Notifications,
                                            contentDescription = "Notification",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (activeNotification.appName.isNotBlank()) activeNotification.appName else "ALERT",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                } else if (isTransferActive) {
                                    CyberBeamIcon(
                                        isUpload = activeTransfer.isUpload,
                                        accentColor = Color.White,
                                        size = 18.dp
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (activeTransfer.isUpload) "UP" else "DL",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else if (isMediaActive) {
                                    OriginOSVinylRecord(
                                        isPlaying = mediaTrack.isPlaying,
                                        albumArt = mediaTrack.albumArt,
                                        size = 20.dp,
                                        labelColor = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = if (mediaTrack.title.isNotBlank()) mediaTrack.title.take(10) else "MUSIC",
                                        color = Color(0xEEFFFFFF),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                } else if (isCharging) {
                                    Icon(
                                        imageVector = Icons.Rounded.BatteryChargingFull,
                                        contentDescription = "Charging",
                                        tint = Color(0xFF30D158),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "CHARGING",
                                        color = Color(0xFF30D158),
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {
                                    // Stealth Idle: Minimal subtle dot, zero robotic status text
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(Color(0x33FFFFFF))
                                    )
                                }
                            }

                            // ── DEAD-CENTER CAMERA CUTOUT HOLE CLEARANCE ──
                            Spacer(modifier = Modifier.width(settings.cutoutClearance.dp))

                            // ── Right Wing ──
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End
                            ) {
                                if (activeNotification != null) {
                                    Text(
                                        text = if (activeNotification.title.isNotBlank()) activeNotification.title else activeNotification.text,
                                        color = Color.White,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                } else if (isTransferActive) {
                                    Text(
                                        text = "${(activeTransfer.progress * 100).toInt()}%",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.SansSerif,
                                        modifier = Modifier.padding(end = 4.dp)
                                    )
                                    OriginOSEqualizer(
                                        isPlaying = true,
                                        color = Color.White
                                    )
                                } else if (isMediaActive) {
                                    OriginOSEqualizer(
                                        isPlaying = mediaTrack.isPlaying,
                                        color = Color.White
                                    )
                                } else if (isCharging) {
                                    // Battery percentage shown ONLY when actively charging!
                                    Text(
                                        text = "$batteryLevel%",
                                        color = Color(0xFF30D158),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.SansSerif
                                    )
                                } else {
                                    // Idle: Nothing shown! No battery! Completely clean and stealth.
                                }
                            }
                        }
                    } else {
                        // ══════════════════════════════════════════════════════════
                        // 2. EXPANDED CLEAN APPLE LIVE ACTIVITY CARD
                        // (Single unified header, zero duplicate IP/ping debug clutter)
                        // ══════════════════════════════════════════════════════════
                        if (activeNotification != null) {
                            ExpressiveNotificationCard(
                                event = activeNotification,
                                accentColor = palette.primaryAccent,
                                onOpen = onNotificationClick,
                                onDismiss = onNotificationDismiss,
                                onClose = onToggleExpand
                            )
                        } else if (isTransferActive) {
                            AppleAirDropCard(
                                transfer = activeTransfer,
                                accentColor = palette.primaryAccent,
                                secondaryColor = palette.secondaryAccent,
                                onClose = onToggleExpand
                            )
                        } else if (isMediaActive) {
                            OriginOSMusicCard(
                                track = mediaTrack,
                                accentColor = palette.primaryAccent,
                                onPlayPause = onMediaPlayPause,
                                onNext = onMediaNext,
                                onPrev = onMediaPrev,
                                onSeek = onMediaSeek,
                                onClose = onToggleExpand
                            )
                        } else {
                            AppleContinuityCard(
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
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppleAirDropCard(
    transfer: ActiveTransferInfo,
    accentColor: Color,
    secondaryColor: Color,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Camera Clearance Notch
        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                CyberBeamIcon(
                    isUpload = transfer.isUpload,
                    accentColor = Color.White,
                    size = 22.dp
                )
                Column {
                    Text(
                        text = transfer.fileName,
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (transfer.isUpload) "AirDrop Beam • Windows Desktop" else "AirDrop Incoming • JARVIS PC",
                        color = Color(0x99FFFFFF),
                        fontSize = 9.5.sp
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x22FFFFFF))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = transfer.speedMbps,
                        color = Color.White,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (onClose != null) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0x18FFFFFF))
                            .border(0.5.dp, Color(0x22FFFFFF), CircleShape)
                            .clickable { onClose() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = Color(0xDDFFFFFF),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }

        // Apple Progress Track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0x22FFFFFF))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(transfer.progress.coerceIn(0.01f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Brush.horizontalGradient(listOf(Color.White, Color(0xFF30D158)))
                    )
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${(transfer.fileSizeMb * transfer.progress).toInt()} MB of ${transfer.fileSizeMb} MB",
                color = Color(0x88FFFFFF),
                fontSize = 9.5.sp
            )
            Text(
                text = if (transfer.progress >= 1f) "Transfer Complete" else "${(transfer.progress * 100).toInt()}% • 5G Direct",
                color = Color(0xFF30D158),
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AppleContinuityCard(
    device: BridgeDeviceInfo,
    batteryLevel: Int,
    isCharging: Boolean,
    accentColor: Color,
    secondaryColor: Color,
    onBeamFile: () -> Unit,
    onSyncClipboard: () -> Unit,
    onOpenTrackpad: () -> Unit = {},
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Camera Hole Clearance Notch
        Spacer(modifier = Modifier.height(18.dp))

        // Clean Header (Zero technical IP addresses or ping numbers)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x18FFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Computer,
                        contentDescription = "Continuity",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Column {
                    Text(
                        text = if (device.isConnected) "JARVIS Ecosystem" else "JARVIS Standby",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (device.isConnected) "Live Continuity Active" else "Ready to Connect",
                        color = Color(0x99FFFFFF),
                        fontSize = 11.5.sp
                    )
                }
            }

            if (onClose != null) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0x18FFFFFF))
                        .border(0.5.dp, Color(0x22FFFFFF), CircleShape)
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = Color(0xDDFFFFFF),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }

        // Show Battery Status Pill ONLY IF CHARGING (Clean charging animation, no 24/7 battery clutter)
        if (isCharging) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x18FFFFFF))
                    .border(0.5.dp, Color(0x22FFFFFF), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BatteryChargingFull,
                        contentDescription = "Battery",
                        tint = Color(0xFF30D158),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Super-Fast Charging • $batteryLevel%",
                        color = Color.White,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = "Active",
                    color = Color(0xFF30D158),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            // Sleek Ecosystem Connectivity Pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x10FFFFFF))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (device.isConnected) Color(0xFF30D158) else Color(0x44FFFFFF))
                    )
                    Text(
                        text = if (device.isConnected) "High-Speed LAN Link" else "Local Wireless Standby",
                        color = Color(0xCCFFFFFF),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = if (device.isConnected) "Synced" else "Ready",
                    color = if (device.isConnected) Color(0xFF30D158) else Color(0x88FFFFFF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Ecosystem Actions (Beam to PC & Sync Clipboard)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Beam to PC Action
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x1AFFFFFF))
                    .clickable(onClick = onBeamFile)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FileUpload,
                        contentDescription = "Beam",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Beam to PC",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Sync Clipboard Action
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x1AFFFFFF))
                    .clickable(onClick = onSyncClipboard)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Sync",
                        tint = Color(0xCCFFFFFF),
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Sync Clip",
                        color = Color(0xE6FFFFFF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // PC Remote Mousepad Action
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x1AFFFFFF))
                    .clickable(onClick = onOpenTrackpad)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Mouse,
                        contentDescription = "Mouse",
                        tint = Color(0xE6FFFFFF),
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Mousepad",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
