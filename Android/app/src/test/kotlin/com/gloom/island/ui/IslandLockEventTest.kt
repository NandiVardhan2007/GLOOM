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
}
