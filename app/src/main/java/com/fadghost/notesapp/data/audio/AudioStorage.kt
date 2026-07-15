package com.fadghost.notesapp.data.audio

import java.io.File

/**
 * On-disk layout + orphan detection for voice attachments (PLAN.md §6 — "orphaned
 * files cleaned when trash purges; storage usage visible in settings"). Files live
 * under `filesDir/attachments/<noteId>/segment_NNN.m4a`, matching the per-note dir
 * convention [com.fadghost.notesapp.data.repo.NotesRepository] already purges on
 * hard-delete. Every function takes explicit [File] roots so the maths is testable
 * against a JUnit temp dir with no Android context.
 */
object AudioStorage {

    const val DIR = "attachments"

    fun root(filesDir: File): File = File(filesDir, DIR)

    fun noteDir(filesDir: File, noteId: Long): File = File(root(filesDir), noteId.toString())

    /** A recording session never shares segment filenames with another session. */
    fun sessionDir(noteDir: File, sessionId: String): File {
        val safe = sessionId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(File(noteDir, "voice_sessions"), safe)
    }

    fun pruneEmptySessionParents(sessionDir: File) {
        var current: File? = sessionDir
        repeat(3) {
            val dir = current ?: return
            if (dir.name == DIR || dir.listFiles()?.isNotEmpty() == true) return
            runCatching { dir.delete() }
            current = dir.parentFile
        }
    }

    /** Remove empty nested session directories bottom-up while retaining the attachment root. */
    fun pruneEmptyDirectories(root: File) {
        if (!root.exists()) return
        root.walkBottomUp()
            .filter { it.isDirectory && it != root }
            .forEach { dir -> if (dir.listFiles()?.isEmpty() == true) runCatching { dir.delete() } }
    }

    /** Segment file for [index] inside [noteDir], creating parent dirs as needed. */
    fun segmentFile(noteDir: File, index: Int): File {
        if (!noteDir.exists()) noteDir.mkdirs()
        return File(noteDir, AudioSegments.fileName(index))
    }

    /** Total bytes of every regular file under [root] (recursive). Missing root -> 0. */
    fun totalBytes(root: File): Long {
        if (!root.exists()) return 0L
        return root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /** Total bytes for a single note's attachment dir. */
    fun noteBytes(filesDir: File, noteId: Long): Long = totalBytes(noteDir(filesDir, noteId))

    /**
     * Audio files under [root] not referenced by any live attachment. [referenced] is
     * the set of absolute paths recorded in the DB; any on-disk audio file whose path
     * is absent is an orphan (e.g. a discarded recording or a purged note's leftovers).
     */
    fun findOrphans(root: File, referenced: Set<String>): List<File> {
        if (!root.exists()) return emptyList()
        val live = referenced.map { File(it).absolutePath }.toHashSet()
        return root.walkTopDown()
            .filter { it.isFile }
            .filter { it.absolutePath !in live }
            .toList()
    }

    /** Human-readable size for the chip popover / storage row (e.g. "1.4 MB"). */
    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 KB"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.0f KB".format(kb)
        val mb = kb / 1024.0
        return "%.1f MB".format(mb)
    }
}
