package com.gloom.island.automation

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.gloom.island.service.GloomIslandAccessibilityService
import com.gloom.island.service.GloomIslandOverlayService
import com.gloom.island.settings.GloomIslandSettings

/**
 * Counts short-form video reels (Instagram, Facebook, YouTube Shorts) as they are watched.
 *
 * Requirements:
 *  1. Count a reel ONLY when a new reel is brought into view and remains for a minimum dwell time (1200ms).
 *  2. Debounce duplicate accessibility events.
 *  3. Ignore scroll jitter and micro-scrolls.
 *  4. Don't double-count when returning or swiping back to a previously seen reel.
 */
object InstagramReelTracker {

    private const val TAG = "GloomReelTracker"
    // Minimum continuous dwell time before an on-screen reel is committed as watched
    private const val MIN_DWELL_MS = 1200L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val detector = ReelDetector(debugLogging = false)
    private val tracker = ReelViewTracker(debounceMs = 450L)

    @Volatile private var pendingUi: TrackedAppUi? = null
    @Volatile private var inspectScheduled = false
    @Volatile private var firstPendingAt = 0L

    // Pending dwell confirmation state
    private var pendingDwellRunnable: Runnable? = null
    private var pendingDwellFingerprint: String? = null

    fun onEvent(service: AccessibilityService, event: AccessibilityEvent) {
        if (!GloomIslandSettings.instance.reelCounterEnabled) return

        val pkg = event.packageName?.toString().orEmpty()
        val ui = TrackedAppConfig.forPackage(pkg)
        val now = System.currentTimeMillis()

        if (ui == null) {
            if (tracker.isViewOpen) {
                cancelPendingDwell()
                applyActions(service, tracker.onLeftViewer(now))
                cancelInspection()
                notifyViewerState(false)
            }
            return
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                // Reject scroll jitter / accidental tremors
                if (detector.isScrollJitter(ui, event)) {
                    return
                }
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
        // If there's an active dwell confirmation, keep it alive unless the reel changes
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

    private fun cancelPendingDwell() {
        pendingDwellRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingDwellRunnable = null
        pendingDwellFingerprint = null
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
                cancelPendingDwell()
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
                    // Cancel previous unconfirmed dwell if any
                    cancelPendingDwell()

                    if (action.isNewView) {
                        val targetFingerprint = tracker.currentFingerprint
                        pendingDwellFingerprint = targetFingerprint

                        // Schedule count confirmation only after minimum dwell time has elapsed
                        val dwellTask = Runnable {
                            if (tracker.isViewOpen && tracker.currentFingerprint == targetFingerprint) {
                                val today = ReelCounter.record(service)
                                Log.i(TAG, "Reel dwelled >= ${MIN_DWELL_MS}ms. New ${action.app} reel counted; today=$today")
                                GloomIslandAccessibilityService.instance?.updateReelCount(today)
                                GloomIslandOverlayService.instance?.updateReelCount(today)
                            }
                            pendingDwellRunnable = null
                            pendingDwellFingerprint = null
                        }
                        pendingDwellRunnable = dwellTask
                        mainHandler.postDelayed(dwellTask, MIN_DWELL_MS)
                    } else {
                        Log.d(TAG, "Re-viewing previous reel; counter not incremented")
                    }
                }
                is ReelViewTracker.Action.Closed -> {
                    // Left this reel segment; if it didn't complete dwell, it is not counted
                    cancelPendingDwell()
                    Log.d(TAG, "Closed reel after ${action.durationMs}ms")
                }
            }
        }
    }

    private fun notifyViewerState(inViewer: Boolean) {
        val a11y = GloomIslandAccessibilityService.instance
        val overlay = GloomIslandOverlayService.instance
        a11y?.updateReelViewerActive(inViewer)
        overlay?.updateReelViewerActive(inViewer)
    }

    fun reset() {
        cancelPendingDwell()
        cancelInspection()
        detector.forget()
        tracker.reset()
    }
}
