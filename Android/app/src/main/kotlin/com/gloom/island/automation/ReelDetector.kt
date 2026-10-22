package com.gloom.island.automation

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs

/**
 * Inspects a short-form video app's node tree and decides whether the reels
 * viewer is open and which reel is on screen.
 *
 * Adapted from the open-source reels-tracker (https://github.com/s0uvik/reels-tracker).
 * Any exception or non-viewer screen (such as Settings, Profile, or Feed)
 * yields [Result.NotInViewer] safely.
 */
class ReelDetector(private val debugLogging: Boolean = false) {

    sealed interface Result {
        data class InViewer(val fingerprint: String) : Result
        /** Viewer is open but the reel is still loading or transitions; keep current reel open. */
        data object InViewerUnidentified : Result
        data object NotInViewer : Result
    }

    private val lastScrollIndex = HashMap<String, Int>()

    /**
     * Checks if a scroll event is a minor jitter or micro-tremor that should not
     * trigger a state machine transition or reel change.
     */
    fun isScrollJitter(ui: TrackedAppUi, event: AccessibilityEvent): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val deltaY = event.scrollDeltaY
            val deltaX = event.scrollDeltaX
            if (deltaY != 0 || deltaX != 0) {
                // Ignore micro-scrolls under 45px
                if (abs(deltaY) in 1..45 && abs(deltaX) in 0..45) {
                    return true
                }
            }
        }
        val prev = lastScrollIndex[ui.packageName]
        val curr = event.fromIndex
        if (prev != null && prev == curr && event.itemCount > 1) {
            return true
        }
        return false
    }

    fun onScrollEvent(ui: TrackedAppUi, event: AccessibilityEvent) {
        val index = event.fromIndex
        if (index >= 0 && event.itemCount > 1) {
            lastScrollIndex[ui.packageName] = index
        }
    }

    fun forget() {
        lastScrollIndex.clear()
    }

    fun inspect(ui: TrackedAppUi, root: AccessibilityNodeInfo?): Result {
        if (root == null) return Result.NotInViewer
        return try {
            inspectUnsafe(ui, root)
        } catch (t: Throwable) {
            if (debugLogging) Log.w(TAG, "inspect failed for ${ui.packageName}", t)
            Result.NotInViewer
        }
    }

    private fun inspectUnsafe(ui: TrackedAppUi, root: AccessibilityNodeInfo): Result {
        val rootPackage = root.packageName ?: return Result.NotInViewer
        if (!ui.packageName.contentEquals(rootPackage)) return Result.NotInViewer

        val container = findFirstVisible(root, ui, ui.viewerContainerIds)
        if (container == null) {
            return Result.NotInViewer
        }

        val screen = Rect().also { root.getBoundsInScreen(it) }

        // 1) Preferred: identifying labels of the reel nearest the screen centre.
        val parts = ArrayList<CharSequence?>(8)
        for (id in ui.fingerprintIds) {
            val node = mostCentralNode(root, fullId(ui, id), screen) ?: continue
            parts += node.text
            parts += node.contentDescription
        }
        Fingerprint.of(parts)?.let { return Result.InViewer(it) }

        // 2) Bounded walk of the pager's most visible page, collecting descriptions.
        Fingerprint.of(boundedDescriptions(container, screen))?.let { return Result.InViewer(it) }

        // 3) Pager index from the last scroll event.
        lastScrollIndex[ui.packageName]?.let { return Result.InViewer(Fingerprint.ofIndex(it)) }

        if (debugLogging) Log.d(TAG, "viewer found but reel unidentified (${ui.packageName})")
        return Result.InViewerUnidentified
    }

    private fun fullId(ui: TrackedAppUi, id: String) = "${ui.packageName}:id/$id"

    private fun findFirstVisible(
        root: AccessibilityNodeInfo,
        ui: TrackedAppUi,
        ids: List<String>
    ): AccessibilityNodeInfo? {
        for (id in ids) {
            val visible = root.findAccessibilityNodeInfosByViewId(fullId(ui, id))
                .firstOrNull { it.isVisibleToUser }
            if (visible != null) return visible
        }
        return null
    }

    /** Visible node with [viewId] closest to screen center. */
    private fun mostCentralNode(
        root: AccessibilityNodeInfo,
        viewId: String,
        screen: Rect
    ): AccessibilityNodeInfo? {
        val matches = root.findAccessibilityNodeInfosByViewId(viewId)
        if (matches.isEmpty()) return null
        val bounds = Rect()
        var best: AccessibilityNodeInfo? = null
        var bestDistance = Long.MAX_VALUE
        var bestArea = -1L
        for (node in matches) {
            if (!node.isVisibleToUser) continue
            node.getBoundsInScreen(bounds)
            if (!bounds.intersect(screen)) continue
            val distance = abs(bounds.centerY() - screen.centerY()).toLong()
            val area = bounds.width().toLong() * bounds.height().toLong()
            if (distance < bestDistance || (distance == bestDistance && area > bestArea)) {
                bestDistance = distance
                bestArea = area
                best = node
            }
        }
        return best
    }

    private fun boundedDescriptions(container: AccessibilityNodeInfo, screen: Rect): List<CharSequence?> {
        val page = mostVisibleChild(container, screen) ?: container
        val out = ArrayList<CharSequence?>(8)
        var visited = 0
        val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        stack.addLast(page to 0)
        while (stack.isNotEmpty() && visited < TrackedAppConfig.MAX_TRAVERSAL_NODES) {
            val (node, depth) = stack.removeLast()
            visited++
            node.contentDescription?.let { out += it }
            if (depth >= TrackedAppConfig.MAX_TRAVERSAL_DEPTH) continue
            for (i in node.childCount - 1 downTo 0) {
                val child = node.getChild(i) ?: continue
                if (child.isVisibleToUser) stack.addLast(child to depth + 1)
            }
        }
        return out
    }

    private fun mostVisibleChild(container: AccessibilityNodeInfo, screen: Rect): AccessibilityNodeInfo? {
        val bounds = Rect()
        var best: AccessibilityNodeInfo? = null
        var bestArea = 0L
        for (i in 0 until container.childCount) {
            val child = container.getChild(i) ?: continue
            child.getBoundsInScreen(bounds)
            if (!bounds.intersect(screen)) continue
            val area = bounds.width().toLong() * bounds.height().toLong()
            if (area > bestArea) {
                bestArea = area
                best = child
            }
        }
        return best
    }

    companion object {
        const val TAG = "ReelsDetector"
    }
}
