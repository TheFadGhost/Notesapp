package com.fadghost.notesapp.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Curated accent palette (PLAN.md §9 — "8–10 curated accents") reused as the tag
 * colour choices. Stored on [com.fadghost.notesapp.data.db.entity.Tag.color] as
 * an ARGB int; index 0 (transparent) means "no colour picked yet".
 */
object AuraAccents {
    val palette: List<Color> = listOf(
        Color(0xFF5B5BD6), // indigo (default accent)
        Color(0xFF3BA5A0), // teal
        Color(0xFF2E9E5B), // green
        Color(0xFFC7963B), // amber
        Color(0xFFD6693B), // orange
        Color(0xFFD64545), // red
        Color(0xFFC44B9B), // magenta
        Color(0xFF7A5BD6), // violet
        Color(0xFF3B7FD6), // blue
        Color(0xFF6E7681)  // slate
    )

    /** Resolve a stored ARGB int to a display colour, falling back to [fallback]. */
    fun resolve(argb: Int, fallback: Color): Color =
        if (argb == 0) fallback else Color(argb)

    /**
     * The 8 curated accents offered in the Settings accent picker (PLAN.md §9).
     * Index [THEME_DEFAULT] (-1) means "use the theme's own accent" — i.e. no override.
     */
    val themeAccents: List<Color> = listOf(
        Color(0xFF5B5BD6), // indigo
        Color(0xFF3B7FD6), // blue
        Color(0xFF3BA5A0), // teal
        Color(0xFF2E9E5B), // green
        Color(0xFFC7963B), // amber
        Color(0xFFD6693B), // orange
        Color(0xFFD64545), // red
        Color(0xFFC44B9B)  // magenta
    )

    const val THEME_DEFAULT: Int = -1

    /**
     * Map a persisted accent index to a colour override, or null for "theme default".
     * Out-of-range indices clamp to null so a corrupt/stale pref never crashes.
     */
    fun accentForIndex(index: Int): Color? =
        themeAccents.getOrNull(index)
}
