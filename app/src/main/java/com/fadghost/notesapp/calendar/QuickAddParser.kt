package com.fadghost.notesapp.calendar

import com.fadghost.notesapp.data.db.entity.Recurrence
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Outcome of parsing a quick-add phrase (PLAN.md §8 — prefilled confirm chip). */
data class QuickAddResult(
    val title: String,
    val dateTime: LocalDateTime,
    val recurrence: Recurrence
)

/**
 * Local, offline natural-language quick-add parser (PLAN.md §8 — "gym tomorrow
 * 7am", no AI round-trip). UK conventions: dd/mm date order. Pure and deterministic
 * against an injected [now] so it is fully unit-testable. Returns null when the
 * phrase has neither a recognisable date nor time (nothing to prefill).
 *
 * Recognises: today / tomorrow, weekday names (optionally "next"), dd/mm[/yyyy],
 * 12h (7am, 2pm, 3:30pm) and 24h (15:30) times, noon / midnight, and repeat phrases
 * (daily / weekly / monthly / "every <weekday>").
 */
object QuickAddParser {

    private val WEEKDAYS = mapOf(
        "mon" to DayOfWeek.MONDAY, "tue" to DayOfWeek.TUESDAY, "wed" to DayOfWeek.WEDNESDAY,
        "thu" to DayOfWeek.THURSDAY, "fri" to DayOfWeek.FRIDAY, "sat" to DayOfWeek.SATURDAY,
        "sun" to DayOfWeek.SUNDAY
    )

    private val EDGE_FILLER = setOf("at", "on", "this", "for", "a", "an", "the")

