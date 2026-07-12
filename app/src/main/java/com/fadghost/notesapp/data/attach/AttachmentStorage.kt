package com.fadghost.notesapp.data.attach

import java.io.File
import java.util.Locale
import java.util.UUID

/**
 * On-disk layout + orphan detection for note attachments (M-A). Files live under
 * `filesDir/attachments/<noteId>/<uuid>.<ext>` — the SAME per-note root the voice
 * [com.fadghost.notesapp.data.audio.AudioStorage] uses, so a note's whole attachment
 * folder (audio + files) is deleted together on hard-delete. The two owners partition
 * the folder by extension: audio owns `*.m4a`, attachments own everything else, so the
 * two orphan sweeps never delete each other's live files.
 *
 * Every function takes explicit [File] roots so the maths is testable against a JUnit
 * temp dir with no Android context.
 */
object AttachmentStorage {

    /** Shared with AudioStorage.DIR — both live under one per-note folder. */
    const val DIR = "attachments"

    /** Extension owned by the voice recorder; the attachment sweep never touches it. */
    const val AUDIO_EXT = "m4a"

    fun root(filesDir: File): File = File(filesDir, DIR)

    fun noteDir(filesDir: File, noteId: Long): File = File(root(filesDir), noteId.toString())

    /** A fresh `<uuid>.<ext>` file inside [noteDir], creating parent dirs as needed. */
    fun newFile(noteDir: File, ext: String): File {
        if (!noteDir.exists()) noteDir.mkdirs()
        val safe = ext.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }.take(8)
        val name = if (safe.isEmpty()) UUID.randomUUID().toString()
        else "${UUID.randomUUID()}.$safe"
        return File(noteDir, name)
    }

    /** File extension for a [displayName] / [mime], no leading dot. Empty if unknown. */
    fun extFor(displayName: String?, mime: String?): String {
        val fromName = displayName?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() && it.length <= 8 }
        if (fromName != null) return fromName.lowercase(Locale.ROOT)
        return when {
            mime == null -> ""
            mime == "image/jpeg" -> "jpg"
            mime.startsWith("image/") -> mime.substringAfter('/').substringBefore('+')
            mime == "application/pdf" -> "pdf"
            else -> ""
        }
    }

    /** True for the voice recorder's files, which the attachment sweep must skip. */
    fun isAudioFile(file: File): Boolean = file.extension.equals(AUDIO_EXT, ignoreCase = true)

    /** Total bytes of every regular file under [root] (recursive). Missing root -> 0. */
    fun totalBytes(root: File): Long {
        if (!root.exists()) return 0L
        return root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /** Total bytes for a single note's attachment dir. */
    fun noteBytes(filesDir: File, noteId: Long): Long = totalBytes(noteDir(filesDir, noteId))

    /**
     * Non-audio files under [root] not referenced by any live attachment row. [referenced]
     * is the set of absolute paths recorded in the DB; audio files (`*.m4a`) are excluded
     * so this never removes a live voice note (that is the audio sweep's job).
     */
    fun findOrphans(root: File, referenced: Set<String>): List<File> {
        if (!root.exists()) return emptyList()
        val live = referenced.map { File(it).absolutePath }.toHashSet()
        return root.walkTopDown()
            .filter { it.isFile }
            .filter { !isAudioFile(it) }
            .filter { it.absolutePath !in live }
            .toList()
    }
}
