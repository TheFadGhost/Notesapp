package com.fadghost.notesapp

import androidx.compose.ui.graphics.Color
import com.fadghost.notesapp.ui.theme.AuraAccents
import com.fadghost.notesapp.ui.theme.DarkTokens
import com.fadghost.notesapp.ui.theme.ReduceMotion
import com.fadghost.notesapp.ui.theme.withAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentAndMotionTest {

    @Test fun `there are exactly 8 curated accents`() {
        assertEquals(8, AuraAccents.themeAccents.size)
    }

    @Test fun `accent index maps to its colour`() {
        assertEquals(AuraAccents.themeAccents[0], AuraAccents.accentForIndex(0))
        assertEquals(AuraAccents.themeAccents[7], AuraAccents.accentForIndex(7))
    }

    @Test fun `default and out-of-range indices resolve to null`() {
        assertNull(AuraAccents.accentForIndex(AuraAccents.THEME_DEFAULT))
        assertNull(AuraAccents.accentForIndex(-1))
        assertNull(AuraAccents.accentForIndex(8))
        assertNull(AuraAccents.accentForIndex(999))
    }

    @Test fun `withAccent overrides only the accent`() {
        val custom = Color(0xFF123456)
        val out = DarkTokens.withAccent(custom)
        assertEquals(custom, out.colors.accent)
        // Everything else is untouched.
        assertEquals(DarkTokens.colors.background, out.colors.background)
        assertEquals(DarkTokens.colors.textPrimary, out.colors.textPrimary)
    }

    @Test fun `withAccent null keeps the theme accent`() {
        assertEquals(DarkTokens, DarkTokens.withAccent(null))
    }

    @Test fun `reduce motion effective when toggle on or system animations off`() {
        assertTrue(ReduceMotion.effective(userToggle = true, systemAnimatorScale = 1f))
        assertTrue(ReduceMotion.effective(userToggle = false, systemAnimatorScale = 0f))
        assertTrue(ReduceMotion.effective(userToggle = true, systemAnimatorScale = 0f))
        assertFalse(ReduceMotion.effective(userToggle = false, systemAnimatorScale = 1f))
        assertFalse(ReduceMotion.effective(userToggle = false, systemAnimatorScale = 0.5f))
    }
}
