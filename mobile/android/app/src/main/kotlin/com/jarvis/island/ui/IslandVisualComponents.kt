package com.jarvis.island.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

/**
 * Visual Theme Colors & Brushes for JARVIS Dynamic Island
 */
data class IslandThemeColors(
    val background: Color,
    val cardBackground: Color,
    val primaryAccent: Color,
    val secondaryAccent: Color,
    val borderBrush: Brush
)

object IslandThemePalettes {
    fun getTheme(themeName: String): IslandThemeColors {
        return when (themeName) {
            "Cyber Cyan" -> IslandThemeColors(
                background = Color(0xFF030712),
                cardBackground = Color(0xFF0B132B),
                primaryAccent = Color(0xFF38BDF8),
                secondaryAccent = Color(0xFF00E5FF),
                borderBrush = Brush.linearGradient(
                    listOf(Color(0xFF38BDF8), Color(0xFF00E5FF), Color(0x3338BDF8))
                )
            )
            "Neon Green" -> IslandThemeColors(
                background = Color(0xFF021008),
                cardBackground = Color(0xFF071911),
                primaryAccent = Color(0xFF10B981),
                secondaryAccent = Color(0xFF34D399),
                borderBrush = Brush.linearGradient(
                    listOf(Color(0xFF10B981), Color(0xFF059669), Color(0x3310B981))
                )
            )
            "Material Purple" -> IslandThemeColors(
                background = Color(0xFF0E0414),
                cardBackground = Color(0xFF1B0B26),
                primaryAccent = Color(0xFFC084FC),
                secondaryAccent = Color(0xFFA855F7),
                borderBrush = Brush.linearGradient(
                    listOf(Color(0xFFC084FC), Color(0xFF9333EA), Color(0x33C084FC))
                )
            )
            else -> IslandThemeColors( // "Obsidian Black" (Default)
                background = Color(0xFF000000),
                cardBackground = Color(0xFF0D0D12),
                primaryAccent = Color(0xFF38BDF8),
                secondaryAccent = Color(0xFF22C55E),
                borderBrush = Brush.linearGradient(
                    listOf(Color(0x6638BDF8), Color(0x22FFFFFF), Color(0x4438BDF8))
                )
            )
        }
    }
}

/**
 * Animated Cyber Beaming Icon with vertical motion oscillation and radiant glow.
 */
