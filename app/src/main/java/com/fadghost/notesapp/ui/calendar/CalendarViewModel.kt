package com.fadghost.notesapp.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fadghost.notesapp.alarm.AlarmScheduler
import com.fadghost.notesapp.data.db.dao.EventDao
import com.fadghost.notesapp.data.db.dao.ReminderDao
import com.fadghost.notesapp.data.db.entity.Event
import com.fadghost.notesapp.data.db.entity.Recurrence
import com.fadghost.notesapp.data.db.entity.Reminder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Base rows the screen expands into occurrences (see [CalendarExpand]). */
data class CalendarData(
    val events: List<Event> = emptyList(),
    val reminders: List<Reminder> = emptyList()
)

/**
 * Backs the Calendar tab (PLAN.md §8). Streams the raw event + reminder rows;
 * occurrence expansion for the visible window happens in the composables so month
 * swipes never re-hit the DB. Owns create/edit/delete and — for reminders — keeps
 * the exact alarm in sync through [AlarmScheduler].
 */
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val eventDao: EventDao,
    private val reminderDao: ReminderDao,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    val data: StateFlow<CalendarData> =
        combine(eventDao.observeAll(), reminderDao.observeAll()) { events, reminders ->
            CalendarData(events, reminders)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarData())

    fun canScheduleExact(): Boolean = alarmScheduler.canExact()

    // --- Events -----------------------------------------------------------------

    fun saveEvent(
        baseId: Long,
        title: String,
        startAt: Long,
        endAt: Long,
        timezone: String,
        notes: String?,
        recurrence: Recurrence
    ) {
        viewModelScope.launch {
            eventDao.upsert(
                Event(
                    id = baseId,
                    title = title.trim().ifBlank { "Event" },
                    startAt = startAt,
                    endAt = endAt.coerceAtLeast(startAt),
                    timezone = timezone,
                    notes = notes?.trim()?.takeIf { it.isNotBlank() },
                    recurrence = recurrence
                )
            )
        }
    }

    fun deleteEvent(baseId: Long) {
        viewModelScope.launch { eventDao.deleteById(baseId) }
    }

    // --- Reminders --------------------------------------------------------------

    fun saveReminder(
        baseId: Long,
        title: String,
        triggerAt: Long,
        timezone: String,
        recurrence: Recurrence
    ) {
        viewModelScope.launch {
            val id = reminderDao.upsert(
                Reminder(
                    id = baseId,
                    title = title.trim().ifBlank { "Reminder" },
                    triggerAt = triggerAt,
                    timezone = timezone,
                    done = false,
                    snoozedUntil = null,
                    recurrence = recurrence
                )
            )
            val effectiveId = if (baseId != 0L) baseId else id
            reminderDao.getById(effectiveId)?.let { alarmScheduler.scheduleReminder(it) }
        }
    }

    fun deleteReminder(baseId: Long) {
        viewModelScope.launch {
            alarmScheduler.cancelReminder(baseId)
            reminderDao.deleteById(baseId)
        }
    }

    fun setReminderDone(baseId: Long, done: Boolean) {
        viewModelScope.launch {
            reminderDao.setDone(baseId, done)
            if (done) {
                alarmScheduler.cancelReminder(baseId)
            } else {
                reminderDao.getById(baseId)?.let { alarmScheduler.scheduleReminder(it) }
            }
        }
    }
}
