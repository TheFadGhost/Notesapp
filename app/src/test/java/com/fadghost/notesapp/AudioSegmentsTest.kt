package com.fadghost.notesapp

import com.fadghost.notesapp.data.audio.AudioSegments
import com.fadghost.notesapp.data.audio.SegmentAccumulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Segment-split bookkeeping (PLAN.md §3 — 5-min cap with auto-chunking). */
class AudioSegmentsTest {

    private val cap = AudioSegments.MAX_SEGMENT_MS // 5 min

    @Test fun splitsElevenMinutesIntoThreeSegments() {
        val durations = AudioSegments.splitDurations(11 * 60_000L)
        assertEquals(listOf(cap, cap, 60_000L), durations)
        assertEquals(11 * 60_000L, durations.sum())
    }

    @Test fun exactMultipleSplitsEvenly() {
        val durations = AudioSegments.splitDurations(10 * 60_000L)
        assertEquals(listOf(cap, cap), durations)
        assertEquals(2, AudioSegments.segmentCount(10 * 60_000L))
    }

    @Test fun shortRecordingIsOneSegment() {
        assertEquals(listOf(90_000L), AudioSegments.splitDurations(90_000L))
        assertEquals(1, AudioSegments.segmentCount(90_000L))
    }

    @Test fun zeroOrNegativeYieldsNoSegments() {
        assertTrue(AudioSegments.splitDurations(0).isEmpty())
        assertTrue(AudioSegments.splitDurations(-5).isEmpty())
    }

    @Test fun rolloverTriggersAtCap() {
        assertFalse(AudioSegments.shouldRollover(cap - 1))
        assertTrue(AudioSegments.shouldRollover(cap))
        assertTrue(AudioSegments.shouldRollover(cap + 500))
    }

    @Test fun fileNamesAreZeroPaddedAndSortable() {
        assertEquals("segment_000.m4a", AudioSegments.fileName(0))
        assertEquals("segment_007.m4a", AudioSegments.fileName(7))
        assertEquals("segment_042.m4a", AudioSegments.fileName(42))
    }

    @Test fun accumulatorTracksIndicesAndTotals() {
        val acc = SegmentAccumulator()
        assertEquals(0, acc.nextIndex())
        acc.add("/a/segment_000.m4a", 300_000L)
        assertEquals(1, acc.nextIndex())
        acc.add("/a/segment_001.m4a", 120_000L)
        assertEquals(2, acc.count)
        assertEquals(420_000L, acc.totalDurationMs)
        assertEquals(listOf("/a/segment_000.m4a", "/a/segment_001.m4a"), acc.paths())
    }
}
