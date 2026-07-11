package com.fadghost.notesapp.ui.whatsnew

/**
 * Version-gate for the post-update "What's new" sheet (PLAN.md §13). Pure logic so it
 * is unit-testable: show the sheet exactly once per versionName change.
 */
object ChangelogGate {
    /**
     * Show when the current versionName is non-blank and differs from the last version
     * the sheet was shown for. A blank [lastSeen] (fresh install / never shown) still
     * shows — first-run users get the changelog too, which is acceptable and matches
     * "once per versionName".
     */
    fun shouldShow(lastSeen: String, current: String): Boolean =
        current.isNotBlank() && lastSeen != current
}
