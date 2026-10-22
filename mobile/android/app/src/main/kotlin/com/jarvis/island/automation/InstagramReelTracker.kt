package com.jarvis.island.automation

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.jarvis.island.service.JarvisIslandAccessibilityService
import com.jarvis.island.service.JarvisIslandOverlayService
import com.jarvis.island.settings.JarvisIslandSettings

/**
 * Counts short-form video reels (Instagram, Facebook, YouTube Shorts) as they are watched.
 *
 * Implements the open-source detection architecture from s0uvik/reels-tracker
 * (https://github.com/s0uvik/reels-tracker):
 *  1. Structural verification of the full-screen reel viewer container view IDs
 *     (never counts in Settings, Profiles, or Feeds).
 *  2. Center-screen author & caption content fingerprint hashing.
 *  3. Debounced state machine with session history ring-buffer to completely
 *     ignore backward navigation when swiping down to see previous reels.
 *  4. Trailing-edge coalescing to eliminate CPU waste during video playback.
 */
object InstagramReelTracker {

    private const val TAG = "JarvisReelTracker"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val detector = ReelDetector(debugLogging = false)
    private val tracker = ReelViewTracker(debounceMs = 500L)

    @Volatile private var pendingUi: TrackedAppUi? = null
    @Volatile private var inspectScheduled = false
    @Volatile private var firstPendingAt = 0L

    fun onEvent(service: AccessibilityService, event: AccessibilityEvent) {
        if (!JarvisIslandSettings.instance.reelCounterEnabled) return

        val pkg = event.packageName?.toString().orEmpty()
        val ui = TrackedAppConfig.forPackage(pkg)
        val now = System.currentTimeMillis()

        if (ui == null) {
            if (tracker.isViewOpen) {
                applyActions(service, tracker.onLeftViewer(now))
                cancelInspection()
                notifyViewerState(false)
            }
            return
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                detector.onScrollEvent(ui, event)
                scheduleInspection(service, ui)
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                scheduleInspection(service, ui)
            }
        }
    }

    private fun scheduleInspection(service: AccessibilityService, ui: TrackedAppUi) {
        pendingUi = ui
        val now = SystemClock.uptimeMillis()
        if (!inspectScheduled) {
            inspectScheduled = true
            firstPendingAt = now
        }
        mainHandler.removeCallbacksAndMessages(null)
        val waited = now - firstPendingAt
        val delay = if (waited >= TrackedAppConfig.INSPECT_MAX_WAIT_MS) 0L else TrackedAppConfig.INSPECT_SETTLE_MS
        mainHandler.postDelayed({
            runInspection(service)
        }, delay)
    }

    private fun cancelInspection() {
        mainHandler.removeCallbacksAndMessages(null)
        inspectScheduled = false
        pendingUi = null
    }

    private fun runInspection(service: AccessibilityService) {
        inspectScheduled = false
        val ui = pendingUi ?: return
        pendingUi = null

        val root = runCatching { service.rootInActiveWindow }.getOrNull()
        val now = System.currentTimeMillis()

        when (val result = detector.inspect(ui, root)) {
            is ReelDetector.Result.InViewer -> {
                notifyViewerState(true)
                applyActions(service, tracker.onReelVisible(ui.appKey, result.fingerprint, now))
            }
            ReelDetector.Result.NotInViewer -> {
                if (tracker.isViewOpen) {
                    applyActions(service, tracker.onLeftViewer(now))
                }
                notifyViewerState(false)
            }
            ReelDetector.Result.InViewerUnidentified -> {
                // Viewer is open but page is mid-scroll or loading; maintain current viewer state
                notifyViewerState(true)
            }
        }
    }

    private fun applyActions(service: AccessibilityService, actions: List<ReelViewTracker.Action>) {
        if (actions.isEmpty()) return
        for (action in actions) {
            when (action) {
                is ReelViewTracker.Action.Started -> {
                    if (action.isNewView) {
                        val today = ReelCounter.record(service)
                        Log.i(TAG, "New ${action.app} reel counted; today=$today")
                        JarvisIslandAccessibilityService.instance?.updateReelCount(today)
                        JarvisIslandOverlayService.instance?.updateReelCount(today)
                    } else {
                        Log.d(TAG, "Re-viewing previous reel; counter not incremented")
                    }
                }
                is ReelViewTracker.Action.Closed -> {
                    Log.d(TAG, "Closed reel after ${action.durationMs}ms")
                }
            }
        }
    }

    private fun notifyViewerState(inViewer: Boolean) {
        val a11y = JarvisIslandAccessibilityService.instance
        val overlay = JarvisIslandOverlayService.instance
        a11y?.updateReelViewerActive(inViewer)
        overlay?.updateReelViewerActive(inViewer)
    }

    fun reset() {
        cancelInspection()
        detector.forget()
        tracker.reset()
    }
}
