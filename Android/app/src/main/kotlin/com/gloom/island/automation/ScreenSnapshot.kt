package com.gloom.island.automation

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.view.Display
import com.gloom.island.service.GloomIslandAccessibilityService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/**
 * Screenshot aim grid.
 *
 * Some apps (WebViews, Flutter, games, video players) expose an empty
 * accessibility tree. When the outline has nothing to act on, this module
 * captures the screen, divides it into a grid, and reports which cells carry
 * content. The planner can then tap a cell, and a cell can be zoomed with a
 * crosshair for precise aiming.
 *
 * Screenshots are analysed in memory and never written to disk.
 */
object ScreenSnapshot {

    private const val TAG = "GloomScreenSnapshot"
    private const val THUMB_WIDTH = 360
    private const val DEFAULT_COLS = 4
    private const val DEFAULT_ROWS = 8

    private val executor: Executor = Executor { runnable ->
        Thread(runnable, "gloom-screenshot").start()
    }

    data class Cell(
        val index: Int,
        val row: Int,
        val col: Int,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val luminance: Int,
        val deviation: Int,
        val edges: Float,
    ) {
        val hasContent: Boolean
            get() = deviation >= 9 || edges >= 0.06f

        val bounds: android.graphics.Rect
            get() = android.graphics.Rect(left, top, right, bottom)
    }

