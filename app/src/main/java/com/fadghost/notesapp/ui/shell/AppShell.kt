package com.fadghost.notesapp.ui.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.LaunchedEffect
import com.fadghost.notesapp.data.prefs.ThemeMode
import com.fadghost.notesapp.ui.capture.CaptureLaunch
import com.fadghost.notesapp.ui.capture.CaptureRequest
import com.fadghost.notesapp.ui.DraftRecoveryViewModel
import com.fadghost.notesapp.ui.diary.DiaryNavViewModel
import com.fadghost.notesapp.ui.diary.DiaryScreen
import com.fadghost.notesapp.ui.editor.EditorScreen
import com.fadghost.notesapp.ui.calendar.CalendarDeepLink
import com.fadghost.notesapp.ui.calendar.CalendarScreen
import com.fadghost.notesapp.ui.notes.NotesScreen
import com.fadghost.notesapp.ui.screens.SettingsScreen
import com.fadghost.notesapp.ui.theme.Aura
import com.fadghost.notesapp.ui.theme.AuraType

@Composable
fun AppShell(
    themeMode: ThemeMode,
    onSelectThemeMode: (ThemeMode) -> Unit,
    draftRecovery: DraftRecoveryViewModel = hiltViewModel(),
    diaryNav: DiaryNavViewModel = hiltViewModel(),
    captureVm: com.fadghost.notesapp.ui.capture.CaptureViewModel = hiltViewModel()
) {
    val tokens = Aura.tokens
    val reduceMotion = com.fadghost.notesapp.ui.theme.LocalReduceMotion.current
    var selectedTab by rememberSaveable { mutableStateOf(NavTab.NOTES) }
    var captureVisible by remember { mutableStateOf(false) }
    // Editor overlay: null == list; value == open note id (0 == new). Survives config change.
    var editorNoteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var restoringDraft by remember { mutableStateOf(false) }
    var showQuickReminder by remember { mutableStateOf(false) }
    var showVoiceCapture by remember { mutableStateOf(false) }

    // Capture paths (PLAN.md §6): tile / shortcuts / share → route into the shell.
    val captureRequest by CaptureLaunch.request.collectAsState()
    LaunchedEffect(captureRequest) {
        when (val req = captureRequest) {
            is CaptureRequest.NewNote -> { editorNoteId = 0L; restoringDraft = false }
            is CaptureRequest.Voice -> { editorNoteId = null; captureVisible = true }
            is CaptureRequest.TodayDiary -> { selectedTab = NavTab.DIARY; editorNoteId = null }
            is CaptureRequest.SharedText -> captureVm.createNoteFromText(req.text)
            null -> {}
        }
        if (captureRequest != null) CaptureLaunch.clear()
    }
    // Shared-text note created off-thread → open it in the editor.
    val sharedNoteId by captureVm.openNoteId.collectAsState()
    LaunchedEffect(sharedNoteId) {
        sharedNoteId?.let { id ->
            editorNoteId = id
            restoringDraft = false
            captureVm.consumeOpen()
        }
    }

    // Journaling-nudge deep link (PLAN.md §7): jump to the Diary tab when requested.
    val diaryRequests by diaryNav.openDiaryRequests.collectAsState()
    LaunchedEffect(diaryRequests) {
        if (diaryRequests > 0) {
            selectedTab = NavTab.DIARY
            editorNoteId = null
        }
    }

    // Reminder-notification deep link (PLAN.md §8): jump to Calendar; the screen
    // then opens the item's edit sheet from CalendarDeepLink.
    val calendarDeepLink by CalendarDeepLink.pendingReminderId.collectAsState()
    LaunchedEffect(calendarDeepLink) {
        if (calendarDeepLink != null) {
            selectedTab = NavTab.CALENDAR
            editorNoteId = null
        }
    }

    val recoverableDraft by draftRecovery.draft.collectAsState()

    val systemBars = WindowInsets.systemBars.asPaddingValues()
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.colors.background)
    ) {
        // Content layer (respects top inset; nav bar floats over the bottom).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = systemBars.calculateTopPadding())
        ) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn(com.fadghost.notesapp.ui.theme.MotionTokens.mediumFinite(reduceMotion))
                        .togetherWith(fadeOut(com.fadghost.notesapp.ui.theme.MotionTokens.mediumFinite(reduceMotion)))
                },
                label = "tab"
            ) { tab ->
                when (tab) {
                    NavTab.NOTES -> NotesScreen(onOpenNote = { editorNoteId = it })
                    NavTab.DIARY -> DiaryScreen()
                    NavTab.CALENDAR -> CalendarScreen()
                    NavTab.SETTINGS -> SettingsScreen(
                        currentMode = themeMode,
                        onSelectMode = onSelectThemeMode
                    )
                }
            }
        }

        // Floating translucent nav bar (hidden while the editor is open).
        AnimatedVisibility(
            visible = editorNoteId == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = navInset + 16.dp)
        ) {
            AuraNavBar(
                selected = selectedTab,
                onSelect = { selectedTab = it },
                onCapture = { captureVisible = true }
            )
        }

        CaptureSheet(
            visible = captureVisible,
            onDismiss = { captureVisible = false },
            onAction = { action ->
                when (action.label) {
                    // Wire "New note" to a blank editor with the keyboard up (PLAN.md §6.9).
                    "New note" -> editorNoteId = 0L
                    // Wire "New diary entry" to the Diary tab (today's entry is front-and-centre).
                    "New diary entry" -> { selectedTab = NavTab.DIARY; editorNoteId = null }
                    // Wire "Voice ramble" to the spring-up recording sheet (PLAN.md §5).
                    "Voice ramble" -> showVoiceCapture = true
                    // Wire "Quick reminder" to the minimal Aura dialog (PLAN.md §4/§8).
                    "Quick reminder" -> showQuickReminder = true
                }
            }
        )

        com.fadghost.notesapp.ui.reminder.QuickReminderDialog(
            visible = showQuickReminder,
            onDismiss = { showQuickReminder = false },
            onCreated = { showQuickReminder = false }
        )

        // Voice ramble from the capture sheet → transcribe into a fresh note, then open it.
        com.fadghost.notesapp.ui.voice.VoiceRecordingSheet(
            visible = showVoiceCapture,
            targetNoteId = 0L,
            appendMode = false,
            onDismiss = { showVoiceCapture = false },
            onNewNoteReady = { id -> showVoiceCapture = false; editorNoteId = id }
        )

        // Editor overlay above the whole shell.
        AnimatedVisibility(
            visible = editorNoteId != null,
            enter = slideInVertically(com.fadghost.notesapp.ui.theme.MotionTokens.mediumFinite(reduceMotion)) { it } +
                fadeIn(com.fadghost.notesapp.ui.theme.MotionTokens.fastFinite(reduceMotion)),
            exit = slideOutVertically(com.fadghost.notesapp.ui.theme.MotionTokens.mediumFinite(reduceMotion)) { it } +
                fadeOut(com.fadghost.notesapp.ui.theme.MotionTokens.fastFinite(reduceMotion)),
            modifier = Modifier.fillMaxSize()
        ) {
            val id = editorNoteId
            if (id != null) {
                EditorScreen(
                    noteId = id,
                    restoreDraft = if (restoringDraft) recoverableDraft else null,
                    onExit = { editorNoteId = null; restoringDraft = false },
                    onOpenAiSettings = {
                        editorNoteId = null; restoringDraft = false; selectedTab = NavTab.SETTINGS
                    }
                )
            }
        }

        // Draft crash-recovery prompt (PLAN.md §6): only on the list, when unsaved text exists.
        val draft = recoverableDraft
        if (editorNoteId == null && draft != null && !draft.isEmpty) {
            RestoreDraftBanner(
                title = draft.title.ifBlank { "Untitled note" },
                onRestore = { restoringDraft = true; editorNoteId = draft.noteId },
                onDismiss = { draftRecovery.discard() },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun RestoreDraftBanner(
    title: String,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = Aura.tokens
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(tokens.radii.md))
            .background(tokens.colors.surfaceTranslucent)
            .border(1.dp, tokens.colors.outline, RoundedCornerShape(tokens.radii.md))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            BasicText("Restore unsaved note", style = AuraType.body.copy(color = tokens.colors.textPrimary))
            BasicText(title, style = AuraType.label.copy(color = tokens.colors.textSecondary))
        }
        BannerButton("Restore", tokens.colors.accent, onRestore)
        Spacer(Modifier.width(6.dp))
        BannerButton("Dismiss", tokens.colors.textSecondary, onDismiss)
    }
}

@Composable
private fun BannerButton(label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    val tokens = Aura.tokens
    BasicText(
        text = label,
        style = AuraType.label.copy(color = color),
        modifier = Modifier
            .clip(RoundedCornerShape(tokens.radii.pill))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}
