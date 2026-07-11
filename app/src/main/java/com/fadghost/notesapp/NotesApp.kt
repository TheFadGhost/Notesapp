package com.fadghost.notesapp

import android.app.Application
import com.fadghost.notesapp.data.work.TrashPurgeWorker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class NotesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Deferrable guaranteed work: purge 30-day-old trash daily (PLAN.md §7).
        TrashPurgeWorker.schedule(this)
    }
}
