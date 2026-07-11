package com.fadghost.notesapp

import com.fadghost.notesapp.data.backup.BackupData
import com.fadghost.notesapp.data.backup.BackupFolder
import com.fadghost.notesapp.data.backup.BackupNote
import com.fadghost.notesapp.data.backup.BackupSerializer
import com.fadghost.notesapp.data.backup.BackupTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.junit.Test

class BackupRoundTripTest {

    private fun sample() = BackupData(
        notes = listOf(
            BackupNote(1, "Groceries", "- milk\n- eggs", 100, 200, pinned = true, archived = false, tags = listOf("home")),
            BackupNote(2, "Ideas", "**big** idea", 101, 201, pinned = false, archived = false, folderName = "Work")
        ),
        folders = listOf(BackupFolder("Work")),
        tags = listOf(BackupTag("home", -0x10000))
    )

    @Test fun exportThenParseRoundTrips() {
        val data = sample()
        val out = ByteArrayOutputStream()
        BackupSerializer.export(data, out, now = 1234L)

        val preview = BackupSerializer.parse(ByteArrayInputStream(out.toByteArray()))

        assertTrue("checksums must match", preview.isIntact)
        assertEquals(1234L, preview.manifest.createdAt)
        assertEquals(2, preview.manifest.noteCount)
        assertEquals(1, preview.manifest.folderCount)
        assertEquals(1, preview.manifest.tagCount)
        assertEquals(data.notes, preview.data.notes)
        assertEquals(data.folders, preview.data.folders)
        assertEquals(data.tags, preview.data.tags)
    }

    @Test fun everyManifestEntryHasChecksum() {
        val out = ByteArrayOutputStream()
        BackupSerializer.export(sample(), out, now = 1L)
        val preview = BackupSerializer.parse(ByteArrayInputStream(out.toByteArray()))
        // notes(2) + metadata.json = 3 checksummed entries.
        assertEquals(3, preview.manifest.entries.size)
        preview.manifest.entries.forEach { assertEquals(64, it.sha256.length) }
    }

    @Test fun tamperedContentIsDetected() {
        val out = ByteArrayOutputStream()
        BackupSerializer.export(sample(), out, now = 1L)

        // Rebuild the archive with one note's content altered but the original
        // manifest kept — the stored checksum should no longer match.
        val repacked = ByteArrayOutputStream()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zin ->
            ZipOutputStream(repacked).use { zout ->
                var e = zin.nextEntry
                while (e != null) {
                    val original = zin.readBytes()
                    val payload = if (e.name == "notes/1.md") "TAMPERED".toByteArray() else original
                    zout.putNextEntry(ZipEntry(e.name))
                    zout.write(payload)
                    zout.closeEntry()
                    e = zin.nextEntry
                }
            }
        }

        val preview = BackupSerializer.parse(ByteArrayInputStream(repacked.toByteArray()))
        assertTrue("tampered file must fail checksum", preview.checksumMismatches.contains("notes/1.md"))
    }

    @Test fun sha256IsStable() {
        assertEquals(
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            BackupSerializer.sha256("hello".toByteArray())
        )
    }
}
