package com.fadghost.notesapp.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.fadghost.notesapp.ui.components.AuraGlyph
import com.fadghost.notesapp.ui.components.Glyph
import com.fadghost.notesapp.ui.theme.Aura

/**
 * Sticky formatting toolbar (PLAN.md §6) — thumb-reachable, docked above the
 * keyboard by the caller via imePadding. Bold / italic / heading cycle /
 * checklist / bullet / undo / redo, all custom-drawn.
 */
@Composable
fun FormattingToolbar(
    canUndo: Boolean,
    canRedo: Boolean,
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onHeading: () -> Unit,
    onChecklist: () -> Unit,
    onBullet: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = Aura.tokens
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(tokens.radii.pill))
            .background(tokens.colors.surfaceTranslucent)
            .border(1.dp, tokens.colors.outline, RoundedCornerShape(tokens.radii.pill))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ToolButton(Glyph.BOLD, enabled = true, onClick = onBold)
        ToolButton(Glyph.ITALIC, enabled = true, onClick = onItalic)
        ToolButton(Glyph.HEADING, enabled = true, onClick = onHeading)
        ToolButton(Glyph.CHECKLIST, enabled = true, onClick = onChecklist)
        ToolButton(Glyph.BULLET, enabled = true, onClick = onBullet)
        ToolButton(Glyph.UNDO, enabled = canUndo, onClick = onUndo)
        ToolButton(Glyph.REDO, enabled = canRedo, onClick = onRedo)
    }
}

@Composable
private fun ToolButton(glyph: Glyph, enabled: Boolean, onClick: () -> Unit) {
    val tokens = Aura.tokens
    val color = if (enabled) tokens.colors.textPrimary else tokens.colors.textSecondary.copy(alpha = 0.4f)
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(tokens.radii.sm))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        AuraGlyph(glyph, color, Modifier.size(22.dp))
    }
}
