package com.jarvis.island.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
    fun getTheme(name: String): IslandThemeColors {
        return when (name) {
            "Cyber Cyan" -> IslandThemeColors(
                background = Color(0xFF040814),
                cardBackground = Color(0xFF0A1124),
                primaryAccent = Color(0xFF00F0FF),
                secondaryAccent = Color(0xFF38BDF8),
                borderBrush = Brush.linearGradient(
                    listOf(Color(0xFF00F0FF), Color(0xFF0284C7), Color(0x3300F0FF))
                )
            )
            "Emerald Matrix" -> IslandThemeColors(
                background = Color(0xFF030D08),
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
                // Shaft
                drawLine(
                    color = accentColor,
                    start = Offset(cx, cy + h * 0.35f),
                    end = Offset(cx, cy - h * 0.25f),
                    strokeWidth = 2.2f,
                    cap = StrokeCap.Round
                )
                // Chevron Head
                path.moveTo(cx - w * 0.28f, cy - h * 0.05f)
                path.lineTo(cx, cy - h * 0.35f)
                path.lineTo(cx + w * 0.28f, cy - h * 0.05f)
            } else {
                // Shaft
                drawLine(
                    color = accentColor,
                    start = Offset(cx, cy - h * 0.35f),
                    end = Offset(cx, cy + h * 0.25f),
                    strokeWidth = 2.2f,
                    cap = StrokeCap.Round
                )
                // Chevron Head
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
 * Animated Spinning Vinyl Record for Music Player Card.
 */
@Composable
fun SpinningVinylRecord(
    isPlaying: Boolean,
    size: Dp = 44.dp,
    labelColor: Color = Color(0xFF38BDF8)
) {
    val transition = rememberInfiniteTransition(label = "vinylSpin")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "vinylRotation"
    )

    Box(
        modifier = Modifier
            .size(size)
            .rotate(if (isPlaying) rotation else 0f)
            .clip(CircleShape)
            .background(Color(0xFF0F172A)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val r = this.size.width / 2f
            val center = Offset(r, r)

            // Vinyl groove lines
            drawCircle(
                color = Color(0x33FFFFFF),
                radius = r * 0.85f,
                style = Stroke(width = 1f)
            )
            drawCircle(
                color = Color(0x22FFFFFF),
                radius = r * 0.68f,
                style = Stroke(width = 1f)
            )
            drawCircle(
                color = Color(0x28FFFFFF),
                radius = r * 0.50f,
                style = Stroke(width = 1f)
            )

            // Center label
            drawCircle(
                color = labelColor,
                radius = r * 0.32f
            )

            // Spindle hole
            drawCircle(
                color = Color.Black,
                radius = r * 0.10f
            )
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
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveScale"
    )
    val waveAlpha by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveAlpha"
    )

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        if (isConnected) {
            Canvas(modifier = Modifier.size(size)) {
                drawCircle(
                    color = color.copy(alpha = waveAlpha),
                    radius = (this.size.width / 2f) * waveScale
                )
            }
        }
        Box(
            modifier = Modifier
                .size(size * 0.44f)
                .clip(CircleShape)
                .background(if (isConnected) color else Color(0xFFEF4444))
        )
    }
}

/**
 * 4-Bar Equalizer with Smooth Harmonic Motion.
 */
@Composable
fun AnimatedEqualizer(
    isPlaying: Boolean = true,
    color: Color = Color(0xFF22C55E),
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "waveform")

    val h1 by transition.animateFloat(
        initialValue = 4f,
        targetValue = if (isPlaying) 16f else 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h1"
    )

    val h2 by transition.animateFloat(
        initialValue = 8f,
        targetValue = if (isPlaying) 20f else 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(360, delayMillis = 80, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h2"
    )

    val h3 by transition.animateFloat(
        initialValue = 5f,
        targetValue = if (isPlaying) 18f else 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(490, delayMillis = 40, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h3"
    )

    val h4 by transition.animateFloat(
        initialValue = 3f,
        targetValue = if (isPlaying) 14f else 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(390, delayMillis = 120, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h4"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(h1, h2, h3, h4).forEach { height ->
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height(height.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(color)
            )
        }
    }
}
