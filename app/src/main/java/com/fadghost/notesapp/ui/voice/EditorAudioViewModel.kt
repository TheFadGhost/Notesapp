package com.fadghost.notesapp.ui.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fadghost.notesapp.data.audio.AudioAttachmentRepository
import com.fadghost.notesapp.data.db.entity.AudioAttachment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Editor-scoped voice attachments (PLAN.md §2.3/§6): observes the audio chips for the
 * open note and deletes a single attachment (files + row) from the popover player.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EditorAudioViewModel @Inject constructor(
    private val attachments: AudioAttachmentRepository
) : ViewModel() {

    private val noteId = MutableStateFlow(0L)

    val chips: StateFlow<List<AudioAttachment>> = noteId
        .flatMapLatest { id -> if (id > 0) attachments.observeForNote(id) else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun bind(id: Long) { noteId.value = id }

    fun noteBytes(id: Long): Long = attachments.noteBytes(id)

    fun deleteAttachment(id: Long) {
        viewModelScope.launch { attachments.delete(id) }
    }
}
