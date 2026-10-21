package com.jarvis.island.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
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

    // ── Apple & HarmonyOS Critically Damped Spring Physics ──
    val springSpecDp = spring<androidx.compose.ui.unit.Dp>(
        dampingRatio = 0.68f, // Subtle organic bounce
        stiffness = 360f      // Ultra-fluid responsiveness
    )

    val targetWidth by animateDpAsState(
        targetValue = if (isExpanded) {
            settings.expandedWidth.dp
        } else if (activeNotification != null) {
            (settings.pillWidth + 56).coerceAtMost(360).dp
        } else {
            settings.pillWidth.dp
        },
        animationSpec = springSpecDp,
        label = "islandWidth"
    )

    val targetHeight by animateDpAsState(
        targetValue = if (isExpanded) settings.expandedHeight.dp else settings.pillHeight.dp,
        animationSpec = springSpecDp,
        label = "islandHeight"
    )

    val targetCorner by animateDpAsState(
        targetValue = if (isExpanded) settings.expandedCornerRadius.dp else settings.cornerRadius.dp,
        animationSpec = springSpecDp,
        label = "islandCorner"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "islandGlowTransition")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val boxAlignment = when (settings.gravity) {
        "Left" -> Alignment.TopStart
        "Right" -> Alignment.TopEnd
        else -> Alignment.TopCenter
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (isExpanded) {
                    Modifier
                        .background(Color(0x2A000000))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                if (settings.hapticFeedback) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                onToggleExpand()
                            }
                        )
                } else Modifier
            ),
        contentAlignment = boxAlignment
    ) {
        Box(
            modifier = Modifier
                .padding(top = settings.positionY.dp) // Calibrated 2px top
                .offset(x = settings.positionX.dp)
        ) {
            Card(
                shape = RoundedCornerShape(targetCorner),
                colors = CardDefaults.cardColors(containerColor = palette.background),
                modifier = Modifier
                    .width(targetWidth)
                    .height(targetHeight)
                    .defaultMinSize(minWidth = 140.dp, minHeight = 36.dp)
                    .shadow(
                        elevation = if (isExpanded) 28.dp else 10.dp,
                        shape = RoundedCornerShape(targetCorner),
                        ambientColor = Color(0xFF000000),
                        spotColor = if (isExpanded) palette.primaryAccent.copy(alpha = 0.4f) else Color(0x33000000)
                    )
                    .border(
                        width = if (settings.showBorders) (if (activeTransfer.isActive) 1.5.dp else 1.dp) else 0.dp,
                        brush = if (activeTransfer.isActive) {
                            Brush.linearGradient(
                                listOf(
                                    palette.primaryAccent.copy(alpha = pulseAlpha),
                                    palette.secondaryAccent.copy(alpha = pulseAlpha)
                                )
                            )
                        } else palette.borderBrush,
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
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // ── Left Wing: Official Vector Icons (Zero Emojis) ──
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
                                            .border(0.8.dp, palette.primaryAccent.copy(alpha = 0.6f), CircleShape)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Notifications,
                                        contentDescription = "Notification",
                                        tint = palette.primaryAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (activeNotification.appName.isNotBlank()) activeNotification.appName else "ALERT",
                                    color = palette.primaryAccent,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else if (activeTransfer.isActive) {
                                CyberBeamIcon(
                                    isUpload = activeTransfer.isUpload,
                                    accentColor = palette.primaryAccent,
                                    size = 20.dp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (activeTransfer.isUpload) "UP" else "DL",
                                    color = palette.primaryAccent,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black
                                )
                            } else if (mediaTrack.isPlaying || mediaTrack.title.isNotBlank()) {
                                OriginOSVinylRecord(
                                    isPlaying = mediaTrack.isPlaying,
                                    albumArt = mediaTrack.albumArt,
                                    size = 20.dp,
                                    labelColor = palette.primaryAccent
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (mediaTrack.title.isNotBlank()) mediaTrack.title.take(10) else (if (mediaTrack.appName.isNotBlank()) mediaTrack.appName else "MUSIC"),
                                    color = Color(0xEEFFFFFF),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                PulseConnectionDot(
                                    isConnected = bridgeDevice.isConnected,
                                    color = palette.secondaryAccent,
                                    size = 14.dp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (bridgeDevice.isConnected) "JARVIS" else "STANDALONE",
                                    color = Color(0xCCFFFFFF),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // ── DEAD-CENTER CAMERA CUTOUT HOLE CLEARANCE ──
                        Spacer(modifier = Modifier.width(settings.cutoutClearance.dp))

                        // ── Right Wing: Canonical Battery / Transfer % / Equalizer ──
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
                            } else if (activeTransfer.isActive) {
                                Text(
                                    text = "${(activeTransfer.progress * 100).toInt()}%",
                                    color = palette.primaryAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.SansSerif,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                                OriginOSEqualizer(
                                    isPlaying = true,
                                    color = palette.primaryAccent
                                )
                            } else if (mediaTrack.isPlaying || mediaTrack.title.isNotBlank()) {
                                OriginOSEqualizer(
                                    isPlaying = mediaTrack.isPlaying,
                                    color = palette.secondaryAccent
                                )
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.BatteryChargingFull,
                                        contentDescription = "Battery",
                                        tint = if (isCharging) Color(0xFF22C55E) else if (batteryLevel > 20) palette.primaryAccent else Color(0xFFEF4444),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "$batteryLevel%",
                                        color = if (isCharging) Color(0xFF22C55E) else if (batteryLevel > 20) Color(0xEEFFFFFF) else Color(0xFFEF4444),
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.SansSerif
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // ══════════════════════════════════════════════════════════
                    // 2. EXPANDED STUNNING APPLE LIVE ACTIVITY CARD
                    // ══════════════════════════════════════════════════════════
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // ── Top Camera Hole Clearance Notch ──
                        Spacer(modifier = Modifier.height((settings.pillHeight * 0.7f).dp))

                        // ── Header Bar: Apple Ecosystem & iOS Close Button ──
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
                                        .clip(CircleShape)
                                        .background(palette.primaryAccent.copy(alpha = 0.18f))
                                        .border(0.5.dp, palette.primaryAccent.copy(alpha = 0.35f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Computer,
                                        contentDescription = "Continuity",
                                        tint = palette.primaryAccent,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Text(
                                            text = if (bridgeDevice.isConnected) bridgeDevice.deviceName else "JARVIS Ecosystem",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(if (bridgeDevice.isConnected) Color(0xFF34C759) else Color(0x66FFFFFF))
                                        )
                                    }
                                    Text(
                                        text = if (bridgeDevice.isConnected) "${bridgeDevice.ipAddress} • ${bridgeDevice.pingMs}ms" else "Ecosystem Standby",
                                        color = Color(0x99FFFFFF),
                                        fontSize = 9.5.sp
                                    )
                                }
                            }

                            // iOS-style Circular Glass Minimize Button
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x22FFFFFF))
                                    .border(0.5.dp, Color(0x33FFFFFF), CircleShape)
                                    .combinedClickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {
                                            if (settings.hapticFeedback) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                            onToggleExpand()
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Close",
                                    tint = Color(0xDDFFFFFF),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        // ── Center Live Activity Presentation (Strict Apple HIG) ──
                        if (activeNotification != null) {
                            ExpressiveNotificationCard(
                                event = activeNotification,
                                accentColor = palette.primaryAccent,
                                onOpen = onNotificationClick,
                                onDismiss = onNotificationDismiss
                            )
                        } else if (activeTransfer.isActive) {
                            AppleAirDropCard(
                                transfer = activeTransfer,
                                accentColor = palette.primaryAccent,
                                secondaryColor = palette.secondaryAccent
                            )
                        } else if (mediaTrack.isPlaying || mediaTrack.title.isNotBlank()) {
                            OriginOSMusicCard(
                                track = mediaTrack,
                                accentColor = palette.primaryAccent,
                                onPlayPause = onMediaPlayPause,
                                onNext = onMediaNext,
                                onPrev = onMediaPrev,
                                onSeek = onMediaSeek
                            )
                        } else {
                            AppleContinuityCard(
                                device = bridgeDevice,
                                batteryLevel = batteryLevel,
                                isCharging = isCharging,
                                accentColor = palette.primaryAccent,
                                secondaryColor = palette.secondaryAccent,
                                onBeamFile = onBeamSampleFile,
                                onSyncClipboard = onSyncClipboard
                            )
                        }
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
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x18FFFFFF))
            .border(0.5.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        accentColor = accentColor,
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
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(accentColor.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = transfer.speedMbps,
                        color = accentColor,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )
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
                            Brush.horizontalGradient(listOf(accentColor, secondaryColor))
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
                    color = secondaryColor,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
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
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Battery & System Status Pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0x18FFFFFF))
                .border(0.5.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
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
                    tint = if (isCharging) Color(0xFF34C759) else if (batteryLevel > 20) accentColor else Color(0xFFFF453A),
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = "Battery: $batteryLevel%",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (isCharging) "Super-Fast Charging" else "Battery Healthy",
                        color = Color(0x99FFFFFF),
                        fontSize = 9.5.sp
                    )
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(secondaryColor.copy(alpha = 0.2f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (device.isConnected) "Continuity Synced" else "Standby",
                    color = secondaryColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
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
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.2f))
                    .border(0.5.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                    .clickable(onClick = onBeamFile)
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FileUpload,
                        contentDescription = "Beam",
                        tint = accentColor,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Beam to PC",
                        color = accentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Sync Clipboard Action
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x18FFFFFF))
                    .border(0.5.dp, Color(0x28FFFFFF), RoundedCornerShape(10.dp))
                    .clickable(onClick = onSyncClipboard)
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Sync",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Sync Clipboard",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
