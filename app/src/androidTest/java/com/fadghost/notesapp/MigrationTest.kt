package com.fadghost.notesapp

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fadghost.notesapp.data.db.MIGRATION_6_7
import com.fadghost.notesapp.data.db.NotesDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room migration test for M-A's schema bump (v6 -> v7, the `attachments` table).
 * Seeds a real v6 database, runs [MIGRATION_6_7], and lets Room validate the resulting
 * schema against the exported 7.json — then proves the new table works and the seeded
 * note survived. Instrumented because Room's [MigrationTestHelper] needs a device DB
 * (framework SQLite); run with `.\gradlew.bat connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val testDb = "migration-test-notes.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        NotesDatabase::class.java
    )

    @Test
    fun migrate6To7_addsAttachmentsTable_andKeepsData() {
        // Seed a v6 DB with one note the attachment will reference.
        helper.createDatabase(testDb, 6).use { db ->
            db.execSQL(
                "INSERT INTO Note (id, title, body, createdAt, updatedAt, pinned, archived, deletedAt, folderId) " +
                    "VALUES (1, 'Trip', 'body [[att:1]]', 100, 200, 0, 0, NULL, NULL)"
            )
        }

        // Migrate to v7; Room validates the schema against schemas/7.json (throws on mismatch).
        val db = helper.runMigrationsAndValidate(testDb, 7, true, MIGRATION_6_7)

        // The seeded note survived the migration.
        db.query("SELECT title FROM Note WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Trip", c.getString(0))
        }

        // The new attachments table exists and accepts a row referencing the note.
        db.execSQL(
            "INSERT INTO attachments " +
                "(id, noteId, kind, path, displayName, mime, sizeBytes, createdAt, annotatedOfId, ocrText, description) " +
                "VALUES (1, 1, 'image', '/data/x/y.jpg', 'y.jpg', 'image/jpeg', 2048, 300, NULL, NULL, NULL)"
        )
        db.query("SELECT noteId, kind, displayName, sizeBytes FROM attachments WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1L, c.getLong(0))
            assertEquals("image", c.getString(1))
            assertEquals("y.jpg", c.getString(2))
            assertEquals(2048L, c.getLong(3))
        }
    }
}
