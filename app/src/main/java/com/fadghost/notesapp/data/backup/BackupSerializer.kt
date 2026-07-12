package com.fadghost.notesapp.data.backup

import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Pure, Android-free ZIP (de)serialisation for backups so it can be unit-tested
 * on the JVM (PLAN.md §15 — "backup round-trip: export → parse → checksums
 * match"). The Android SAF plumbing lives in [BackupManager]; this class only
 * touches streams.
 */
object BackupSerializer {

    private const val META_PATH = "metadata.json"
    private const val MANIFEST_PATH = "manifest.json"
    private const val NOTES_DIR = "notes/"
    private const val ATTACHMENTS_DIR = "attachments/"

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Write [data] as a backup ZIP into [out]. [now] is the manifest timestamp.
     * [attachmentBytes] maps each attachment's [BackupAttachment.zipPath] to its file
     * bytes; each is stored and checksummed so restore can rebuild the file on disk.
     */
    fun export(
        data: BackupData,
        out: OutputStream,
        now: Long,
        attachmentBytes: Map<String, ByteArray> = emptyMap()
    ) {
        val entries = mutableListOf<ManifestEntry>()
        ZipOutputStream(out).use { zip ->
            // One markdown file per note.
            for (note in data.notes) {
                val path = "$NOTES_DIR${note.id}.md"
                val bytes = renderNoteMarkdown(note).toByteArray(Charsets.UTF_8)
                writeEntry(zip, path, bytes)
                entries += ManifestEntry(path, sha256(bytes), bytes.size.toLong())
            }
            // Attachment files (M-A) — bytes verbatim under attachments/<noteId>/<uuid>.
            for (att in data.attachments) {
                val bytes = attachmentBytes[att.zipPath] ?: continue
                writeEntry(zip, att.zipPath, bytes)
                entries += ManifestEntry(att.zipPath, sha256(bytes), bytes.size.toLong())
            }
            // Full structured metadata (source of truth for restore).
            val metaBytes = json.encodeToString(BackupData.serializer(), data).toByteArray(Charsets.UTF_8)
            writeEntry(zip, META_PATH, metaBytes)
            entries += ManifestEntry(META_PATH, sha256(metaBytes), metaBytes.size.toLong())

            val manifest = BackupManifest(
                formatVersion = BACKUP_FORMAT_VERSION,
                createdAt = now,
                noteCount = data.notes.size,
                folderCount = data.folders.size,
                tagCount = data.tags.size,
                entries = entries,
                attachmentCount = data.attachments.size
            )
            val manifestBytes = json.encodeToString(BackupManifest.serializer(), manifest)
                .toByteArray(Charsets.UTF_8)
            writeEntry(zip, MANIFEST_PATH, manifestBytes)
        }
    }

    /**
     * Read a backup ZIP from [input] into a [BackupPreview], recomputing every
     * checksum against the manifest. Throws [IllegalArgumentException] if the
     * archive is missing its manifest or metadata.
     */
    fun parse(input: InputStream): BackupPreview {
        val files = HashMap<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) files[entry.name] = zip.readBytes()
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val manifestBytes = files[MANIFEST_PATH]
            ?: throw IllegalArgumentException("Not a Notesapp backup: manifest.json missing")
        val metaBytes = files[META_PATH]
            ?: throw IllegalArgumentException("Not a Notesapp backup: metadata.json missing")

        val manifest = json.decodeFromString(BackupManifest.serializer(), manifestBytes.decodeToString())
        val data = json.decodeFromString(BackupData.serializer(), metaBytes.decodeToString())

        val mismatches = manifest.entries.filter { e ->
            val actual = files[e.path]
            actual == null || sha256(actual) != e.sha256
        }.map { it.path }

        val attachmentFiles = files.filterKeys { it.startsWith(ATTACHMENTS_DIR) }

        return BackupPreview(manifest, data, mismatches, attachmentFiles)
    }

    /** A note serialised as human-readable markdown with a small front-matter block. */
    fun renderNoteMarkdown(note: BackupNote): String = buildString {
        append("---\n")
        append("id: ${note.id}\n")
        append("created: ${note.createdAt}\n")
        append("updated: ${note.updatedAt}\n")
        append("pinned: ${note.pinned}\n")
        append("archived: ${note.archived}\n")
        note.folderName?.let { append("folder: $it\n") }
        if (note.tags.isNotEmpty()) append("tags: ${note.tags.joinToString(", ")}\n")
        append("---\n\n")
        if (note.title.isNotBlank()) append("# ${note.title}\n\n")
        append(note.body)
    }

    private fun writeEntry(zip: ZipOutputStream, path: String, bytes: ByteArray) {
        val e = ZipEntry(path)
        e.time = 0L // deterministic archives (stable checksums across identical exports)
        zip.putNextEntry(e)
        zip.write(bytes)
        zip.closeEntry()
    }

    fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            val i = b.toInt() and 0xFF
            sb.append(HEX[i ushr 4]).append(HEX[i and 0x0F])
        }
        return sb.toString()
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
