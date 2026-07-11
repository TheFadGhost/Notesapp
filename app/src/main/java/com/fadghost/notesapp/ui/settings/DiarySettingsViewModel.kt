package com.fadghost.notesapp.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fadghost.notesapp.data.prefs.DiaryPreferences
import com.fadghost.notesapp.data.prefs.NudgeTime
import com.fadghost.notesapp.data.work.DiaryNudgeWorker
import com.fadghost.notesapp.ui.diary.DiaryLockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DiarySettingsViewModel @Inject constructor(
    private val prefs: DiaryPreferences,
    private val lockManager: DiaryLockManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val biometricEnabled: StateFlow<Boolean> =
        prefs.biometricEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val nudgeEnabled: StateFlow<Boolean> =
        prefs.nudgeEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val nudgeTime: StateFlow<NudgeTime> =
        prefs.nudgeTime.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000),
            NudgeTime(DiaryPreferences.DEFAULT_HOUR, DiaryPreferences.DEFAULT_MINUTE)
        )

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setBiometricEnabled(enabled)
            // Turning the gate on should re-lock immediately so it takes effect now.
            if (enabled) lockManager.lock() else lockManager.unlock()
        }
    }

    fun setNudgeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setNudgeEnabled(enabled)
            if (enabled) {
                val t = prefs.nudgeTime.first()
                DiaryNudgeWorker.schedule(context, t.hour, t.minute)
            } else {
                DiaryNudgeWorker.cancel(context)
            }
        }
    }

    fun setNudgeTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            prefs.setNudgeTime(hour, minute)
            if (prefs.nudgeEnabled.first()) DiaryNudgeWorker.schedule(context, hour, minute)
        }
    }
}
