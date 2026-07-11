package com.fadghost.notesapp.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Central spring/tween vocabulary (PLAN.md §10). Every custom surface pulls its
 * motion from here instead of hand-rolling `spring()` params, so the whole app
 * shares one feel and the reduce-motion pass has a single choke point.
 *
 * Each preset has a spring form (normal) and a fast-tween/snap form (reduce-motion).
 * Callers pick via [spec] passing the ambient [LocalReduceMotion] value.
 */
object MotionTokens {

    /** Snappy, minimal overshoot — menus, chips, selection highlights. */
    fun <T> fast(reduceMotion: Boolean): AnimationSpec<T> =
        if (reduceMotion) tween(90)
        else spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)

    /** Balanced default — content swaps, sheet settle, card transitions. */
    fun <T> medium(reduceMotion: Boolean): AnimationSpec<T> =
        if (reduceMotion) tween(120)
        else spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)

    /** Playful overshoot — nav tab pop, capture sheet spring-up. */
    fun <T> bouncy(reduceMotion: Boolean): AnimationSpec<T> =
        if (reduceMotion) tween(120)
        else spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)

    /** Finite variants for transition APIs (AnimatedVisibility/AnimatedContent). */
    fun <T> fastFinite(reduceMotion: Boolean): FiniteAnimationSpec<T> =
        if (reduceMotion) snap() else tween(160)

    fun <T> mediumFinite(reduceMotion: Boolean): FiniteAnimationSpec<T> =
        if (reduceMotion) snap() else tween(240)
}

/**
 * Ambient "reduce motion" flag (PLAN.md §10). True when the system animator
 * duration scale is 0 OR the in-app "Reduce motion" toggle is on. Defaults false.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }
