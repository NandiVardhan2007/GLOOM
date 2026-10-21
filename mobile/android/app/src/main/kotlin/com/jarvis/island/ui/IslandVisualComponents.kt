package com.jarvis.island.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.graphics.SolidColor
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
    private val hairline = SolidColor(Color(0x1FFFFFFF))

    fun getTheme(themeName: String): IslandThemeColors {
        return when (themeName) {
            "Cyber Cyan" -> IslandThemeColors(
                background = Color(0xFF030712),
                cardBackground = Color(0xFF0B132B),
                primaryAccent = Color(0xFF0A84FF),
                secondaryAccent = Color(0xFF64D2FF),
                borderBrush = hairline
            )
            "Emerald Matrix", "Neon Green" -> IslandThemeColors(
                background = Color(0xFF021008),
                cardBackground = Color(0xFF071911),
                primaryAccent = Color(0xFF30D158),
                secondaryAccent = Color(0xFF34D399),
                borderBrush = hairline
            )
            "Material Purple" -> IslandThemeColors(
                background = Color(0xFF0E0414),
                cardBackground = Color(0xFF1B0B26),
                primaryAccent = Color(0xFFBF5AF2),
                secondaryAccent = Color(0xFFA855F7),
                borderBrush = hairline
            )
            else -> IslandThemeColors( // "Obsidian Black" (Default)
                background = Color(0xFF000000),
                cardBackground = Color(0xFF1C1C1E),
                primaryAccent = Color(0xFF0A84FF),
                secondaryAccent = Color(0xFF30D158),
                borderBrush = hairline
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
    accentColor: Color = Color(0xFF0A84FF),
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
    color: Color = Color(0xFF30D158),
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
    color: Color = Color(0xFF30D158),
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
    fun formatMs(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val minutes = total / 60
        val seconds = total % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    val target = progress.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 320, easing = LinearEasing),
        label = "seekProgress"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
                .pointerInput(onSeek) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            onSeek?.invoke((offset.x / size.width.toFloat()).coerceIn(0f, 1f))
                        },
                        onHorizontalDrag = { change, _ ->
                            onSeek?.invoke((change.position.x / size.width.toFloat()).coerceIn(0f, 1f))
                        }
                    )
                }
                .pointerInput(onSeek) {
                    detectTapGestures { offset ->
                        onSeek?.invoke((offset.x / size.width.toFloat()).coerceIn(0f, 1f))
                    }
                }
        ) {
            val midY = size.height / 2f
            val stroke = 3.dp.toPx()
            val x = size.width * animatedProgress
            drawLine(
                color = Color(0x33FFFFFF),
                start = Offset(0f, midY),
                end = Offset(size.width, midY),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            if (x > 0f) {
                drawLine(
                    color = Color.White,
                    start = Offset(0f, midY),
                    end = Offset(x, midY),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = Color.White,
                    radius = 5.dp.toPx(),
                    center = Offset(x, midY)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatMs(positionMs),
                color = Color(0x99FFFFFF),
                fontSize = 11.sp
            )
            Text(
                text = if (durationMs > 0) formatMs(durationMs) else "--:--",
                color = Color(0x99FFFFFF),
                fontSize = 11.sp
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
    labelColor: Color = Color(0xFF0A84FF)
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
            .background(Color(0xFF000000))
            .border(0.5.dp, Color(0x22FFFFFF), CircleShape),
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
                    imageVector = JarvisIcons.MusicNote,
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
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Top Camera Clearance Notch
        Spacer(modifier = Modifier.height(18.dp))

        // Top Header: Streaming App & Actions (Lyrics Toggle + Visualizer + Close)
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
                        .background(if (track.isPlaying) Color(0xFF30D158) else Color(0x66FFFFFF))
                )
                Text(
                    text = if (track.appName.isNotBlank()) track.appName else "Now Playing",
                    color = Color(0x99FFFFFF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.sp
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onClose != null) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0x1FFFFFFF))
                            .clickable { onClose() },
                        contentAlignment = Alignment.Center
                    ) {
                        JarvisIcon(
                            icon = JarvisIcons.Close,
                            tint = Color(0xDDFFFFFF),
                            size = 13.dp
                        )
                    }
                }
            }
        }


            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Album Artwork Squircle (58x58 dp)
                if (track.albumArt != null) {
                    Image(
                        bitmap = track.albumArt.asImageBitmap(),
                        contentDescription = "Album Art",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x1FFFFFFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        JarvisIcon(JarvisIcons.MusicNote, Color(0x8CFFFFFF), 24.dp)
                    }
                }

                // Title, Artist & Live Lyric Line Preview
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (track.title.isNotBlank()) track.title else "Nothing Playing",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (track.artist.isNotBlank()) track.artist else "Start playback in any app",
                        color = Color(0x99FFFFFF),
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Real-Time Active Lyric Preview Line
                    if (track.lyrics.isNotEmpty()) {
                        val currentPos = if (track.durationMs > 0) track.positionMs else (track.progress * 180000L).toLong()
                        val activeLine = track.lyrics.lastOrNull { it.timeMs <= currentPos } ?: track.lyrics.firstOrNull()
                        if (activeLine != null && activeLine.text.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = activeLine.text,
                                color = Color(0x9EFFFFFF),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

        // Apple Fluid Seek Bar
        OriginOSWavySeekBar(
            progress = track.progress,
            durationMs = track.durationMs,
            positionMs = track.positionMs,
            accentColor = Color.White,
            isPlaying = track.isPlaying,
            onSeek = onSeek
        )

        // Apple Circular Transport Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Previous Track (Glass Circle)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onPrev),
                contentAlignment = Alignment.Center
            ) {
                JarvisIcon(JarvisIcons.SkipBackFill, Color(0xE6FFFFFF), 22.dp)
            }

            // Play / Pause (Solid White Apple Circle with Black Icon)
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(onClick = onPlayPause),
                contentAlignment = Alignment.Center
            ) {
                JarvisIcon(
                    icon = if (track.isPlaying) JarvisIcons.PauseFill else JarvisIcons.PlayFill,
                    tint = Color.Black,
                    size = 22.dp
                )
            }

            // Next Track (Glass Circle)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onNext),
                contentAlignment = Alignment.Center
            ) {
                JarvisIcon(JarvisIcons.SkipForwardFill, Color(0xE6FFFFFF), 22.dp)
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
    color: Color = Color(0xFF30D158),
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
                .background(if (isConnected) color else Color(0xFFFF453A))
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
    accentColor: Color = Color.White,
    onOpen: () -> Unit = {},
    onDismiss: () -> Unit = {},
    onClose: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top Camera Clearance Notch
        Spacer(modifier = Modifier.height(18.dp))

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
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
                Text(
                    text = if (event.appName.isNotBlank()) event.appName.uppercase() else "NOTIFICATION",
                    color = Color.White,
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
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color(0x18FFFFFF))
                        .border(0.5.dp, Color(0x22FFFFFF), CircleShape)
                        .clickable(onClick = onClose ?: onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = JarvisIcons.Close,
                        contentDescription = "Close",
                        tint = Color(0xDDFFFFFF),
                        modifier = Modifier.size(13.dp)
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
                        imageVector = JarvisIcons.Bell,
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
                    imageVector = JarvisIcons.Globe,
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
