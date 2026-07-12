package com.fadghost.notesapp.data.ai

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
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
    private val autoCleanTranscriptKey = booleanPreferencesKey("auto_clean_transcript")

    val textModel: Flow<String> = context.aiSettingsStore.data.map { it[textModelKey] ?: DEFAULT_TEXT_MODEL }

    /**
     * Selected STT model. The transcription IDs OpenRouter actually accepts are a fixed
     * hardcoded trio ([STT_MODELS]) that do NOT appear in `/models`; the old default
     * (`qwen/qwen3-asr-flash-*`) no longer exists. Any stored value outside the trio —
     * the dead qwen id, a stale free-text pick — is transparently healed to
     * [DEFAULT_STT_MODEL] here on read, so existing installs recover with no user action.
     */
    val sttModel: Flow<String> = context.aiSettingsStore.data.map { prefs ->
        prefs[sttModelKey]?.takeIf { it in STT_MODELS } ?: DEFAULT_STT_MODEL
    }
    val favorites: Flow<Set<String>> = context.aiSettingsStore.data.map { it[favoritesKey] ?: emptySet() }
    val recents: Flow<List<String>> = context.aiSettingsStore.data.map { p ->
        p[recentsKey]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
    }
    val modelsFetchedAt: Flow<Long> = context.aiSettingsStore.data.map { it[modelsFetchedKey] ?: 0L }

    /**
     * Voice transcript post-processing (PLAN.md §5): OFF = keep verbatim, ON = auto
     * run the M2 Clean-up flow after transcription. Default OFF (verbatim, no key needed).
     */
    val autoCleanTranscript: Flow<Boolean> =
        context.aiSettingsStore.data.map { it[autoCleanTranscriptKey] ?: false }

    suspend fun setTextModel(id: String) {
        context.aiSettingsStore.edit { it[textModelKey] = id.trim() }
        pushRecent(id)
    }

    suspend fun setSttModel(id: String) {
        // Only ever persist a supported transcription id; anything else falls back to the
        // default so the stored value can never drift back into an unusable state.
        val safe = id.trim().takeIf { it in STT_MODELS } ?: DEFAULT_STT_MODEL
        context.aiSettingsStore.edit { it[sttModelKey] = safe }
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

    suspend fun setAutoCleanTranscript(enabled: Boolean) {
        context.aiSettingsStore.edit { it[autoCleanTranscriptKey] = enabled }
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

        /** Cheapest working transcription model (verified live on `/audio/transcriptions`). */
        const val DEFAULT_STT_MODEL = "openai/gpt-4o-mini-transcribe"

        /**
         * The exact transcription models OpenRouter's multipart STT endpoint accepts today.
         * These IDs are NOT returned by `/models`, so the STT picker offers this fixed list
         * (no free-text, no /models filtering). Order = cheapest → most accurate.
         */
        val STT_MODELS = listOf(
            "openai/gpt-4o-mini-transcribe",
            "openai/gpt-4o-transcribe",
            "openai/whisper-1"
        )

        private const val MAX_RECENTS = 8
    }
}
