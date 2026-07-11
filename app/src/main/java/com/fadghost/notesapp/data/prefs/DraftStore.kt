package com.fadghost.notesapp.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A scratch buffer that continuously mirrors the in-progress editor text
 * (PLAN.md §6 — draft crash-recovery). Persisted separately from the Room note so
 * that after a process death / crash with unsaved content we can offer
 * "Restore unsaved note" on next launch. Cleared on a clean save/close.
 */
data class DraftSnapshot(
    val noteId: Long,   // 0 == a brand-new note
    val title: String,
    val body: String,
    val updatedAt: Long
) {
    val isEmpty: Boolean get() = title.isBlank() && body.isBlank()
}

private val Context.draftDataStore by preferencesDataStore(name = "editor_draft")

@Singleton
class DraftStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val activeKey = booleanPreferencesKey("active")
    private val noteIdKey = longPreferencesKey("note_id")
    private val titleKey = stringPreferencesKey("title")
    private val bodyKey = stringPreferencesKey("body")
    private val updatedKey = longPreferencesKey("updated_at")

    val draft: Flow<DraftSnapshot?> = context.draftDataStore.data.map { p ->
        if (p[activeKey] != true) null
        else DraftSnapshot(
            noteId = p[noteIdKey] ?: 0L,
            title = p[titleKey] ?: "",
            body = p[bodyKey] ?: "",
            updatedAt = p[updatedKey] ?: 0L
        )
    }

    suspend fun save(snapshot: DraftSnapshot) {
        context.draftDataStore.edit { p ->
            p[activeKey] = true
            p[noteIdKey] = snapshot.noteId
            p[titleKey] = snapshot.title
            p[bodyKey] = snapshot.body
            p[updatedKey] = snapshot.updatedAt
        }
    }

    suspend fun clear() {
        context.draftDataStore.edit { it.clear() }
    }
}
