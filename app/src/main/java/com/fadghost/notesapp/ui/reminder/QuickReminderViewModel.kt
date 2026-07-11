package com.fadghost.notesapp.ui.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fadghost.notesapp.data.db.dao.ReminderDao
import com.fadghost.notesapp.data.db.entity.Reminder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.util.TimeZone
import javax.inject.Inject

/**
 * Minimal reminder creator for the capture-sheet "Quick reminder" action
 * (PLAN.md §4/§8 — a Reminder row is enough; alarms + calendar UI arrive in M3).
 */
@HiltViewModel
class QuickReminderViewModel @Inject constructor(
    private val reminderDao: ReminderDao
) : ViewModel() {

    fun create(title: String, triggerAt: Long, onDone: () -> Unit) {
        val clean = title.trim().ifBlank { "Reminder" }
        viewModelScope.launch {
            reminderDao.upsert(
                Reminder(title = clean, triggerAt = triggerAt, timezone = TimeZone.getDefault().id)
            )
            onDone()
        }
    }
}
