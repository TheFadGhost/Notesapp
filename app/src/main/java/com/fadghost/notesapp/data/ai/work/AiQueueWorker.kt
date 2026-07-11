package com.fadghost.notesapp.data.ai.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.fadghost.notesapp.data.ai.AiRepository
import com.fadghost.notesapp.data.ai.AiResultStore
import com.fadghost.notesapp.data.repo.NotesRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Offline AI retry queue (PLAN.md §5 — "requests queue (WorkManager) and auto-run
 * when back online"). Enqueued with a CONNECTED constraint, so it stays pending
 * (the editor shows a queued chip via WorkInfo) until connectivity returns, then
 * runs a non-streaming Clean-up and stashes the result in [AiResultStore] for the
 * editor to apply. The note itself is never mutated here — on failure the editor
 * shows "AI unavailable — your note is untouched".
 */
class AiQueueWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun aiRepository(): AiRepository
        fun notesRepository(): NotesRepository
        fun resultStore(): AiResultStore
    }

    override suspend fun doWork(): Result {
        val noteId = inputData.getLong(KEY_NOTE_ID, -1L)
        if (noteId <= 0) return Result.failure()
        val ep = EntryPointAccessors.fromApplication(applicationContext, WorkerEntryPoint::class.java)
        val note = ep.notesRepository().getNote(noteId) ?: return Result.failure()
        if (note.body.isBlank()) return Result.failure()
        return runCatching {
            val cleaned = ep.aiRepository().cleanupOnce(note.body, noteId)
            ep.resultStore().putCleanup(noteId, cleaned)
        }.fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }

    companion object {
        const val KEY_NOTE_ID = "note_id"
        private const val PREFIX = "ai_cleanup_"

        fun uniqueName(noteId: Long) = "$PREFIX$noteId"

        /** Enqueue a queued Clean-up for [noteId], running when the network returns. */
        fun enqueueCleanup(context: Context, noteId: Long) {
            val request = OneTimeWorkRequestBuilder<AiQueueWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(workDataOf(KEY_NOTE_ID to noteId))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueName(noteId),
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun data(noteId: Long): Data = workDataOf(KEY_NOTE_ID to noteId)
    }
}
