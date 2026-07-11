package com.fadghost.notesapp.data.ai

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the result of a Clean-up that ran while offline (PLAN.md §5 — "requests
 * queue and auto-run when back online"). The [AiQueueWorker] writes the cleaned
 * Markdown here keyed by note id; the editor observes it and offers to apply the
 * result (undoable). Cleared once applied/dismissed.
 */
private val Context.aiResultsStore by preferencesDataStore(name = "ai_results")

@Singleton
class AiResultStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private fun key(noteId: Long) = stringPreferencesKey("cleanup_$noteId")

    fun pendingCleanup(noteId: Long): Flow<String?> =
        context.aiResultsStore.data.map { it[key(noteId)] }

    suspend fun putCleanup(noteId: Long, cleaned: String) {
        context.aiResultsStore.edit { it[key(noteId)] = cleaned }
    }

    suspend fun clearCleanup(noteId: Long) {
        context.aiResultsStore.edit { it.remove(key(noteId)) }
    }
}
