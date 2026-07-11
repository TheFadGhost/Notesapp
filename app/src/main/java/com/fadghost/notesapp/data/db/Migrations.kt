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

/**
 * v2 -> v3 (M1 -> M2): adds the AI layer's two new tables — per-call cost rows
 * (PLAN.md §5/§7) and the cached OpenRouter model list (PLAN.md §5). Purely
 * additive; no existing table or the FTS index is touched. Column definitions
 * mirror the [com.fadghost.notesapp.data.ai.cost.AiCallCost] and
 * [com.fadghost.notesapp.data.ai.model.CachedModel] entities exactly so Room's
 * post-migration schema validation passes.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `AiCallCost` (" +
                "`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`feature` TEXT NOT NULL, " +
                "`model` TEXT NOT NULL, " +
                "`promptTokens` INTEGER NOT NULL, " +
                "`completionTokens` INTEGER NOT NULL, " +
                "`totalTokens` INTEGER NOT NULL, " +
                "`costUsd` REAL NOT NULL, " +
                "`noteId` INTEGER)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_AiCallCost_createdAt` ON `AiCallCost` (`createdAt`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `CachedModel` (" +
                "`id` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`contextLength` INTEGER NOT NULL, " +
                "`promptPrice` TEXT, " +
                "`completionPrice` TEXT, " +
                "`inputModalities` TEXT NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))"
        )
    }
}

/**
 * v3 -> v4 (M2 -> M3): reminders gain a simple repeat cycle so the calendar can
 * reschedule the next occurrence on fire/completion (PLAN.md §8). The [Event]
 * table already carries `recurrence`; this adds the mirror column to [Reminder].
 * Purely additive with a non-null default matching the entity's
 * `Recurrence.NONE`, so Room's post-migration validation passes.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `Reminder` ADD COLUMN `recurrence` TEXT NOT NULL DEFAULT 'NONE'")
    }
}
