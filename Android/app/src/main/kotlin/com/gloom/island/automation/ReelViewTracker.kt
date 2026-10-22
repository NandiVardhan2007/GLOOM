package com.gloom.island.automation

/**
 * Pure state machine that turns "this reel is visible now" observations into
 * view start / close actions.
 *
 * Adapted from the open-source reels-tracker (https://github.com/s0uvik/reels-tracker)
 * and enhanced with an LRU recent history buffer to strictly reject counting when
 * swiping backward/down to see previous reels.
 */
class ReelViewTracker(
    private val debounceMs: Long = DEFAULT_DEBOUNCE_MS,
    private val maxViewDurationMs: Long = DEFAULT_MAX_VIEW_DURATION_MS,
    private val historyCapacity: Int = DEFAULT_HISTORY_CAPACITY
) {
    sealed interface Action {
        /** A reel view started. [isNewView] is false when resuming or swiping back to a previously seen reel. */
        data class Started(val app: String, val at: Long, val isNewView: Boolean) : Action
        /** The open segment ended; add [durationMs] to the view that was open. */
        data class Closed(val app: String, val startedAt: Long, val durationMs: Long) : Action
    }

    private data class OpenSegment(val app: String, val fingerprint: String, val startedAt: Long)

    private var open: OpenSegment? = null
    private var lastFingerprint: String? = null
    private var lastApp: String? = null
    private var lastRecordedAt: Long? = null

    /** Recently viewed reel fingerprints in this session (LRU order). */
    private val recentFingerprints = LinkedHashSet<String>()

    val isViewOpen: Boolean
        get() = open != null

    val currentFingerprint: String?
        get() = open?.fingerprint

    val openSince: Long?
        get() = open?.startedAt

    fun openElapsedMs(now: Long): Long {
        val current = open ?: return 0L
        return (now - current.startedAt).coerceIn(0L, maxViewDurationMs)
    }

    fun onReelVisible(app: String, fingerprint: String, now: Long): List<Action> {
        val current = open
        if (current != null && current.app == app && current.fingerprint == fingerprint) {
            return emptyList()
        }

        val recordedAt = lastRecordedAt
        if (current != null && recordedAt != null && now - recordedAt < debounceMs) {
            return emptyList()
        }

        val actions = ArrayList<Action>(2)
        if (current != null) {
            actions += close(current, now)
        }

        val resuming = current == null && lastFingerprint == fingerprint && lastApp == app
        val isAlreadySeen = recentFingerprints.contains(fingerprint)
        val isNew = !resuming && !isAlreadySeen

        open = OpenSegment(app, fingerprint, now)
        lastFingerprint = fingerprint
        lastApp = app

        if (isNew) {
            lastRecordedAt = now
            recentFingerprints.add(fingerprint)
            if (recentFingerprints.size > historyCapacity) {
                val oldest = recentFingerprints.iterator().next()
                recentFingerprints.remove(oldest)
            }
        }

        actions += Action.Started(app, now, isNewView = isNew)
        return actions
    }

    /** User left the reels viewer (went to settings, profile, home feed, or backgrounded the app). */
    fun onLeftViewer(now: Long): List<Action> {
        val current = open ?: return emptyList()
        open = null
        return listOf(close(current, now))
    }

    fun reset() {
        open = null
        lastFingerprint = null
        lastApp = null
        lastRecordedAt = null
        recentFingerprints.clear()
    }

    private fun close(segment: OpenSegment, now: Long): Action.Closed {
        val duration = (now - segment.startedAt).coerceIn(0L, maxViewDurationMs)
        return Action.Closed(segment.app, segment.startedAt, duration)
    }

    companion object {
        const val DEFAULT_DEBOUNCE_MS = 500L
        const val DEFAULT_MAX_VIEW_DURATION_MS = 10 * 60 * 1000L
        const val DEFAULT_HISTORY_CAPACITY = 100
    }
}
