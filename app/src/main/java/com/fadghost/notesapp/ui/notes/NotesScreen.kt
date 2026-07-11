package com.fadghost.notesapp.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fadghost.notesapp.ui.components.AuraEmptyState
import com.fadghost.notesapp.ui.components.AuraGlyph
import com.fadghost.notesapp.ui.components.AuraUndoSnackbar
import com.fadghost.notesapp.ui.components.EmptyGlyph
import com.fadghost.notesapp.ui.components.Glyph
import com.fadghost.notesapp.ui.components.PlainChip
import com.fadghost.notesapp.ui.theme.Aura
import com.fadghost.notesapp.ui.theme.AuraType

/**
 * Notes list/grid (PLAN.md §6): search, filter chips, pinned-first cards with
 * swipe actions + context menu, universal undo snackbar.
 */
@Composable
fun NotesScreen(
    onOpenNote: (Long) -> Unit,
    viewModel: NotesViewModel = hiltViewModel()
) {
    val tokens = Aura.tokens
    val notes by viewModel.notes.collectAsState()
    val query by viewModel.query.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val isGrid by viewModel.isGrid.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val snackbar by viewModel.snackbar.collectAsState()

    var menuFor by remember { mutableStateOf<NoteCardUi?>(null) }
    var movingNote by remember { mutableStateOf<NoteCardUi?>(null) }
    var managingTag by remember { mutableStateOf<com.fadghost.notesapp.data.db.entity.Tag?>(null) }

    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // Header + grid/list toggle.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText("Notes", style = AuraType.title.copy(color = tokens.colors.textPrimary))
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(tokens.radii.pill))
                        .background(tokens.colors.surface)
                        .border(1.dp, tokens.colors.outline, RoundedCornerShape(tokens.radii.pill))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = viewModel::toggleGrid
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    AuraGlyph(if (isGrid) Glyph.LIST else Glyph.GRID, tokens.colors.textPrimary, Modifier.size(20.dp))
                }
            }

            SearchBar(query = query, onQueryChange = viewModel::setQuery)
            Spacer(Modifier.height(12.dp))
            FilterBar(
                filter = filter,
                folders = folders,
                tags = tags,
                onSelect = viewModel::setFilter,
                onManageTag = { managingTag = it }
            )
            Spacer(Modifier.height(12.dp))

            if (notes.isEmpty()) {
                EmptyNotes(filter = filter, searching = query.isNotBlank())
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(if (isGrid) 2 else 1),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 4.dp,
                        bottom = navInset + 110.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(notes, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            index = notes.indexOf(note),
                            onOpen = { if (!note.inTrash) onOpenNote(note.id) else menuFor = note },
                            onLongPress = { menuFor = note },
                            onPin = { viewModel.togglePin(note.id, note.pinned) },
                            onArchive = { if (note.archived) viewModel.unarchive(note.id) else viewModel.archive(note.id) },
                            onDelete = { if (note.inTrash) viewModel.deleteForever(note.id) else viewModel.delete(note.id) },
                            modifier = Modifier.animateItem(),
                            query = query
                        )
                    }
                }
            }
        }

        AuraUndoSnackbar(
            message = snackbar,
            onAction = viewModel::undoSnackbar,
            onDismiss = viewModel::dismissSnackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = navInset + 96.dp)
        )

        menuFor?.let { note ->
            NoteContextMenu(
                items = contextItemsFor(note, viewModel) { movingNote = it },
                onDismiss = { menuFor = null }
            )
        }

        movingNote?.let { note ->
            FolderMoveOverlay(
                folders = folders,
                onPick = { folderId -> viewModel.moveToFolder(note.id, folderId); movingNote = null },
                onDismiss = { movingNote = null }
            )
        }

        managingTag?.let { tag ->
            TagManagerOverlay(
                tag = tag,
                otherTags = tags.filter { it.id != tag.id },
                onRename = { viewModel.renameTag(tag.id, it); managingTag = null },
                onRecolor = { viewModel.recolorTag(tag.id, it); managingTag = null },
                onDelete = { viewModel.deleteTag(tag.id); managingTag = null },
                onMerge = { target -> viewModel.mergeTag(tag.id, target); managingTag = null },
                onDismiss = { managingTag = null }
            )
        }
    }
}

