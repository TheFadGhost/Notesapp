package com.fadghost.notesapp.ui.calendar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fadghost.notesapp.data.db.entity.Recurrence
import com.fadghost.notesapp.ui.ai.SoftButton
import com.fadghost.notesapp.ui.components.AuraDateTimePicker
import com.fadghost.notesapp.ui.theme.Aura
import com.fadghost.notesapp.ui.theme.AuraType
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Spring-up create/edit sheet for a calendar item (PLAN.md §8 — title, start/end
 * via [AuraDateTimePicker], timezone-aware, notes, recurrence). One sheet serves
 * both events and reminders via a kind toggle; end-time and notes only apply to
 * events (the [com.fadghost.notesapp.data.db.entity.Reminder] row has neither).
 */
@Composable
fun ItemDetailSheet(
    draft: ItemDraft?,
    zone: ZoneId,
    onDismiss: () -> Unit,
    onSave: (ItemDraft) -> Unit,
    onDelete: (ItemDraft) -> Unit
) {
    val tokens = Aura.tokens
    val visible = draft != null

    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = tokens.elevation.scrim))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter
        ) {
            AnimatedVisibility(
                visible,
                enter = slideInVertically(spring(stiffness = Spring.StiffnessLow)) { it } + fadeIn(tween(140)),
                exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(120))
            ) {
                val seed = draft ?: return@AnimatedVisibility
                SheetBody(seed, zone, onDismiss, onSave, onDelete)
            }
        }
    }
}

@Composable
private fun SheetBody(
    seed: ItemDraft,
    zone: ZoneId,
    onDismiss: () -> Unit,
    onSave: (ItemDraft) -> Unit,
    onDelete: (ItemDraft) -> Unit
) {
    val tokens = Aura.tokens
    val isNew = seed.baseId == 0L

    var kind by remember(seed) { mutableStateOf(seed.kind) }
    var title by remember(seed) { mutableStateOf(seed.title) }
    var start by remember(seed) { mutableStateOf(toLdt(seed.start, zone)) }
    var end by remember(seed) { mutableStateOf(toLdt(seed.end, zone)) }
    var notes by remember(seed) { mutableStateOf(seed.notes) }
    var recurrence by remember(seed) { mutableStateOf(seed.recurrence) }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = tokens.radii.lg, topEnd = tokens.radii.lg))
            .background(tokens.colors.surface)
            .border(1.dp, tokens.colors.outline, RoundedCornerShape(topStart = tokens.radii.lg, topEnd = tokens.radii.lg))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = {})
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .navigationBarsPadding()
    ) {
        // Grab handle.
        Box(
            Modifier
                .padding(bottom = 14.dp)
                .size(width = 40.dp, height = 4.dp)
                .clip(RoundedCornerShape(tokens.radii.pill))
                .background(tokens.colors.outline)
                .align(Alignment.CenterHorizontally)
        )

        BasicText(
            if (isNew) "New ${kind.name.lowercase()}" else "Edit",
            style = AuraType.title.copy(color = tokens.colors.textPrimary)
        )
        Spacer(Modifier.size(14.dp))

        // Kind toggle (only when creating; editing keeps the existing kind).
        if (isNew) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                KindTab("Event", kind == CalendarKind.EVENT, Modifier.weight(1f)) { kind = CalendarKind.EVENT }
                KindTab("Reminder", kind == CalendarKind.REMINDER, Modifier.weight(1f)) { kind = CalendarKind.REMINDER }
            }
            Spacer(Modifier.size(14.dp))
        }

        Field(value = title, placeholder = "Title", onChange = { title = it })
        Spacer(Modifier.size(16.dp))

        BasicText(if (kind == CalendarKind.EVENT) "Starts" else "When", style = AuraType.label.copy(color = tokens.colors.textSecondary))
        Spacer(Modifier.size(6.dp))
        Box(Modifier.horizontalScroll(rememberScrollState())) {
            AuraDateTimePicker(value = start, onChange = { newStart ->
                // Keep the event duration when the user shifts the start.
                if (kind == CalendarKind.EVENT) {
                    val delta = java.time.Duration.between(start, newStart)
                    end = end.plus(delta)
                }
                start = newStart
            }, zone = zone)
        }

        if (kind == CalendarKind.EVENT) {
            Spacer(Modifier.size(14.dp))
            BasicText("Ends", style = AuraType.label.copy(color = tokens.colors.textSecondary))
            Spacer(Modifier.size(6.dp))
            Box(Modifier.horizontalScroll(rememberScrollState())) {
                AuraDateTimePicker(value = end, onChange = { end = it }, zone = zone)
            }
        }

        Spacer(Modifier.size(16.dp))
        BasicText("Repeat", style = AuraType.label.copy(color = tokens.colors.textSecondary))
        Spacer(Modifier.size(6.dp))
        RecurrencePicker(value = recurrence, onChange = { recurrence = it }, modifier = Modifier.fillMaxWidth())

        if (kind == CalendarKind.EVENT) {
            Spacer(Modifier.size(16.dp))
            BasicText("Notes", style = AuraType.label.copy(color = tokens.colors.textSecondary))
            Spacer(Modifier.size(6.dp))
            Field(value = notes, placeholder = "Add notes", onChange = { notes = it }, singleLine = false)
        }

        Spacer(Modifier.size(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!isNew) {
                SoftButton("Delete", filled = false, onClick = {
                    onDelete(seed); onDismiss()
                })
            }
            Spacer(Modifier.weight(1f))
            SoftButton("Cancel", filled = false, onClick = onDismiss)
            Spacer(Modifier.size(10.dp))
            SoftButton("Save", filled = true, onClick = {
                val startMs = toMillis(start, zone)
                val endMs = toMillis(if (end.isBefore(start)) start.plusHours(1) else end, zone)
                onSave(
                    seed.copy(
                        kind = kind,
                        title = title,
                        start = startMs,
                        end = endMs,
                        notes = notes,
                        recurrence = recurrence
                    )
                )
                onDismiss()
            })
        }
    }
}

@Composable
private fun KindTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tokens = Aura.tokens
    val bg = if (selected) tokens.colors.accent else tokens.colors.background
    val fg = if (selected) tokens.colors.background else tokens.colors.textSecondary
    Box(
        modifier
            .clip(RoundedCornerShape(tokens.radii.pill))
            .background(bg)
            .border(1.dp, tokens.colors.outline, RoundedCornerShape(tokens.radii.pill))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(label, style = AuraType.label.copy(color = fg))
    }
}

@Composable
private fun Field(
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
    singleLine: Boolean = true
) {
    val tokens = Aura.tokens
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.radii.sm))
            .background(tokens.colors.background)
            .border(1.dp, tokens.colors.outline, RoundedCornerShape(tokens.radii.sm))
            .padding(12.dp)
    ) {
        if (value.isEmpty()) {
            BasicText(placeholder, style = AuraType.body.copy(color = tokens.colors.textSecondary))
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = singleLine,
            textStyle = AuraType.body.copy(color = tokens.colors.textPrimary),
            cursorBrush = SolidColor(tokens.colors.accent),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun toLdt(millis: Long, zone: ZoneId): LocalDateTime =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()

private fun toMillis(ldt: LocalDateTime, zone: ZoneId): Long =
    ldt.atZone(zone).toInstant().toEpochMilli()
