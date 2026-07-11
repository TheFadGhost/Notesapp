package com.fadghost.notesapp.calendar

import com.fadghost.notesapp.data.db.entity.Recurrence
import java.time.Instant
import java.time.ZoneId

/**
 * Pure, Android-free recurrence arithmetic (PLAN.md §8 — simple daily/weekly/
 * monthly repeat, DST-safe). Everything works in wall-clock terms via
 * [java.time.ZonedDateTime]: adding a day keeps the local time-of-day even when a
 * DST transition makes that day 23 or 25 hours long, which is what a user expects
 * from "every day at 9am". Kept out of the `alarm`/`ui` packages so unit tests can
 * exercise it with no Robolectric.
 */
object RecurrenceMath {

    /**
     * The single next occurrence strictly after [fromMillis], preserving the local
     * clock time of the seed in [zone]. Returns [fromMillis] unchanged for
     * [Recurrence.NONE].
     */
    fun nextOccurrence(fromMillis: Long, zone: ZoneId, recurrence: Recurrence): Long {
        val zdt = Instant.ofEpochMilli(fromMillis).atZone(zone)
        val next = when (recurrence) {
            Recurrence.NONE -> return fromMillis
            Recurrence.DAILY -> zdt.plusDays(1)
            Recurrence.WEEKLY -> zdt.plusWeeks(1)
            Recurrence.MONTHLY -> zdt.plusMonths(1)
        }
        return next.toInstant().toEpochMilli()
    }

    /**
     * First occurrence at or after [nowMillis], walking forward from the [seedMillis]
     * anchor. For a non-recurring item this is just the seed (which may be in the
     * past — the caller decides whether to fire immediately). Guards against runaway
     * loops with a generous cap.
     */
    fun nextFrom(seedMillis: Long, zone: ZoneId, recurrence: Recurrence, nowMillis: Long): Long {
        if (recurrence == Recurrence.NONE) return seedMillis
        var t = seedMillis
        var guard = 0
        while (t <= nowMillis && guard < 4000) {
            t = nextOccurrence(t, zone, recurrence)
            guard++
        }
        return t
    }

    /**
     * All occurrence start-millis that fall within [rangeStartMillis, rangeEndMillis)
     * for an item anchored at [seedMillis]. Used to place dots / agenda rows for a
     * recurring event or reminder across the visible calendar window without
     * materialising rows in the DB. Bounded by [max] to stay cheap.
     */
    fun occurrencesInRange(
        seedMillis: Long,
        zone: ZoneId,
        recurrence: Recurrence,
        rangeStartMillis: Long,
        rangeEndMillis: Long,
        max: Int = 400
    ): List<Long> {
        if (recurrence == Recurrence.NONE) {
            return if (seedMillis in rangeStartMillis until rangeEndMillis) listOf(seedMillis) else emptyList()
        }
        val out = ArrayList<Long>()
        // Fast-forward to the first occurrence at/after the range start.
        var t = seedMillis
        var guard = 0
        while (t < rangeStartMillis && guard < 100_000) {
            t = nextOccurrence(t, zone, recurrence)
            guard++
        }
        while (t < rangeEndMillis && out.size < max) {
            if (t >= rangeStartMillis) out.add(t)
            t = nextOccurrence(t, zone, recurrence)
        }
        return out
    }
}
