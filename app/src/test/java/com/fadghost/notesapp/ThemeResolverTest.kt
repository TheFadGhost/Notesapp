package com.fadghost.notesapp

import com.fadghost.notesapp.data.prefs.ThemeMode
import com.fadghost.notesapp.ui.theme.AmoledTokens
import com.fadghost.notesapp.ui.theme.DarkTokens
import com.fadghost.notesapp.ui.theme.GreyTokens
import com.fadghost.notesapp.ui.theme.LightTokens
import com.fadghost.notesapp.ui.theme.ThemeResolver
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeResolverTest {

    @Test fun `explicit modes map to their token sets`() {
        assertEquals(LightTokens, ThemeResolver.baseTokens(ThemeMode.LIGHT, systemDark = true))
        assertEquals(DarkTokens, ThemeResolver.baseTokens(ThemeMode.DARK, systemDark = false))
        assertEquals(AmoledTokens, ThemeResolver.baseTokens(ThemeMode.AMOLED, systemDark = false))
        assertEquals(GreyTokens, ThemeResolver.baseTokens(ThemeMode.GREY, systemDark = true))
    }

    @Test fun `system follows the system dark flag`() {
        assertEquals(DarkTokens, ThemeResolver.baseTokens(ThemeMode.SYSTEM, systemDark = true))
        assertEquals(LightTokens, ThemeResolver.baseTokens(ThemeMode.SYSTEM, systemDark = false))
    }

    @Test fun `amoled background is true black`() {
        assertEquals(Color(0xFF000000), AmoledTokens.colors.background)
    }
}
