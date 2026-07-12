package com.fadghost.notesapp.data.repo

import android.content.Context
import androidx.room.withTransaction
import com.fadghost.notesapp.data.attach.AttachmentStorage
import com.fadghost.notesapp.data.backup.BackupAttachment
import com.fadghost.notesapp.data.backup.BackupData
import com.fadghost.notesapp.data.backup.BackupFolder
import com.fadghost.notesapp.data.backup.BackupNote
import com.fadghost.notesapp.data.backup.BackupTag
import com.fadghost.notesapp.data.backup.ImportMode
import com.fadghost.notesapp.data.db.NotesDatabase
import com.fadghost.notesapp.data.db.dao.AttachmentDao
import com.fadghost.notesapp.data.db.dao.FolderDao
import com.fadghost.notesapp.data.db.dao.NoteDao
import com.fadghost.notesapp.data.db.dao.NoteTagRow
import com.fadghost.notesapp.data.db.dao.TagDao
import com.fadghost.notesapp.data.db.entity.Attachment
import com.fadghost.notesapp.data.db.entity.Folder
import com.fadghost.notesapp.data.db.entity.Note
import com.fadghost.notesapp.data.db.entity.NoteTagCrossRef
import com.fadghost.notesapp.data.db.entity.Tag
import com.fadghost.notesapp.util.Markdown
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for notes, tags, folders and the FTS index. Because the
 * FTS5 table is now Kotlin-managed (see [com.fadghost.notesapp.data.db.NotesFts]),
 * every write that changes searchable text funnels through here so the index
 * stays in sync with markdown-stripped content.
 */
