package com.fadghost.notesapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Hand-drawn line glyphs for note actions and the editor toolbar. Keeps the
 * visible UI free of Material icon components (PLAN.md §3). One Canvas, switched
 * on [Glyph]; every path is stroked with the caller's [color].
 */
enum class Glyph {
    PIN, ARCHIVE, TRASH, DUPLICATE, FOLDER, TAG, SEARCH, CLOSE, GRID, LIST,
    BOLD, ITALIC, HEADING, CHECKLIST, BULLET, UNDO, REDO, RESTORE, CHECK, PLUS,
    CHEVRON, BACK, MORE
}

@Composable
fun AuraGlyph(glyph: Glyph, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val s = size.minDimension
        val st = Stroke(width = s * 0.08f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (glyph) {
            Glyph.PIN -> drawPin(color, s, st)
            Glyph.ARCHIVE -> drawArchive(color, s, st)
            Glyph.TRASH -> drawTrash(color, s, st)
            Glyph.DUPLICATE -> drawDuplicate(color, s, st)
            Glyph.FOLDER -> drawFolder(color, s, st)
            Glyph.TAG -> drawTag(color, s, st)
            Glyph.SEARCH -> drawSearch(color, s, st)
            Glyph.CLOSE -> drawClose(color, s, st)
            Glyph.GRID -> drawGrid(color, s, st)
            Glyph.LIST -> drawList(color, s, st)
            Glyph.BOLD -> drawBold(color, s, st)
            Glyph.ITALIC -> drawItalic(color, s, st)
            Glyph.HEADING -> drawHeading(color, s, st)
            Glyph.CHECKLIST -> drawChecklist(color, s, st)
            Glyph.BULLET -> drawBullet(color, s, st)
            Glyph.UNDO -> drawUndo(color, s, st, mirror = false)
            Glyph.REDO -> drawUndo(color, s, st, mirror = true)
            Glyph.RESTORE -> drawUndo(color, s, st, mirror = false)
            Glyph.CHECK -> drawCheck(color, s, st)
            Glyph.PLUS -> drawPlus(color, s, st)
            Glyph.CHEVRON -> drawChevron(color, s, st, back = false)
            Glyph.BACK -> drawChevron(color, s, st, back = true)
            Glyph.MORE -> drawMore(color, s)
        }
    }
}

private fun DrawScope.line(c: Color, x1: Float, y1: Float, x2: Float, y2: Float, st: Stroke) =
    drawLine(c, Offset(x1, y1), Offset(x2, y2), st.width, st.cap)

private fun DrawScope.drawPin(c: Color, s: Float, st: Stroke) {
    // A push-pin: head circle + stem.
    drawCircle(c, s * 0.16f, Offset(s * 0.5f, s * 0.36f), style = st)
    line(c, s * 0.5f, s * 0.52f, s * 0.5f, s * 0.82f, st)
}

private fun DrawScope.drawArchive(c: Color, s: Float, st: Stroke) {
    drawRect(c, Offset(s * 0.22f, s * 0.24f), Size(s * 0.56f, s * 0.16f), style = st)
    drawRect(c, Offset(s * 0.26f, s * 0.40f), Size(s * 0.48f, s * 0.34f), style = st)
    line(c, s * 0.42f, s * 0.52f, s * 0.58f, s * 0.52f, st)
}

private fun DrawScope.drawTrash(c: Color, s: Float, st: Stroke) {
    line(c, s * 0.28f, s * 0.30f, s * 0.72f, s * 0.30f, st)
    line(c, s * 0.42f, s * 0.30f, s * 0.44f, s * 0.22f, st)
    line(c, s * 0.56f, s * 0.22f, s * 0.58f, s * 0.30f, st)
    val body = Path().apply {
        moveTo(s * 0.33f, s * 0.30f)
        lineTo(s * 0.37f, s * 0.78f)
        lineTo(s * 0.63f, s * 0.78f)
        lineTo(s * 0.67f, s * 0.30f)
    }
    drawPath(body, c, style = st)
    line(c, s * 0.45f, s * 0.40f, s * 0.46f, s * 0.68f, st)
    line(c, s * 0.55f, s * 0.40f, s * 0.54f, s * 0.68f, st)
}

