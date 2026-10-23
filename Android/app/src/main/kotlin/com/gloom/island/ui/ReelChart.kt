package com.gloom.island.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/**
 * Seven-day bar chart for the Home activity card. Bars grow with a critically
 * damped spring, the same motion model used elsewhere in the app, and the
 * today column carries the full accent colour.
 */
@Composable
fun ReelWeekChart(
    days: List<Pair<LocalDate, Int>>,
    modifier: Modifier = Modifier,
    accent: Color = Apple.systemPink,
) {
    val max = (days.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    val today = LocalDate.now()

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        for ((date, count) in days) {
            val isToday = date == today
            val targetHeight: Dp = if (count <= 0) {
                3.dp
            } else {
                (8 + (count.toFloat() / max.toFloat()) * 38f).dp
            }
            val height by animateDpAsState(
                targetValue = targetHeight,
                animationSpec = spring(dampingRatio = 1f, stiffness = 320f),
                label = "reelBar"
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (count > 0) count.toString() else "",
                    style = AppleType.caption2,
                    color = if (isToday) Apple.label else Apple.secondaryLabel
                )
                Box(
                    modifier = Modifier
                        .width(18.dp)
                        .height(height)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (isToday) accent else accent.copy(alpha = 0.38f)
                        )
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = date.dayOfWeek.name.take(1),
                    style = AppleType.caption2,
                    color = if (isToday) Apple.label else Apple.tertiaryLabel
                )
            }
        }
    }
}
