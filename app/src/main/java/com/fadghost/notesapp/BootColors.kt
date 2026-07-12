package com.fadghost.notesapp

import android.content.Context

/**
 * Persists the last-resolved Aura background colour so the pre-Compose window and the
 * first composed frame paint the user's *actual* theme, not the static dark
 * windowBackground — no dark→cream flash on a Light-theme cold start (V2-SPEC item 12,
 * platform.md §5). Written from Compose whenever the resolved background changes; read
 * synchronously in MainActivity.onCreate before setContent.
 */
object BootColors {
    private const val PREFS = "aura_boot"
    private const val KEY_WINDOW_BG = "window_bg"

    /** Warm charcoal — matches the Dark theme, the safe default before any theme resolves. */
    const val DEFAULT_WINDOW_BG: Int = 0xFF1C1A17.toInt()

    fun save(context: Context, argb: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_WINDOW_BG, argb).apply()
    }

    fun windowBackground(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_WINDOW_BG, DEFAULT_WINDOW_BG)
}
