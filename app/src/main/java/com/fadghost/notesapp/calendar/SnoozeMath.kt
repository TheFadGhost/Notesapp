package com.fadghost.notesapp.calendar

/**
 * Pure snooze arithmetic (PLAN.md §8 — "Snooze 10m / Snooze 1h"). A snooze is a
 * fixed wall-time offset from the moment the user taps, so plain millisecond
 * addition is correct and DST-independent (unlike recurrence, which is anchored to
 * a local clock time). Extracted so the notification action receiver stays trivial
 * and the maths is unit-tested.
 */
object SnoozeMath {
    const val SNOOZE_10_MIN_MS = 10 * 60_000L
    const val SNOOZE_1_HOUR_MS = 60 * 60_000L

    /** Absolute trigger time for a snooze of [durationMs] taken at [fromMillis]. */
    fun snoozeUntil(fromMillis: Long, durationMs: Long): Long = fromMillis + durationMs
}
