package com.fadghost.notesapp.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalThemeTokens = staticCompositionLocalOf { DarkTokens }

/** Access point for Aura tokens: `Aura.tokens.colors.accent`. */
object Aura {
    val tokens: ThemeTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalThemeTokens.current
}

// AuraType now lives in AuraType.kt (V2-SPEC #8: full 8-token variable-font scale).

@Composable
fun AuraTheme(
    tokens: ThemeTokens,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalThemeTokens provides tokens) {
        content()
    }
}
