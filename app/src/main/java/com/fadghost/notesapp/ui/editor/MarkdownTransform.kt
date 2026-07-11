package com.fadghost.notesapp.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Live markdown styling for the editor body (PLAN.md §6 — "live rendering of
 * headings, bold/italic, bullet lists, checklists"). Source characters are kept
 * (so the field stays a real editor and offsets map 1:1 with [OffsetMapping.Identity]);
 * we only layer [SpanStyle]s so `**bold**` looks bold, headings grow, markers dim.
 */
class MarkdownVisualTransformation(
    private val textColor: Color,
    private val markerColor: Color,
    private val accent: Color,
    private val baseSize: TextUnit = 15.sp
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val styled = annotate(text.text, textColor, markerColor, accent, baseSize)
        return TransformedText(styled, OffsetMapping.Identity)
    }

    private companion object {
        val BOLD = Regex("""(\*\*|__)(.+?)\1""")
        val ITALIC = Regex("""(?<![*_])([*_])(?!\s)(.+?)(?<!\s)\1(?![*_])""")
        val STRIKE = Regex("""~~(.+?)~~""")
        val CODE = Regex("""`([^`]+)`""")
        val HEADING = Regex("""^(#{1,6}) """)
        val CHECK_DONE = Regex("""^\s*[-*+] \[[xX]] """)
    }

    fun annotate(
        src: String,
        textColor: Color,
        markerColor: Color,
        accent: Color,
        baseSize: TextUnit
    ): AnnotatedString = buildAnnotatedString {
        append(src)
        val consumed = BooleanArray(src.length)

        // Block-level: headings + finished checklist items (strike-through).
        var lineStart = 0
        for (line in src.split("\n")) {
            val lineEnd = lineStart + line.length
            HEADING.find(line)?.let { m ->
                val level = m.groupValues[1].length
                val size = (baseSize.value + (7 - level) * 2f).coerceAtLeast(baseSize.value)
                addStyle(SpanStyle(color = markerColor), lineStart, lineStart + m.range.last + 1)
                addStyle(
                    SpanStyle(fontWeight = FontWeight.Bold, fontSize = size.sp, color = textColor),
                    lineStart + m.range.last + 1,
                    lineEnd
                )
            }
            if (CHECK_DONE.containsMatchIn(line)) {
                addStyle(
                    SpanStyle(color = markerColor, textDecoration = TextDecoration.LineThrough),
                    lineStart,
                    lineEnd
                )
            }
            lineStart = lineEnd + 1 // skip the '\n'
        }

        // Inline emphasis (longest markers first so ** wins over *).
        applyInline(BOLD, src, consumed, markerLen = { it.groupValues[1].length }) { s, e ->
            addStyle(SpanStyle(fontWeight = FontWeight.Bold), s, e)
        }
        applyInline(STRIKE, src, consumed, markerLen = { 2 }) { s, e ->
            addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), s, e)
        }
        applyInline(CODE, src, consumed, markerLen = { 1 }) { s, e ->
            addStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = accent), s, e)
        }
        applyInline(ITALIC, src, consumed, markerLen = { 1 }) { s, e ->
            addStyle(SpanStyle(fontStyle = FontStyle.Italic), s, e)
        }
    }

    private inline fun AnnotatedString.Builder.applyInline(
        regex: Regex,
        src: String,
        consumed: BooleanArray,
        markerLen: (MatchResult) -> Int,
        crossinline styleInner: AnnotatedString.Builder.(Int, Int) -> Unit
    ) {
        for (m in regex.findAll(src)) {
            val range = m.range
            if ((range.first..range.last).any { consumed[it] }) continue
            val ml = markerLen(m)
            val innerStart = range.first + ml
            val innerEnd = range.last + 1 - ml
            if (innerStart >= innerEnd) continue
            styleInner(innerStart, innerEnd)
            // Dim the surrounding markers.
            addStyle(SpanStyle(color = markerColor), range.first, innerStart)
            addStyle(SpanStyle(color = markerColor), innerEnd, range.last + 1)
            for (i in range.first..range.last) consumed[i] = true
        }
    }
}