    fun parse(input: String, now: LocalDateTime): QuickAddResult? {
        val text = input.trim()
        if (text.isEmpty()) return null

        val consumed = ArrayList<IntRange>()
        fun claim(m: MatchResult) { consumed.add(m.range) }

        // --- Recurrence (claimed first so "every monday" doesn't double-match) -----
        var recurrence = Recurrence.NONE
        var recurringWeekday: DayOfWeek? = null

        Regex("""\bevery\s+(mon|tue|wed|thu|fri|sat|sun)[a-z]*\b""", RegexOption.IGNORE_CASE)
            .find(text)?.let { m ->
                recurrence = Recurrence.WEEKLY
                recurringWeekday = WEEKDAYS[m.groupValues[1].lowercase()]
                claim(m)
            }
        if (recurrence == Recurrence.NONE) {
            Regex("""\b(daily|every\s+day)\b""", RegexOption.IGNORE_CASE).find(text)?.let { recurrence = Recurrence.DAILY; claim(it) }
        }
        if (recurrence == Recurrence.NONE) {
            Regex("""\b(weekly|every\s+week)\b""", RegexOption.IGNORE_CASE).find(text)?.let { recurrence = Recurrence.WEEKLY; claim(it) }
        }
        if (recurrence == Recurrence.NONE) {
            Regex("""\b(monthly|every\s+month)\b""", RegexOption.IGNORE_CASE).find(text)?.let { recurrence = Recurrence.MONTHLY; claim(it) }
        }

        // --- Time -----------------------------------------------------------------
        var time: LocalTime? = null
        run {
            val hm = Regex("""\b(\d{1,2}):(\d{2})\s*(am|pm)?\b""", RegexOption.IGNORE_CASE).find(text)
            val h12 = Regex("""\b(\d{1,2})\s*(am|pm)\b""", RegexOption.IGNORE_CASE).find(text)
            when {
                hm != null -> {
                    var h = hm.groupValues[1].toInt()
                    val min = hm.groupValues[2].toInt()
                    val ap = hm.groupValues[3].lowercase()
                    h = applyMeridiem(h, ap)
                    if (h in 0..23 && min in 0..59) { time = LocalTime.of(h, min); claim(hm) }
                }
                h12 != null -> {
                    val h = applyMeridiem(h12.groupValues[1].toInt(), h12.groupValues[2].lowercase())
                    if (h in 0..23) { time = LocalTime.of(h, 0); claim(h12) }
                }
                else -> {
                    Regex("""\bnoon\b""", RegexOption.IGNORE_CASE).find(text)?.let { time = LocalTime.of(12, 0); claim(it) }
                        ?: Regex("""\bmidnight\b""", RegexOption.IGNORE_CASE).find(text)?.let { time = LocalTime.of(0, 0); claim(it) }
                }
            }
        }

        // --- Date -----------------------------------------------------------------
        val today = now.toLocalDate()
        var date: LocalDate? = null

        // Explicit numeric UK date dd/mm[/yyyy].
        Regex("""\b(\d{1,2})/(\d{1,2})(?:/(\d{2,4}))?\b""").find(text)?.let { m ->
            val day = m.groupValues[1].toInt()
            val month = m.groupValues[2].toInt()
            val yearRaw = m.groupValues[3]
            if (day in 1..31 && month in 1..12) {
                val year = when {
                    yearRaw.isEmpty() -> today.year
                    yearRaw.length <= 2 -> 2000 + yearRaw.toInt()
                    else -> yearRaw.toInt()
                }
                val candidate = runCatching { LocalDate.of(year, month, day) }.getOrNull()
                if (candidate != null) {
                    // Bare dd/mm in the past rolls to next year (a future reminder).
                    date = if (yearRaw.isEmpty() && candidate.isBefore(today)) candidate.plusYears(1) else candidate
                    claim(m)
                }
            }
        }

        if (date == null) {
            Regex("""\btoday\b""", RegexOption.IGNORE_CASE).find(text)?.let { date = today; claim(it) }
        }
        if (date == null) {
            Regex("""\btomorrow\b""", RegexOption.IGNORE_CASE).find(text)?.let { date = today.plusDays(1); claim(it) }
        }
        if (date == null) {
            Regex("""\b(next\s+)?(mon|tue|wed|thu|fri|sat|sun)[a-z]*\b""", RegexOption.IGNORE_CASE).find(text)?.let { m ->
                val dow = WEEKDAYS[m.groupValues[2].lowercase()]!!
                val forceNext = m.groupValues[1].isNotBlank()
                date = nextWeekday(today, dow, forceNext)
                claim(m)
            }
        }
        // A recurring weekday phrase ("standup every monday") also fixes the first date.
        if (date == null && recurringWeekday != null) {
            date = nextWeekday(today, recurringWeekday, forceNext = false)
        }

        val hasTime = time != null
        val hasDate = date != null
        if (!hasTime && !hasDate) return null

        val finalTime = time ?: LocalTime.of(9, 0)
        val finalDate = when {
            date != null -> date!!
            // Time only: today if still in the future, else tomorrow.
            else -> if (today.atTime(finalTime).isAfter(now)) today else today.plusDays(1)
        }

        val title = buildTitle(text, consumed)
        return QuickAddResult(
            title = title.ifBlank { "Reminder" },
            dateTime = LocalDateTime.of(finalDate, finalTime),
            recurrence = recurrence
        )
    }

    private fun applyMeridiem(hour: Int, ap: String): Int = when (ap) {
        "pm" -> if (hour == 12) 12 else hour + 12
        "am" -> if (hour == 12) 0 else hour
        else -> hour
    }

    private fun nextWeekday(from: LocalDate, target: DayOfWeek, forceNext: Boolean): LocalDate {
        var d = from
        // Nearest matching weekday at or after today...
        while (d.dayOfWeek != target) d = d.plusDays(1)
        // ...but "next friday" means the following week when today already is/passes it.
        if (forceNext && d == from) d = d.plusWeeks(1)
        return d
    }

    private fun buildTitle(text: String, consumed: List<IntRange>): String {
        if (consumed.isEmpty()) return text.trim()
        val sb = StringBuilder()
        for (i in text.indices) {
            if (consumed.any { i in it }) sb.append(' ') else sb.append(text[i])
        }
        val words = sb.toString().split(Regex("""\s+""")).filter { it.isNotBlank() }.toMutableList()
        while (words.isNotEmpty() && words.first().lowercase() in EDGE_FILLER) words.removeAt(0)
        while (words.isNotEmpty() && words.last().lowercase() in EDGE_FILLER) words.removeAt(words.size - 1)
        return words.joinToString(" ")
    }
}
