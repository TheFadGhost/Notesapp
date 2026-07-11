package com.fadghost.notesapp

import com.fadghost.notesapp.calendar.QuickAddParser
import com.fadghost.notesapp.data.db.entity.Recurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.Month

/**
 * Covers the local NL quick-add parser (PLAN.md §8 / §15). Reference "now" is a
 * fixed Saturday, 2026-07-11 10:00, so every expectation below is deterministic.
 */
class QuickAddParserTest {

    private val now = LocalDateTime.of(2026, 7, 11, 10, 0) // Saturday

    private fun parse(s: String) = QuickAddParser.parse(s, now)

    // --- Relative days ----------------------------------------------------------

    @Test fun tomorrow_with_am() {
        val r = parse("gym tomorrow 7am")!!
        assertEquals("gym", r.title)
        assertEquals(LocalDateTime.of(2026, 7, 12, 7, 0), r.dateTime)
        assertEquals(Recurrence.NONE, r.recurrence)
    }

    @Test fun today_keyword() {
        val r = parse("standup today 4pm")!!
        assertEquals(LocalDateTime.of(2026, 7, 11, 16, 0), r.dateTime)
    }

    @Test fun time_only_future_is_today() {
        val r = parse("review 3pm")!!
        assertEquals(LocalDateTime.of(2026, 7, 11, 15, 0), r.dateTime)
    }

    @Test fun time_only_past_rolls_to_tomorrow() {
        val r = parse("call bank 9am")!! // 09:00 < 10:00 now
        assertEquals(LocalDateTime.of(2026, 7, 12, 9, 0), r.dateTime)
    }

    // --- Weekdays ---------------------------------------------------------------

    @Test fun weekday_next_occurrence() {
        val r = parse("call mum friday 15:30")!!
        assertEquals("call mum", r.title)
        assertEquals(LocalDateTime.of(2026, 7, 17, 15, 30), r.dateTime) // Fri after Sat 11th
    }

    @Test fun next_prefix_pushes_a_week() {
        // Saturday "next saturday" => the following Saturday (18th), not today.
        val r = parse("laundry next saturday 11am")!!
        assertEquals(LocalDateTime.of(2026, 7, 18, 11, 0), r.dateTime)
    }

    @Test fun filler_words_stripped_from_title() {
        val r = parse("call mum on friday 15:30")!!
        assertEquals("call mum", r.title)
    }

    // --- am/pm + 24h ------------------------------------------------------------

    @Test fun twentyfour_hour_time() {
        val r = parse("deploy today 15:00")!!
        assertEquals(LocalDateTime.of(2026, 7, 11, 15, 0), r.dateTime)
    }

    @Test fun noon_and_midnight() {
        assertEquals(12, parse("lunch today noon")!!.dateTime.hour)
        assertEquals(0, parse("sleep today midnight")!!.dateTime.hour)
    }

    @Test fun twelve_am_pm_edges() {
        assertEquals(0, parse("x today 12am")!!.dateTime.hour)
        assertEquals(12, parse("x today 12pm")!!.dateTime.hour)
    }

    // --- UK date order (dd/mm) --------------------------------------------------

    @Test fun uk_date_order_dd_mm() {
        val r = parse("dentist 12/08 2pm")!!
        assertEquals("dentist", r.title)
        assertEquals(Month.AUGUST, r.dateTime.month)
        assertEquals(12, r.dateTime.dayOfMonth)
        assertEquals(14, r.dateTime.hour)
    }

    @Test fun uk_date_with_year() {
        val r = parse("party 25/12/2026 8pm")!!
        assertEquals(LocalDateTime.of(2026, 12, 25, 20, 0), r.dateTime)
    }

    @Test fun bare_past_date_rolls_to_next_year() {
        // 05/01 already passed in 2026 -> next year.
        val r = parse("renew 05/01")!!
        assertEquals(2027, r.dateTime.year)
        assertEquals(Month.JANUARY, r.dateTime.month)
        assertEquals(5, r.dateTime.dayOfMonth)
    }

    // --- Recurrence phrases -----------------------------------------------------

    @Test fun every_weekday_is_weekly_and_dated() {
        val r = parse("standup every monday 9am")!!
        assertEquals("standup", r.title)
        assertEquals(Recurrence.WEEKLY, r.recurrence)
        assertEquals(LocalDateTime.of(2026, 7, 13, 9, 0), r.dateTime) // next Monday
    }

    @Test fun daily_phrase() {
        val r = parse("vitamins every day 8am")!!
        assertEquals(Recurrence.DAILY, r.recurrence)
        assertEquals(LocalDateTime.of(2026, 7, 12, 8, 0), r.dateTime) // 08:00 past -> tomorrow
    }

    @Test fun monthly_phrase() {
        val r = parse("rent 1/08 monthly 9am")!!
        assertEquals(Recurrence.MONTHLY, r.recurrence)
        assertEquals(Month.AUGUST, r.dateTime.month)
    }

    @Test fun weekly_keyword() {
        val r = parse("report friday weekly 5pm")!!
        assertEquals(Recurrence.WEEKLY, r.recurrence)
        assertEquals(17, r.dateTime.hour)
    }

    // --- Nothing to parse -------------------------------------------------------

    @Test fun no_date_or_time_returns_null() {
        assertNull(parse("buy milk"))
    }

    @Test fun blank_returns_null() {
        assertNull(parse("   "))
    }
}
