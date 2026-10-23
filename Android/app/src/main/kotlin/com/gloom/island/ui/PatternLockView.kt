package com.gloom.island.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

/**
 * A 3x3 pattern lock drawn to the same visual language as the rest of the app.
 * The gesture must connect at least four unique dots.
 */
@Composable
fun PatternLockView(
    modifier: Modifier = Modifier,
    error: Boolean = false,
    enabled: Boolean = true,
    onPatternComplete: (List<Int>) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var selected by remember { mutableStateOf<List<Int>>(emptyList()) }
    var cursor by remember { mutableStateOf<Offset?>(null) }
    var dragging by remember { mutableStateOf(false) }

    fun dotCenter(index: Int, width: Float): Offset {
        val cell = width / 3f
        return Offset(
            x = cell * (index % 3) + cell / 2f,
            y = cell * (index / 3) + cell / 2f
        )
    }

    fun nearestDot(position: Offset, width: Float, threshold: Float): Int? {
        var best: Int? = null
        var bestDistance = threshold
        for (i in 0 until 9) {
            val center = dotCenter(i, width)
            val distance = hypot(center.x - position.x, center.y - position.y)
            if (distance < bestDistance) {
                bestDistance = distance
                best = i
            }
        }
        return best
    }

    val lineColor = if (error) Apple.systemRed else Apple.systemBlue
    val dotColor = if (error) Apple.systemRed else Apple.systemBlue

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .semantics { contentDescription = "3 by 3 pattern lock" }
            .pointerInput(enabled, error) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { position ->
                        selected = emptyList()
                        cursor = position
                        dragging = true
                        nearestDot(position, size.width.toFloat(), size.width / 6.5f)?.let { dot ->
                            selected = listOf(dot)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
                    onDrag = { change, _ ->
                        if (!dragging) return@detectDragGestures
                        cursor = change.position
                        val dot = nearestDot(change.position, size.width.toFloat(), size.width / 7f)
                        if (dot != null && !selected.contains(dot)) {
                            selected = selected + dot
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
                    onDragEnd = {
                        dragging = false
                        cursor = null
                        val result = selected
                        selected = emptyList()
                        if (result.size >= 4) {
                            onPatternComplete(result)
                        } else {
                            onPatternComplete(emptyList())
                        }
                    },
                    onDragCancel = {
                        dragging = false
                        cursor = null
                        selected = emptyList()
                    }
                )
            }
    ) {
        val width = size.width
        val cell = width / 3f
        val idleRadius = cell * 0.10f
        val activeRadius = cell * 0.135f

        // Connecting lines
        for (i in 0 until selected.size - 1) {
            drawLine(
                color = lineColor.copy(alpha = 0.85f),
                start = dotCenter(selected[i], width),
                end = dotCenter(selected[i + 1], width),
                strokeWidth = cell * 0.045f,
                cap = StrokeCap.Round
            )
        }
        val currentCursor = cursor
        if (dragging && selected.isNotEmpty() && currentCursor != null) {
            drawLine(
                color = lineColor.copy(alpha = 0.55f),
                start = dotCenter(selected.last(), width),
                end = currentCursor,
                strokeWidth = cell * 0.045f,
                cap = StrokeCap.Round
            )
        }

        // Dots
        for (i in 0 until 9) {
            val center = dotCenter(i, width)
            val isSelected = selected.contains(i)
            drawCircle(
                color = if (isSelected) dotColor else Apple.quaternary,
                radius = if (isSelected) activeRadius else idleRadius,
                center = center
            )
            if (isSelected) {
                drawCircle(
                    color = Apple.background,
                    radius = activeRadius * 0.42f,
                    center = center
                )
            }
        }
    }
}
