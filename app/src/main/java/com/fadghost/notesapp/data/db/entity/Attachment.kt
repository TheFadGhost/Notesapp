package com.fadghost.notesapp.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A note attachment — an image or a generic file (M-A, schema v7). One row per
 * ingested file; the inline body token `[[att:<id>]]` references it by [id]. Files
 * live under `filesDir/attachments/<noteId>/<uuid>.<ext>` (the SAME per-note dir the
 * voice [AudioAttachment] uses, so a note's whole attachment folder is purged together
 * on hard-delete). Deleting the owning note cascades these rows away; the leftover
 * files are swept by the trash-purge orphan pass.
 *
 * [annotatedOfId] links an annotated copy back to the original it was drawn over (the
 * original is preserved; the note token is repointed at the copy). [ocrText]/
 * [description] are filled in silently by the background image-index job (P7) and
 * folded into the owning note's FTS row so search finds a note by what its images say.
 */
@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = Note::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("noteId"), Index("annotatedOfId")]
)
data class Attachment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    /** [KIND_IMAGE] or [KIND_FILE]. */
    val kind: String,
    /** Absolute path to the stored file. */
    val path: String,
    /** Original file name shown on the chip / popover. */
    val displayName: String,
    val mime: String,
    val sizeBytes: Long,
    val createdAt: Long,
    /** Non-null on an annotated copy: the id of the original it was drawn over. */
    val annotatedOfId: Long? = null,
    /** Verbatim OCR transcription from the image-index job (P7); null until indexed. */
    val ocrText: String? = null,
    /** One-line factual description from the image-index job (P7); null until indexed. */
    val description: String? = null
) {
    val isImage: Boolean get() = kind == KIND_IMAGE

    companion object {
        const val KIND_IMAGE = "image"
        const val KIND_FILE = "file"
    }
}
