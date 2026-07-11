package com.fadghost.notesapp.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView

/**
 * App-wide haptics vocabulary (PLAN.md §10):
 *  - [tick]    light selection feedback — nav taps, chip/swatch selection.
 *  - [confirm] medium confirmation — sheet confirms, destructive actions, undo.
 *  - [success] distinct success pattern — AI completion.
 *
 * Wraps Compose's [HapticFeedback] (which only exposes LongPress/TextHandleMove) and
 * falls back to the platform [android.view.View.performHapticFeedback] constants for
 * the richer effects so the vocabulary reads correctly across API levels.
 */
class AuraHaptics(
    private val compose: HapticFeedback,
    private val view: android.view.View
) {
    fun tick() {
        // CONTEXT_CLICK / CLOCK_TICK read as a light selection tap.
        if (!view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)) {
            compose.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    fun confirm() {
        if (!view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)) {
            compose.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    fun success() {
        // No dedicated "success" constant pre-API-30 friendly everywhere; CONFIRM is the
        // closest positive pattern and degrades to a long-press vibe.
        if (!view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)) {
            compose.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
}

@Composable
fun rememberAuraHaptics(): AuraHaptics {
    val compose = LocalHapticFeedback.current
    val view = LocalView.current
    return androidx.compose.runtime.remember(compose, view) { AuraHaptics(compose, view) }
}
