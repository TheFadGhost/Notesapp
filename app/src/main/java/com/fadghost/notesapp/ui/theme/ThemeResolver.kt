package com.fadghost.notesapp.ui.theme

import com.fadghost.notesapp.data.prefs.ThemeMode

/**
 * Pure mapping from the persisted [ThemeMode] (+ the current system dark flag for the
 * SYSTEM option) to a base token set. Kept free of Compose so it is unit-testable
 * (PLAN.md §9 theme enum mapping).
 */
object ThemeResolver {
    fun baseTokens(mode: ThemeMode, systemDark: Boolean): ThemeTokens = when (mode) {
        ThemeMode.LIGHT -> LightTokens
        ThemeMode.DARK -> DarkTokens
        ThemeMode.AMOLED -> AmoledTokens
        ThemeMode.GREY -> GreyTokens
        ThemeMode.SYSTEM -> if (systemDark) DarkTokens else LightTokens
    }
}
