package com.fadghost.notesapp.util

/**
 * Lightweight markdown helpers. [strip] removes markdown syntax so the FTS index
 * and card previews contain clean prose (PLAN.md §6 — "markdown syntax stripped
 * before indexing"). Deliberately dependency-free and pure so it is unit-tested
 * on the JVM.
 */
object Markdown {

    private val IMAGE = Regex("""!\[([^\]]*)]\([^)]*\)""")
    private val LINK = Regex("""\[([^\]]*)]\([^)]*\)""")
    private val BOLD = Regex("""(\*\*|__)(.+?)\1""")
    private val ITALIC = Regex("""(\*|_)(.+?)\1""")
    private val STRIKE = Regex("""~~(.+?)~~""")
    private val CODE_INLINE = Regex("""`([^`]*)`""")
    private val HEADING = Regex("""^\s{0,3}#{1,6}\s+""")
    private val BLOCKQUOTE = Regex("""^\s{0,3}>\s?""")
    private val CHECKLIST = Regex("""^\s*[-*+]\s+\[[ xX]]\s+""")
    private val BULLET = Regex("""^\s*[-*+]\s+""")
    private val ORDERED = Regex("""^\s*\d+[.)]\s+""")
    private val FENCE = Regex("""^\s*```.*$""")
    private val WHITESPACE = Regex("""[ \t]{2,}""")

    /** Strip markdown syntax, preserving the readable text content. */
    fun strip(source: String): String {
        if (source.isBlank()) return ""
        val out = StringBuilder()
        var inFence = false
        for (rawLine in source.lines()) {
            if (FENCE.containsMatchIn(rawLine)) { // toggle fenced-block state, drop the fence line
                inFence = !inFence
                continue
            }
            if (inFence) continue // drop everything inside a fenced code block
            var line = rawLine
            line = HEADING.replaceFirst(line, "")
            line = BLOCKQUOTE.replaceFirst(line, "")
            line = CHECKLIST.replaceFirst(line, "")
            line = BULLET.replaceFirst(line, "")
            line = ORDERED.replaceFirst(line, "")
            line = stripInline(line)
            out.append(line.trim()).append('\n')
        }
        return out.toString()
            .replace(WHITESPACE, " ")
            .lines()
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()
    }

    /** Strip inline emphasis / links / code from a single line of text. */
    fun stripInline(line: String): String {
        var s = line
        s = IMAGE.replace(s) { it.groupValues[1] }
        s = LINK.replace(s) { it.groupValues[1] }
        s = BOLD.replace(s) { it.groupValues[2] }
        s = ITALIC.replace(s) { it.groupValues[2] }
        s = STRIKE.replace(s) { it.groupValues[1] }
        s = CODE_INLINE.replace(s) { it.groupValues[1] }
        return s
    }

    /** Extract the first non-blank stripped line — used as an auto-title fallback. */
    fun firstLine(source: String): String =
        source.lines().firstOrNull { it.isNotBlank() }?.let { stripInline(HEADING.replaceFirst(it, "")).trim() }
            ?: ""
}
