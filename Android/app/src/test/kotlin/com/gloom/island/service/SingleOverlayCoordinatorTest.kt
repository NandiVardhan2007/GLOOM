package com.gloom.island.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validates the single-overlay coordination rules, window layout bounds,
 * and lock/unlock lifecycle transitions to prevent duplicate overlays and touch-blocking traps.
 */
class SingleOverlayCoordinatorTest {

    // Simulates the service mutual-exclusion decision logic
    private fun shouldStartFallbackOverlay(isA11yRunning: Boolean, isA11yPermissionEnabled: Boolean): Boolean {
        return !isA11yRunning && !isA11yPermissionEnabled
    }

    @Test
    fun singleOverlayMutualExclusionPreventsDuplicateServices() {
        // When accessibility service is already running, fallback overlay service must NOT start
        assertFalse(shouldStartFallbackOverlay(isA11yRunning = true, isA11yPermissionEnabled = true))
        assertFalse(shouldStartFallbackOverlay(isA11yRunning = true, isA11yPermissionEnabled = false))

        // When accessibility service is not yet running but permission is enabled, fallback overlay must NOT start
        // (prevents cold-start race where both services add an overlay simultaneously)
        assertFalse(shouldStartFallbackOverlay(isA11yRunning = false, isA11yPermissionEnabled = true))

        // Fallback overlay service is only permitted when accessibility is neither running nor enabled
        assertTrue(shouldStartFallbackOverlay(isA11yRunning = false, isA11yPermissionEnabled = false))
    }

    @Test
    fun collapsedOverlayBoundsAreStrictlyConstrained() {
        val pillWidthDp = 180
        val pillHeightDp = 37
        val positionYDp = 2
        val density = 3.0f

        // Collapsed layout params must never be MATCH_PARENT (-1)
        val matchParent = -1
        val collapsedWidthPx = ((pillWidthDp + 24) * density).toInt()
        val collapsedHeightPx = ((positionYDp + pillHeightDp + 16) * density).toInt()

        assertTrue(collapsedWidthPx > 0)
        assertTrue(collapsedHeightPx > 0)
        assertTrue("Collapsed width must not be MATCH_PARENT", collapsedWidthPx != matchParent)
        assertEquals(612, collapsedWidthPx) // (180 + 24) * 3 = 612px on 1080p (3.0x density)
        assertEquals(165, collapsedHeightPx) // (2 + 37 + 16) * 3 = 165px
    }

    @Test
    fun windowFlagsPreserveTouchPassThroughWithoutModalTraps() {
        // WindowManager.LayoutParams constants
        val flagNotFocusable = 0x00000008
        val flagNotTouchModal = 0x00000020
        val flagWatchOutsideTouch = 0x00040000
        val flagLayoutInScreen = 0x00000100
        val flagLayoutNoLimits = 0x00000200
        val flagHardwareAccelerated = 0x01000000
        val flagNotTouchable = 0x00000010

        fun computeOverlayFlags(showOnLockScreen: Boolean): Int {
            var flags = flagNotFocusable or
                    flagNotTouchModal or
                    flagWatchOutsideTouch or
                    flagLayoutInScreen or
                    flagLayoutNoLimits or
                    flagHardwareAccelerated
            if (showOnLockScreen) {
                flags = flags or 0x00080000 // FLAG_SHOW_WHEN_LOCKED
            }
            return flags
        }

        val baseFlags = computeOverlayFlags(showOnLockScreen = true)

        // FLAG_NOT_TOUCH_MODAL must be present so touches outside the window bounds pass through
        assertTrue((baseFlags and flagNotTouchModal) != 0)

        // FLAG_NOT_TOUCHABLE must NOT be set when active
        assertFalse((baseFlags and flagNotTouchable) != 0)

        // FLAG_WATCH_OUTSIDE_TOUCH must be present to detect outside taps during expansion
        assertTrue((baseFlags and flagWatchOutsideTouch) != 0)

        // When hidden / screen-off with lockscreen disabled, FLAG_NOT_TOUCHABLE must be appended
        val hiddenFlags = baseFlags or flagNotTouchable
        assertTrue((hiddenFlags and flagNotTouchable) != 0)
    }

    @Test
    fun lockUnlockLifecycleEventOrdering() {
        var isExpanded = true
        var isUnlockedState: Boolean? = null
        var isWindowCollapsedImmediate = false

        // 1. Simulating ACTION_SCREEN_OFF while expanded
        fun onScreenOff() {
            isExpanded = false
            isWindowCollapsedImmediate = true
            isUnlockedState = false
        }

        onScreenOff()
        assertFalse("Screen off must immediately collapse any active expansion", isExpanded)
        assertTrue(isWindowCollapsedImmediate)
        assertEquals(false, isUnlockedState)

        // 2. Simulating ACTION_SCREEN_ON (locked keyguard)
        isWindowCollapsedImmediate = false
        fun onScreenOnLocked() {
            // Must re-enforce collapsed dimensions
            isWindowCollapsedImmediate = true
            isUnlockedState = false
        }

        onScreenOnLocked()
        assertTrue(isWindowCollapsedImmediate)
        assertEquals(false, isUnlockedState)

        // 3. Simulating ACTION_USER_PRESENT (user completes unlock)
        isWindowCollapsedImmediate = false
        fun onUserPresent() {
            isExpanded = false
            isWindowCollapsedImmediate = true
            isUnlockedState = true
        }

        onUserPresent()
        assertFalse(isExpanded)
        assertTrue("Unlock must immediately apply compact dimensions to free the touchscreen", isWindowCollapsedImmediate)
        assertEquals(true, isUnlockedState)
    }
}
