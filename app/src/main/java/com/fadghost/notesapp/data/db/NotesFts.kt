package com.fadghost.notesapp.data.db

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * FTS5 index for notes (PLAN.md §3/§6). Unlike M0's external-content design, the
 * index is now a *regular* FTS5 table whose rows are written from Kotlin
 * ([com.fadghost.notesapp.data.repo.NotesRepository]). This lets us store
 * markdown-*stripped* text (so `#`, `**`, link URLs, etc. never pollute matches
 * or highlighted snippets) which triggers on the raw Note table could not do.
 *
 * Row identity: `rowid == Note.id`, so search joins straight back to Note for
 * pin/archive/trash filtering. `snippet()` supplies highlighted match previews.
 */
object NotesFts {

    const val TABLE = "note_fts"

    /** Regular (content-owning) FTS5 table over stripped title/body. */
    private const val CREATE_TABLE =
        "CREATE VIRTUAL TABLE IF NOT EXISTS note_fts USING fts5(title, body)"

    /** Fresh installs: create the empty index (repository fills it on save). */
    fun create(db: SupportSQLiteDatabase) {
        db.execSQL(CREATE_TABLE)
    }

    /**
     * Migrate M0's external-content table + triggers to the regular table.
     * Existing rows are repopulated from Note (raw text — they get re-stripped on
     * the next edit; acceptable one-time degradation for already-stored notes).
     */
    fun migrateFromExternalContent(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TRIGGER IF EXISTS note_fts_ai")
        db.execSQL("DROP TRIGGER IF EXISTS note_fts_ad")
        db.execSQL("DROP TRIGGER IF EXISTS note_fts_au")
        db.execSQL("DROP TABLE IF EXISTS note_fts")
        db.execSQL(CREATE_TABLE)
        db.execSQL(
            "INSERT INTO note_fts(rowid, title, body) " +
                "SELECT id, title, body FROM Note WHERE deletedAt IS NULL"
        )
    }
}
