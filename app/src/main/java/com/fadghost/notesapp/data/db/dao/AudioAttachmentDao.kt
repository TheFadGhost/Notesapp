package com.fadghost.notesapp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.fadghost.notesapp.data.db.entity.AudioAttachment
import kotlinx.coroutines.flow.Flow

/**
 * Reads/writes for voice-note audio attachments (schema v5, PLAN.md §5/§6). The
 * repository funnels file deletion alongside row deletion so disk and DB stay in
 * sync; [allPaths] backs the storage total and the orphan-file sweep.
 */
@Dao
interface AudioAttachmentDao {

    @Insert
    suspend fun insert(attachment: AudioAttachment): Long

    @Query("SELECT * FROM AudioAttachment WHERE noteId = :noteId ORDER BY transcriptStart")
    fun observeForNote(noteId: Long): Flow<List<AudioAttachment>>

    @Query("SELECT * FROM AudioAttachment WHERE noteId = :noteId ORDER BY transcriptStart")
    suspend fun forNote(noteId: Long): List<AudioAttachment>

    @Query("SELECT * FROM AudioAttachment WHERE id = :id")
    suspend fun byId(id: Long): AudioAttachment?

    @Query("SELECT * FROM AudioAttachment")
    suspend fun all(): List<AudioAttachment>

    @Query("SELECT filePath FROM AudioAttachment")
    suspend fun allPaths(): List<String>

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM AudioAttachment")
    fun observeTotalBytes(): Flow<Long>

    @Query("DELETE FROM AudioAttachment WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM AudioAttachment WHERE noteId = :noteId")
    suspend fun deleteForNote(noteId: Long)
}
