package com.jarvis.island.ui

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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.island.settings.JarvisIslandSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun JarvisIslandApp(
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onBeamSampleFile: () -> Unit = {},
    onSyncClipboard: () -> Unit = {},
    activeTransfer: ActiveTransferInfo = ActiveTransferInfo(),
    mediaTrack: MediaTrackInfo = MediaTrackInfo(),
    bridgeDevice: BridgeDeviceInfo = BridgeDeviceInfo(),
    settings: JarvisIslandSettings = JarvisIslandSettings.instance
) {
    // Apple / HarmonyOS fluid critically damped spring curves
    val targetWidth by animateDpAsState(
        targetValue = if (isExpanded) 364.dp else settings.pillWidth.dp,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "islandWidth"
    )

    val targetHeight by animateDpAsState(
        targetValue = if (isExpanded) 232.dp else settings.pillHeight.dp,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "islandHeight"
    )

    val targetCorner by animateDpAsState(
        targetValue = if (isExpanded) 26.dp else settings.cornerRadius.dp,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "islandCorner"
    )

    var currentTab by remember { mutableStateOf("transfer") } // "transfer" | "media" | "status"

    // Pulsing beam glow animation for active file transfer
    val infiniteTransition = rememberInfiniteTransition(label = "beamPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = settings.positionY.dp)
            .offset(x = settings.positionX.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Card(
            shape = RoundedCornerShape(targetCorner),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF000000)
            ),
            modifier = Modifier
                .width(targetWidth)
                .height(targetHeight)
                .shadow(
                    elevation = if (isExpanded) 24.dp else 8.dp,
                    shape = RoundedCornerShape(targetCorner),
                    ambientColor = Color(0xFF000000),
                    spotColor = if (isExpanded) Color(0x6638BDF8) else Color(0x33000000)
                )
                .border(
                    width = if (activeTransfer.isActive) 1.5.dp else 1.dp,
                    color = if (activeTransfer.isActive) Color(0xFF38BDF8).copy(alpha = pulseAlpha)
                            else if (isExpanded) Color(0x33FFFFFF) else Color(0x22FFFFFF),
                    shape = RoundedCornerShape(targetCorner)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggleExpand
                )
        ) {
            if (!isExpanded) {
                // ── Collapsed Pill (Physical Camera Cutout Clearance Layout) ──
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. LEFT SLOT (Positioned safely to the LEFT of the center punch hole)
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        if (activeTransfer.isActive) {
                            // High-speed beaming upload/download arrow
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x3338BDF8)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (activeTransfer.isUpload) "↑" else "↓",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        } else {
                            // LAN green pulse connection indicator
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x3322C55E)),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF22C55E))
                                )
                            }
                        }
                    }

                    // 2. DEAD-CENTER CUTOUT CLEARANCE (Camera Hole sits right here with 0 text obstruction)
                    Spacer(modifier = Modifier.width(settings.cutoutClearance.dp))

                    // 3. RIGHT SLOT (Positioned safely to the RIGHT of the center punch hole)
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (activeTransfer.isActive) {
                            Text(
                                text = "${(activeTransfer.progress * 100).toInt()}%",
                                color = Color(0xFF38BDF8),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            AnimatedEqualizer(
                                isPlaying = true,
                                color = Color(0xFF38BDF8)
                            )
                        } else {
                            AnimatedEqualizer(
                                isPlaying = mediaTrack.isPlaying,
                                color = Color(0xFF38BDF8)
                            )
                        }
                    }
                }
            } else {
                // ── Expanded Stunning Obsidian Card ──
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Header: LiveBridge Device & Connection Status
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
                                    .background(Color(0x2238BDF8)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "⚡", fontSize = 14.sp)
                            }
                            Column {
                                Text(
                                    text = bridgeDevice.deviceName,
                                    color = Color.White,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${bridgeDevice.ipAddress} • ${bridgeDevice.pingMs}ms latency",
                                    color = Color(0x99FFFFFF),
                                    fontSize = 10.5.sp
                                )
                            }
                        }

                        // Subtab pills
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("transfer", "media").forEach { tab ->
                                val active = currentTab == tab
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (active) Color(0x33FFFFFF) else Color(0x11FFFFFF))
                                        .clickable { currentTab = tab }
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = tab.replaceFirstChar { it.uppercase() },
                                        color = if (active) Color.White else Color(0x88FFFFFF),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Center Content Card
                    if (currentTab == "transfer") {
                        // High-Speed Local File Beam Card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x11FFFFFF))
                                .border(1.dp, Color(0x2238BDF8), RoundedCornerShape(12.dp))
                                .padding(10.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = activeTransfer.fileName,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Text(
                                        text = activeTransfer.speedMbps,
                                        color = Color(0xFF38BDF8),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // Glowing Cyan Gradient Progress Bar
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
                                                    listOf(Color(0xFF0284C7), Color(0xFF38BDF8))
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
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        text = "${(activeTransfer.progress * 100).toInt()}% • 5G WiFi Direct",
                                        color = Color(0xFF22C55E),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    } else {
                        // Music Card
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x11FFFFFF))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1E293B)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "🎵", fontSize = 18.sp)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mediaTrack.title,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = mediaTrack.artist,
                                    color = Color(0x99FFFFFF),
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                            AnimatedEqualizer(
                                isPlaying = mediaTrack.isPlaying,
                                color = Color(0xFF22C55E)
                            )
                        }
                    }

                    // Bottom Action Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x2238BDF8))
                                .border(1.dp, Color(0x4438BDF8), RoundedCornerShape(8.dp))
                                .clickable { onBeamSampleFile() }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Beam File to PC",
                                color = Color(0xFF38BDF8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x15FFFFFF))
                                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(8.dp))
                                .clickable { onSyncClipboard() }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
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
    }
}
