package com.fadghost.notesapp.data.attach

import android.content.Context
import com.fadghost.notesapp.data.db.dao.AttachmentDao
import com.fadghost.notesapp.data.db.entity.Attachment
import com.fadghost.notesapp.data.repo.NotesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** A just-removed attachment retained for the undo window (row + its file bytes). */
data class RemovedAttachment(val attachment: Attachment, val bytes: ByteArray?)

/**
 * Owns the disk<->DB relationship for note attachments (M-A). Storing writes the file
 * then the row; removing deletes the row + file and (for undo) hands the caller the
 * bytes so the exact same attachment — same id, so the body token needs no rewrite —
 * can be restored. Every mutation that changes searchable image text re-folds it into
 * the owning note's FTS row via [NotesRepository.reindexNoteFts]. File maths lives in
 * the pure [AttachmentStorage] helpers.
 */
@Singleton
class AttachmentRepository @Inject constructor(
    private val dao: AttachmentDao,
    private val notes: NotesRepository,
    @ApplicationContext private val context: Context
) {
    fun observeForNote(noteId: Long): Flow<List<Attachment>> = dao.observeForNote(noteId)
    fun observeById(id: Long): Flow<Attachment?> = dao.observeById(id)
    fun observeTotalBytes(): Flow<Long> = dao.observeTotalBytes()

    suspend fun forNote(noteId: Long): List<Attachment> = dao.forNote(noteId)
    suspend fun byId(id: Long): Attachment? = dao.byId(id)

    fun noteDir(noteId: Long): File = AttachmentStorage.noteDir(context.filesDir, noteId)
    fun noteBytes(noteId: Long): Long = AttachmentStorage.noteBytes(context.filesDir, noteId)

    /**
     * Persist [bytes] as an attachment on [noteId]. [kind] defaults to image when the
     * mime is an image type. Returns the inserted row (with its assigned id).
     */
    suspend fun store(
        noteId: Long,
        bytes: ByteArray,
        displayName: String,
        mime: String,
        annotatedOfId: Long? = null,
        kind: String = kindFor(mime),
        now: Long = System.currentTimeMillis()
    ): Attachment = withContext(Dispatchers.IO) {
        val ext = AttachmentStorage.extFor(displayName, mime)
        val file = AttachmentStorage.newFile(noteDir(noteId), ext)
        file.writeBytes(bytes)
        val row = Attachment(
            noteId = noteId,
            kind = kind,
            path = file.absolutePath,
            displayName = displayName.ifBlank { file.name },
            mime = mime,
            sizeBytes = bytes.size.toLong(),
            createdAt = now,
            annotatedOfId = annotatedOfId
        )
        val id = dao.insert(row)
        dao.byId(id) ?: row.copy(id = id)
    }

    /** Read an attachment's bytes (share / annotate load / undo retention). */
    suspend fun readBytes(att: Attachment): ByteArray? = withContext(Dispatchers.IO) {
        runCatching { File(att.path).readBytes() }.getOrNull()
    }

    /**
     * Remove an attachment, retaining its row + bytes so [restore] can undo it exactly.
     * Deletes the file and row; re-folds the note's FTS (drops the image's OCR text).
     */
    suspend fun removeForUndo(id: Long): RemovedAttachment? = withContext(Dispatchers.IO) {
        val row = dao.byId(id) ?: return@withContext null
        val bytes = runCatching { File(row.path).readBytes() }.getOrNull()
        runCatching { File(row.path).delete() }
        dao.deleteById(id)
        pruneEmptyDir(row.noteId)
        notes.reindexNoteFts(row.noteId)
        RemovedAttachment(row, bytes)
    }

    /** Undo a [removeForUndo]: rewrite the file and re-insert the row with its old id. */
    suspend fun restore(removed: RemovedAttachment) = withContext(Dispatchers.IO) {
        removed.bytes?.let { b ->
            val f = File(removed.attachment.path)
            f.parentFile?.mkdirs()
            runCatching { f.writeBytes(b) }
        }
        dao.insert(removed.attachment) // id != 0 -> Room keeps it; token stays valid
        notes.reindexNoteFts(removed.attachment.noteId)
    }

    /** Store the silent image-index result (P7) and re-fold it into the note's FTS. */
    suspend fun setIndex(id: Long, ocr: String?, description: String?) {
        dao.setIndex(id, ocr, description)
        dao.byId(id)?.let { notes.reindexNoteFts(it.noteId) }
    }

    suspend fun unindexedImages(): List<Attachment> = dao.unindexedImages()

    /**
     * Delete attachment files under the root that no live row references (M-A). Audio
     * files are skipped (see [AttachmentStorage.findOrphans]). Returns files removed.
     */
    suspend fun sweepOrphans(): Int = withContext(Dispatchers.IO) {
        val referenced = dao.allPaths().toSet()
        val root = AttachmentStorage.root(context.filesDir)
        val orphans = AttachmentStorage.findOrphans(root, referenced)
        orphans.forEach { runCatching { it.delete() } }
        orphans.size
    }

    private fun pruneEmptyDir(noteId: Long) {
        val dir = noteDir(noteId)
        runCatching { if (dir.listFiles()?.isEmpty() == true) dir.delete() }
    }

    companion object {
        fun kindFor(mime: String): String =
            if (mime.startsWith("image/")) Attachment.KIND_IMAGE else Attachment.KIND_FILE
    }
}
