package com.fadghost.notesapp.ui.calendar

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Tiny process-wide relay for the "open the app at this reminder" deep link fired
 * by a notification tap (PLAN.md §8). MainActivity pushes the reminder id here from
 * the launch/new intent; AppShell switches to the Calendar tab and CalendarScreen
 * opens the item's edit sheet, then clears it. Kept as a plain object (not a VM) so
 * both the activity and composables can reach it without extra Hilt wiring.
 */
object CalendarDeepLink {
    /** Reminder id to open, or null when nothing pending. */
    val pendingReminderId = MutableStateFlow<Long?>(null)

    fun request(reminderId: Long) {
        if (reminderId > 0) pendingReminderId.value = reminderId
    }

    fun clear() {
        pendingReminderId.value = null
    }
}
