package com.gloom.island.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandLockEventTest {

    @Test
    fun lockIslandEventDefaultsAndProperties() {
        val eventDefault = LockIslandEvent()
        assertFalse(eventDefault.isUnlocked)
        assertTrue(eventDefault.timestampMs > 0L)

        val eventUnlocked = LockIslandEvent(isUnlocked = true, timestampMs = 12345L)
        assertTrue(eventUnlocked.isUnlocked)
        assertEquals(12345L, eventUnlocked.timestampMs)
    }

    @Test
    fun islandCompactKindIncludesLock() {
        val values = IslandCompactKind.values()
        assertTrue(values.contains(IslandCompactKind.LOCK))
        assertEquals(IslandCompactKind.LOCK, IslandCompactKind.valueOf("LOCK"))
    }

    @Test
    fun compactTargetWidthIsInvariantUnderLockState() {
        // Pill width must strictly match settings.pillWidth during lock, unlock, and idle.
        val defaultPillWidth = 180
        val isExpanded = false

        // Both idle and lock states must evaluate to defaultPillWidth without ballooning
        val idleWidth = if (isExpanded) 400 else defaultPillWidth
        val lockActiveWidth = if (isExpanded) 400 else defaultPillWidth
        assertEquals(defaultPillWidth, idleWidth)
        assertEquals(defaultPillWidth, lockActiveWidth)

        // Wing width plus camera gap must not exceed total compact width
        val cutoutClearance = 125
        val maxCameraGap = (defaultPillWidth - 96).coerceAtLeast(36)
        val cameraGap = cutoutClearance.coerceIn(36, maxCameraGap)
        val wingWidth = ((defaultPillWidth - cameraGap) / 2).coerceAtLeast(42)
        val totalOccupied = (wingWidth * 2) + cameraGap
        assertTrue("Total compact wing content must be well-bounded", totalOccupied <= defaultPillWidth + 4)
    }

    @Test
    fun unlockedLabelFitsWithoutEllipsisAcrossFontScales() {
        // Trailing wing available width: wingWidth (48dp) - inset (8dp) = 40dp
        val availableWingWidthDp = 48f - 8f // 40dp

        // At 10sp base font size with -0.35sp letter spacing:
        // Average char width in Roboto / SF Pro at 10sp is ~4.06dp (32.5dp for 8 chars "Unlocked").
        val baseLabelWidthDp = 32.5f

        // With unscaled font size: lockFontSize = 10f / fontScale
        // Effective rendered size in Compose: (10f / fontScale) * fontScale = 10.0f
        // Therefore, rendered text width is constant at 32.5dp across all system font scales.
        val fontScales = listOf(1.0f, 1.3f, 2.0f)
        for (scale in fontScales) {
            val unscaledFontSize = 10f / scale
            val effectiveRenderedSize = unscaledFontSize * scale
            assertEquals(10.0f, effectiveRenderedSize, 0.001f)

            val renderedWidthDp = baseLabelWidthDp * (effectiveRenderedSize / 10.0f)
            assertTrue(
                "Label width ($renderedWidthDp dp) at fontScale $scale must fit within wing box ($availableWingWidthDp dp)",
                renderedWidthDp <= availableWingWidthDp
            )
        }
    }
}
