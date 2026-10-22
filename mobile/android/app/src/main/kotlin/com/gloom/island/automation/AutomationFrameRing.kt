package com.gloom.island.automation

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Rolling execution trace ring buffer for visual and action debugging (Google Artemis architecture).
 *
 * Maintains the last 15 execution steps in memory. If an autonomous automation
 * run stalls or fails, dumps a structured diagnostic report to storage for debugging.
 */
object AutomationFrameRing {

    private const val TAG = "GloomFrameRing"
    private const val MAX_FRAMES = 15

    data class Frame(
        val step: Int,
        val timestamp: Long,
        val action: String,
        val targetApp: String,
        val outlineSnippet: String,
        val screenDiffResult: String,
        val success: Boolean
    )

    private val ring = ArrayDeque<Frame>(MAX_FRAMES)

    @Synchronized
    fun recordFrame(
        step: Int,
        action: String,
        targetApp: String,
        outlineSnippet: String,
        diffResult: String,
        success: Boolean
    ) {
        if (ring.size >= MAX_FRAMES) {
            ring.removeFirst()
        }
        ring.addLast(
            Frame(
                step = step,
                timestamp = System.currentTimeMillis(),
                action = action,
                targetApp = targetApp,
                outlineSnippet = outlineSnippet.take(400),
                screenDiffResult = diffResult,
                success = success
            )
        )
    }

    @Synchronized
    fun dumpFailureTrace(context: Context, goal: String, errorReason: String): String? {
        return try {
            val logDir = File(context.getExternalFilesDir(null), "logs").apply { mkdirs() }
            val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val traceFile = File(logDir, "trace_$time.json")

            val json = JSONObject()
            json.put("goal", goal)
            json.put("error", errorReason)
            json.put("timestamp", System.currentTimeMillis())

            val framesArr = JSONArray()
            for (f in ring) {
                val fObj = JSONObject()
                fObj.put("step", f.step)
                fObj.put("time", f.timestamp)
                fObj.put("action", f.action)
                fObj.put("app", f.targetApp)
                fObj.put("diff", f.screenDiffResult)
                fObj.put("success", f.success)
                fObj.put("outline", f.outlineSnippet)
                framesArr.put(fObj)
            }
            json.put("frames", framesArr)

            traceFile.writeText(json.toString(2), Charsets.UTF_8)
            Log.i(TAG, "Dumped automation failure trace to ${traceFile.absolutePath}")
            traceFile.absolutePath
        } catch (e: Exception) {
            Log.w(TAG, "Failed dumping failure trace: ${e.message}")
            null
        }
    }
}
