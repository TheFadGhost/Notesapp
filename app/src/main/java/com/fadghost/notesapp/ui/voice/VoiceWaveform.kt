package com.fadghost.notesapp.ui.voice

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

/**
 * Live amplitude waveform (PLAN.md §5 — "record (live waveform + timer)"). Draws a
 * scrolling bar-per-sample history on one Canvas: newest sample on the right, older
 * samples fading toward the left. Amplitudes are pre-normalised to 0..1 by the
 * view-model. Kept custom (no Material) per PLAN.md §3.
 */
@Composable
fun VoiceWaveform(
    amplitudes: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    barCount: Int = 48
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
    ) {
        val w = size.width
        val h = size.height
        val midY = h / 2f
        val slot = w / barCount
        val barWidth = (slot * 0.5f).coerceAtLeast(2f)
        // Take the most recent [barCount] samples, right-aligned.
        val recent = if (amplitudes.size > barCount) amplitudes.takeLast(barCount) else amplitudes
        val start = barCount - recent.size
        recent.forEachIndexed { i, amp ->
            val idx = start + i
            val x = idx * slot + slot / 2f
            // Minimum visible nub so silence still reads as a centre line.
            val norm = amp.coerceIn(0f, 1f)
            val barH = (norm * (h * 0.9f)).coerceAtLeast(h * 0.04f)
            // Fade older bars.
            val alpha = 0.35f + 0.65f * (idx.toFloat() / barCount)
            drawLine(
                color = color.copy(alpha = alpha),
                start = Offset(x, midY - barH / 2f),
                end = Offset(x, midY + barH / 2f),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}
