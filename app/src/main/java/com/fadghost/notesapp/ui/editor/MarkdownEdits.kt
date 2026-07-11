package com.fadghost.notesapp.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Pure text transforms behind the formatting toolbar and smart lists
 * (PLAN.md §6). Each operates on a [TextFieldValue] and returns a new one with a
 * sensible selection so the caller can push it straight back into the field.
 */
object MarkdownEdits {

    private val CHECKLIST = Regex("""^(\s*)([-*+]) \[([ xX])] (.*)$""")
    private val BULLET = Regex("""^(\s*)([-*+]) (.*)$""")
    private val ORDERED = Regex("""^(\s*)(\d+)([.)]) (.*)$""")
    private val HEADING = Regex("""^(#{1,6}) (.*)$""")

    private fun lineStart(text: String, cursor: Int): Int =
        text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }

    private fun lineEnd(text: String, cursor: Int): Int =
        text.indexOf('\n', cursor).let { if (it < 0) text.length else it }

    /** Wrap the selection with [marker] (bold/italic); insert an empty pair if none. */
    fun wrap(value: TextFieldValue, marker: String): TextFieldValue {
        val sel = value.selection
        val text = value.text
        return if (sel.collapsed) {
            val at = sel.start
            val newText = text.substring(0, at) + marker + marker + text.substring(at)
            TextFieldValue(newText, TextRange(at + marker.length))
        } else {
            val s = minOf(sel.start, sel.end)
            val e = maxOf(sel.start, sel.end)
            val newText = text.substring(0, s) + marker + text.substring(s, e) + marker + text.substring(e)
            TextFieldValue(newText, TextRange(s + marker.length, e + marker.length))
        }
    }

    /** Cycle the current line's heading level: none -> H1 -> H2 -> H3 -> none. */
    fun cycleHeading(value: TextFieldValue): TextFieldValue {
        val text = value.text
        val ls = lineStart(text, value.selection.start)
        val le = lineEnd(text, value.selection.start)
        val line = text.substring(ls, le)
        val m = HEADING.find(line)
        val (newLine, delta) = when {
            m == null -> "# ${line}" to 2
            m.groupValues[1].length >= 3 -> m.groupValues[2] to -(m.groupValues[1].length + 1)
            else -> "#".repeat(m.groupValues[1].length + 1) + " " + m.groupValues[2] to 1
        }
        val newText = text.substring(0, ls) + newLine + text.substring(le)
        val cursor = (value.selection.start + delta).coerceIn(ls, ls + newLine.length)
        return TextFieldValue(newText, TextRange(cursor))
    }

    /** Toggle a `- ` bullet on the current line. */
    fun toggleBullet(value: TextFieldValue): TextFieldValue = toggleLinePrefix(value, "- ")

    /** Toggle a `- [ ] ` checklist item on the current line. */
    fun toggleChecklist(value: TextFieldValue): TextFieldValue = toggleLinePrefix(value, "- [ ] ")

    private fun toggleLinePrefix(value: TextFieldValue, prefix: String): TextFieldValue {
        val text = value.text
        val ls = lineStart(text, value.selection.start)
        val le = lineEnd(text, value.selection.start)
        val line = text.substring(ls, le)
        val stripped = stripAnyListPrefix(line)
        val newLine = if (line == prefix + stripped) stripped else prefix + stripped
        val newText = text.substring(0, ls) + newLine + text.substring(le)
        val delta = newLine.length - line.length
        val cursor = (value.selection.start + delta).coerceAtLeast(ls)
        return TextFieldValue(newText, TextRange(cursor))
    }

    private fun stripAnyListPrefix(line: String): String {
        CHECKLIST.find(line)?.let { return it.groupValues[4] }
        BULLET.find(line)?.let { return it.groupValues[3] }
        ORDERED.find(line)?.let { return it.groupValues[4] }
        return line
    }

    /** Indent the current line by two spaces (swipe-right gesture). */
    fun indent(value: TextFieldValue): TextFieldValue {
        val text = value.text
        val ls = lineStart(text, value.selection.start)
        val newText = text.substring(0, ls) + "  " + text.substring(ls)
        return TextFieldValue(newText, TextRange(value.selection.start + 2))
    }

    /**
     * Smart-list continuation. Given the value before and after a single edit,
     * returns a transformed value when the edit was an Enter press inside a list
     * item (continue the list, or exit on an empty item), else null.
     */
    fun onNewline(old: TextFieldValue, new: TextFieldValue): TextFieldValue? {
        val start = new.selection.start
        val insertedNewline = new.text.length == old.text.length + 1 &&
            new.selection.collapsed &&
            start > 0 &&
            new.text[start - 1] == '\n'
        if (!insertedNewline) return null

        val prevStart = lineStart(new.text, start - 1)
        val prevLine = new.text.substring(prevStart, start - 1)

        CHECKLIST.find(prevLine)?.let { m ->
            return continueOrExit(new, prevStart, start, m.groupValues[1], "${m.groupValues[2]} [ ] ", m.groupValues[4])
        }
        BULLET.find(prevLine)?.let { m ->
            return continueOrExit(new, prevStart, start, m.groupValues[1], "${m.groupValues[2]} ", m.groupValues[3])
        }
        ORDERED.find(prevLine)?.let { m ->
            val next = (m.groupValues[2].toIntOrNull() ?: 0) + 1
            return continueOrExit(new, prevStart, start, m.groupValues[1], "$next${m.groupValues[3]} ", m.groupValues[4])
        }
        return null
    }

    private fun continueOrExit(
        new: TextFieldValue,
        prevStart: Int,
        cursor: Int,
        indent: String,
        marker: String,
        content: String
    ): TextFieldValue {
        return if (content.isBlank()) {
            // Empty item + Enter => exit the list: clear the item and its newline.
            val newText = new.text.removeRange(prevStart, cursor)
            TextFieldValue(newText, TextRange(prevStart))
        } else {
            val prefix = indent + marker
            val newText = new.text.substring(0, cursor) + prefix + new.text.substring(cursor)
            TextFieldValue(newText, TextRange(cursor + prefix.length))
        }
    }

    /** If [offset] is on a checklist line, flip its checkbox; else null. */
    fun toggleCheckboxAt(text: String, offset: Int): String? {
        val ls = lineStart(text, offset)
        val le = lineEnd(text, offset)
        val line = text.substring(ls, le)
        val m = CHECKLIST.find(line) ?: return null
        val checked = m.groupValues[3].lowercase() == "x"
        val flipped = m.groupValues[1] + m.groupValues[2] + " [" + (if (checked) " " else "x") + "] " + m.groupValues[4]
        return text.substring(0, ls) + flipped + text.substring(le)
    }
}
