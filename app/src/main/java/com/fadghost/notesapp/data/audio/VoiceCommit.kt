package com.fadghost.notesapp.data.audio

import com.fadghost.notesapp.data.repo.NotesRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Commits a finished voice transcript into a note (PLAN.md §5 result flow): appends
 * the transcript text to the note body and records the audio attachment anchored at
 * the transcript line start (the editor draws the circular chip there, PLAN.md §2.3).
 * Shared by the recording view-model (capture-sheet → new note) and the offline
 * [com.fadghost.notesapp.data.ai.work.TranscribeQueueWorker] so both produce an
 * identical body/attachment pairing.
 */
@Singleton
class VoiceCommit @Inject constructor(
    private val notes: NotesRepository,
    private val attachments: AudioAttachmentRepository
) {
    data class Committed(val noteId: Long, val transcriptStart: Int, val transcriptEnd: Int)

    /** Append [transcript] to note [noteId]'s body and record its audio [segments]. */
    suspend fun appendTranscript(
        noteId: Long,
        transcript: String,
        segments: List<RecordedSegment>,
        now: Long = System.currentTimeMillis()
    ): Committed? {
        val note = notes.getNote(noteId) ?: return null
        val sep = if (note.body.isBlank()) "" else "\n\n"
        val start = note.body.length + sep.length
        val end = start + transcript.length
        notes.saveNote(note.copy(body = note.body + sep + transcript, updatedAt = now))
        if (segments.isNotEmpty()) {
            attachments.record(noteId, segments, transcriptStart = start, transcriptEnd = end, now = now)
        }
        return Committed(noteId, start, end)
    }
}
