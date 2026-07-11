package com.fadghost.notesapp.data.ai

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Prompt text + the Extract JSON schema (PLAN.md §5). Cleanup preserves every
 * fact and the user's language; Extract is constrained to a strict shape and
 * given the current instant so relative dates ("tomorrow 7am") resolve.
 */
object AiPrompts {

    /** Clean-up system prompt (PLAN.md §5): grammar, filler, structure, keep all facts + language. */
    const val CLEANUP_SYSTEM = """You are a careful note editor. Rewrite the user's note so it is clean and well structured, following ALL of these rules:
- Fix grammar, spelling and punctuation.
- Remove filler words and verbal tics (um, uh, like, you know, basically, sort of).
- Organise the content with Markdown: short headings (##), bullet lists and checklists where natural.
- Preserve EVERY fact, name, number, date and intent. Never invent, drop or summarise away information.
- Keep the user's original language (do not translate).
- Output ONLY the rewritten note in Markdown. No preamble, no commentary, no code fences."""

    /** When map-reducing a long note, the reduce step stitches cleaned chunks. */
    const val CLEANUP_REDUCE_SYSTEM = """You are joining several already-cleaned sections of one note into a single coherent Markdown document. Merge them smoothly, remove duplicate headings, keep every fact, and keep the user's language. Output ONLY the merged Markdown."""

    /** Extract system prompt; [nowIso] anchors relative dates. */
    fun extractSystem(nowIso: String): String = """You extract actionable items from a note.
The current date-time is $nowIso (use it to resolve relative dates like "tomorrow" or "next Friday").
Return a JSON object of the form {"items": [...]} where each item has:
- "type": one of "event", "reminder", "todo"
- "title": a short imperative title (required, non-empty)
- "datetime": ISO-8601 (e.g. 2026-07-12T09:00:00) when there is a clear time; omit for a plain todo
- "notes": optional extra detail
Only include items genuinely implied by the note. If there are none, return {"items": []}.
Output ONLY the JSON object — no prose, no code fences."""

    /** Revision prompt used by a card's "Other" free-text instruction. */
    fun reviseSystem(nowIso: String): String = """You revise a single extracted action based on the user's instruction.
The current date-time is $nowIso.
Return ONLY a JSON object for the one revised item: {"type":..., "title":..., "datetime":..., "notes":...}. No prose, no code fences."""

    /** The strict-ish JSON schema handed to `response_format`. */
    val EXTRACT_SCHEMA: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("items") {
                put("type", "array")
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("type") {
                            put("type", "string")
                            putJsonArray("enum") { add("event"); add("reminder"); add("todo") }
                        }
                        putJsonObject("title") { put("type", "string") }
                        putJsonObject("datetime") { put("type", "string") }
                        putJsonObject("notes") { put("type", "string") }
                    }
                    putJsonArray("required") { add("type"); add("title") }
                }
            }
        }
        putJsonArray("required") { add("items") }
    }

    private val ISO: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    fun nowIso(now: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        ISO.format(Instant.ofEpochMilli(now).atZone(zone))
}
