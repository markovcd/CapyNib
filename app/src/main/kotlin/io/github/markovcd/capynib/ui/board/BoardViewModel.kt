package io.github.markovcd.capynib.ui.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.markovcd.capynib.AppGraph
import io.github.markovcd.capynib.core.board.BoardContent
import io.github.markovcd.capynib.core.note.Checklist
import io.github.markovcd.capynib.core.sync.SyncStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class BoardViewModel(private val graph: AppGraph) : ViewModel() {
    val query = MutableStateFlow("")
    val archive = MutableStateFlow(false)

    val content: StateFlow<BoardContent?> =
        combine(graph.store.observeVisible(), query, archive) { notes, query, archive -> BoardContent.of(notes, query, archive) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val status: StateFlow<SyncStatus> = graph.scheduler.status

    /** Makes an empty note, or a list with one empty item, and returns its id to open it. */
    fun newNote(checklist: Boolean): String =
        graph.store.create(graph.clock(), body = if (checklist) Checklist.toChecklist("") else "").id
}
