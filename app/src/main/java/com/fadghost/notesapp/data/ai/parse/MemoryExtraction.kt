package com.fadghost.notesapp.data.ai.parse

import com.fadghost.notesapp.data.memory.MemoryEntryModel
import com.fadghost.notesapp.data.memory.MemoryFormat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Defensive parse + validate/clamp for P1 MEMORY_EXTRACT_V1 output (V3-PROMPTS.md §1.2).
 * Even with strict json_schema the model can wrap JSON in prose or over-run the field
 * limits, so — exactly like [ActionExtractionParser] — we locate the JSON via
 * [JsonExtractor], deserialise leniently, then clamp every field to the vault rules
 * ([MemoryFormat]): slug kebab ≤40, type ∈ 8, ≤5 tags, ≤6 links, hook ≤90, body ≤120
 * words, ≤10 entries. Pure; `now`/`source` are injected so it is deterministic and testable.
 */

@Serializable
data class RawMemoryEntry(
    val op: String? = null,
    val slug: String? = null,
    val title: String? = null,
    val type: String? = null,
    val tags: List<String> = emptyList(),
    val links: List<String> = emptyList(),
    val hook: String? = null,
    val body: String? = null
)

@Serializable
data class RawMemoryResult(
    val entries: List<RawMemoryEntry> = emptyList(),
    @SerialName("skipped_reason") val skippedReason: String? = null
)

/** A validated entry ready to become a confirm card. [op] drives create-vs-update copy. */
data class ProposedMemoryEntry(
    val op: String,
    val model: MemoryEntryModel
) {
    val isUpdate: Boolean get() = op == "update"
}

sealed interface MemoryExtractOutcome {
    data class Success(
        val entries: List<ProposedMemoryEntry>,
        val skippedReason: String?
    ) : MemoryExtractOutcome
    /** JSON could not be located/parsed — caller re-asks once, then shows [raw]. */
    data class ParseFailure(val raw: String) : MemoryExtractOutcome
}

class MemoryExtractionParser(
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
) {
    /**
     * @param now epoch millis (drives created/updated ISO stamps).
     * @param source the entry provenance, e.g. `note:12` | `manual` | `chat`.
     */
    fun parse(raw: String, now: Long, source: String): MemoryExtractOutcome {
        val jsonText = JsonExtractor.extract(raw) ?: return MemoryExtractOutcome.ParseFailure(raw)
        val result = runCatching { json.decodeFromString<RawMemoryResult>(jsonText) }.getOrNull()
            ?: return MemoryExtractOutcome.ParseFailure(raw)

        val today = AiPromptsDate.iso(now)
        val seen = HashSet<String>()
        val out = ArrayList<ProposedMemoryEntry>()
        for (item in result.entries) {
            if (out.size >= MAX_ENTRIES) break
            val slug = MemoryFormat.sanitizeSlug(item.slug.orEmpty())
            if (slug.isBlank() || !seen.add(slug)) continue // drop blank / duplicate slugs
            val title = item.title?.trim().orEmpty().ifBlank { slug.replace('-', ' ') }
            val body = item.body?.trim().orEmpty()
            if (body.isBlank()) continue // no durable content → skip
            val hook = item.hook?.let { MemoryFormat.clampHook(it) }?.takeIf { it.isNotBlank() }
                ?: MemoryFormat.clampHook(body)
            val model = MemoryEntryModel(
                slug = slug,
                title = title.take(120),
                type = MemoryFormat.sanitizeType(item.type.orEmpty()),
                tags = item.tags.map { it.trim().lowercase() }.filter { it.isNotBlank() }
                    .distinct().take(MemoryFormat.TAGS_MAX),
                links = item.links.map { MemoryFormat.sanitizeSlug(it) }
                    .filter { it.isNotBlank() && it != slug }.distinct().take(MemoryFormat.LINKS_MAX),
                hook = hook,
                source = source,
                created = today,
                updated = today,
                body = MemoryFormat.clampBody(body)
            )
            val op = if (item.op?.trim()?.lowercase() == "update") "update" else "create"
            out += ProposedMemoryEntry(op, model)
        }
        return MemoryExtractOutcome.Success(
            entries = out,
            skippedReason = result.skippedReason?.trim()?.takeIf { it.isNotBlank() }
        )
    }

    companion object {
        const val MAX_ENTRIES = 10
    }
}

/** Tiny date helper kept next to the parser so it stays pure/JVM-testable. */
internal object AiPromptsDate {
    fun iso(now: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String =
        java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toString()
}
