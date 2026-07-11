package com.fadghost.notesapp.ui.diary

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Exposes the [DiaryLaunch] deep-link signal to the shell so it can switch tabs. */
@HiltViewModel
class DiaryNavViewModel @Inject constructor(
    diaryLaunch: DiaryLaunch
) : ViewModel() {
    val openDiaryRequests: StateFlow<Int> = diaryLaunch.requests
}
