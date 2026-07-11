package com.fadghost.notesapp.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fadghost.notesapp.data.backup.BackupManager
import com.fadghost.notesapp.data.backup.BackupPreview
import com.fadghost.notesapp.data.backup.ImportMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backup: BackupManager
) : ViewModel() {

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Non-null when an import ZIP has been read and is awaiting a merge/replace choice. */
    private val _pendingPreview = MutableStateFlow<BackupPreview?>(null)
    val pendingPreview: StateFlow<BackupPreview?> = _pendingPreview.asStateFlow()

    fun export(target: Uri) {
        run("Backup") {
            val count = backup.export(target)
            _status.value = "Exported $count notes."
        }
    }

    fun loadPreview(source: Uri) {
        run("Read backup") {
            val preview = backup.preview(source)
            _pendingPreview.value = preview
            _status.value = if (preview.isIntact) {
                "Backup verified: ${preview.manifest.noteCount} notes, " +
                    "${preview.manifest.folderCount} folders, ${preview.manifest.tagCount} tags."
            } else {
                "Warning: ${preview.checksumMismatches.size} file(s) failed checksum verification."
            }
        }
    }

    fun confirmImport(mode: ImportMode) {
        val preview = _pendingPreview.value ?: return
        run("Import") {
            backup.restore(preview, mode)
            _pendingPreview.value = null
            val verb = if (mode == ImportMode.REPLACE) "Replaced with" else "Merged in"
            _status.value = "$verb ${preview.manifest.noteCount} notes."
        }
    }

    fun cancelImport() {
        _pendingPreview.value = null
        _status.value = null
    }

    private fun run(label: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            runCatching { block() }.onFailure { _status.value = "$label failed: ${it.message}" }
            _busy.value = false
        }
    }
}
