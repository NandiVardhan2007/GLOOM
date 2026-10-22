package com.gloom.island.automation

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Dynamic Dwell Timing for Network Spinners (KuafuAI Andee architecture).
 *
 * Scans the active screen for transient progress bars, spinners, and buffering indicators,
 * ensuring the agent does not attempt to interact with partially loaded screens.
 */
object SpinnerGuard {

    private const val TAG = "GloomSpinnerGuard"

    private val spinnerClasses = setOf(
        "android.widget.ProgressBar",
        "com.google.android.material.progressindicator.CircularProgressIndicator",
        "com.google.android.material.progressindicator.LinearProgressIndicator"
    )

    private val spinnerKeywords = listOf(
        "loading", "buffering", "please wait", "refreshing", "fetching", "connecting"
    )

    /**
     * True if the screen currently contains active progress indicators or buffering views.
     */
    fun hasActiveSpinner(service: AccessibilityService?): Boolean {
        if (service == null) return false
        val root = runCatching { service.rootInActiveWindow }.getOrNull() ?: return false
        return inspectNode(root, 0)
    }

    private fun inspectNode(node: AccessibilityNodeInfo?, depth: Int): Boolean {
        if (node == null || depth > 20) return false

        val cls = node.className?.toString().orEmpty()
        val text = node.text?.toString().orEmpty().lowercase()
        val desc = node.contentDescription?.toString().orEmpty().lowercase()
        val id = node.viewIdResourceName.orEmpty().lowercase()

        // Check widget class
        if (cls in spinnerClasses) {
            // Indeterminate progress bars are active spinners
            return true
        }

        // Check IDs and descriptions
        if (id.contains("spinner") || id.contains("progress_bar") || id.contains("loading_view") || id.contains("shimmer")) {
            return true
        }

        for (kw in spinnerKeywords) {
            if (desc.contains(kw) || (text.contains(kw) && text.length < 25)) {
                return true
            }
        }

        for (i in 0 until node.childCount) {
            val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
            if (inspectNode(child, depth + 1)) return true
        }

        return false
    }

    /**
     * Waits up to [maxWaitMs] for any active loading spinners to dismiss before continuing.
     */
    fun waitForScreenIdle(service: AccessibilityService?, maxWaitMs: Long = 2500L): Boolean {
        if (service == null) return true
        val start = SystemClock.uptimeMillis()
        var spinnerSeen = false

        while (SystemClock.uptimeMillis() - start < maxWaitMs) {
            if (hasActiveSpinner(service)) {
                spinnerSeen = true
                Log.d(TAG, "Screen is busy (active spinner detected), dwelling...")
                Thread.sleep(250)
            } else {
                if (spinnerSeen) {
                    Log.i(TAG, "Screen transitioned from loading to idle in ${SystemClock.uptimeMillis() - start}ms")
                    Thread.sleep(150) // Settle time
                }
                return true
            }
        }

        Log.w(TAG, "Spinner wait timed out after ${maxWaitMs}ms; proceeding anyway")
        return false
    }
}
