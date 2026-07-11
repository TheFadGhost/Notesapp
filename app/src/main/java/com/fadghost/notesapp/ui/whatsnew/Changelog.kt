package com.fadghost.notesapp.ui.whatsnew

/** A parsed changelog line, classified for rendering (PLAN.md §13). */
data class ChangelogLine(val kind: Kind, val text: String) {
    enum class Kind { TITLE, SECTION, BULLET, BODY }
}

/**
 * Minimal, dependency-free Markdown-ish parser for the bundled changelog. Pure so it
 * is unit-testable. Recognises `# ` (title), `## ` (section), `- ` (bullet); anything
 * else non-blank is body. Blank lines are dropped.
 */
fun parseChangelog(raw: String): List<ChangelogLine> =
    raw.lineSequence()
        .map { it.trimEnd() }
        .filter { it.isNotBlank() }
        .map { line ->
            when {
                line.startsWith("## ") -> ChangelogLine(ChangelogLine.Kind.SECTION, line.removePrefix("## ").trim())
                line.startsWith("# ") -> ChangelogLine(ChangelogLine.Kind.TITLE, line.removePrefix("# ").trim())
                line.startsWith("- ") -> ChangelogLine(ChangelogLine.Kind.BULLET, line.removePrefix("- ").trim())
                else -> ChangelogLine(ChangelogLine.Kind.BODY, line.trim())
            }
        }
        .toList()
