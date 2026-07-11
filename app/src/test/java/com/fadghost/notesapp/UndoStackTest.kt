package com.fadghost.notesapp

import com.fadghost.notesapp.util.UndoStack
import com.fadghost.notesapp.util.UndoStack.CoalesceKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoStackTest {

    @Test fun emptyStackCannotUndoOrRedo() {
        val s = UndoStack<String>()
        assertFalse(s.canUndo)
        assertFalse(s.canRedo)
        assertNull(s.undo())
    }

    @Test fun basicUndoRedo() {
        val s = UndoStack<String>()
        s.reset("")
        s.record("a", CoalesceKey.BOUNDARY)
        s.record("ab", CoalesceKey.BOUNDARY)
        assertTrue(s.canUndo)
        assertEquals("a", s.undo())
        assertEquals("", s.undo())
        assertFalse(s.canUndo)
        assertEquals("a", s.redo())
        assertEquals("ab", s.redo())
        assertFalse(s.canRedo)
    }

    @Test fun typingCoalescesIntoOneStep() {
        val s = UndoStack<String>()
        s.reset("")
        s.record("h", CoalesceKey.TYPING)
        s.record("he", CoalesceKey.TYPING)
        s.record("hel", CoalesceKey.TYPING)
        // All three typing edits collapse — one undo returns to the seed.
        assertEquals("", s.undo())
        assertFalse(s.canUndo)
    }

    @Test fun differentKindsBreakCoalescing() {
        val s = UndoStack<String>()
        s.reset("")
        s.record("hi", CoalesceKey.TYPING)
        s.record("h", CoalesceKey.DELETING)
        assertEquals("hi", s.undo())
        assertEquals("", s.undo())
    }

    @Test fun newRecordTruncatesRedoBranch() {
        val s = UndoStack<String>()
        s.reset("")
        s.record("a", CoalesceKey.BOUNDARY)
        s.record("ab", CoalesceKey.BOUNDARY)
        s.undo() // back to "a"
        s.record("aX", CoalesceKey.BOUNDARY)
        assertFalse(s.canRedo) // old "ab" branch is gone
        assertEquals("aX", s.current)
        assertEquals("a", s.undo())
    }

    @Test fun duplicateValueIsIgnored() {
        val s = UndoStack<String>()
        s.reset("x")
        s.record("x", CoalesceKey.BOUNDARY)
        assertFalse(s.canUndo)
    }

    @Test fun respectsMaxSize() {
        val s = UndoStack<Int>(maxSize = 3)
        s.reset(0)
        for (i in 1..10) s.record(i, CoalesceKey.BOUNDARY)
        assertEquals(10, s.current)
        // Only a bounded amount of history is retained.
        var count = 0
        while (s.canUndo) { s.undo(); count++ }
        assertTrue(count <= 3)
    }
}
