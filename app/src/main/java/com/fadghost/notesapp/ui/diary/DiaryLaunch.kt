package com.fadghost.notesapp.ui.diary

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-way signal from the journaling-nudge deep link (PLAN.md §7) to the shell:
 * the Activity bumps [requests] when launched with the "open diary" intent extra,
 * and the shell switches to the Diary tab. A monotonic counter so repeated taps
 * each trigger a fresh navigation.
 */
@Singleton
class DiaryLaunch @Inject constructor() {

    private val _requests = MutableStateFlow(0)
    val requests: StateFlow<Int> = _requests.asStateFlow()

    fun requestOpenDiary() {
        _requests.value += 1
    }
}