private fun DrawScope.drawDuplicate(c: Color, s: Float, st: Stroke) {
    drawRect(c, Offset(s * 0.24f, s * 0.24f), Size(s * 0.36f, s * 0.36f), style = st)
    drawRect(c, Offset(s * 0.40f, s * 0.40f), Size(s * 0.36f, s * 0.36f), style = st)
}

private fun DrawScope.drawFolder(c: Color, s: Float, st: Stroke) {
    val p = Path().apply {
        moveTo(s * 0.22f, s * 0.34f)
        lineTo(s * 0.44f, s * 0.34f)
        lineTo(s * 0.50f, s * 0.42f)
        lineTo(s * 0.78f, s * 0.42f)
        lineTo(s * 0.78f, s * 0.72f)
        lineTo(s * 0.22f, s * 0.72f)
        close()
    }
    drawPath(p, c, style = st)
}

private fun DrawScope.drawTag(c: Color, s: Float, st: Stroke) {
    val p = Path().apply {
        moveTo(s * 0.28f, s * 0.28f)
        lineTo(s * 0.52f, s * 0.28f)
        lineTo(s * 0.74f, s * 0.50f)
        lineTo(s * 0.52f, s * 0.72f)
        lineTo(s * 0.28f, s * 0.48f)
        close()
    }
    drawPath(p, c, style = st)
    drawCircle(c, s * 0.035f, Offset(s * 0.40f, s * 0.40f))
}

private fun DrawScope.drawSearch(c: Color, s: Float, st: Stroke) {
    drawCircle(c, s * 0.20f, Offset(s * 0.44f, s * 0.44f), style = st)
    line(c, s * 0.58f, s * 0.58f, s * 0.74f, s * 0.74f, st)
}

private fun DrawScope.drawClose(c: Color, s: Float, st: Stroke) {
    line(c, s * 0.32f, s * 0.32f, s * 0.68f, s * 0.68f, st)
    line(c, s * 0.68f, s * 0.32f, s * 0.32f, s * 0.68f, st)
}

private fun DrawScope.drawGrid(c: Color, s: Float, st: Stroke) {
    drawRect(c, Offset(s * 0.24f, s * 0.24f), Size(s * 0.20f, s * 0.20f), style = st)
    drawRect(c, Offset(s * 0.56f, s * 0.24f), Size(s * 0.20f, s * 0.20f), style = st)
    drawRect(c, Offset(s * 0.24f, s * 0.56f), Size(s * 0.20f, s * 0.20f), style = st)
    drawRect(c, Offset(s * 0.56f, s * 0.56f), Size(s * 0.20f, s * 0.20f), style = st)
}

private fun DrawScope.drawList(c: Color, s: Float, st: Stroke) {
    for (i in 0..2) {
        val y = s * (0.32f + i * 0.18f)
        drawCircle(c, s * 0.03f, Offset(s * 0.30f, y))
        line(c, s * 0.40f, y, s * 0.74f, y, st)
    }
}

private fun DrawScope.drawBold(c: Color, s: Float, st: Stroke) {
    val thick = Stroke(width = s * 0.13f, cap = StrokeCap.Round)
    line(c, s * 0.38f, s * 0.28f, s * 0.38f, s * 0.72f, thick)
    line(c, s * 0.38f, s * 0.30f, s * 0.58f, s * 0.30f, thick)
    line(c, s * 0.58f, s * 0.30f, s * 0.58f, s * 0.48f, thick)
    line(c, s * 0.38f, s * 0.50f, s * 0.60f, s * 0.50f, thick)
    line(c, s * 0.60f, s * 0.50f, s * 0.60f, s * 0.70f, thick)
    line(c, s * 0.38f, s * 0.70f, s * 0.60f, s * 0.70f, thick)
}

private fun DrawScope.drawItalic(c: Color, s: Float, st: Stroke) {
    line(c, s * 0.44f, s * 0.30f, s * 0.62f, s * 0.30f, st)
    line(c, s * 0.38f, s * 0.70f, s * 0.56f, s * 0.70f, st)
    line(c, s * 0.56f, s * 0.30f, s * 0.44f, s * 0.70f, st)
}

