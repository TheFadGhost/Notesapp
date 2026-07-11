package com.fadghost.notesapp.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 -> v2 (M0 -> M1): no Room *entity* changed, but the FTS5 index switched from
 * an external-content table with sync triggers to a Kotlin-managed regular table
 * so we can index markdown-stripped text (PLAN.md §6). Nothing else moves, so the
 * generated schema hash is unchanged; only the virtual table is rebuilt.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        NotesFts.migrateFromExternalContent(db)
    }
}
