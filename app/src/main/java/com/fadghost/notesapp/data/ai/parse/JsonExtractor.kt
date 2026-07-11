package com.fadghost.notesapp.data.ai.parse

/**
 * Pulls a single balanced JSON object out of a model reply (PLAN.md §5 — "models
 * wrap JSON in prose … defensive parser"). Handles the four cases the extract
 * flow must survive:
 *   1. clean JSON                — `{"items":[...]}`
 *   2. fenced JSON               — ```json\n{...}\n``` (any fence label)
 *   3. prose-wrapped JSON        — `Sure! Here you go: {...}. Hope that helps.`
 *   4. truncated JSON            — `{"items":[{"title":"a"` → returns null
 *
 * Pure and allocation-light: scans for the first `{`, then brace-matches while
 * respecting string literals and escapes, so braces inside string values do not
 * fool the counter. A run that never returns to depth 0 (truncation) yields null,
 * which the caller treats as "re-ask once, then show raw".
 */
object JsonExtractor {

    /** @return the first balanced top-level JSON object, or null if none is complete. */
    fun extract(raw: String): String? {
        val start = raw.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        var i = start
        while (i < raw.length) {
            val c = raw[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return raw.substring(start, i + 1)
                    }
                }
            }
            i++
        }
        return null // never closed → truncated / malformed
    }
}
