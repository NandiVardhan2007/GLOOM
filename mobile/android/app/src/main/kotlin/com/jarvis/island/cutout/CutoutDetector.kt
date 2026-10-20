package com.jarvis.island.cutout

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.view.DisplayCutout
import android.view.WindowInsets
import android.view.WindowManager

data class CutoutMetrics(
    val hasCutout: Boolean = false,
    val centerX: Float = 0f,
    val centerY: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f,
    val isPill: Boolean = false,
    val isCenter: Boolean = true,
    val defaultPillWidth: Float = 120f,
    val defaultPillHeight: Float = 36f,
)

object CutoutDetector {

    /**
     * Inspects the Android DisplayCutout API to determine exact physical camera cutout
     * coordinates, width, height, and alignment on the device screen.
     */
    fun detectCutout(context: Context, insets: WindowInsets?): CutoutMetrics {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && insets != null) {
            val displayCutout: DisplayCutout? = insets.displayCutout
            if (displayCutout != null) {
                val rects = displayCutout.boundingRects
                if (rects.isNotEmpty()) {
                    val rect = rects[0]
                    val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                    val screenWidth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        wm.currentWindowMetrics.bounds.width()
                    } else {
                        context.resources.displayMetrics.widthPixels
                    }

                    val cx = rect.exactCenterX()
                    val cy = rect.exactCenterY()
                    val w = rect.width().toFloat()
                    val h = rect.height().toFloat()

                    // Check if center or side hole punch
                    val isCenter = Math.abs(cx - (screenWidth / 2f)) < (screenWidth * 0.15f)
                    val isPill = w > (h * 1.6f)

                    return CutoutMetrics(
                        hasCutout = true,
                        centerX = cx,
                        centerY = cy,
                        width = w,
                        height = h,
                        isPill = isPill,
                        isCenter = isCenter,
                        defaultPillWidth = Math.max(w + 32f, 130f),
                        defaultPillHeight = Math.max(h + 12f, 36f)
                    )
                }
            }
        }

        // Fallback for devices without reported hardware cutouts (e.g. top status bar center pill)
        val metrics = context.resources.displayMetrics
        return CutoutMetrics(
            hasCutout = false,
            centerX = metrics.widthPixels / 2f,
            centerY = 24f * metrics.density,
            width = 30f * metrics.density,
            height = 30f * metrics.density,
            isPill = false,
            isCenter = true,
            defaultPillWidth = 140f * metrics.density,
            defaultPillHeight = 36f * metrics.density
        )
    }
}