@Singleton
class NotesRepository @Inject constructor(
    private val db: NotesDatabase,
    private val noteDao: NoteDao,
    private val tagDao: TagDao,
    private val folderDao: FolderDao,
    private val attachmentDao: AttachmentDao,
    @ApplicationContext private val context: Context
) {
    // --- Observation ------------------------------------------------------------

    fun observeActive(): Flow<List<Note>> = noteDao.observeActive()
    fun observeArchived(): Flow<List<Note>> = noteDao.observeArchived()
    fun observeTrash(): Flow<List<Note>> = noteDao.observeTrash()
    fun observeByFolder(folderId: Long): Flow<List<Note>> = noteDao.observeByFolder(folderId)
    fun observeByTag(tagId: Long): Flow<List<Note>> = noteDao.observeByTag(tagId)
    fun observeUntagged(): Flow<List<Note>> = noteDao.observeUntagged()
    fun observeNote(id: Long): Flow<Note?> = noteDao.observeById(id)
    fun search(match: String): Flow<List<Note>> = noteDao.search(match)

    fun observeFolders(): Flow<List<Folder>> = folderDao.observeAll()
    fun observeTags(): Flow<List<Tag>> = tagDao.observeAll()
    fun observeAllNoteTags(): Flow<List<NoteTagRow>> = tagDao.observeAllNoteTags()
    fun observeTagsForNote(noteId: Long): Flow<List<Tag>> = tagDao.observeTagsForNote(noteId)

    suspend fun getNote(id: Long): Note? = noteDao.getById(id)

    // --- Note writes ------------------------------------------------------------

    /**
     * Insert or update a note and mirror stripped text into the FTS index. Runs on
     * [Dispatchers.IO] and wraps the upsert + FTS write in a single transaction so the
     * synchronous `execSQL`/Markdown-strip work never lands on the caller's (Main)
     * dispatcher during the 500ms autosave, and the note and its index stay atomic
     * (audit H2/L1).
     */
    suspend fun saveNote(note: Note): Long = withContext(Dispatchers.IO) {
        db.withTransaction {
            // For an existing note use UPDATE, never INSERT-OR-REPLACE: REPLACE deletes
            // the row and re-inserts it, which fires the ON DELETE CASCADE and wipes the
            // note's attachment / audio child rows on every autosave (M-A regression fix).
            val realId = if (note.id == 0L) noteDao.upsert(note) else { noteDao.update(note); note.id }
            syncFts(realId, note.title, note.body)
            realId
        }
    }

    suspend fun setPinned(id: Long, pinned: Boolean, now: Long = System.currentTimeMillis()) =
        noteDao.setPinned(id, pinned, now)

    suspend fun setArchived(id: Long, archived: Boolean, now: Long = System.currentTimeMillis()) =
        noteDao.setArchived(id, archived, now)

    /** Soft-delete into the 30-day trash (PLAN.md §6/§7). */
    suspend fun softDelete(id: Long, now: Long = System.currentTimeMillis()) =
        noteDao.setDeletedAt(id, now, now)

    suspend fun restore(id: Long, now: Long = System.currentTimeMillis()) =
        noteDao.setDeletedAt(id, null, now)

    suspend fun moveToFolder(id: Long, folderId: Long?, now: Long = System.currentTimeMillis()) =
        noteDao.moveToFolder(id, folderId, now)

    suspend fun duplicate(id: Long, now: Long = System.currentTimeMillis()): Long? {
        val src = noteDao.getById(id) ?: return null
        val copy = src.copy(
            id = 0,
            title = if (src.title.isBlank()) src.title else "${src.title} (copy)",
            createdAt = now,
            updatedAt = now,
            pinned = false,
            deletedAt = null
        )
        val newId = saveNote(copy)
        tagDao.tagsForNote(id).forEach { tagDao.link(NoteTagCrossRef(newId, it.id)) }
        return newId
    }

    /** Permanently remove a note, its FTS row and any orphaned attachment files. */
    suspend fun hardDelete(id: Long) {
        withContext(Dispatchers.IO) {
            // Row + index removal atomic and off the caller's dispatcher (audit H2/L1).
            db.withTransaction {
                noteDao.hardDelete(id)
                deleteFts(id)
            }
            // File IO stays outside the DB transaction.
            attachmentDirFor(id).takeIf { it.exists() }?.deleteRecursively()
        }
    }

    /**
     * Hard-delete a note only if it is still completely empty. Used to clean up the
     * placeholder note created up front for a voice ramble the user then discarded
     * (PLAN.md §5), without ever removing a note the user actually typed into.
     */
    suspend fun hardDeleteIfEmpty(id: Long) {
        val n = noteDao.getById(id) ?: return
        if (n.title.isBlank() && n.body.isBlank()) hardDelete(id)
    }

    /** Purge trash older than [cutoff]; returns how many notes were removed. */
    suspend fun purgeExpiredTrash(cutoff: Long): Int {
        val ids = noteDao.expiredTrashIds(cutoff)
        ids.forEach { hardDelete(it) }
        sweepOrphanAttachments()
        return ids.size
    }

    // --- Tags -------------------------------------------------------------------

    suspend fun setTagsForNote(noteId: Long, tagIds: Collection<Long>) {
        db.withTransaction {
            tagDao.clearTagsForNote(noteId)
            tagIds.forEach { tagDao.link(NoteTagCrossRef(noteId, it)) }
        }
    }

    suspend fun createTag(name: String, color: Int): Long {
        val existing = tagDao.getByName(name)
        return existing?.id ?: tagDao.upsert(Tag(name = name.trim(), color = color))
    }

    suspend fun renameTag(id: Long, name: String) = tagDao.rename(id, name.trim())
    suspend fun setTagColor(id: Long, color: Int) = tagDao.setColor(id, color)

    suspend fun deleteTag(id: Long) {
        db.withTransaction {
            tagDao.deleteLinksForTag(id)
            tagDao.deleteById(id)
        }
    }

    /** Merge [sourceId] into [targetId]: relink every note, then drop the source. */
    suspend fun mergeTags(sourceId: Long, targetId: Long) {
        if (sourceId == targetId) return
        db.withTransaction {
            tagDao.reassignLinks(sourceId, targetId)
            tagDao.deleteLinksForTag(sourceId)
            tagDao.deleteById(sourceId)
        }
    }

    // --- Folders ----------------------------------------------------------------

    suspend fun createFolder(name: String): Long {
        val existing = folderDao.getByName(name)
        return existing?.id ?: folderDao.upsert(Folder(name = name.trim(), createdAt = System.currentTimeMillis()))
    }

    suspend fun renameFolder(id: Long, name: String) = folderDao.rename(id, name.trim())
    suspend fun deleteFolder(id: Long) = folderDao.deleteById(id)

    // --- Backup / restore -------------------------------------------------------

    suspend fun buildBackup(): BackupData {
        val notes = noteDao.allForExport()
        val exportedIds = notes.map { it.id }.toHashSet()
        val folders = folderDao.all()
        val folderById = folders.associateBy { it.id }
        val backupNotes = notes.map { n ->
            BackupNote(
                id = n.id,
                title = n.title,
                body = n.body,
                createdAt = n.createdAt,
                updatedAt = n.updatedAt,
                pinned = n.pinned,
                archived = n.archived,
                folderName = n.folderId?.let { folderById[it]?.name },
                tags = tagDao.tagsForNote(n.id).map { it.name }
            )
        }
        val backupAttachments = attachmentDao.all()
            .filter { it.noteId in exportedIds }
            .map { a -> BackupAttachment(
                id = a.id,
                noteId = a.noteId,
                kind = a.kind,
                displayName = a.displayName,
                mime = a.mime,
                sizeBytes = a.sizeBytes,
                createdAt = a.createdAt,
                annotatedOfId = a.annotatedOfId,
                ocrText = a.ocrText,
                description = a.description,
                zipPath = attachmentZipPath(a.noteId, a.path)
            ) }
        return BackupData(
            notes = backupNotes,
            folders = folders.map { BackupFolder(it.name) },
            tags = tagDao.all().map { BackupTag(it.name, it.color) },
            attachments = backupAttachments
        )
    }

    /** ZIP-relative path -> file bytes for every exported attachment (M-A). */
    suspend fun exportAttachmentBytes(): Map<String, ByteArray> = withContext(Dispatchers.IO) {
        val exportedIds = noteDao.allForExport().map { it.id }.toHashSet()
        attachmentDao.all()
            .filter { it.noteId in exportedIds }
            .mapNotNull { a ->
                val bytes = runCatching { File(a.path).readBytes() }.getOrNull() ?: return@mapNotNull null
                attachmentZipPath(a.noteId, a.path) to bytes
            }.toMap()
    }

    private fun attachmentZipPath(noteId: Long, path: String): String =
        "attachments/$noteId/${File(path).name}"

    /** Apply an imported backup. REPLACE wipes existing notes first; MERGE appends. */
    suspend fun importBackup(
        data: BackupData,
        mode: ImportMode,
        attachmentFiles: Map<String, ByteArray> = emptyMap()
    ) {
        db.withTransaction {
            if (mode == ImportMode.REPLACE) {
                noteDao.allForExport().forEach { noteDao.hardDelete(it.id) }
                folderDao.all().forEach { folderDao.deleteById(it.id) }
                tagDao.all().forEach { tagDao.deleteById(it.id) }
            }
            val folderIds = HashMap<String, Long>()
            data.folders.forEach { folderIds[it.name] = createFolder(it.name) }
            val tagIds = HashMap<String, Long>()
            data.tags.forEach { tagIds[it.name] = createTag(it.name, it.color) }

            val now = System.currentTimeMillis()
            val noteIdMap = HashMap<Long, Long>() // old backup note id -> new id
            data.notes.forEach { bn ->
                val folderId = bn.folderName?.let { folderIds[it] ?: createFolder(it) }
                val newId = saveNote(
                    Note(
                        id = 0,
                        title = bn.title,
                        body = bn.body,
                        createdAt = bn.createdAt.takeIf { it > 0 } ?: now,
                        updatedAt = bn.updatedAt.takeIf { it > 0 } ?: now,
                        pinned = bn.pinned,
                        archived = bn.archived,
                        deletedAt = null,
                        folderId = folderId
                    )
                )
                noteIdMap[bn.id] = newId
                bn.tags.forEach { tagName ->
                    val tid = tagIds[tagName] ?: createTag(tagName, 0).also { tagIds[tagName] = it }
                    tagDao.link(NoteTagCrossRef(newId, tid))
                }
            }

            // Restore attachment files + rows, remapping every id (M-A). Note bodies'
            // `[[att:<id>]]` tokens are rewritten so they still point at the right row.
            if (data.attachments.isNotEmpty()) {
                restoreAttachments(data, attachmentFiles, noteIdMap, now)
            }
        }
    }

    /** Rebuild attachment files + rows and repoint note tokens; called inside the txn. */
    private suspend fun restoreAttachments(
        data: BackupData,
        attachmentFiles: Map<String, ByteArray>,
        noteIdMap: Map<Long, Long>,
        now: Long
    ) {
        val attIdMap = HashMap<Long, Long>() // old attachment id -> new id
        val notesWithAttachments = HashSet<Long>()
        for (ba in data.attachments) {
            val newNoteId = noteIdMap[ba.noteId] ?: continue
            val bytes = attachmentFiles[ba.zipPath] ?: continue
            val ext = AttachmentStorage.extFor(ba.displayName, ba.mime)
            val file = AttachmentStorage.newFile(AttachmentStorage.noteDir(context.filesDir, newNoteId), ext)
            runCatching { file.writeBytes(bytes) }
            val newId = attachmentDao.insert(
                Attachment(
                    id = 0,
                    noteId = newNoteId,
                    kind = ba.kind,
                    path = file.absolutePath,
                    displayName = ba.displayName,
                    mime = ba.mime,
                    sizeBytes = ba.sizeBytes.takeIf { it > 0 } ?: bytes.size.toLong(),
                    createdAt = ba.createdAt.takeIf { it > 0 } ?: now,
                    annotatedOfId = null, // patched in the second pass once all ids are known
                    ocrText = ba.ocrText,
                    description = ba.description
                )
            )
            attIdMap[ba.id] = newId
            notesWithAttachments += newNoteId
        }
        // Second pass: repoint annotatedOfId now that every id is mapped.
        for (ba in data.attachments) {
            val newId = attIdMap[ba.id] ?: continue
            val newAnnotatedOf = ba.annotatedOfId?.let { attIdMap[it] } ?: continue
            attachmentDao.byId(newId)?.let { attachmentDao.update(it.copy(annotatedOfId = newAnnotatedOf)) }
        }
        // Rewrite `[[att:<old>]]` -> `[[att:<new>]]` in each restored note body.
        if (attIdMap.isNotEmpty()) {
            val token = Regex("""\[\[att:(\d+)]]""")
            for (bn in data.notes) {
                val newNoteId = noteIdMap[bn.id] ?: continue
                if (!token.containsMatchIn(bn.body)) {
                    if (newNoteId in notesWithAttachments) reindexBodyFts(newNoteId)
                    continue
                }
                val rewritten = token.replace(bn.body) { m ->
                    val mapped = attIdMap[m.groupValues[1].toLong()]
                    if (mapped != null) "[[att:$mapped]]" else m.value
                }
                if (rewritten != bn.body) {
                    noteDao.getById(newNoteId)?.let { note ->
                        noteDao.update(note.copy(body = rewritten))
                        syncFts(newNoteId, note.title, rewritten)
                    }
                } else if (newNoteId in notesWithAttachments) {
                    reindexBodyFts(newNoteId)
                }
            }
        }
    }

    /** Re-fold a note's FTS row (title/body + attachment index text) inside the txn. */
    private fun reindexBodyFts(noteId: Long) {
        // noteDao.getById is suspend; caller is already suspend and inside the txn.
        // Use a lightweight raw read to avoid changing signatures.
        val wdb = db.openHelper.writableDatabase
        wdb.query("SELECT title, body FROM Note WHERE id = ?", arrayOf<Any>(noteId)).use { c ->
            if (c.moveToNext()) syncFts(noteId, c.getString(0) ?: "", c.getString(1) ?: "")
        }
    }

    // --- FTS index plumbing -----------------------------------------------------

    private fun syncFts(id: Long, title: String, body: String) {
        val wdb = db.openHelper.writableDatabase
        wdb.execSQL("DELETE FROM note_fts WHERE rowid = ?", arrayOf<Any>(id))
        // Fold any image OCR / description text (M-A P7) into the body column so a note
        // is findable by what its images say. Stored markdown-stripped, same as body.
        val indexed = Markdown.strip(body) + attachmentIndexText(id)
        wdb.execSQL(
            "INSERT INTO note_fts(rowid, title, body) VALUES (?, ?, ?)",
            arrayOf<Any>(id, Markdown.stripInline(title), indexed)
        )
    }

    /**
     * Re-fold a note's searchable text (M-A): re-reads the note plus its attachment
     * OCR/description and rewrites the FTS row. Called after an image-index result lands
     * or an attachment is removed, so search reflects the current image text.
     */
    suspend fun reindexNoteFts(noteId: Long) = withContext(Dispatchers.IO) {
        val note = noteDao.getById(noteId) ?: return@withContext
        db.withTransaction { syncFts(noteId, note.title, note.body) }
    }

    /** Concatenated, markdown-stripped OCR + description for a note's images (may be ""). */
    private fun attachmentIndexText(id: Long): String {
        val wdb = db.openHelper.writableDatabase
        val sb = StringBuilder()
        runCatching {
            wdb.query(
                "SELECT COALESCE(ocrText, '') || ' ' || COALESCE(description, '') " +
                    "FROM attachments WHERE noteId = ?",
                arrayOf<Any>(id)
            ).use { c ->
                while (c.moveToNext()) {
                    val t = c.getString(0)
                    if (!t.isNullOrBlank()) sb.append(' ').append(t)
                }
            }
        }
        return sb.toString()
    }

    private fun deleteFts(id: Long) {
        db.openHelper.writableDatabase.execSQL("DELETE FROM note_fts WHERE rowid = ?", arrayOf<Any>(id))
    }

    // --- Attachments (dir scaffolding; real files land in later milestones) -----

    private fun attachmentsRoot(): File = File(context.filesDir, "attachments")

    private fun attachmentDirFor(noteId: Long): File = File(attachmentsRoot(), noteId.toString())

    /** Delete per-note attachment folders whose note no longer exists. */
    private suspend fun sweepOrphanAttachments() {
        val root = attachmentsRoot()
        if (!root.isDirectory) return
        root.listFiles()?.forEach { dir ->
            val id = dir.name.toLongOrNull() ?: return@forEach
            if (noteDao.getById(id) == null) dir.deleteRecursively()
        }
    }
}
