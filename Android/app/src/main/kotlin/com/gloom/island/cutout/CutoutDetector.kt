package com.gloom.island.cutout

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.view.DisplayCutout
import android.view.WindowInsets
import android.view.WindowManager

enum class CutoutAlignment {
    CENTER,
    LEFT,
    RIGHT,
    NONE
}

data class CutoutMetrics(
    val hasCutout: Boolean = false,
    val centerX: Float = 0f,
    val centerY: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f,
    val isPill: Boolean = false,
    val isCenter: Boolean = true,
    val alignment: CutoutAlignment = CutoutAlignment.CENTER,
    val defaultPillWidth: Float = 184f,
    val defaultPillHeight: Float = 38f,
    val isLandscape: Boolean = false,
    val statusBarHeightPx: Float = 0f,
)

object CutoutDetector {

    /**
     * Inspects live Android WindowInsets and DisplayCutout APIs to determine exact physical camera cutout
     * coordinates, width, height, and alignment on the device screen.
     * Accurately supports devices with center, left, right, or NO hardware cutout, as well as landscape mode.
     */
    fun detectCutout(context: Context, insets: WindowInsets?, forcePreset: String? = null): CutoutMetrics {
        val metrics = context.resources.displayMetrics
        val density = metrics.density
        val isLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val screenBounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wm.currentWindowMetrics.bounds
        } else {
            android.graphics.Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
        }
        val screenWidth = screenBounds.width().toFloat()
        val screenHeight = screenBounds.height().toFloat()

        // 1. Optional Hardware Calibration Presets
        if (forcePreset.equals("motorola_edge_50_pro", ignoreCase = true)) {
            return getMotorolaEdge50ProPreset(density, isLandscape, screenWidth)
        }

        // Determine status bar height for safe clearance
        var statusBarHeightPx = 24f * density
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && insets != null) {
            val statusInsets = insets.getInsets(WindowInsets.Type.statusBars())
            if (statusInsets.top > 0) {
                statusBarHeightPx = statusInsets.top.toFloat()
            }
        }

        // 2. Prioritize Live Android DisplayCutout API inspection (Android 9.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && insets != null) {
            val displayCutout: DisplayCutout? = insets.displayCutout
            if (displayCutout != null) {
                val rects = displayCutout.boundingRects
                if (rects.isNotEmpty()) {
                    val rect = rects[0]
                    val cx = rect.exactCenterX()
                    val cy = rect.exactCenterY()
                    val w = rect.width().toFloat()
                    val h = rect.height().toFloat()

                    val isCenter = Math.abs(cx - (screenWidth / 2f)) < (screenWidth * 0.15f)
                    val alignment = when {
                        isCenter -> CutoutAlignment.CENTER
                        cx < screenWidth / 2f -> CutoutAlignment.LEFT
                        else -> CutoutAlignment.RIGHT
                    }
                    val isPill = w > (h * 1.5f)

                    val pillWidth = Math.max(w + (32f * density), 180f * density)
                    val pillHeight = Math.max(h + (10f * density), 36f * density)

                    return CutoutMetrics(
                        hasCutout = true,
                        centerX = cx,
                        centerY = cy,
                        width = w,
                        height = h,
                        isPill = isPill,
                        isCenter = isCenter,
                        alignment = alignment,
                        defaultPillWidth = pillWidth,
                        defaultPillHeight = pillHeight,
                        isLandscape = isLandscape,
                        statusBarHeightPx = statusBarHeightPx
                    )
                }
            }
        }

        // 3. Graceful Fallback for devices WITHOUT a camera cutout (e.g. tablet, emulator, pop-up cam, notchless)
        val defaultCenter = screenWidth / 2f
        val fallbackPillWidth = 176f * density
        val fallbackPillHeight = 36f * density
        val fallbackCenterY = statusBarHeightPx / 2f + 4f * density

        return CutoutMetrics(
            hasCutout = false,
            centerX = defaultCenter,
            centerY = fallbackCenterY,
            width = 0f,
            height = 0f,
            isPill = false,
            isCenter = true,
            alignment = CutoutAlignment.NONE,
            defaultPillWidth = fallbackPillWidth,
            defaultPillHeight = fallbackPillHeight,
            isLandscape = isLandscape,
            statusBarHeightPx = statusBarHeightPx
        )
    }

    /**
     * Dedicated hardware calibration preset for Motorola Edge 50 Pro (1220x2712 px, 446 PPI).
     * Available as an optional explicit preset.
     */
    fun getMotorolaEdge50ProPreset(density: Float, isLandscape: Boolean, screenWidth: Float): CutoutMetrics {
        return CutoutMetrics(
            hasCutout = true,
            centerX = if (isLandscape) screenWidth / 2f else 610f,
            centerY = 52f,
            width = 48f,
            height = 48f,
            isPill = false,
            isCenter = true,
            alignment = CutoutAlignment.CENTER,
            defaultPillWidth = 184f * density,
            defaultPillHeight = 38f * density,
            isLandscape = isLandscape,
            statusBarHeightPx = 104f
        )
    }
}
