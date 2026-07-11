package com.fadghost.notesapp.ui.theme

import android.content.Context
import android.provider.Settings

/**
 * Reduce-motion resolution (PLAN.md §10). Kept as pure logic so it is unit-testable;
 * the Android read of the animator duration scale is a thin wrapper.
 */
object ReduceMotion {

    /**
     * The effective reduce-motion state: true when the user toggle is on OR the
     * system has animations disabled (animator duration scale == 0).
     */
    fun effective(userToggle: Boolean, systemAnimatorScale: Float): Boolean =
        userToggle || systemAnimatorScale == 0f

    /** Read the system animator duration scale (1.0 default, 0 == animations off). */
    fun systemAnimatorScale(context: Context): Float =
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
        }.getOrDefault(1f)
}
