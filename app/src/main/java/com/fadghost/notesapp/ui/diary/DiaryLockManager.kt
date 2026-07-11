package com.fadghost.notesapp.ui.diary

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide lock state for the Diary tab (PLAN.md §7). The gate starts locked;
 * once the user authenticates it stays unlocked until the app is backgrounded for
 * longer than [RELOCK_AFTER_BACKGROUND_MS]. This is a UI gate only — the DB is not
 * encrypted at rest (documented honestly in the Settings toggle subtitle).
 *
 * The Activity drives [onEnterBackground]/[onEnterForeground] from its lifecycle.
 */
@Singleton
class DiaryLockManager @Inject constructor() {

    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var backgroundedAt: Long? = null

    fun unlock() {
        _locked.value = false
        backgroundedAt = null
    }

    /** Force a re-lock (e.g. the user turned the gate on in Settings). */
    fun lock() {
        _locked.value = true
    }

    fun onEnterBackground(now: Long = System.currentTimeMillis()) {
        // Only start the timer if we're currently unlocked; otherwise nothing to do.
        if (!_locked.value) backgroundedAt = now
    }

    fun onEnterForeground(now: Long = System.currentTimeMillis()) {
        val since = backgroundedAt
        if (since != null && now - since > RELOCK_AFTER_BACKGROUND_MS) {
            _locked.value = true
        }
        backgroundedAt = null
    }

    companion object {
        /** Re-lock once the app has been in the background longer than this (PLAN.md §7). */
        const val RELOCK_AFTER_BACKGROUND_MS = 30_000L
    }
}
