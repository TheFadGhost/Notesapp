package com.fadghost.notesapp.ui.editor

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.defaultMinSize
import com.fadghost.notesapp.ui.components.AuraGlyph
import com.fadghost.notesapp.ui.components.Glyph
import com.fadghost.notesapp.ui.components.TagChip
import com.fadghost.notesapp.ui.theme.Aura
import com.fadghost.notesapp.ui.theme.AuraType
import com.fadghost.notesapp.util.UndoStack

/**
 * Markdown editor (PLAN.md §6): live-styled body, sticky toolbar, smart lists,
 * tappable checkboxes, undo/redo, tag/folder assignment. Autosave + draft
 * recovery live in [EditorViewModel].
 */
@Composable
fun EditorScreen(
    noteId: Long,
    onExit: () -> Unit,
    restoreDraft: com.fadghost.notesapp.data.prefs.DraftSnapshot? = null,
    viewModel: EditorViewModel = hiltViewModel()
) {
    val tokens = Aura.tokens
    val state by viewModel.state.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val allTags by viewModel.allTags.collectAsState()
    val noteTags by viewModel.noteTags.collectAsState()
    val haptics = LocalHapticFeedback.current
    val focus = LocalFocusManager.current
    val bodyFocus = remember { FocusRequester() }

    LaunchedEffect(noteId) { viewModel.open(noteId, restoreDraft) }

    var titleValue by remember { mutableStateOf(TextFieldValue("")) }
    var bodyValue by remember { mutableStateOf(TextFieldValue("")) }
    var initializedFor by remember { mutableStateOf(-1L) }
    var showPicker by remember { mutableStateOf(false) }
    var bodyLayout by remember { mutableStateOf<TextLayoutResult?>(null) }

    // Seed the fields once the VM has loaded (also runs after process death).
    LaunchedEffect(state.loaded, state.noteId) {
        if (state.loaded && initializedFor != state.noteId) {
            titleValue = TextFieldValue(state.initialTitle, TextRange(state.initialTitle.length))
            bodyValue = TextFieldValue(state.initialBody, TextRange(state.initialBody.length))
            initializedFor = state.noteId
            // New, blank note from the capture sheet: pop the keyboard immediately.
            if (state.noteId == 0L && state.initialBody.isEmpty() && state.initialTitle.isEmpty()) {
                runCatching { bodyFocus.requestFocus() }
            }
        }
    }

    fun applyBody(newValue: TextFieldValue, coalesce: UndoStack.CoalesceKey) {
        bodyValue = newValue
        viewModel.onBodyChanged(newValue.text, coalesce)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(tokens.colors.background)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 96.dp)
        ) {
            // Top bar.
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconAction(Glyph.BACK) {
                    focus.clearFocus()
                    viewModel.close()
                    onExit()
                }
                Spacer(Modifier.weight(1f))
                PillAction(
                    glyph = Glyph.FOLDER,
                    label = folders.firstOrNull { it.id == state.folderId }?.name ?: "Folder"
                ) { showPicker = true }
                Spacer(Modifier.width(8.dp))
                PillAction(glyph = Glyph.TAG, label = "Tags") { showPicker = true }
                Spacer(Modifier.width(8.dp))
                IconAction(Glyph.TRASH) {
                    viewModel.deleteNote { onExit() }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Title.
            Box {
                if (titleValue.text.isEmpty()) {
                    BasicText("Title", style = AuraType.title.copy(color = tokens.colors.textSecondary))
                }
                BasicTextField(
                    value = titleValue,
                    onValueChange = {
                        titleValue = it
                        viewModel.onTitleChanged(it.text)
                    },
                    singleLine = true,
                    textStyle = AuraType.title.copy(color = tokens.colors.textPrimary),
                    cursorBrush = SolidColor(tokens.colors.accent),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Assigned tags.
            if (noteTags.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                com.fadghost.notesapp.ui.components.FlowChips {
                    noteTags.forEach { TagChip(tag = it, selected = true) }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Body — live-styled markdown with tappable checkboxes + smart lists.
            Box {
                val transformation = remember(tokens) {
                    MarkdownVisualTransformation(
                        textColor = tokens.colors.textPrimary,
                        markerColor = tokens.colors.textSecondary,
                        accent = tokens.colors.accent
                    )
                }
                BasicTextField(
                    value = bodyValue,
                    onValueChange = { new ->
                        val smart = MarkdownEdits.onNewline(bodyValue, new)
                        val applied = smart ?: new
                        val key = when {
                            smart != null -> UndoStack.CoalesceKey.FORMATTING
                            applied.text.length > bodyValue.text.length -> UndoStack.CoalesceKey.TYPING
                            applied.text.length < bodyValue.text.length -> UndoStack.CoalesceKey.DELETING
                            else -> UndoStack.CoalesceKey.FORMATTING
                        }
                        applyBody(applied, key)
                    },
                    onTextLayout = { bodyLayout = it },
                    textStyle = AuraType.body.copy(color = tokens.colors.textPrimary),
                    cursorBrush = SolidColor(tokens.colors.accent),
                    visualTransformation = transformation,
                    modifier = Modifier
                        .focusRequester(bodyFocus)
                        .fillMaxWidth()
                        .heightForBody()
                        // Swipe-right on a line indents it (PLAN.md §6).
                        .pointerInput(Unit) {
                            var accX = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { accX = 0f },
                                onDragEnd = {
                                    if (accX > 90f) {
                                        applyBody(MarkdownEdits.indent(bodyValue), UndoStack.CoalesceKey.FORMATTING)
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                            ) { _, dragAmount -> accX += dragAmount }
                        }
                )
                // Checkbox tap targets (overlay small hit-boxes on the marker only).
                CheckboxOverlay(
                    text = bodyValue.text,
                    layout = bodyLayout,
                    onToggle = { offset ->
                        MarkdownEdits.toggleCheckboxAt(bodyValue.text, offset)?.let { toggled ->
                            applyBody(
                                TextFieldValue(toggled, bodyValue.selection),
                                UndoStack.CoalesceKey.FORMATTING
                            )
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
                )
            }
        }

        // Sticky formatting toolbar, docked above the keyboard.
        FormattingToolbar(
            canUndo = state.canUndo,
            canRedo = state.canRedo,
            onBold = { applyBody(MarkdownEdits.wrap(bodyValue, "**"), UndoStack.CoalesceKey.FORMATTING) },
            onItalic = { applyBody(MarkdownEdits.wrap(bodyValue, "*"), UndoStack.CoalesceKey.FORMATTING) },
            onHeading = { applyBody(MarkdownEdits.cycleHeading(bodyValue), UndoStack.CoalesceKey.FORMATTING) },
            onChecklist = { applyBody(MarkdownEdits.toggleChecklist(bodyValue), UndoStack.CoalesceKey.FORMATTING) },
            onBullet = { applyBody(MarkdownEdits.toggleBullet(bodyValue), UndoStack.CoalesceKey.FORMATTING) },
            onUndo = { viewModel.undo()?.let { bodyValue = TextFieldValue(it, TextRange(it.length)) } },
            onRedo = { viewModel.redo()?.let { bodyValue = TextFieldValue(it, TextRange(it.length)) } },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 12.dp)
        )

        if (showPicker) {
            TagFolderPicker(
                allTags = allTags,
                assignedTagIds = noteTags.map { it.id }.toSet(),
                folders = folders,
                currentFolderId = state.folderId,
                onToggleTag = viewModel::toggleTag,
                onCreateTag = viewModel::createAndAssignTag,
                onSelectFolder = viewModel::moveToFolder,
                onCreateFolder = viewModel::createFolderAndMove,
                onDismiss = { showPicker = false }
            )
        }
    }
}

/** Give the body a comfortable minimum tap area while still growing with content. */
private fun Modifier.heightForBody(): Modifier =
    this.defaultMinSize(minHeight = 320.dp)

@Composable
private fun CheckboxOverlay(
    text: String,
    layout: TextLayoutResult?,
    onToggle: (Int) -> Unit
) {
    val layoutResult = layout ?: return
    val density = LocalDensity.current
    val rects = remember(text, layoutResult) { checkboxRects(text, layoutResult) }
    rects.forEach { (offset, rect) ->
        with(density) {
            Box(
                Modifier
                    .offset(x = rect.left.toDp(), y = rect.top.toDp())
                    .size(width = (rect.width).toDp() + 8.dp, height = (rect.height).toDp())
                    .pointerInput(offset) {
                        detectTapGestures { onToggle(offset) }
                    }
            )
        }
    }
}

/** Bounding rects of the `[ ]` / `[x]` markers, one per checklist line. */
private fun checkboxRects(text: String, layout: TextLayoutResult): List<Pair<Int, Rect>> {
    val out = ArrayList<Pair<Int, Rect>>()
    var lineStart = 0
    val checklist = Regex("""^\s*[-*+] \[[ xX]] """)
    for (line in text.split("\n")) {
        val m = checklist.find(line)
        if (m != null) {
            val open = line.indexOf('[')
            val close = line.indexOf(']')
            if (open >= 0 && close > open) {
                val a = (lineStart + open).coerceIn(0, text.length)
                val b = (lineStart + close).coerceIn(0, text.length)
                runCatching {
                    val ra = layout.getBoundingBox(a)
                    val rb = layout.getBoundingBox(b)
                    out += (lineStart) to Rect(ra.left, ra.top, rb.right, rb.bottom)
                }
            }
        }
        lineStart += line.length + 1
    }
    return out
}

@Composable
private fun IconAction(glyph: Glyph, onClick: () -> Unit) {
    val tokens = Aura.tokens
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(tokens.radii.pill))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        AuraGlyph(glyph, tokens.colors.textPrimary, Modifier.size(22.dp))
    }
}

@Composable
private fun PillAction(glyph: Glyph, label: String, onClick: () -> Unit) {
    val tokens = Aura.tokens
    Row(
        Modifier
            .clip(RoundedCornerShape(tokens.radii.pill))
            .background(tokens.colors.surface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AuraGlyph(glyph, tokens.colors.textSecondary, Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        BasicText(label, style = AuraType.label.copy(color = tokens.colors.textPrimary))
    }
}
