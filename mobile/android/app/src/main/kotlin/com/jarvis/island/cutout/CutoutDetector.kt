package com.jarvis.island.cutout

import android.content.Context
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
    val defaultPillWidth: Float = 184f,
    val defaultPillHeight: Float = 38f,
)

object CutoutDetector {

    /**
     * Inspects device hardware or Android DisplayCutout API to determine exact physical camera cutout
     * coordinates, width, height, and alignment on the device screen.
     * Includes dedicated hardware calibration for Motorola Edge 50 Pro (1220x2712 px, 446 PPI).
     */
    fun detectCutout(context: Context, insets: WindowInsets?): CutoutMetrics {
        val metrics = context.resources.displayMetrics
        val model = Build.MODEL.lowercase()
        val device = Build.DEVICE.lowercase()
        val isMotoEdge50Pro = model.contains("edge 50 pro") ||
                device.contains("edge 50 pro") ||
                model.contains("motorola edge 50 pro") ||
                (metrics.widthPixels == 1220 && metrics.heightPixels == 2712)

        // 1. Motorola Edge 50 Pro Dedicated Hardware Grounding
        if (isMotoEdge50Pro) {
            return CutoutMetrics(
                hasCutout = true,
                centerX = 610f,             // Exactly dead-center (1220 / 2)
                centerY = 52f,              // Active screen Y center in px
                width = 48f,                // Hole diameter in px (44-52 px)
                height = 48f,
                isPill = false,
                isCenter = true,
                defaultPillWidth = 184f * metrics.density,
                defaultPillHeight = 38f * metrics.density
            )
        }

        // 2. DisplayCutout API inspection
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
                        metrics.widthPixels
                    }

                    val cx = rect.exactCenterX()
                    val cy = rect.exactCenterY()
                    val w = rect.width().toFloat()
                    val h = rect.height().toFloat()

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
                        defaultPillWidth = Math.max(w + 32f, 184f),
                        defaultPillHeight = Math.max(h + 12f, 38f)
                    )
                }
            }
        }

        // 3. Fallback for center punch hole displays
        return CutoutMetrics(
            hasCutout = true,
            centerX = metrics.widthPixels / 2f,
            centerY = 52f,
            width = 48f,
            height = 48f,
            isPill = false,
            isCenter = true,
            defaultPillWidth = 184f * metrics.density,
            defaultPillHeight = 38f * metrics.density
        )
    }
}