    data class Frame(
        val bitmap: Bitmap,
        val cols: Int,
        val rows: Int,
        val cells: List<Cell>,
    )

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    fun capture(onResult: (Bitmap?) -> Unit) {
        val service = GloomIslandAccessibilityService.instance
        if (service == null || !isSupported()) {
            onResult(null)
            return
        }
        try {
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                executor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        val buffer = screenshot.hardwareBuffer
                        val bitmap = try {
                            Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                                ?.copy(Bitmap.Config.ARGB_8888, false)
                        } catch (e: Exception) {
                            null
                        } finally {
                            runCatching { buffer.close() }
                        }
                        onResult(bitmap)
                    }

                    override fun onFailure(errorCode: Int) {
                        onResult(null)
                    }
                }
            )
        } catch (e: Exception) {
            onResult(null)
        }
    }

    fun captureBlocking(timeoutMs: Long = 1_500L): Bitmap? {
        if (!isSupported()) return null
        val latch = CountDownLatch(1)
        var result: Bitmap? = null
        capture { bitmap ->
            result = bitmap
            latch.countDown()
        }
        return if (latch.await(timeoutMs, TimeUnit.MILLISECONDS)) result else null
    }

    /** Captures and analyses in one call. Safe to call off the main thread. */
    fun analyseBlocking(cols: Int = DEFAULT_COLS, rows: Int = DEFAULT_ROWS): Frame? {
        val bitmap = captureBlocking() ?: return null
        return analyse(bitmap, cols, rows)
    }

    fun analyse(bitmap: Bitmap, cols: Int = DEFAULT_COLS, rows: Int = DEFAULT_ROWS): Frame {
        val safeCols = cols.coerceIn(2, 8)
        val safeRows = rows.coerceIn(2, 12)
        val height = (THUMB_WIDTH * bitmap.height.toFloat() / bitmap.width.toFloat())
            .toInt()
            .coerceAtLeast(1)
        val thumb = Bitmap.createScaledBitmap(bitmap, THUMB_WIDTH, height, true)
        val width = thumb.width
        val scaledHeight = thumb.height
        val pixels = IntArray(width * scaledHeight)
        thumb.getPixels(pixels, 0, width, 0, 0, width, scaledHeight)

        val cellWidth = width / safeCols
        val cellHeight = scaledHeight / safeRows
        val cells = ArrayList<Cell>(safeCols * safeRows)
        var index = 0
        for (row in 0 until safeRows) {
            for (col in 0 until safeCols) {
                val x0 = col * cellWidth
                val y0 = row * cellHeight
                val x1 = if (col == safeCols - 1) width else x0 + cellWidth
                val y1 = if (row == safeRows - 1) scaledHeight else y0 + cellHeight
                var sum = 0L
                var sumSq = 0L
                var edgeSamples = 0
                var edgeHits = 0
                var previousLeft = -1
                for (y in y0 until y1) {
                    val rowOffset = y * width
                    previousLeft = -1
                    for (x in x0 until x1) {
                        val color = pixels[rowOffset + x]
                        val luma = lumaOf(color)
                        sum += luma
                        sumSq += (luma * luma).toLong()
                        if (previousLeft >= 0) {
                            edgeSamples += 1
                            if (Math.abs(luma - previousLeft) >= 24) edgeHits += 1
                        }
                        previousLeft = luma
                    }
                }
                val count = ((x1 - x0) * (y1 - y0)).coerceAtLeast(1)
                val mean = (sum / count).toInt()
                val variance = (sumSq / count) - (mean.toLong() * mean)
                val deviation = Math.sqrt(variance.toDouble().coerceAtLeast(0.0)).toInt()
                val edges = if (edgeSamples == 0) 0f else edgeHits.toFloat() / edgeSamples.toFloat()

                val left = x0 * bitmap.width / width
                val top = y0 * bitmap.height / scaledHeight
                val right = x1 * bitmap.width / width
                val bottom = y1 * bitmap.height / scaledHeight
                cells.add(
                    Cell(
                        index = index,
                        row = row,
                        col = col,
                        left = left,
                        top = top,
                        right = right,
                        bottom = bottom,
                        luminance = mean,
                        deviation = deviation,
                        edges = edges,
                    )
                )
                index += 1
            }
        }
        if (thumb !== bitmap) thumb.recycle()
        return Frame(bitmap = bitmap, cols = safeCols, rows = safeRows, cells = cells)
    }

    private fun lumaOf(color: Int): Int {
        val red = (color shr 16) and 0xFF
        val green = (color shr 8) and 0xFF
        val blue = color and 0xFF
        return (red * 299 + green * 587 + blue * 114) / 1000
    }

    /**
     * Compact outline for the planner:
     * `g<index>|r<row>c<col>|lum=<0-255>|sd=<0-255>|edge=<0-1>|content|empty`
     */
    fun describe(frame: Frame, onlyContent: Boolean = true): String {
        val lines = ArrayList<String>(frame.cells.size + 3)
        val contentCount = frame.cells.count { it.hasContent }
        lines.add(
            "screenshot=${frame.bitmap.width}x${frame.bitmap.height} grid=${frame.cols}x${frame.rows} " +
                "contentCells=$contentCount/${frame.cells.size}"
        )
        for (cell in frame.cells) {
            if (onlyContent && !cell.hasContent) continue
            lines.add(
                "g${cell.index}|r${cell.row}c${cell.col}|lum=${cell.luminance}|" +
                    "sd=${cell.deviation}|edge=${(cell.edges * 100).toInt() / 100f}|" +
                    if (cell.hasContent) "content" else "empty"
            )
        }
        if (contentCount == 0) lines.add("(the screen looks uniform; no aimable content)")
        return lines.joinToString("\n")
    }

    /** Captures and describes the current screen. Returns null when unsupported. */
    fun describeScreen(): String? {
        val frame = analyseBlocking() ?: return null
        val text = describe(frame)
        frame.bitmap.recycle()
        return text
    }

    fun tapCell(frame: Frame, index: Int): Boolean {
        val cell = frame.cells.firstOrNull { it.index == index } ?: return false
        return ScreenAutomation.tapPoint(
            cell.bounds.centerX().toFloat(),
            cell.bounds.centerY().toFloat()
        )
    }

    /** Taps a 0-1000 screen fraction, the same coordinate space the outline uses. */
    fun tapFraction(x: Int, y: Int): Boolean {
        val width = ScreenAutomation.screenWidth().coerceAtLeast(1)
        val height = ScreenAutomation.screenHeight().coerceAtLeast(1)
        val px = (x.coerceIn(0, 1000) / 1000f) * width
        val py = (y.coerceIn(0, 1000) / 1000f) * height
        return ScreenAutomation.tapPoint(px, py)
    }

    /**
     * Crops a cell at 3x with a crosshair on its centre, for precise visual
     * aiming. Returns null when the cell has no bounds.
     */
    fun zoomCell(frame: Frame, index: Int, scale: Int = 3): Bitmap? {
        val cell = frame.cells.firstOrNull { it.index == index } ?: return null
        val source = cell.bounds
        if (source.width() <= 0 || source.height() <= 0) return null
        val crop = Bitmap.createBitmap(
            frame.bitmap,
            source.left.coerceIn(0, frame.bitmap.width - 1),
            source.top.coerceIn(0, frame.bitmap.height - 1),
            source.width().coerceAtMost(frame.bitmap.width - source.left.coerceAtLeast(0)).coerceAtLeast(1),
            source.height().coerceAtMost(frame.bitmap.height - source.top.coerceAtLeast(0)).coerceAtLeast(1)
        )
        val zoomed = Bitmap.createScaledBitmap(
            crop,
            (crop.width * scale).coerceAtLeast(1),
            (crop.height * scale).coerceAtLeast(1),
            true
        )
        if (crop !== zoomed) crop.recycle()
        val canvas = Canvas(zoomed)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = 2f
        }
        val cx = zoomed.width / 2f
        val cy = zoomed.height / 2f
        val arm = minOf(zoomed.width, zoomed.height) * 0.12f
        canvas.drawLine(cx - arm, cy, cx + arm, cy, paint)
        canvas.drawLine(cx, cy - arm, cx, cy + arm, paint)
        canvas.drawCircle(cx, cy, arm * 0.8f, paint)
        return zoomed
    }
}
