package io.github.markovcd.capynib.ui.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.markovcd.capynib.AppGraph
import io.github.markovcd.capynib.core.board.BoardContent
import io.github.markovcd.capynib.core.board.Shelf
import io.github.markovcd.capynib.core.note.Checklist
import io.github.markovcd.capynib.core.sync.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BoardViewModel(private val graph: AppGraph) : ViewModel() {
    val query = MutableStateFlow("")
    val shelf = MutableStateFlow(Shelf.Notes)

    val content: StateFlow<BoardContent?> =
        combine(graph.store.observeVisible(), query, shelf) { notes, query, shelf -> BoardContent.of(notes, query, shelf) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // Without a server nothing else empties the trash.
        viewModelScope.launch(Dispatchers.IO) {
            if (graph.store.emptyTrash(graph.clock()) > 0) graph.scheduler.requestSync()
        }
    }

    val status: StateFlow<SyncStatus> = graph.scheduler.status

    /** Makes an empty note, or a list with one empty item, and returns its id to open it. */
    fun newNote(checklist: Boolean): String =
        graph.store.create(graph.clock(), body = if (checklist) Checklist.toChecklist("") else "").id
}
