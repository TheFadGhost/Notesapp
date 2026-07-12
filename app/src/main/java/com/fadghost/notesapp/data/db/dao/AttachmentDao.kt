package com.fadghost.notesapp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.fadghost.notesapp.data.db.entity.Attachment
import kotlinx.coroutines.flow.Flow

/**
 * Reads/writes for note attachments (schema v7, M-A). The repository funnels file
 * deletion alongside row deletion so disk and DB stay in sync; [allPaths] backs the
 * storage total and the orphan-file sweep, and [searchTextForNote] feeds the owning
 * note's FTS row with image OCR/description text.
 */
@Dao
interface AttachmentDao {

    @Insert
    suspend fun insert(attachment: Attachment): Long

    @Update
    suspend fun update(attachment: Attachment)

    @Query("SELECT * FROM attachments WHERE noteId = :noteId ORDER BY createdAt, id")
    fun observeForNote(noteId: Long): Flow<List<Attachment>>

    @Query("SELECT * FROM attachments WHERE noteId = :noteId ORDER BY createdAt, id")
    suspend fun forNote(noteId: Long): List<Attachment>

    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun byId(id: Long): Attachment?

    @Query("SELECT * FROM attachments WHERE id = :id")
    fun observeById(id: Long): Flow<Attachment?>

    @Query("SELECT * FROM attachments")
    suspend fun all(): List<Attachment>

    @Query("SELECT path FROM attachments")
    suspend fun allPaths(): List<String>

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM attachments")
    fun observeTotalBytes(): Flow<Long>

    /** Images that have not been indexed yet (P7 backfill). */
    @Query("SELECT * FROM attachments WHERE kind = 'image' AND ocrText IS NULL AND description IS NULL")
    suspend fun unindexedImages(): List<Attachment>

    /** Store the image-index result (P7); called silently from the background job. */
    @Query("UPDATE attachments SET ocrText = :ocr, description = :description WHERE id = :id")
    suspend fun setIndex(id: Long, ocr: String?, description: String?)

    /** Concatenated OCR + description for a note, for folding into its FTS row. */
    @Query(
        "SELECT COALESCE(ocrText, '') || ' ' || COALESCE(description, '') " +
            "FROM attachments WHERE noteId = :noteId"
    )
    suspend fun indexTextForNote(noteId: Long): List<String>

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM attachments WHERE noteId = :noteId")
    suspend fun deleteForNote(noteId: Long)
}
