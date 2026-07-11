package com.fadghost.notesapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fadghost.notesapp.data.prefs.DraftSnapshot
import com.fadghost.notesapp.data.prefs.DraftStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Surfaces a leftover editor draft so the shell can offer "Restore unsaved note". */
@HiltViewModel
class DraftRecoveryViewModel @Inject constructor(
    private val draftStore: DraftStore
) : ViewModel() {

    val draft: StateFlow<DraftSnapshot?> =
        draftStore.draft.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun discard() {
        viewModelScope.launch { draftStore.clear() }
    }
}
