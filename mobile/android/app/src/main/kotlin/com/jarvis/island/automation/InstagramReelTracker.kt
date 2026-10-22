package com.jarvis.island.automation

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.jarvis.island.service.JarvisIslandAccessibilityService
import com.jarvis.island.service.JarvisIslandOverlayService
import com.jarvis.island.settings.JarvisIslandSettings

/**
 * Counts Reels as they are watched.
 *
 * A reel advances when the reels pager scrolls vertically. The counter
 * updates the live Dynamic Island pill in real-time with the Instagram icon
 * and live watch count.
 */
object InstagramReelTracker {

    private const val TAG = "JarvisReelCounter"
    private const val INSTAGRAM = "com.instagram.android"
    private const val FACEBOOK = "com.facebook.katana"
    private const val COUNT_DEBOUNCE_MS = 500L
    private const val TREE_CHECK_MS = 1_500L

    private val supportedPackages = setOf(INSTAGRAM, FACEBOOK)

    @Volatile private var inReels = false
    @Volatile private var lastCountAt = 0L
    @Volatile private var currentReelStartedAt = 0L
    @Volatile private var lastTreeCheckAt = 0L
    @Volatile private var lastPackage = ""
    @Volatile private var lastReelSignature = ""

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
                if (isSettingsOrNonReelScreen(service)) {
                    finishLastReel(service)
                    inReels = false
                } else if (isReelActivity(className) || treeHasReels(service)) {
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
                // If scrolling backwards (swiping down to see previous reel), ignore completely
                if (event.scrollDeltaY < 0) {
                    Log.d(TAG, "Ignoring backward scroll: swiping down (scrollDeltaY=${event.scrollDeltaY})")
                    return
                }

                // If currently inside settings or non-reel page, ensure we exit reels mode
                if (isSettingsOrNonReelScreen(service)) {
                    finishLastReel(service)
                    inReels = false
                    return
                }

                if (isReelsPager(event.source)) {
                    advance(service)
                }
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (isSettingsOrNonReelScreen(service)) {
                    finishLastReel(service)
                    inReels = false
                    return
                }

                val now = SystemClock.uptimeMillis()
                if (inReels) {
                    val desc = event.contentDescription?.toString().orEmpty()
                    val txt = event.text.joinToString(" ")
                    val candidate = if (desc.contains("Original audio", ignoreCase = true) || desc.contains("Reel by", ignoreCase = true)) desc
                    else if (txt.contains("Original audio", ignoreCase = true)) txt else ""
                    if (candidate.isNotEmpty() && candidate != lastReelSignature) {
                        lastReelSignature = candidate
                        advance(service)
                    }
                }
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
                    label.contains("settings") || label.contains("options") || label.contains("profile") ||
                        label.contains("home") || label.contains("search") || label.contains("explore") -> {
                        finishLastReel(service)
                        inReels = false
                    }
                    label.contains("reels") || label.contains("reel") || label.contains("clips") -> {
                        if (!isSettingsOrNonReelScreen(service)) {
                            enterReels(service)
                        }
                    }
                }
            }
        }
    }

    private fun enterReels(service: AccessibilityService) {
        if (inReels) return
        inReels = true
        currentReelStartedAt = SystemClock.uptimeMillis() - 1200L
        val currentCount = ReelCounter.today(service)
        JarvisIslandAccessibilityService.instance?.updateReelCount(currentCount)
        JarvisIslandOverlayService.instance?.updateReelCount(currentCount)
        Log.i(TAG, "Reels viewer opened (today count: $currentCount)")
    }

    /** A new reel is on screen. Count the previous one if it was watched. */
    private fun advance(service: AccessibilityService) {
        val now = SystemClock.uptimeMillis()
        if (now - lastCountAt < COUNT_DEBOUNCE_MS) return
        val watchedMs = if (currentReelStartedAt > 0L) now - currentReelStartedAt else 1200L
        currentReelStartedAt = now
        val minimum = (JarvisIslandSettings.instance.reelMinDwellMs.toLong()).coerceIn(250L, 1500L)
        if (watchedMs < minimum) {
            Log.d(TAG, "Skipping a reel watched for only ${watchedMs}ms (minimum: ${minimum}ms)")
            return
        }
        lastCountAt = now
        val today = ReelCounter.record(service)
        Log.i(TAG, "Reel counted after ${watchedMs}ms; today=$today")
        JarvisIslandAccessibilityService.instance?.updateReelCount(today)
        JarvisIslandOverlayService.instance?.updateReelCount(today)
    }

    private fun finishLastReel(service: AccessibilityService) {
        if (!inReels) return
        val watchedMs = SystemClock.uptimeMillis() - currentReelStartedAt
        val minimum = (JarvisIslandSettings.instance.reelMinDwellMs.toLong()).coerceIn(250L, 1500L)
        if (watchedMs >= minimum) {
            lastCountAt = SystemClock.uptimeMillis()
            val today = ReelCounter.record(service)
            Log.i(TAG, "Counted the last reel after ${watchedMs}ms; today=$today")
            JarvisIslandAccessibilityService.instance?.updateReelCount(today)
            JarvisIslandOverlayService.instance?.updateReelCount(today)
        }
    }

    private fun isSettingsOrNonReelScreen(service: AccessibilityService): Boolean {
        val root = runCatching { service.rootInActiveWindow }.getOrNull() ?: return false
        return checkSettingsNode(root, 0)
    }

    private fun checkSettingsNode(node: AccessibilityNodeInfo?, depth: Int): Boolean {
        if (node == null || depth > 12) return false
        val text = runCatching { node.text?.toString().orEmpty() }.getOrDefault("")
        val desc = runCatching { node.contentDescription?.toString().orEmpty() }.getOrDefault("")
        val id = runCatching { node.viewIdResourceName.orEmpty() }.getOrDefault("")

        if (text.equals("Settings and privacy", ignoreCase = true) ||
            text.equals("Settings", ignoreCase = true) ||
            text.equals("Accounts Center", ignoreCase = true) ||
            desc.equals("Settings and privacy", ignoreCase = true) ||
            (id.contains("action_bar_title", ignoreCase = true) && text.contains("Settings", ignoreCase = true))
        ) {
            return true
        }

        val count = runCatching { node.childCount }.getOrDefault(0)
        for (i in 0 until count.coerceAtMost(25)) {
            val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
            if (checkSettingsNode(child, depth + 1)) return true
        }
        return false
    }

    private fun isReelActivity(className: String): Boolean =
        className.contains("Clips", ignoreCase = true) ||
            className.contains("Reel", ignoreCase = true)

    private fun isReelsPager(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 8) {
            val cls = runCatching { current?.className?.toString().orEmpty() }.getOrDefault("")
            if (cls.contains("ViewPager", ignoreCase = true) ||
                cls.contains("RecyclerView", ignoreCase = true) ||
                cls.contains("ViewGroup", ignoreCase = true)
            ) {
                if (nodeNamesReels(current)) return true
            }
            current = runCatching { current?.parent }.getOrNull()
            depth += 1
        }
        return false
    }

    private fun nodeNamesReels(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val id = runCatching { node.viewIdResourceName.orEmpty() }.getOrDefault("")
        val desc = runCatching { node.contentDescription?.toString().orEmpty() }.getOrDefault("")

        // Never consider bottom navigation bar tabs as reels pager
        if (id.contains("tab") || id.contains("bar") || desc.contains("tab", ignoreCase = true)) {
            return false
        }

        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 6) {
            val cId = runCatching { current?.viewIdResourceName.orEmpty() }.getOrDefault("")
            val cDesc = runCatching { current?.contentDescription?.toString().orEmpty() }.getOrDefault("")
            if (cId.contains("clip", ignoreCase = true) ||
                cId.contains("reel", ignoreCase = true) ||
                cDesc.contains("Reel by", ignoreCase = true) ||
                cDesc.contains("Original audio", ignoreCase = true)
            ) {
                return true
            }
            current = runCatching { current?.parent }.getOrNull()
            depth += 1
        }
        return false
    }

    private fun treeHasReels(service: AccessibilityService): Boolean {
        if (isSettingsOrNonReelScreen(service)) return false
        val root = runCatching { service.rootInActiveWindow }.getOrNull() ?: return false
        return findReelsNode(root, 0)
    }

    private fun findReelsNode(node: AccessibilityNodeInfo?, depth: Int): Boolean {
        if (node == null || depth > 20) return false
        val id = runCatching { node.viewIdResourceName.orEmpty() }.getOrDefault("")
        val cls = runCatching { node.className?.toString().orEmpty() }.getOrDefault("")
        val desc = runCatching { node.contentDescription?.toString().orEmpty() }.getOrDefault("")
        val text = runCatching { node.text?.toString().orEmpty() }.getOrDefault("")

        // Ignore bottom navigation tabs so they do not falsely identify home/search/profile as reels
        if (id.contains("tab") || id.contains("nav") || desc.contains("tab", ignoreCase = true)) {
            return false
        }

        if (id.contains("clips_video_container", ignoreCase = true) || id.contains("reel_viewer", ignoreCase = true)) return true
        if (desc.contains("Reel by", ignoreCase = true) || desc.contains("Original audio", ignoreCase = true)) return true
        if (text.contains("Original audio", ignoreCase = true)) return true
        if (cls.contains("ViewPager", ignoreCase = true) && id.contains("pager", ignoreCase = true) && id.contains("reel", ignoreCase = true)) return true

        val count = runCatching { node.childCount }.getOrDefault(0)
        for (index in 0 until count.coerceAtMost(50)) {
            val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
            if (findReelsNode(child, depth + 1)) return true
        }
        return false
    }
}
