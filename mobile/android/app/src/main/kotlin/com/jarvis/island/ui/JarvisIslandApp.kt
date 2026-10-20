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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
        targetValue = if (isExpanded) settings.expandedWidth.dp else settings.pillWidth.dp,
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

    var currentTab by remember { mutableStateOf("transfer") } // "transfer" | "music" | "system"

    androidx.compose.runtime.LaunchedEffect(mediaTrack.isPlaying, mediaTrack.title, activeTransfer.isActive) {
        if (activeTransfer.isActive) {
            currentTab = "transfer"
        } else if (mediaTrack.isPlaying || mediaTrack.title.isNotBlank()) {
            currentTab = "music"
        }
    }

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
            .fillMaxWidth()
            .padding(top = settings.positionY.dp) // User default 2px top
            .offset(x = settings.positionX.dp),
        contentAlignment = boxAlignment
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
                        onToggleExpand()
                    },
                    onLongClick = {
                        if (settings.hapticFeedback) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        if (settings.longPressToExpand) {
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
                            if (activeTransfer.isActive) {
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
                            } else if (isCharging) {
                                Icon(
                                    imageVector = Icons.Rounded.BatteryChargingFull,
                                    contentDescription = "Charging",
                                    tint = palette.secondaryAccent,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "$batteryLevel%",
                                    color = palette.secondaryAccent,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                PulseConnectionDot(
                                    isConnected = bridgeDevice.isConnected,
                                    color = palette.secondaryAccent,
                                    size = 14.dp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "JARVIS",
                                    color = Color(0xCCFFFFFF),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // ── DEAD-CENTER CAMERA CUTOUT HOLE CLEARANCE ──
                        Spacer(modifier = Modifier.width(settings.cutoutClearance.dp))

                        // ── Right Wing: Transfer % or Equalizer Waveform or Battery ──
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (activeTransfer.isActive) {
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
                                        tint = if (batteryLevel > 20) palette.primaryAccent else Color(0xFFEF4444),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "$batteryLevel%",
                                        color = if (batteryLevel > 20) Color(0xCCFFFFFF) else Color(0xFFEF4444),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // ══════════════════════════════════════════════════════════
                    // 2. EXPANDED STUNNING OBSIDIAN CARD (SmartIsland Parity)
                    // ══════════════════════════════════════════════════════════
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // ── Top Camera Hole Clearance Notch ──
                        Spacer(modifier = Modifier.height((settings.pillHeight * 0.7f).dp))

                        // ── Header Bar: Device Info & Subtab Selector ──
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
                                        .size(26.dp)
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(palette.primaryAccent.copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Bolt,
                                        contentDescription = "JARVIS Continuity",
                                        tint = palette.primaryAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = bridgeDevice.deviceName,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${bridgeDevice.ipAddress} • ${bridgeDevice.pingMs}ms",
                                        color = Color(0x99FFFFFF),
                                        fontSize = 9.5.sp
                                    )
                                }
                            }

                            // Subtab Pills (Official Vector Icons)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("transfer", "music", "system").forEach { tab ->
                                    val active = currentTab == tab
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (active) palette.primaryAccent.copy(alpha = 0.25f) else Color(0x11FFFFFF))
                                            .border(
                                                width = if (active) 1.dp else 0.dp,
                                                color = if (active) palette.primaryAccent.copy(alpha = 0.6f) else Color.Transparent,
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .combinedClickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = {
                                                    if (settings.hapticFeedback) {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    }
                                                    currentTab = tab
                                                }
                                            )
                                            .padding(horizontal = 7.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = tab.replaceFirstChar { it.uppercase() },
                                            color = if (active) palette.primaryAccent else Color(0x88FFFFFF),
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // ── Center Content Area ──
                        when (currentTab) {
                            "transfer" -> {
                                // High-Speed File Beam Card
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(palette.cardBackground)
                                        .border(1.dp, palette.primaryAccent.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                        .padding(10.dp)
                                ) {
                                    if (activeTransfer.isActive) {
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    CyberBeamIcon(
                                                        isUpload = activeTransfer.isUpload,
                                                        accentColor = palette.primaryAccent,
                                                        size = 20.dp
                                                    )
                                                    Text(
                                                        text = activeTransfer.fileName,
                                                        color = Color.White,
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Text(
                                                    text = activeTransfer.speedMbps,
                                                    color = palette.primaryAccent,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            // Glowing Gradient Progress Bar
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(7.dp)
                                                    .clip(RoundedCornerShape(3.5.dp))
                                                    .background(Color(0x22FFFFFF))
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth(activeTransfer.progress.coerceIn(0.01f, 1f))
                                                        .height(7.dp)
                                                        .clip(RoundedCornerShape(3.5.dp))
                                                        .background(
                                                            Brush.horizontalGradient(
                                                                listOf(palette.primaryAccent, palette.secondaryAccent)
                                                            )
                                                        )
                                                )
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "${(activeTransfer.fileSizeMb * activeTransfer.progress).toInt()} MB of ${activeTransfer.fileSizeMb} MB",
                                                    color = Color(0x88FFFFFF),
                                                    fontSize = 9.5.sp
                                                )
                                                Text(
                                                    text = "${(activeTransfer.progress * 100).toInt()}% • 5G WiFi Direct",
                                                    color = palette.secondaryAccent,
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                CyberBeamIcon(
                                                    isUpload = false,
                                                    accentColor = palette.primaryAccent,
                                                    size = 22.dp
                                                )
                                                Column {
                                                    Text(
                                                        text = "File Beam Standby",
                                                        color = Color.White,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = "5G WiFi Direct • Port 53391",
                                                        color = Color(0x99FFFFFF),
                                                        fontSize = 9.5.sp
                                                    )
                                                }
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(palette.primaryAccent.copy(alpha = 0.2f))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = "Ready",
                                                    color = palette.primaryAccent,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            "music" -> {
                                OriginOSMusicCard(
                                    track = mediaTrack,
                                    accentColor = palette.primaryAccent,
                                    onPlayPause = onMediaPlayPause,
                                    onNext = onMediaNext,
                                    onPrev = onMediaPrev,
                                    onSeek = onMediaSeek
                                )
                            }

                            else -> {
                                // System & Continuity Card
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(palette.cardBackground)
                                        .padding(10.dp),
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
                                            tint = palette.secondaryAccent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column {
                                            Text(text = "Battery: $batteryLevel%", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = if (isCharging) "Super-Fast Charging" else "Discharging",
                                                color = Color(0x99FFFFFF),
                                                fontSize = 9.5.sp
                                            )
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(palette.secondaryAccent.copy(alpha = 0.2f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(text = "Synced", color = palette.secondaryAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // ── Bottom Action Bar (Official Icons, Zero Emojis) ──
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(palette.primaryAccent.copy(alpha = 0.2f))
                                    .border(1.dp, palette.primaryAccent.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .combinedClickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {
                                            if (settings.hapticFeedback) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                            onBeamSampleFile()
                                        }
                                    )
                                    .padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.FileUpload,
                                        contentDescription = "Beam",
                                        tint = palette.primaryAccent,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Beam to PC",
                                        color = palette.primaryAccent,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x15FFFFFF))
                                    .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(8.dp))
                                    .combinedClickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {
                                            if (settings.hapticFeedback) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                            onSyncClipboard()
                                        }
                                    )
                                    .padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ContentCopy,
                                        contentDescription = "Sync",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Sync Clipboard",
                                        color = Color.White,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x15FFFFFF))
                                    .combinedClickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {
                                            if (settings.hapticFeedback) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                            onToggleExpand()
                                        }
                                    )
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Close",
                                    tint = Color(0xCCFFFFFF),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
