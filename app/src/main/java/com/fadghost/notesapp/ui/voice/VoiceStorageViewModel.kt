package com.fadghost.notesapp.ui.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fadghost.notesapp.data.audio.AudioAttachmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Settings → Storage read-out (PLAN.md §6): total audio-attachment size and a
 * clear-orphans action that deletes audio files no live note references.
 */
@HiltViewModel
class VoiceStorageViewModel @Inject constructor(
    private val attachments: AudioAttachmentRepository
) : ViewModel() {

    val totalBytes: StateFlow<Long> =
        attachments.observeTotalBytes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    fun clearOrphans() {
        viewModelScope.launch {
            val removed = attachments.sweepOrphans()
            _status.value = if (removed == 0) "No orphaned audio found" else "Removed $removed orphaned file(s)"
        }
    }
}
