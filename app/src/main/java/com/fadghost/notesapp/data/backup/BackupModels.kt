package com.fadghost.notesapp.data.backup

import kotlinx.serialization.Serializable

/**
 * Backup wire format (PLAN.md §6/§12): a ZIP of one `.md` file per note plus a
 * JSON metadata blob and a manifest of SHA-256 checksums. The API key and any
 * secret is NEVER represented here — these DTOs only carry note content.
 */

const val BACKUP_FORMAT_VERSION = 1

@Serializable
data class BackupNote(
    val id: Long,
    val title: String,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean,
    val archived: Boolean,
    val folderName: String? = null,
    val tags: List<String> = emptyList()
)

@Serializable
data class BackupFolder(val name: String)

@Serializable
data class BackupTag(val name: String, val color: Int)

/**
 * An attachment's metadata (M-A). The file bytes live in the ZIP at [zipPath]; on
 * restore the [id]/[noteId]/[annotatedOfId] are remapped to freshly-assigned ids and
 * the note bodies' `[[att:<id>]]` tokens are rewritten to match.
 */
@Serializable
data class BackupAttachment(
    val id: Long,
    val noteId: Long,
    val kind: String,
    val displayName: String,
    val mime: String,
    val sizeBytes: Long,
    val createdAt: Long,
    val annotatedOfId: Long? = null,
    val ocrText: String? = null,
    val description: String? = null,
    /** ZIP-relative path of the stored bytes, e.g. `attachments/3/uuid.jpg`. */
    val zipPath: String
)

/** Everything exported, in memory. Contains no secrets. */
@Serializable
data class BackupData(
    val notes: List<BackupNote> = emptyList(),
    val folders: List<BackupFolder> = emptyList(),
    val tags: List<BackupTag> = emptyList(),
    val attachments: List<BackupAttachment> = emptyList()
)

@Serializable
data class ManifestEntry(val path: String, val sha256: String, val bytes: Long)

@Serializable
data class BackupManifest(
    val formatVersion: Int,
    val createdAt: Long,
    val noteCount: Int,
    val folderCount: Int,
    val tagCount: Int,
    val entries: List<ManifestEntry>,
    val attachmentCount: Int = 0
)

/** Result of reading a backup ZIP without committing it — drives the import preview. */
data class BackupPreview(
    val manifest: BackupManifest,
    val data: BackupData,
    /** Paths whose recomputed checksum did not match the manifest. Empty == intact. */
    val checksumMismatches: List<String>,
    /** ZIP-relative path -> file bytes for attachments, applied on restore. */
    val attachmentFiles: Map<String, ByteArray> = emptyMap()
) {
    val isIntact: Boolean get() = checksumMismatches.isEmpty()
}

/** Import strategy chosen after preview (PLAN.md §12 — never blind overwrite). */
enum class ImportMode { REPLACE, MERGE }