@Composable
fun CyberBeamIcon(
    isUpload: Boolean,
    accentColor: Color = Color(0xFF38BDF8),
    size: Dp = 22.dp
) {
    val transition = rememberInfiniteTransition(label = "cyberBeamMotion")
    val offsetY by transition.animateFloat(
        initialValue = if (isUpload) 2f else -2f,
        targetValue = if (isUpload) -2f else 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arrowOffset"
    )

    val pulseAlpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "haloAlpha"
    )

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(accentColor.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.7f)) {
            val w = this.size.width
            val h = this.size.height
            val cx = w / 2f
            val cy = h / 2f + offsetY

            // Outer subtle glowing ring
            drawCircle(
                color = accentColor.copy(alpha = pulseAlpha),
                radius = w / 2f - 1f,
                style = Stroke(width = 1.2f)
            )

            // Dynamic Arrow Path (Up for Upload, Down for Download)
            val path = Path()
            if (isUpload) {
                drawLine(
                    color = accentColor,
                    start = Offset(cx, cy + h * 0.35f),
                    end = Offset(cx, cy - h * 0.25f),
                    strokeWidth = 2.2f,
                    cap = StrokeCap.Round
                )
                path.moveTo(cx - w * 0.28f, cy - h * 0.05f)
                path.lineTo(cx, cy - h * 0.35f)
                path.lineTo(cx + w * 0.28f, cy - h * 0.05f)
            } else {
                drawLine(
                    color = accentColor,
                    start = Offset(cx, cy - h * 0.35f),
                    end = Offset(cx, cy + h * 0.25f),
                    strokeWidth = 2.2f,
                    cap = StrokeCap.Round
                )
                path.moveTo(cx - w * 0.28f, cy + h * 0.05f)
                path.lineTo(cx, cy + h * 0.35f)
                path.lineTo(cx + w * 0.28f, cy + h * 0.05f)
            }

            drawPath(
                path = path,
                color = accentColor,
                style = Stroke(width = 2.2f, cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * OriginOS Style Animated 4-Bar Soundwave Equalizer.
 */
@Composable
fun OriginOSEqualizer(
    isPlaying: Boolean,
    color: Color = Color(0xFF22C55E),
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "originEq")
    val b1 by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b1"
    )
    val b2 by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.30f,
        animationSpec = infiniteRepeatable(tween(560, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b2"
    )
    val b3 by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(380, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b3"
    )
    val b4 by transition.animateFloat(
        initialValue = 0.70f,
        targetValue = 0.20f,
        animationSpec = infiniteRepeatable(tween(510, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b4"
    )

    Row(
        modifier = modifier.height(16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val bars = listOf(b1, b2, b3, b4)
        bars.forEach { hFraction ->
            val actualH = if (isPlaying) (16.dp * hFraction).coerceAtLeast(3.dp) else 3.dp
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(actualH)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}

/**
 * Legacy Equalizer alias for compatibility
 */
@Composable
fun AnimatedEqualizer(
    isPlaying: Boolean,
    color: Color = Color(0xFF22C55E),
    modifier: Modifier = Modifier
) {
    OriginOSEqualizer(isPlaying = isPlaying, color = color, modifier = modifier)
}

/**
 * OriginOS-Inspired Wavy Audio SeekBar with live elapsed and remaining timer.
 */
@Composable
fun OriginOSWavySeekBar(
    progress: Float,
    durationMs: Long,
    positionMs: Long,
    accentColor: Color,
    isPlaying: Boolean,
    onSeek: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "waveProgress")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (isPlaying) 6.28f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    fun formatMs(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val m = s / 60
        val remSec = s % 60
        return "%02d:%02d".format(m, remSec)
    }

    val elapsedText = formatMs(positionMs)
    val totalText = if (durationMs > 0) formatMs(durationMs) else "--:--"

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(
            modifier = Modifier
                .height(18.dp)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val ratio = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onSeek?.invoke(ratio)
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val midY = h / 2f
            val filledW = (w * progress.coerceIn(0f, 1f))

            // Background Track
            drawLine(
                color = Color(0x33FFFFFF),
                start = Offset(0f, midY),
                end = Offset(w, midY),
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )

            // Dynamic Wave Active Progress
            if (filledW > 2f) {
                val path = Path()
                path.moveTo(0f, midY)
                val step = 4f
                var x = 0f
                while (x <= filledW) {
                    val waveAmplitude = if (isPlaying) 3.5f else 0f
                    val y = midY + sin((x / 14f) + phase) * waveAmplitude
                    path.lineTo(x, y)
                    x += step
                }
                drawPath(
                    path = path,
                    color = accentColor,
                    style = Stroke(width = 3.5f, cap = StrokeCap.Round)
                )

                // Glowing Playhead Dot
                drawCircle(
                    color = Color.White,
                    radius = 5.5f,
                    center = Offset(filledW, midY)
                )
                drawCircle(
                    color = accentColor.copy(alpha = 0.5f),
                    radius = 8.5f,
                    center = Offset(filledW, midY)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = elapsedText,
                color = Color(0xFF94A3B8),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = totalText,
                color = Color(0xFF94A3B8),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * OriginOS Style Spinning Vinyl Record (Displays real album artwork bitmap when available).
 */
@Composable
fun OriginOSVinylRecord(
    isPlaying: Boolean,
    albumArt: Bitmap? = null,
    size: Dp = 46.dp,
    labelColor: Color = Color(0xFF38BDF8)
) {
    val transition = rememberInfiniteTransition(label = "originVinylSpin")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "originVinylRotation"
    )

    Box(
        modifier = Modifier
            .size(size)
            .rotate(if (isPlaying) rotation else 0f)
            .clip(CircleShape)
            .background(Color(0xFF0F172A))
            .border(1.dp, Color(0x33FFFFFF), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val r = this.size.width / 2f
            // Vinyl grooves
            drawCircle(color = Color(0x28FFFFFF), radius = r * 0.88f, style = Stroke(width = 1f))
            drawCircle(color = Color(0x1FFFFFFF), radius = r * 0.72f, style = Stroke(width = 1f))
            drawCircle(color = Color(0x28FFFFFF), radius = r * 0.56f, style = Stroke(width = 1f))
        }

        if (albumArt != null) {
            Image(
                bitmap = albumArt.asImageBitmap(),
                contentDescription = "Album Artwork",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size * 0.65f)
                    .clip(CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(size * 0.55f)
                    .clip(CircleShape)
                    .background(labelColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(size * 0.3f)
                )
            }
        }

        // Center spindle hole
        Box(
            modifier = Modifier
                .size(size * 0.16f)
                .clip(CircleShape)
                .background(Color(0xFF050508))
                .border(0.8.dp, Color(0x88FFFFFF), CircleShape)
        )
    }
}

/**
 * OriginOS Style Dynamic Island Music Card.
 */
@Composable
fun OriginOSMusicCard(
    track: MediaTrackInfo,
    accentColor: Color,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showLyrics by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF0A0A10).copy(alpha = 0.94f))
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top App Badge, Lyrics Toggle, and Live Equalizer
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (track.isPlaying) Color(0xFF22C55E) else Color(0xFF94A3B8))
                )
                Text(
                    text = if (track.appName.isNotBlank()) track.appName.uppercase() else "NOW PLAYING",
                    color = if (track.isPlaying) Color(0xFF22C55E) else Color(0xFF94A3B8),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Apple Music Live Lyrics Mode Toggle
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (showLyrics) accentColor.copy(alpha = 0.25f) else Color(0x1AFFFFFF))
                        .clickable { showLyrics = !showLyrics },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ChatBubbleOutline,
                        contentDescription = "Live Lyrics",
                        tint = if (showLyrics) accentColor else Color(0xAAFFFFFF),
                        modifier = Modifier.size(13.dp)
                    )
                }

                OriginOSEqualizer(
                    isPlaying = track.isPlaying,
                    color = accentColor,
                    modifier = Modifier.height(12.dp)
                )
            }
        }

        if (showLyrics) {
            // Apple Music Synchronized Live Lyrics
            val effectiveLyrics = if (track.lyrics.isNotEmpty()) {
                track.lyrics
            } else {
                remember(track.title) {
                    listOf(
                        LyricLine(0L, "♪ ${if (track.title.isNotBlank()) track.title else "Live Audio"} ♪"),
                        LyricLine(12000L, "Streaming via ${if (track.artist.isNotBlank()) track.artist else "JARVIS Island"}"),
                        LyricLine(24000L, "High-fidelity spatial audio active"),
                        LyricLine(36000L, "Synchronized ecosystem playback"),
                        LyricLine(50000L, "Feel the acoustic resonance in real time"),
                        LyricLine(70000L, "Connected across all your devices")
                    )
                }
            }

            val currentPos = if (track.durationMs > 0) track.positionMs else (track.progress * 120000L).toLong()
            val activeIndex = effectiveLyrics.indexOfLast { it.timeMs <= currentPos }.coerceAtLeast(0)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x15FFFFFF))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.Center
            ) {
                for (offset in -1..1) {
                    val idx = activeIndex + offset
                    if (idx in effectiveLyrics.indices) {
                        val line = effectiveLyrics[idx]
                        val isActive = idx == activeIndex
                        Text(
                            text = line.text,
                            color = if (isActive) Color.White else Color(0x55FFFFFF),
                            fontSize = if (isActive) 13.5.sp else 11.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clickable {
                                    if (track.durationMs > 0 && onSeek != null) {
                                        onSeek(line.timeMs.toFloat() / track.durationMs.toFloat())
                                    }
                                }
                        )
                    } else {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        } else {
            // Center: Album Art & Track Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Album Artwork: Large 64dp Card
                if (track.albumArt != null) {
                    Image(
                        bitmap = track.albumArt.asImageBitmap(),
                        contentDescription = "Album Art",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp))
                    )
                } else {
                    OriginOSVinylRecord(
                        isPlaying = track.isPlaying,
                        albumArt = null,
                        size = 64.dp,
                        labelColor = accentColor
                    )
                }

                // Track Title & Artist
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (track.title.isNotBlank()) track.title else "No Media Playing",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (track.artist.isNotBlank()) track.artist else "Apple Music • Live Audio",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (track.album.isNotBlank()) {
                        Text(
                            text = track.album,
                            color = Color(0xFF64748B),
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // OriginOS Wavy Seek Bar
        OriginOSWavySeekBar(
            progress = track.progress,
            durationMs = track.durationMs,
            positionMs = track.positionMs,
            accentColor = accentColor,
            isPlaying = track.isPlaying,
            onSeek = onSeek
        )

        // Transport Controls Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Previous Track
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0x1AFFFFFF))
                    .clickable(onClick = onPrev),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "Previous Track",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Play / Pause (Large Circular Button)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(accentColor)
                    .clickable(onClick = onPlayPause),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (track.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (track.isPlaying) "Pause" else "Play",
                    tint = Color.Black,
                    modifier = Modifier.size(26.dp)
                )
            }

            // Next Track
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0x1AFFFFFF))
                    .clickable(onClick = onNext),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Next Track",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Animated LAN LiveBridge Status Pulse with Expanding Radar Waves.
 */
