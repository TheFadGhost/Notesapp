package com.fadghost.notesapp.data.backup

import android.content.Context
import android.net.Uri
import com.fadghost.notesapp.data.repo.NotesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android SAF wrapper around [BackupSerializer] (PLAN.md §6/§12). Reads/writes the
 * user-picked ZIP through the ContentResolver; all format logic stays in the pure
 * serializer. The API key / settings secrets are never part of [BackupData], so
 * they can't leak into an export.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: NotesRepository
) {
    /** Export every note to the user-picked ZIP [target]. Returns note count. */
    suspend fun export(target: Uri): Int = withContext(Dispatchers.IO) {
        val data = repository.buildBackup()
        context.contentResolver.openOutputStream(target)?.use { out ->
            BackupSerializer.export(data, out, now = System.currentTimeMillis())
        } ?: error("Could not open $target for writing")
        data.notes.size
    }

    /** Read a backup for the import preview (counts + checksum verification). */
    suspend fun preview(source: Uri): BackupPreview = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(source)?.use { BackupSerializer.parse(it) }
            ?: error("Could not open $source for reading")
    }

    /** Commit a previewed backup with the chosen [mode]. */
    suspend fun restore(preview: BackupPreview, mode: ImportMode) = withContext(Dispatchers.IO) {
        repository.importBackup(preview.data, mode)
    }
}
