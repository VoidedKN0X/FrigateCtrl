package com.vknxworks.frigatectrl.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnoozeTest {
    @Test fun roundTrip() {
        val map = mapOf("front_door" to 2_000L, "back-yard" to 3_000L)
        assertEquals(map, Snooze.decode(Snooze.encode(map)))
        assertEquals(emptyMap<String, Long>(), Snooze.decode(""))
    }

    @Test fun dropsOddEntries() {
        assertEquals(mapOf("cam" to 5L), Snooze.decode("cam:5,:7,bad name:9,other:x,noseparator"))
    }

    @Test fun snoozeEndsOnTime() {
        val map = Snooze.with(emptyMap(), "cam", 1_000L, nowMs = 0L)
        assertTrue(Snooze.isSnoozed(map, "cam", 999L))
        assertFalse(Snooze.isSnoozed(map, "cam", 1_000L))
        assertFalse(Snooze.isSnoozed(map, "other", 10L))
    }

    @Test fun addingDropsFinishedSnoozes() {
        val map = Snooze.with(mapOf("old" to 10L), "cam", 2_000L, nowMs = 500L)
        assertEquals(mapOf("cam" to 2_000L), map)
        assertEquals(mapOf("old" to 10L), Snooze.with(mapOf("old" to 10L), "bad name", 2_000L, nowMs = 5L))
    }
}