@Composable
fun PulseConnectionDot(
    isConnected: Boolean,
    color: Color = Color(0xFF22C55E),
    size: Dp = 16.dp
) {
    val transition = rememberInfiniteTransition(label = "pulseRadar")
    val waveScale by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarScale"
    )

    val waveAlpha by transition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarAlpha"
    )

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        if (isConnected) {
            Canvas(modifier = Modifier.size(size)) {
                drawCircle(
                    color = color.copy(alpha = waveAlpha),
                    radius = (size.toPx() / 2f) * waveScale,
                    style = Stroke(width = 1.5f)
                )
            }
        }

        Box(
            modifier = Modifier
                .size(size * 0.45f)
                .clip(CircleShape)
                .background(if (isConnected) color else Color(0xFFEF4444))
        )
    }
}

/**
 * Expressive Notification Card (Apple Dynamic Island & Expressive-Cutout style)
 * Intercepts WhatsApp, Telegram, Phone, SMS, System notifications and presents
 * a polished heads-up notification card with quick action triggers.
 */
@Composable
fun ExpressiveNotificationCard(
    event: NotificationIslandEvent,
    accentColor: Color = Color(0xFF38BDF8),
    onOpen: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0C0E14))
            .border(1.dp, Brush.horizontalGradient(listOf(accentColor.copy(alpha = 0.4f), Color(0x22FFFFFF))), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top Header: App Pill & Time & Dismiss Icon
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
                Text(
                    text = if (event.appName.isNotBlank()) event.appName.uppercase() else "NOTIFICATION",
                    color = accentColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                if (event.subText.isNotBlank()) {
                    Text(
                        text = "• ${event.subText}",
                        color = Color(0x88FFFFFF),
                        fontSize = 9.5.sp
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Just now",
                    color = Color(0x66FFFFFF),
                    fontSize = 9.5.sp
                )
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color(0x15FFFFFF))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Dismiss",
                        tint = Color(0xAAFFFFFF),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // Body: Avatar + Title & Message
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (event.largeIcon != null) {
                Image(
                    bitmap = event.largeIcon.asImageBitmap(),
                    contentDescription = "Sender Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .border(1.dp, Color(0x33FFFFFF), CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.18f))
                        .border(1.dp, accentColor.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Notifications,
                        contentDescription = "Notification",
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (event.title.isNotBlank()) event.title else (if (event.appName.isNotBlank()) event.appName else "New Notification"),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (event.text.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = event.text,
                        color = Color(0xCCFFFFFF),
                        fontSize = 11.5.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        // Action Buttons Row: Open App & Dismiss
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.22f))
                    .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .clickable(onClick = onOpen)
                .padding(vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = "Open",
                    tint = accentColor,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "Open App",
                    color = accentColor,
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
                .clickable(onClick = onDismiss)
                .padding(vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Dismiss",
                color = Color(0xCCFFFFFF),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
}
