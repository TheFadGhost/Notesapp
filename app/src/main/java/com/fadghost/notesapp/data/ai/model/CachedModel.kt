package com.fadghost.notesapp.data.ai.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached OpenRouter model list (PLAN.md §5 — "fetch model list from /models,
 * cache in Room"). The picker reads this so it works offline after a first
 * fetch; favourites/recents/selection live in DataStore ([com.fadghost.notesapp.data.ai.AiPreferences]).
 */
@Entity(tableName = "CachedModel")
data class CachedModel(
    @PrimaryKey val id: String,
    val name: String,
    val contextLength: Int = 0,
    /** OpenRouter price strings (USD per token) kept verbatim; null when unknown. */
    val promptPrice: String? = null,
    val completionPrice: String? = null,
    /** Comma-separated input modalities; used to filter STT-capable (audio) models. */
    val inputModalities: String = "",
    val updatedAt: Long = 0
) {
    val supportsAudio: Boolean get() = inputModalities.split(",").any { it.trim() == "audio" }
}
