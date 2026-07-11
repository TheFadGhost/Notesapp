package com.fadghost.notesapp

import com.fadghost.notesapp.data.audio.TranscriptText
import org.junit.Assert.assertEquals
import org.junit.Test

/** Multi-segment transcript concatenation (PLAN.md §5). */
class TranscriptTextTest {

    @Test fun joinsSegmentsWithSingleSpace() {
        assertEquals("Hello world", TranscriptText.concatenate(listOf("Hello ", " world")))
    }

    @Test fun dropsBlankSegments() {
        assertEquals("one two", TranscriptText.concatenate(listOf("one", "", "   ", "two")))
    }

    @Test fun collapsesInternalWhitespaceAndTrims() {
        assertEquals(
            "the quick brown fox",
            TranscriptText.concatenate(listOf("  the   quick  ", "brown\n\nfox  "))
        )
    }

    @Test fun emptyInputYieldsEmptyString() {
        assertEquals("", TranscriptText.concatenate(emptyList()))
        assertEquals("", TranscriptText.concatenate(listOf("", "  ")))
    }

    @Test fun singleSegmentPreserved() {
        assertEquals("just one part", TranscriptText.concatenate(listOf("just one part")))
    }
}
