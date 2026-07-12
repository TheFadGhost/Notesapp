package com.fadghost.notesapp.ui.capture

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fadghost.notesapp.data.attach.AttachmentIngest
import com.fadghost.notesapp.data.db.entity.Note
import com.fadghost.notesapp.data.repo.NotesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the shared-text capture path (PLAN.md §6): persists a note from ACTION_SEND /
 * PROCESS_TEXT content and emits its id so the shell can open it in the editor. The
 * DB write happens off the main thread here rather than in MainActivity.onCreate.
 */
@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val repo: NotesRepository,
    private val ingest: AttachmentIngest
) : ViewModel() {

    private val _openNoteId = MutableStateFlow<Long?>(null)
    val openNoteId: StateFlow<Long?> = _openNoteId.asStateFlow()

    fun createNoteFromText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            // First line becomes the title (capped); the rest is the body.
            val firstLine = trimmed.lineSequence().firstOrNull().orEmpty().take(80)
            val id = repo.saveNote(
                Note(
                    title = firstLine,
                    body = trimmed,
                    createdAt = now,
                    updatedAt = now
                )
            )
            _openNoteId.value = id
        }
    }

    /**
     * Create a note from shared image/file content (M-A): persist the note first (its
     * id anchors the attachment dir), ingest each uri, then write a body of one
     * `[[att:<id>]]` token per attachment so they render as chips. Emits the note id.
     */
    fun createNoteWithAttachments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val noteId = repo.saveNote(Note(title = "", body = "", createdAt = now, updatedAt = now))
            if (noteId <= 0) return@launch
            val body = StringBuilder()
            uris.forEach { uri ->
                ingest.ingest(noteId, uri)?.let { att -> body.append("[[att:${att.id}]]\n") }
            }
            repo.getNote(noteId)?.let { note ->
                repo.saveNote(note.copy(body = body.toString().trimEnd(), updatedAt = System.currentTimeMillis()))
            }
            _openNoteId.value = noteId
        }
    }

    fun consumeOpen() {
        _openNoteId.value = null
    }
}
