package com.jarvis.island.automation

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.jarvis.island.settings.JarvisIslandSettings

/**
 * Counts Reels as they are watched.
 *
 * A reel advances when the reels pager scrolls vertically. The counter only
 * listens while the clips viewer is in front, requires the scroll to come from
 * a pager or a node whose id names clips/reels, and only counts a reel that was
 * actually on screen for the configured dwell time. A flicked-through reel is
 * not watched, so it is not counted. The last reel is counted when the viewer
 * is closed, if it was watched long enough.
 */
object InstagramReelTracker {

    private const val TAG = "JarvisReelCounter"
    private const val INSTAGRAM = "com.instagram.android"
    private const val FACEBOOK = "com.facebook.katana"
    private const val COUNT_DEBOUNCE_MS = 600L
    private const val TREE_CHECK_MS = 2_000L

    private val supportedPackages = setOf(INSTAGRAM, FACEBOOK)

    @Volatile private var inReels = false
    @Volatile private var lastCountAt = 0L
    @Volatile private var currentReelStartedAt = 0L
    @Volatile private var lastTreeCheckAt = 0L
    @Volatile private var lastPackage = ""

    fun onEvent(service: AccessibilityService, event: AccessibilityEvent) {
        if (!JarvisIslandSettings.instance.reelCounterEnabled) return
        val pkg = event.packageName?.toString().orEmpty()
        if (pkg !in supportedPackages) {
            if (pkg.isNotEmpty() && pkg != lastPackage) {
                finishLastReel(service)
                inReels = false
                lastPackage = pkg
            }
            return
        }
        lastPackage = pkg

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val className = event.className?.toString().orEmpty()
                if (isReelActivity(className)) {
                    enterReels(service)
                } else if (className.contains("Activity", ignoreCase = true) &&
                    !className.contains("MainTab", ignoreCase = true) &&
                    !className.contains("MainActivity", ignoreCase = true)
                ) {
                    finishLastReel(service)
                    inReels = false
                }
            }

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (isReelsPager(event.source) && (inReels || nodeNamesReels(event.source))) {
                    advance(service)
                }
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val now = SystemClock.uptimeMillis()
                if (now - lastTreeCheckAt >= TREE_CHECK_MS) {
                    lastTreeCheckAt = now
                    if (!inReels && treeHasReels(service)) {
                        enterReels(service)
                    }
                }
            }

            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val label = buildString {
                    append(event.text.joinToString(" "))
                    append(' ')
                    append(event.contentDescription?.toString().orEmpty())
                }.lowercase()
                when {
                    label.contains("reels") || label.contains("reel") -> enterReels(service)
                    label.contains("home") -> {
                        finishLastReel(service)
                        inReels = false
                    }
                }
            }
        }
    }

    private fun enterReels(service: AccessibilityService) {
        if (inReels) return
        inReels = true
        currentReelStartedAt = SystemClock.uptimeMillis()
        Log.i(TAG, "Reels viewer opened")
    }

    /** A new reel is on screen. Count the previous one if it was watched. */
    private fun advance(service: AccessibilityService) {
        val now = SystemClock.uptimeMillis()
        if (now - lastCountAt < COUNT_DEBOUNCE_MS) return
        val watchedMs = now - currentReelStartedAt
        currentReelStartedAt = now
        val minimum = JarvisIslandSettings.instance.reelMinDwellMs.toLong()
        if (watchedMs < minimum) {
            Log.d(TAG, "Skipping a reel watched for only ${watchedMs}ms")
            return
        }
        lastCountAt = now
        val today = ReelCounter.record(service)
        Log.i(TAG, "Reel counted after ${watchedMs}ms; today=$today")
    }

    private fun finishLastReel(service: AccessibilityService) {
        if (!inReels) return
        val watchedMs = SystemClock.uptimeMillis() - currentReelStartedAt
        val minimum = JarvisIslandSettings.instance.reelMinDwellMs.toLong()
        if (watchedMs >= minimum) {
            lastCountAt = SystemClock.uptimeMillis()
            ReelCounter.record(service)
            Log.i(TAG, "Counted the last reel after ${watchedMs}ms")
        }
    }

    private fun isReelActivity(className: String): Boolean =
        className.contains("Clips", ignoreCase = true) ||
            className.contains("Reel", ignoreCase = true)

    private fun isReelsPager(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 6) {
            val cls = runCatching { current?.className?.toString().orEmpty() }.getOrDefault("")
            if (cls.contains("ViewPager", ignoreCase = true) ||
                cls.contains("RecyclerView", ignoreCase = true)
            ) {
                if (nodeNamesReels(current)) return true
                if (cls.contains("ViewPager", ignoreCase = true) && inReels) return true
            }
            current = runCatching { current?.parent }.getOrNull()
            depth += 1
        }
        return false
    }

    private fun nodeNamesReels(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 6) {
            val id = runCatching { current?.viewIdResourceName.orEmpty() }.getOrDefault("")
            if (id.contains("clip", ignoreCase = true) || id.contains("reel", ignoreCase = true)) {
                return true
            }
            current = runCatching { current?.parent }.getOrNull()
            depth += 1
        }
        return false
    }

    private fun treeHasReels(service: AccessibilityService): Boolean {
        val root = runCatching { service.rootInActiveWindow }.getOrNull() ?: return false
        return findReelsNode(root, 0)
    }

    private fun findReelsNode(node: AccessibilityNodeInfo?, depth: Int): Boolean {
        if (node == null || depth > 18) return false
        val id = runCatching { node.viewIdResourceName.orEmpty() }.getOrDefault("")
        val cls = runCatching { node.className?.toString().orEmpty() }.getOrDefault("")
        if (id.contains("clip", ignoreCase = true) || id.contains("reel", ignoreCase = true)) return true
        if (cls.contains("ViewPager", ignoreCase = true) && id.contains("pager", ignoreCase = true)) return true
        val count = runCatching { node.childCount }.getOrDefault(0)
        for (index in 0 until count.coerceAtMost(60)) {
            val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
            if (findReelsNode(child, depth + 1)) return true
        }
        return false
    }
}
