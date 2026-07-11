package com.fadghost.notesapp

import com.fadghost.notesapp.util.Markdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTest {

    @Test fun stripsHeadings() {
        assertEquals("Hello world", Markdown.strip("## Hello world"))
        assertEquals("Title", Markdown.strip("###### Title"))
    }

    @Test fun stripsEmphasis() {
        assertEquals("bold and italic", Markdown.strip("**bold** and *italic*"))
        assertEquals("under strike", Markdown.strip("__under__ ~~strike~~"))
    }

    @Test fun stripsListsAndChecklists() {
        assertEquals("milk eggs done", Markdown.strip("- milk\n* eggs\n- [x] done"))
        assertEquals("first second", Markdown.strip("1. first\n2. second"))
    }

    @Test fun stripsLinksKeepingText() {
        assertEquals("see Google here", Markdown.strip("see [Google](https://google.com) here"))
        assertEquals("a diagram", Markdown.strip("![a diagram](img.png)"))
    }

    @Test fun stripsInlineCodeAndFences() {
        assertEquals("run x", Markdown.strip("run `x`"))
        // Fenced block content is dropped, surrounding prose kept.
        assertEquals("before after", Markdown.strip("before\n```\ncode()\n```\nafter"))
    }

    @Test fun cleanTextUnchanged() {
        assertEquals("just plain text", Markdown.strip("just plain text"))
    }

    @Test fun strippedTextHasNoMarkdownSymbols() {
        val out = Markdown.strip("# Big\n**b** _i_ [l](u) `c` - item")
        assertFalse(out.contains("#"))
        assertFalse(out.contains("*"))
        assertFalse(out.contains("`"))
        assertTrue(out.contains("Big"))
        assertTrue(out.contains("item"))
    }

    @Test fun firstLineForTitleFallback() {
        assertEquals("My Note", Markdown.firstLine("# My Note\nbody here"))
        assertEquals("plain start", Markdown.firstLine("\n\nplain start\nmore"))
    }
}
