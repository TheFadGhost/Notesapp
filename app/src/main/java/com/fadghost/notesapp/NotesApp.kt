package com.fadghost.notesapp

import android.app.Application
import com.fadghost.notesapp.data.work.TrashPurgeWorker
import com.fadghost.notesapp.notify.NotificationChannels
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class NotesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Deferrable guaranteed work: purge 30-day-old trash daily (PLAN.md §7).
        TrashPurgeWorker.schedule(this)
        // Idempotent notification channels (PLAN.md §8) — safe alongside the diary
        // agent creating the shared Nudges channel.
        NotificationChannels.ensure(this)
    }
}