private fun DrawScope.drawHeading(c: Color, s: Float, st: Stroke) {
    val thick = Stroke(width = s * 0.11f, cap = StrokeCap.Round)
    line(c, s * 0.32f, s * 0.30f, s * 0.32f, s * 0.70f, thick)
    line(c, s * 0.56f, s * 0.30f, s * 0.56f, s * 0.70f, thick)
    line(c, s * 0.32f, s * 0.50f, s * 0.56f, s * 0.50f, thick)
    line(c, s * 0.66f, s * 0.44f, s * 0.66f, s * 0.70f, st)
}

private fun DrawScope.drawChecklist(c: Color, s: Float, st: Stroke) {
    drawRect(c, Offset(s * 0.24f, s * 0.30f), Size(s * 0.16f, s * 0.16f), style = st)
    val tick = Path().apply {
        moveTo(s * 0.26f, s * 0.38f); lineTo(s * 0.31f, s * 0.43f); lineTo(s * 0.40f, s * 0.30f)
    }
    drawPath(tick, c, style = st)
    line(c, s * 0.50f, s * 0.38f, s * 0.76f, s * 0.38f, st)
    line(c, s * 0.50f, s * 0.62f, s * 0.76f, s * 0.62f, st)
    drawRect(c, Offset(s * 0.24f, s * 0.54f), Size(s * 0.16f, s * 0.16f), style = st)
}

private fun DrawScope.drawBullet(c: Color, s: Float, st: Stroke) {
    for (i in 0..1) {
        val y = s * (0.38f + i * 0.24f)
        drawCircle(c, s * 0.045f, Offset(s * 0.30f, y))
        line(c, s * 0.42f, y, s * 0.74f, y, st)
    }
}

private fun DrawScope.drawUndo(c: Color, s: Float, st: Stroke, mirror: Boolean) {
    val arc = Path().apply {
        if (!mirror) {
            moveTo(s * 0.34f, s * 0.40f)
            cubicTo(s * 0.34f, s * 0.68f, s * 0.68f, s * 0.72f, s * 0.72f, s * 0.56f)
            moveTo(s * 0.34f, s * 0.40f); lineTo(s * 0.28f, s * 0.30f)
            moveTo(s * 0.34f, s * 0.40f); lineTo(s * 0.46f, s * 0.38f)
        } else {
            moveTo(s * 0.66f, s * 0.40f)
            cubicTo(s * 0.66f, s * 0.68f, s * 0.32f, s * 0.72f, s * 0.28f, s * 0.56f)
            moveTo(s * 0.66f, s * 0.40f); lineTo(s * 0.72f, s * 0.30f)
            moveTo(s * 0.66f, s * 0.40f); lineTo(s * 0.54f, s * 0.38f)
        }
    }
    drawPath(arc, c, style = st)
}

private fun DrawScope.drawCheck(c: Color, s: Float, st: Stroke) {
    val p = Path().apply {
        moveTo(s * 0.28f, s * 0.52f); lineTo(s * 0.44f, s * 0.68f); lineTo(s * 0.74f, s * 0.32f)
    }
    drawPath(p, c, style = st)
}

private fun DrawScope.drawPlus(c: Color, s: Float, st: Stroke) {
    line(c, s * 0.30f, s * 0.50f, s * 0.70f, s * 0.50f, st)
    line(c, s * 0.50f, s * 0.30f, s * 0.50f, s * 0.70f, st)
}

private fun DrawScope.drawChevron(c: Color, s: Float, st: Stroke, back: Boolean) {
    if (back) {
        line(c, s * 0.58f, s * 0.32f, s * 0.40f, s * 0.50f, st)
        line(c, s * 0.40f, s * 0.50f, s * 0.58f, s * 0.68f, st)
    } else {
        line(c, s * 0.44f, s * 0.32f, s * 0.62f, s * 0.50f, st)
        line(c, s * 0.62f, s * 0.50f, s * 0.44f, s * 0.68f, st)
    }
}

private fun DrawScope.drawMore(c: Color, s: Float) {
    for (i in 0..2) drawCircle(c, s * 0.045f, Offset(s * (0.32f + i * 0.18f), s * 0.5f))
}
