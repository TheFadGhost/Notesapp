package com.fadghost.notesapp.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Time-of-day for the journaling nudge (PLAN.md §7). */
data class NudgeTime(val hour: Int, val minute: Int)

/**
 * Diary-only preferences (PLAN.md §7): the biometric gate toggle and the daily
 * journaling-nudge settings. All diary gate/nudge state lives here in DataStore —
 * no Room schema changes (the DiaryEntry table is owned/frozen for M5).
 */
private val Context.diaryStore by preferencesDataStore(name = "diary_settings")

@Singleton
class DiaryPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val biometricKey = booleanPreferencesKey("biometric_gate")
    private val nudgeEnabledKey = booleanPreferencesKey("nudge_enabled")
    private val nudgeHourKey = intPreferencesKey("nudge_hour")
    private val nudgeMinuteKey = intPreferencesKey("nudge_minute")

    /** Biometric gate on the Diary tab. Default OFF (privacy is opt-in per plan). */
    val biometricEnabled: Flow<Boolean> =
        context.diaryStore.data.map { it[biometricKey] ?: false }

    val nudgeEnabled: Flow<Boolean> =
        context.diaryStore.data.map { it[nudgeEnabledKey] ?: false }

    val nudgeTime: Flow<NudgeTime> = context.diaryStore.data.map {
        NudgeTime(
            hour = (it[nudgeHourKey] ?: DEFAULT_HOUR).coerceIn(0, 23),
            minute = (it[nudgeMinuteKey] ?: DEFAULT_MINUTE).coerceIn(0, 59)
        )
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.diaryStore.edit { it[biometricKey] = enabled }
    }

    suspend fun setNudgeEnabled(enabled: Boolean) {
        context.diaryStore.edit { it[nudgeEnabledKey] = enabled }
    }

    suspend fun setNudgeTime(hour: Int, minute: Int) {
        context.diaryStore.edit {
            it[nudgeHourKey] = hour.coerceIn(0, 23)
            it[nudgeMinuteKey] = minute.coerceIn(0, 59)
        }
    }

    companion object {
        const val DEFAULT_HOUR = 20
        const val DEFAULT_MINUTE = 0
    }
}
