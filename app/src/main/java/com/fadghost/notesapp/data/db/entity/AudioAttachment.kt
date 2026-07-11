package com.fadghost.notesapp.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A voice-note audio attachment (PLAN.md §2.3/§5 — "Keep audio attached"). Added in
 * schema v5 (M4). One row per recorded voice ramble; [transcriptStart] is the caret
 * offset in the owning note's body where the transcript line begins, so the editor
 * can anchor the circular audio chip at the start of that line. Deleting the note
 * cascades these rows away; the file itself is swept by the trash-purge orphan pass.
 */
@Entity(
    tableName = "AudioAttachment",
    foreignKeys = [
        ForeignKey(
            entity = Note::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("noteId")]
)
data class AudioAttachment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    /** Absolute path to the first .m4a segment file (canonical / chip reference). */
    val filePath: String,
    /** Newline-joined absolute paths of every segment, in order (first == [filePath]). */
    val segmentPaths: String = "",
    /** Total duration across all segments. */
    val durationMs: Long,
    /** Total bytes across all segments. */
    val sizeBytes: Long,
    val createdAt: Long,
    /** Caret offset of the transcript line start in the note body (chip anchor). */
    val transcriptStart: Int = 0,
    /** Caret offset just past the inserted transcript text. */
    val transcriptEnd: Int = 0
) {
    /** Every segment file path, in playback order. */
    val segments: List<String>
        get() = if (segmentPaths.isBlank()) listOf(filePath)
        else segmentPaths.split("\n").filter { it.isNotBlank() }
}

