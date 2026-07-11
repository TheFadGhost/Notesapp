package com.fadghost.notesapp

import com.fadghost.notesapp.calendar.SnoozeMath
import org.junit.Assert.assertEquals
import org.junit.Test

/** Snooze arithmetic (PLAN.md §15). Fixed offsets from tap time, DST-independent. */
class SnoozeMathTest {

    @Test fun ten_minutes() {
        assertEquals(600_000L, SnoozeMath.SNOOZE_10_MIN_MS)
        assertEquals(1_000L + 600_000L, SnoozeMath.snoozeUntil(1_000L, SnoozeMath.SNOOZE_10_MIN_MS))
    }

    @Test fun one_hour() {
        assertEquals(3_600_000L, SnoozeMath.SNOOZE_1_HOUR_MS)
        assertEquals(5_000L + 3_600_000L, SnoozeMath.snoozeUntil(5_000L, SnoozeMath.SNOOZE_1_HOUR_MS))
    }

    @Test fun snooze_is_pure_offset() {
        val from = 1_700_000_000_000L
        assertEquals(from + 600_000L, SnoozeMath.snoozeUntil(from, SnoozeMath.SNOOZE_10_MIN_MS))
        assertEquals(from + 3_600_000L, SnoozeMath.snoozeUntil(from, SnoozeMath.SNOOZE_1_HOUR_MS))
    }
}
