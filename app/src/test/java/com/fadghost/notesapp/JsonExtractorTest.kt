package com.fadghost.notesapp

import com.fadghost.notesapp.data.ai.parse.JsonExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonExtractorTest {

    @Test fun cleanJsonReturnedVerbatim() {
        val s = """{"items":[{"title":"Buy milk"}]}"""
        assertEquals(s, JsonExtractor.extract(s))
    }

    @Test fun fencedJsonIsUnwrapped() {
        val s = "```json\n{\"items\":[]}\n```"
        assertEquals("{\"items\":[]}", JsonExtractor.extract(s))
    }

    @Test fun proseWrappedJsonIsExtracted() {
        val s = "Sure! Here you go: {\"items\":[{\"title\":\"Call Sam\"}]} Hope that helps."
        assertEquals("{\"items\":[{\"title\":\"Call Sam\"}]}", JsonExtractor.extract(s))
    }

    @Test fun bracesInsideStringsDoNotFoolTheCounter() {
        val s = """{"title":"weird } value {","ok":true}"""
        assertEquals(s, JsonExtractor.extract(s))
    }

    @Test fun escapedQuotesHandled() {
        val s = """{"title":"say \"hi\" now"}"""
        assertEquals(s, JsonExtractor.extract(s))
    }

    @Test fun truncatedJsonReturnsNull() {
        val s = """{"items":[{"title":"a""" // never closed
        assertNull(JsonExtractor.extract(s))
    }

    @Test fun noJsonReturnsNull() {
        assertNull(JsonExtractor.extract("I could not find any actions in that note."))
    }

    @Test fun nestedObjectsBalanceCorrectly() {
        val s = """{"a":{"b":{"c":1}},"d":2}"""
        assertTrue(JsonExtractor.extract(s) == s)
    }
}
