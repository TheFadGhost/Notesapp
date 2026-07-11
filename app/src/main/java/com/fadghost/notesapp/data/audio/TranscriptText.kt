package com.fadghost.notesapp.data.audio

/**
 * Pure transcript text helpers (PLAN.md §5 — "Multi-segment recordings transcribe
 * sequentially and concatenate"). No Android deps so the concatenation contract is
 * unit-testable without a network or recorder.
 */
object TranscriptText {

    /**
     * Join per-segment transcripts into one clean block. Each part is trimmed, blank
     * parts are dropped, and the pieces are joined with a single space so the seam
     * between two segments reads as continuous prose. Internal runs of whitespace are
     * collapsed to keep the inserted note line tidy.
     */
    fun concatenate(parts: List<String>): String =
        parts.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .replace(WHITESPACE, " ")
            .trim()

    private val WHITESPACE = Regex("""\s+""")
}
