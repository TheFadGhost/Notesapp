package com.fadghost.notesapp.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.toArgb
import com.fadghost.notesapp.ui.components.FlowChips
import com.fadghost.notesapp.ui.components.PlainChip
import com.fadghost.notesapp.ui.components.TagChip
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.fadghost.notesapp.data.db.entity.Folder
import com.fadghost.notesapp.data.db.entity.Tag
import com.fadghost.notesapp.ui.components.AuraGlyph
import com.fadghost.notesapp.ui.components.Glyph
import com.fadghost.notesapp.ui.theme.Aura
import com.fadghost.notesapp.ui.theme.AuraAccents
import com.fadghost.notesapp.ui.theme.AuraType
import com.fadghost.notesapp.ui.theme.auraFloatShadow

/**
 * Two SEPARATE, focused editor pickers (bug 3): the Folder chip opens [FolderPicker]
 * (folder list + new-folder row only), the Tags chip opens [TagPicker] (tag list +
 * new-tag row only). Each is an anchored popover dropping from the top-bar chips — a
 * scrim dims + dismisses, the card is a custom Aura surface (no Material dialog), and
 * neither ever shows the other's section. The old combined sheet is gone.
 */

/** Folder-only anchored popover. */
@Composable
fun FolderPicker(
    folders: List<Folder>,
    currentFolderId: Long?,
    onSelectFolder: (Long?) -> Unit,
    onCreateFolder: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newFolder by remember { mutableStateOf(TextFieldValue("")) }
    val current = folders.firstOrNull { it.id == currentFolderId }?.name ?: "None"
    PopoverScaffold(title = "Folder", subtitle = "In: $current", onDismiss = onDismiss) {
        FlowChips {
            PlainChip("None", selected = currentFolderId == null) { onSelectFolder(null) }
            folders.forEach { f ->
                PlainChip(f.name, selected = currentFolderId == f.id) { onSelectFolder(f.id) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PickerField(
                value = newFolder,
                onValueChange = { newFolder = it },
                placeholder = "New folder",
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            AddButton {
                val name = newFolder.text.trim()
                if (name.isNotEmpty()) {
                    onCreateFolder(name)
                    newFolder = TextFieldValue("")
                }
            }
        }
    }
}

/** Tag-only anchored popover. */
@Composable
fun TagPicker(
    allTags: List<Tag>,
    assignedTagIds: Set<Long>,
    onToggleTag: (Long) -> Unit,
    onCreateTag: (String, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var newTag by remember { mutableStateOf(TextFieldValue("")) }
    var colorIndex by remember { mutableStateOf(0) }
    val assignedCount = assignedTagIds.size
    PopoverScaffold(
        title = "Tags",
        subtitle = if (assignedCount == 0) "None assigned" else "$assignedCount assigned",
        onDismiss = onDismiss
    ) {
        if (allTags.isEmpty()) {
            BasicText(
                "No tags yet — create one below.",
                style = AuraType.label.copy(color = Aura.tokens.colors.textSecondary)
            )
        } else {
            FlowChips {
                allTags.forEach { tag ->
                    TagChip(tag = tag, selected = tag.id in assignedTagIds, onClick = { onToggleTag(tag.id) })
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Colour swatch cycler for the new tag.
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(AuraAccents.palette[colorIndex])
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { colorIndex = (colorIndex + 1) % AuraAccents.palette.size }
                    )
            )
            Spacer(Modifier.width(10.dp))
            PickerField(
                value = newTag,
                onValueChange = { newTag = it },
                placeholder = "New tag",
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            AddButton {
                val name = newTag.text.trim()
                if (name.isNotEmpty()) {
                    onCreateTag(name, AuraAccents.palette[colorIndex].toArgb())
                    newTag = TextFieldValue("")
                }
            }
        }
    }
}

/**
 * Shared chrome for the two pickers: a dismiss scrim plus a compact card anchored at the
 * top-right, dropping from the top-bar chips. [content] is the picker's own focused body.
 */
@Composable
private fun PopoverScaffold(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val tokens = Aura.tokens
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = tokens.elevation.scrim))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                // Sit just below the top-bar row so the popover reads as anchored to its chip.
                .padding(top = 60.dp, end = 12.dp, start = 12.dp)
                .widthIn(max = 340.dp)
                .auraFloatShadow(RoundedCornerShape(tokens.radii.lg))
                .clip(RoundedCornerShape(tokens.radii.lg))
                .background(tokens.colors.surface)
                .border(1.dp, tokens.colors.outline, RoundedCornerShape(tokens.radii.lg))
                // Swallow taps inside the card so they don't dismiss.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .heightIn(max = 460.dp)
                .verticalScroll(rememberScrollState())
                .padding(18.dp)
        ) {
            BasicText(title, style = AuraType.title.copy(color = tokens.colors.textPrimary))
            Spacer(Modifier.height(2.dp))
            BasicText(subtitle, style = AuraType.label.copy(color = tokens.colors.textSecondary))
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun AddButton(onClick: () -> Unit) {
    val tokens = Aura.tokens
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(tokens.colors.accent.copy(alpha = 0.16f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        AuraGlyph(Glyph.PLUS, tokens.colors.accent, Modifier.size(18.dp))
    }
}

@Composable
private fun PickerField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val tokens = Aura.tokens
    Box(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(tokens.radii.sm))
            .background(tokens.colors.background)
            .border(1.dp, tokens.colors.outline, RoundedCornerShape(tokens.radii.sm))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.text.isEmpty()) {
            BasicText(placeholder, style = AuraType.body.copy(color = tokens.colors.textSecondary))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = AuraType.body.copy(color = tokens.colors.textPrimary),
            cursorBrush = SolidColor(tokens.colors.accent),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
