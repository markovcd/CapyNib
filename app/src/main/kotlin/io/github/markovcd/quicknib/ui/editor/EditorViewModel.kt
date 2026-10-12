package io.github.markovcd.quicknib.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.markovcd.quicknib.AppGraph
import io.github.markovcd.quicknib.core.editor.NoteEditor
import kotlinx.coroutines.Dispatchers

/** Holds the open note's [NoteEditor]; null when the note no longer exists. */
class EditorViewModel(graph: AppGraph, noteId: String) : ViewModel() {
    val editor: NoteEditor? = graph.store.get(noteId)?.let { note ->
        NoteEditor(
            store = graph.store,
            initial = note,
            scope = viewModelScope,
            io = Dispatchers.IO,
            clock = graph.clock,
            writeScope = graph.scope,
            onSaved = { graph.scheduler.requestSync() },
        )
    }
}
