package com.fadghost.notesapp.data.ai.text

/**
 * Conservative token estimation + note chunking (PLAN.md §5/§7 — "long notes:
 * chunking strategy … no pre-call token estimates for cost, but a coarse size
 * guard for context"). We cannot tokenise arbitrary models exactly, so [estimate]
 * is deliberately pessimistic (~3.5 chars/token) and used only to decide whether
 * Clean-up must run map-reduce and where Extract truncates. Pure + unit-tested.
 */
object TokenEstimator {
    /** Chars-per-token; low on purpose so we over- rather than under-estimate. */
    private const val CHARS_PER_TOKEN = 3.5

    fun estimate(text: String): Int = Math.ceil(text.length / CHARS_PER_TOKEN).toInt()

    /** True when [text] plus [reservedForPrompt] would exceed a safe fraction of [contextTokens]. */
    fun exceedsBudget(text: String, contextTokens: Int, reservedForPrompt: Int = 1500): Boolean =
        estimate(text) + reservedForPrompt > (contextTokens * 0.7).toInt()
}

object Chunker {

    /**
     * Split [text] into chunks each estimated at or below [maxTokens], preferring
     * paragraph then sentence then hard-character boundaries so Clean-up can run
     * map-reduce over a long note without shredding words. Never returns an empty
     * list for non-blank input.
     */
    fun chunk(text: String, maxTokens: Int): List<String> {
        if (text.isBlank()) return emptyList()
        if (!TokenEstimator.exceedsBudget(text, contextTokens = maxTokens * 2, reservedForPrompt = 0) &&
            TokenEstimator.estimate(text) <= maxTokens
        ) return listOf(text)

        val maxChars = (maxTokens * 3.5).toInt().coerceAtLeast(200)
        val paragraphs = text.split(Regex("\n{2,}"))
        val chunks = ArrayList<String>()
        val current = StringBuilder()

        fun flush() {
            if (current.isNotBlank()) chunks += current.toString().trim()
            current.setLength(0)
        }

        for (para in paragraphs) {
            val piece = para.trim()
            if (piece.isEmpty()) continue
            when {
                piece.length > maxChars -> {
                    flush()
                    chunks += splitHard(piece, maxChars)
                }
                current.length + piece.length + 2 > maxChars -> {
                    flush()
                    current.append(piece)
                }
                else -> {
                    if (current.isNotEmpty()) current.append("\n\n")
                    current.append(piece)
                }
            }
        }
        flush()
        return chunks.ifEmpty { listOf(text.trim()) }
    }

    /** Break an over-long paragraph on sentence ends, then hard char windows. */
    private fun splitHard(text: String, maxChars: Int): List<String> {
        val out = ArrayList<String>()
        val sentences = text.split(Regex("(?<=[.!?])\\s+"))
        val cur = StringBuilder()
        for (s in sentences) {
            if (s.length > maxChars) {
                if (cur.isNotBlank()) { out += cur.toString().trim(); cur.setLength(0) }
                var i = 0
                while (i < s.length) {
                    out += s.substring(i, minOf(i + maxChars, s.length))
                    i += maxChars
                }
            } else if (cur.length + s.length + 1 > maxChars) {
                out += cur.toString().trim()
                cur.setLength(0)
                cur.append(s)
            } else {
                if (cur.isNotEmpty()) cur.append(' ')
                cur.append(s)
            }
        }
        if (cur.isNotBlank()) out += cur.toString().trim()
        return out
    }
}
