package com.jarvis.island.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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
            animation = tween(450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h1"
    )

    val h2 by transition.animateFloat(
        initialValue = 8f,
        targetValue = if (isPlaying) 20f else 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(380, delayMillis = 100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h2"
    )

    val h3 by transition.animateFloat(
        initialValue = 5f,
        targetValue = if (isPlaying) 18f else 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(520, delayMillis = 50, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h3"
    )

    val h4 by transition.animateFloat(
        initialValue = 3f,
        targetValue = if (isPlaying) 14f else 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(410, delayMillis = 150, easing = FastOutSlowInEasing),
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
                    .background(color, RoundedCornerShape(2.dp))
            )
        }
    }
}
