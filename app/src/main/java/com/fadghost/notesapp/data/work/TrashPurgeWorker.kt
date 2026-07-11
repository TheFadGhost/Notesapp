package com.fadghost.notesapp.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fadghost.notesapp.data.repo.NotesRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/**
 * Periodic trash auto-purge (PLAN.md §7): hard-delete notes soft-deleted more than
 * 30 days ago and clean up orphaned attachment files. Uses a Hilt [EntryPoint]
 * rather than hilt-work so no extra dependency / Configuration.Provider wiring is
 * needed (PLAN.md §3 boundary: WorkManager for deferrable guaranteed work).
 */
class TrashPurgeWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun repository(): NotesRepository
    }

    override suspend fun doWork(): Result {
        val repo = EntryPointAccessors
            .fromApplication(applicationContext, WorkerEntryPoint::class.java)
            .repository()
        val cutoff = System.currentTimeMillis() - TRASH_TTL_MS
        return runCatching { repo.purgeExpiredTrash(cutoff) }
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }

    companion object {
        const val TRASH_TTL_MS = 30L * 24 * 60 * 60 * 1000
        private const val WORK_NAME = "trash_purge"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<TrashPurgeWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
