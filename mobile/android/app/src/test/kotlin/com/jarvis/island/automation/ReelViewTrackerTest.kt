package com.jarvis.island.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReelViewTrackerTest {
    private val app = "instagram"

    @Test
    fun firstReelStartsANewView() {
        val tracker = ReelViewTracker()
        val actions = tracker.onReelVisible(app, "reel_1", 1000L)
        assertEquals(listOf(ReelViewTracker.Action.Started(app, 1000L, isNewView = true)), actions)
        assertTrue(tracker.isViewOpen)
    }

    @Test
    fun sameFingerprintIsIgnored() {
        val tracker = ReelViewTracker()
        tracker.onReelVisible(app, "reel_1", 1000L)
        assertTrue(tracker.onReelVisible(app, "reel_1", 2000L).isEmpty())
        assertTrue(tracker.onReelVisible(app, "reel_1", 5000L).isEmpty())
    }

    @Test
    fun newReelClosesPreviousAndStartsNext() {
        val tracker = ReelViewTracker()
        tracker.onReelVisible(app, "reel_1", 1000L)
        val actions = tracker.onReelVisible(app, "reel_2", 3000L)
        assertEquals(
            listOf(
                ReelViewTracker.Action.Closed(app, startedAt = 1000L, durationMs = 2000L),
                ReelViewTracker.Action.Started(app, 3000L, isNewView = true)
            ),
            actions
        )
    }

    @Test
    fun swipingDownToPreviousReelDoesNotCount() {
        val tracker = ReelViewTracker()
        // User watches reel 1
        tracker.onReelVisible(app, "reel_1", 1000L)
        // User scrolls to reel 2
        tracker.onReelVisible(app, "reel_2", 3000L)
        // User swipes down to see previous reel 1 again!
        val actions = tracker.onReelVisible(app, "reel_1", 5000L)
        assertEquals(1, actions.count { it is ReelViewTracker.Action.Started })
        val started = actions.first { it is ReelViewTracker.Action.Started } as ReelViewTracker.Action.Started
        // Must NOT count as a new view because reel_1 was already seen in session history!
        assertFalse(started.isNewView)
    }

    @Test
    fun changesWithinDebounceAreIgnored() {
        val tracker = ReelViewTracker(debounceMs = 500L)
        tracker.onReelVisible(app, "reel_1", 1000L)
        assertTrue(tracker.onReelVisible(app, "reel_2", 1300L).isEmpty())
        // After debounce passes
        val actions = tracker.onReelVisible(app, "reel_2", 1600L)
        assertEquals(ReelViewTracker.Action.Started(app, 1600L, isNewView = true), actions.last())
    }

    @Test
    fun leavingViewerClosesOpenView() {
        val tracker = ReelViewTracker()
        tracker.onReelVisible(app, "reel_1", 1000L)
        val closed = tracker.onLeftViewer(3500L)
        assertEquals(listOf(ReelViewTracker.Action.Closed(app, startedAt = 1000L, durationMs = 2500L)), closed)
        assertFalse(tracker.isViewOpen)
    }

    @Test
    fun fingerprintHashingProducesConsistentNonEmptyHash() {
        val fp1 = Fingerprint.of(listOf("creator_name", "Awesome reel caption"))
        val fp2 = Fingerprint.of(listOf("creator_name", "Awesome reel caption"))
        val fp3 = Fingerprint.of(listOf("different_creator", "Different caption"))

        assertTrue(fp1 != null && fp1.length == 32)
        assertEquals(fp1, fp2)
        assertNotEquals(fp1, fp3)
    }
}
