package com.fadghost.notesapp

import com.fadghost.notesapp.util.FtsQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FtsQueryTest {

    @Test fun singleTermGetsPrefixStar() {
        assertEquals("\"hello\"*", FtsQuery.build("hello"))
    }

    @Test fun multipleTermsAreImplicitAnd() {
        assertEquals("\"hello\"* \"wor\"*", FtsQuery.build("hello wor"))
    }

    @Test fun blankAndPunctuationOnlyReturnNull() {
        assertNull(FtsQuery.build(""))
        assertNull(FtsQuery.build("   "))
        assertNull(FtsQuery.build("!!! ??? ***"))
    }

    @Test fun ftsOperatorsAreNeutralisedNotInterpreted() {
        // A user typing "AND" or quotes must not create a malformed MATCH.
        assertEquals("\"foo\"* \"AND\"* \"bar\"*", FtsQuery.build("foo AND bar"))
        assertEquals("\"a\"* \"b\"*", FtsQuery.build("a\"b"))
        assertEquals("\"drop\"*", FtsQuery.build("drop*"))
    }

    @Test fun unicodeLettersAreKept() {
        assertEquals("\"café\"*", FtsQuery.build("café"))
    }
}
