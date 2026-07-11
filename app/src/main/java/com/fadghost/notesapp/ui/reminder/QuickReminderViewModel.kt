package com.fadghost.notesapp.ui.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fadghost.notesapp.alarm.AlarmScheduler
import com.fadghost.notesapp.data.db.dao.ReminderDao
import com.fadghost.notesapp.data.db.entity.Reminder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.util.TimeZone
import javax.inject.Inject

/**
 * Minimal reminder creator for the capture-sheet "Quick reminder" action
 * (PLAN.md §4/§8). Since M3 it also arms the exact alarm so the reminder actually
 * fires, via the shared [AlarmScheduler].
 */
@HiltViewModel
class QuickReminderViewModel @Inject constructor(
    private val reminderDao: ReminderDao,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    fun create(title: String, triggerAt: Long, onDone: () -> Unit) {
        val clean = title.trim().ifBlank { "Reminder" }
        viewModelScope.launch {
            val id = reminderDao.upsert(
                Reminder(title = clean, triggerAt = triggerAt, timezone = TimeZone.getDefault().id)
            )
            reminderDao.getById(id)?.let { alarmScheduler.scheduleReminder(it) }
            onDone()
        }
    }
}
