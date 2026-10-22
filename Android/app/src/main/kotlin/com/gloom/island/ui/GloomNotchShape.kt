package com.gloom.island.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Authentic GLOOM Notch Shape matching the desktop version.
 * Replicates the cubic-bezier concave flares bridging the screen top edge into
 * the notch walls, with a flat top edge flush against the bezel and smoothly
 * rounded bottom corners.
 */
class GloomNotchShape(
    val flareWidth: Dp = 14.dp,
    val flareHeight: Dp = 13.dp,
    val bottomRadius: Dp = 18.dp,
    val isNotch: Boolean = true
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val w = size.width
        val h = size.height

        if (!isNotch || flareWidth.value <= 0f) {
            val rPx = with(density) { bottomRadius.toPx() }.coerceAtMost(w / 2f).coerceAtMost(h / 2f)
            return Outline.Rounded(
                RoundRect(0f, 0f, w, h, CornerRadius(rPx, rPx))
            )
        }

        val fw = with(density) { flareWidth.toPx() }.coerceAtMost(w / 4f)
        val fh = with(density) { flareHeight.toPx() }.coerceAtMost(h / 2f)
        val r = with(density) { bottomRadius.toPx() }.coerceAtMost((w - 2 * fw) / 2f).coerceAtMost(h)

        val k = 0.55228475f // Cubic circle constant
        val bleed = with(density) { 4.dp.toPx() }

        val path = Path().apply {
            moveTo(0f, -bleed)
            lineTo(0f, 0f)
            // Left concave flare: cubic bezier from screen top edge (0, 0) into vertical wall (fw, fh)
            cubicTo(
                x1 = fw * (10f / 13f), y1 = 0f,
                x2 = fw, y2 = fh * (3f / 13f),
                x3 = fw, y3 = fh
            )
            // Left vertical wall
            lineTo(fw, h - r)
            // Bottom-left rounded corner
            cubicTo(
                x1 = fw, y1 = h - r + r * k,
                x2 = fw + r - r * k, y2 = h,
                x3 = fw + r, y3 = h
            )
            // Bottom horizontal edge
            lineTo(w - fw - r, h)
            // Bottom-right rounded corner
            cubicTo(
                x1 = w - fw - r + r * k, y1 = h,
                x2 = w - fw, y2 = h - r + r * k,
                x3 = w - fw, y3 = h - r
            )
            // Right vertical wall
            lineTo(w - fw, fh)
            // Right concave flare: cubic bezier from vertical wall (w - fw, fh) into screen top edge (w, 0)
            cubicTo(
                x1 = w - fw, y1 = fh * (3f / 13f),
                x2 = w - fw * (10f / 13f), y2 = 0f,
                x3 = w, y3 = 0f
            )
            // Top flat edge closing across bezel with top bleed
            lineTo(w, -bleed)
            lineTo(0f, -bleed)
            close()
        }
        return Outline.Generic(path)
    }

    /**
     * Builds an open outline path tracing the flares, vertical walls, and bottom corners,
     * leaving the top bezel boundary open (since it meets the display edge).
     */
    fun createHairlineBorderPath(
        size: Size,
        density: Density
    ): Path {
        val w = size.width
        val h = size.height

        if (!isNotch || flareWidth.value <= 0f) {
            val rPx = with(density) { bottomRadius.toPx() }.coerceAtMost(w / 2f).coerceAtMost(h / 2f)
            return Path().apply {
                addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(rPx, rPx)))
            }
        }

        val fw = with(density) { flareWidth.toPx() }.coerceAtMost(w / 4f)
        val fh = with(density) { flareHeight.toPx() }.coerceAtMost(h / 2f)
        val r = with(density) { bottomRadius.toPx() }.coerceAtMost((w - 2 * fw) / 2f).coerceAtMost(h)
        val k = 0.55228475f

        return Path().apply {
            moveTo(0f, 0f)
            cubicTo(
                x1 = fw * (10f / 13f), y1 = 0f,
                x2 = fw, y2 = fh * (3f / 13f),
                x3 = fw, y3 = fh
            )
            lineTo(fw, h - r)
            cubicTo(
                x1 = fw, y1 = h - r + r * k,
                x2 = fw + r - r * k, y2 = h,
                x3 = fw + r, y3 = h
            )
            lineTo(w - fw - r, h)
            cubicTo(
                x1 = w - fw - r + r * k, y1 = h,
                x2 = w - fw, y2 = h - r + r * k,
                x3 = w - fw, y3 = h - r
            )
            lineTo(w - fw, fh)
            cubicTo(
                x1 = w - fw, y1 = fh * (3f / 13f),
                x2 = w - fw * (10f / 13f), y2 = 0f,
                x3 = w, y3 = 0f
            )
        }
    }
}
