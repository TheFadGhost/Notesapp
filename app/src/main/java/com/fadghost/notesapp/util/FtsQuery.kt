package com.fadghost.notesapp.util

/**
 * Builds a safe FTS5 MATCH expression from raw, user-typed search text
 * (PLAN.md §6 — search-as-you-type). Users type prose, not FTS syntax, so we
 * sanitise every token (stripping FTS operators/quotes that would otherwise
 * raise a "malformed MATCH" error) and append `*` for prefix matching so results
 * appear while typing. Pure + unit-tested.
 */
object FtsQuery {

    // Keep letters/digits from any language plus intra-word separators.
    private val TOKEN = Regex("""[\p{L}\p{N}]+""")

    /**
     * @return an FTS5 MATCH string (implicit-AND of prefix tokens), or null when
     *   there is nothing searchable (caller should then show the full list).
     */
    fun build(raw: String): String? {
        val tokens = TOKEN.findAll(raw)
            .map { it.value }
            .filter { it.isNotBlank() }
            .toList()
        if (tokens.isEmpty()) return null
        // Quote each token (defends against a token that is an FTS keyword like
        // AND/OR/NOT) and add the prefix star outside the quotes.
        return tokens.joinToString(" ") { "\"$it\"*" }
    }
}
