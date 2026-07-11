package com.fadghost.notesapp

import com.fadghost.notesapp.data.ai.net.SseParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SseParserTest {

    @Test fun singleDataEventDispatchesOnBlankLine() {
        val p = SseParser()
        assertNull(p.accept("data: hello"))
        assertEquals("hello", p.accept(""))
    }

    @Test fun stripsOnlyOneLeadingSpace() {
        val p = SseParser()
        p.accept("data:  two-spaces") // one stripped, one kept
        assertEquals(" two-spaces", p.accept(""))
    }

    @Test fun multipleDataFieldsJoinWithNewline() {
        val p = SseParser()
        p.accept("data: line1")
        p.accept("data: line2")
        assertEquals("line1\nline2", p.accept(""))
    }

    @Test fun commentsAndOtherFieldsIgnored() {
        val p = SseParser()
        assertNull(p.accept(": keep-alive"))
        assertNull(p.accept("event: message"))
        assertNull(p.accept("id: 42"))
        p.accept("data: payload")
        assertEquals("payload", p.accept(""))
    }

    @Test fun flushEmitsTrailingEventWithoutBlankLine() {
        val p = SseParser()
        p.accept("data: tail")
        assertEquals("tail", p.flush())
        assertNull(p.flush())
    }

    @Test fun parseAllExtractsChatDeltasIncludingDone() {
        val raw = buildString {
            append("data: {\"choices\":[{\"delta\":{\"content\":\"He\"}}]}\n\n")
            append("data: {\"choices\":[{\"delta\":{\"content\":\"llo\"}}]}\n\n")
            append("data: [DONE]\n\n")
        }
        val events = SseParser.parseAll(raw)
        assertEquals(3, events.size)
        assertEquals(SseParser.DONE, events.last())
    }

    @Test fun handlesCrlfLineEndings() {
        val raw = "data: a\r\n\r\ndata: b\r\n\r\n"
        val events = SseParser.parseAll(raw)
        assertEquals(listOf("a", "b"), events)
    }

    @Test fun blankLineWithNoBufferedDataYieldsNothing() {
        val p = SseParser()
        assertNull(p.accept(""))
        assertNull(p.accept(""))
    }
}
