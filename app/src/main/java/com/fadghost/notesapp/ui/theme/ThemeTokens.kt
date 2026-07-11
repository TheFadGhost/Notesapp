package com.fadghost.notesapp.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * "Aura" design tokens. Every custom component consumes these — there is no
 * MaterialTheme dependence in visible UI (PLAN.md §3/§9). Retrofitting later is
 * painful, so the full token surface exists from M0.
 */
@Immutable
data class ThemeColors(
    val background: Color,
    val surface: Color,
    /** Frosted/translucent surface for nav pill, sheets, menus. */
    val surfaceTranslucent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val danger: Color,
    /** Hairline stroke used on translucent surfaces. */
    val outline: Color
)

@Immutable
data class ThemeRadii(
    val sm: Dp = 8.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val pill: Dp = 999.dp
)

@Immutable
data class ThemeBlur(
    /** Blur radius for the nav pill (kept small per PLAN.md §10 blur budget). */
    val navPill: Dp = 24.dp,
    val sheet: Dp = 18.dp
)

@Immutable
data class ThemeElevationAlphas(
    /** Alpha of the tint layer behind translucent surfaces. */
    val translucentTint: Float,
    val scrim: Float,
    val pressed: Float
)

@Immutable
data class ThemeTokens(
    val colors: ThemeColors,
    val radii: ThemeRadii = ThemeRadii(),
    val blur: ThemeBlur = ThemeBlur(),
    val elevation: ThemeElevationAlphas
)

val LightTokens = ThemeTokens(
    colors = ThemeColors(
        background = Color(0xFFF6F6FA),
        surface = Color(0xFFFFFFFF),
        surfaceTranslucent = Color(0xCCFFFFFF),
        textPrimary = Color(0xFF16161D),
        textSecondary = Color(0xFF5B5B66),
        accent = Color(0xFF5B5BD6),
        danger = Color(0xFFD64545),
        outline = Color(0x1A16161D)
    ),
    elevation = ThemeElevationAlphas(
        translucentTint = 0.72f,
        scrim = 0.32f,
        pressed = 0.10f
    )
)

val DarkTokens = ThemeTokens(
    colors = ThemeColors(
        background = Color(0xFF0B0B0F),
        surface = Color(0xFF16161D),
        surfaceTranslucent = Color(0xB316161D),
        textPrimary = Color(0xFFF3F3F7),
        textSecondary = Color(0xFF9B9BA6),
        accent = Color(0xFF8B8BF0),
        danger = Color(0xFFF06565),
        outline = Color(0x22FFFFFF)
    ),
    elevation = ThemeElevationAlphas(
        translucentTint = 0.60f,
        scrim = 0.48f,
        pressed = 0.14f
    )
)

/**
 * Pure Black AMOLED (PLAN.md §9): true #000 backgrounds so OLED pixels power off,
 * high-contrast surfaces lifted just enough to read as cards. Translucent surfaces
 * stay near-opaque black so the frosted pill/sheet never washes out on black.
 */
val AmoledTokens = ThemeTokens(
    colors = ThemeColors(
        background = Color(0xFF000000),
        surface = Color(0xFF0C0C0F),
        surfaceTranslucent = Color(0xE6000000),
        textPrimary = Color(0xFFFAFAFC),
        textSecondary = Color(0xFF9A9AA6),
        accent = Color(0xFF8B8BF0),
        danger = Color(0xFFF06565),
        outline = Color(0x2EFFFFFF)
    ),
    elevation = ThemeElevationAlphas(
        translucentTint = 0.66f,
        scrim = 0.58f,
        pressed = 0.16f
    )
)

/**
 * Grey / graphite (PLAN.md §9): soft dark-grey, lower contrast than Dark, easier on
 * the eyes at night without the hard black of AMOLED.
 */
val GreyTokens = ThemeTokens(
    colors = ThemeColors(
        background = Color(0xFF1B1C20),
        surface = Color(0xFF26272D),
        surfaceTranslucent = Color(0xB326272D),
        textPrimary = Color(0xFFECECEF),
        textSecondary = Color(0xFFA6A7B0),
        accent = Color(0xFF9A9AF2),
        danger = Color(0xFFF07070),
        outline = Color(0x1FFFFFFF)
    ),
    elevation = ThemeElevationAlphas(
        translucentTint = 0.62f,
        scrim = 0.50f,
        pressed = 0.14f
    )
)

/**
 * Return a copy of these tokens with the accent colour overridden (accent picker,
 * PLAN.md §9). Passing null (the "theme default" sentinel) leaves the accent as-is.
 */
fun ThemeTokens.withAccent(accent: Color?): ThemeTokens =
    if (accent == null) this else copy(colors = colors.copy(accent = accent))