private fun contextItemsFor(
    note: NoteCardUi,
    vm: NotesViewModel,
    onMove: (NoteCardUi) -> Unit
): List<ContextMenuItem> = if (note.inTrash) {
    listOf(
        ContextMenuItem(Glyph.RESTORE, "Restore") { vm.restore(note.id) },
        ContextMenuItem(Glyph.TRASH, "Delete forever", danger = true) { vm.deleteForever(note.id) }
    )
} else {
    listOf(
        ContextMenuItem(Glyph.PIN, if (note.pinned) "Unpin" else "Pin") { vm.togglePin(note.id, note.pinned) },
        ContextMenuItem(Glyph.ARCHIVE, if (note.archived) "Unarchive" else "Archive") {
            if (note.archived) vm.unarchive(note.id) else vm.archive(note.id)
        },
        ContextMenuItem(Glyph.DUPLICATE, "Duplicate") { vm.duplicate(note.id) },
        ContextMenuItem(Glyph.FOLDER, "Move to folder") { onMove(note) },
        ContextMenuItem(Glyph.TRASH, "Delete", danger = true) { vm.delete(note.id) }
    )
}

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit) {
    val tokens = Aura.tokens
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(46.dp)
            .clip(RoundedCornerShape(tokens.radii.pill))
            .background(tokens.colors.surface)
            .border(1.dp, tokens.colors.outline, RoundedCornerShape(tokens.radii.pill))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AuraGlyph(Glyph.SEARCH, tokens.colors.textSecondary, Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                BasicText("Search notes", style = AuraType.body.copy(color = tokens.colors.textSecondary))
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = AuraType.body.copy(color = tokens.colors.textPrimary),
                cursorBrush = SolidColor(tokens.colors.accent),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (query.isNotEmpty()) {
            Box(
                Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(tokens.radii.pill))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onQueryChange("") }
                    ),
                contentAlignment = Alignment.Center
            ) { AuraGlyph(Glyph.CLOSE, tokens.colors.textSecondary, Modifier.size(14.dp)) }
        }
    }
}

@Composable
private fun EmptyNotes(filter: NoteFilter, searching: Boolean) {
    val (glyph, title, subtitle) = when {
        searching -> Triple(EmptyGlyph.SEARCH, "No matches", "Try a different search.")
        filter is NoteFilter.Trash -> Triple(EmptyGlyph.TRASH, "Trash is empty", "Deleted notes rest here for 30 days.")
        filter is NoteFilter.Archived -> Triple(EmptyGlyph.ARCHIVE, "Nothing archived", "Archived notes stay out of the way.")
        else -> Triple(EmptyGlyph.NOTES, "No notes yet", "Tap + to capture your first note.")
    }
    AuraEmptyState(glyph = glyph, title = title, subtitle = subtitle)
}

@Composable
private fun FolderMoveOverlay(
    folders: List<com.fadghost.notesapp.data.db.entity.Folder>,
    onPick: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = Aura.tokens
    Box(
        Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = tokens.elevation.scrim))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .padding(24.dp)
                .clip(RoundedCornerShape(tokens.radii.lg))
                .background(tokens.colors.surface)
                .border(1.dp, tokens.colors.outline, RoundedCornerShape(tokens.radii.lg))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .padding(20.dp)
        ) {
            BasicText("Move to folder", style = AuraType.label.copy(color = tokens.colors.textSecondary))
            Spacer(Modifier.height(12.dp))
            com.fadghost.notesapp.ui.components.FlowChips {
                PlainChip("None", selected = false) { onPick(null) }
                folders.forEach { f -> PlainChip(f.name, selected = false) { onPick(f.id) } }
            }
        }
    }
}
