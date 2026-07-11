package com.fadghost.notesapp.data.ai

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Non-secret AI settings (PLAN.md §5): selected text/STT model ids, favourites,
 * recents, and the last time the /models cache was refreshed. Kept in DataStore
 * (no secrets here — the key lives in [ApiKeyStore]).
 */
private val Context.aiSettingsStore by preferencesDataStore(name = "ai_settings")

@Singleton
class AiPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val textModelKey = stringPreferencesKey("text_model")
    private val sttModelKey = stringPreferencesKey("stt_model")
    private val favoritesKey = stringSetPreferencesKey("favorite_models")
    private val recentsKey = stringPreferencesKey("recent_models") // ordered CSV, newest first
    private val modelsFetchedKey = longPreferencesKey("models_fetched_at")

    val textModel: Flow<String> = context.aiSettingsStore.data.map { it[textModelKey] ?: DEFAULT_TEXT_MODEL }
    val sttModel: Flow<String> = context.aiSettingsStore.data.map { it[sttModelKey] ?: DEFAULT_STT_MODEL }
    val favorites: Flow<Set<String>> = context.aiSettingsStore.data.map { it[favoritesKey] ?: emptySet() }
    val recents: Flow<List<String>> = context.aiSettingsStore.data.map { p ->
        p[recentsKey]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
    }
    val modelsFetchedAt: Flow<Long> = context.aiSettingsStore.data.map { it[modelsFetchedKey] ?: 0L }

    suspend fun setTextModel(id: String) {
        context.aiSettingsStore.edit { it[textModelKey] = id.trim() }
        pushRecent(id)
    }

    suspend fun setSttModel(id: String) {
        context.aiSettingsStore.edit { it[sttModelKey] = id.trim() }
    }

    suspend fun toggleFavorite(id: String) {
        context.aiSettingsStore.edit { p ->
            val cur = (p[favoritesKey] ?: emptySet()).toMutableSet()
            if (!cur.add(id)) cur.remove(id)
            p[favoritesKey] = cur
        }
    }

    suspend fun markModelsFetched(now: Long) {
        context.aiSettingsStore.edit { it[modelsFetchedKey] = now }
    }

    private suspend fun pushRecent(id: String) {
        context.aiSettingsStore.edit { p ->
            val cur = (p[recentsKey]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()).toMutableList()
            cur.remove(id)
            cur.add(0, id)
            p[recentsKey] = cur.take(MAX_RECENTS).joinToString("\n")
        }
    }

    companion object {
        const val DEFAULT_TEXT_MODEL = "deepseek/deepseek-v4-flash"
        const val DEFAULT_STT_MODEL = "qwen/qwen3-asr-flash-2026-02-10"
        private const val MAX_RECENTS = 8
    }
}
