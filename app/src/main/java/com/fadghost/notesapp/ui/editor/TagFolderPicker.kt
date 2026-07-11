package com.fadghost.notesapp.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
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

/**
 * Overlay picker for assigning tags (with accent colours) and a folder to the
 * open note (PLAN.md §6). Custom Aura surface; not a Material dialog.
 */
@Composable
fun TagFolderPicker(
    allTags: List<Tag>,
    assignedTagIds: Set<Long>,
    folders: List<Folder>,
    currentFolderId: Long?,
    onToggleTag: (Long) -> Unit,
    onCreateTag: (String, Int) -> Unit,
    onSelectFolder: (Long?) -> Unit,
    onCreateFolder: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = Aura.tokens
    var newTag by remember { mutableStateOf(TextFieldValue("")) }
    var newFolder by remember { mutableStateOf(TextFieldValue("")) }
    var colorIndex by remember { mutableStateOf(0) }

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
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .wrapContentHeight()
                .clip(RoundedCornerShape(topStart = tokens.radii.lg, topEnd = tokens.radii.lg))
                .background(tokens.colors.surface)
                .border(
                    1.dp,
                    tokens.colors.outline,
                    RoundedCornerShape(topStart = tokens.radii.lg, topEnd = tokens.radii.lg)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            BasicText("Tags", style = AuraType.label.copy(color = tokens.colors.textSecondary))
            Spacer(Modifier.height(10.dp))
            FlowChips {
                allTags.forEach { tag ->
                    val selected = tag.id in assignedTagIds
                    TagChip(tag = tag, selected = selected, onClick = { onToggleTag(tag.id) })
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

            Spacer(Modifier.height(20.dp))
            BasicText("Folder", style = AuraType.label.copy(color = tokens.colors.textSecondary))
            Spacer(Modifier.height(10.dp))
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
            Spacer(Modifier.height(12.dp))
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
