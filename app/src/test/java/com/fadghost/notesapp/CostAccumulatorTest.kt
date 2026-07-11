package com.fadghost.notesapp

import com.fadghost.notesapp.data.ai.cost.AiCallCost
import com.fadghost.notesapp.data.ai.cost.CostAccumulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class CostAccumulatorTest {

    private fun row(at: String, cost: Double) =
        AiCallCost(createdAt = Instant.parse(at).toEpochMilli(), feature = "cleanup", model = "m", costUsd = cost)

    private val monthStart = Instant.parse("2026-07-01T00:00:00Z").toEpochMilli()
    private val now = Instant.parse("2026-07-31T23:59:59Z").toEpochMilli()

    @Test fun sumsOnlyRowsInTheMonthWindow() {
        val rows = listOf(
            row("2026-06-30T12:00:00Z", 1.0),  // previous month — excluded
            row("2026-07-02T12:00:00Z", 0.25),
            row("2026-07-20T12:00:00Z", 0.75),
            row("2026-08-01T00:00:01Z", 5.0)   // next month — excluded
        )
        val s = CostAccumulator.summarize(rows, monthStart, now)
        assertEquals(1.0, s.monthTotalUsd, 1e-9)
        assertEquals(2, s.monthCalls)
    }

    @Test fun lastCallIsGloballyNewestRegardlessOfMonth() {
        val rows = listOf(
            row("2026-07-02T12:00:00Z", 0.25),
            row("2026-08-05T12:00:00Z", 0.50)
        )
        val s = CostAccumulator.summarize(rows, monthStart, now)
        assertEquals(Instant.parse("2026-08-05T12:00:00Z").toEpochMilli(), s.lastCall!!.createdAt)
        // The Aug call is outside [monthStart, now] so it doesn't count toward the month.
        assertEquals(0.25, s.monthTotalUsd, 1e-9)
        assertEquals(1, s.monthCalls)
    }

    @Test fun emptyRowsYieldZeroAndNoLastCall() {
        val s = CostAccumulator.summarize(emptyList(), monthStart, now)
        assertEquals(0.0, s.monthTotalUsd, 1e-9)
        assertEquals(0, s.monthCalls)
        assertNull(s.lastCall)
    }

    @Test fun boundaryRowsAreInclusive() {
        val rows = listOf(row("2026-07-01T00:00:00Z", 0.1), row("2026-07-31T23:59:59Z", 0.2))
        val s = CostAccumulator.summarize(rows, monthStart, now)
        assertEquals(0.3, s.monthTotalUsd, 1e-9)
        assertEquals(2, s.monthCalls)
    }

    @Test fun startOfMonthComputesFirstInstant() {
        val computed = CostAccumulator.startOfMonth(now, ZoneOffset.UTC)
        assertEquals(monthStart, computed)
    }
}
