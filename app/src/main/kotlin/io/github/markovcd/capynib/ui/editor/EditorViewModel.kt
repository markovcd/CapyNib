package io.github.markovcd.capynib.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.markovcd.capynib.AppGraph
import io.github.markovcd.capynib.audio.Playback
import io.github.markovcd.capynib.core.editor.NoteEditor
import kotlinx.coroutines.Dispatchers

/** Holds the open note's [NoteEditor], null when the note no longer exists, and records clips and takes photos into it. */
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

    val recorder = Recorder(graph.microphone, graph::newClipFile, graph.clock) { file, duration -> editor?.addRecording(file, duration) }

    val photos = PhotoIntake(graph::newPhotoFile, graph::photoUri, graph.resolver) { file, type -> editor?.addPhoto(file, type) }

    val playback: Playback = graph.playback

    val clock: () -> Long = graph.clock

    /** Leaving the note keeps a clip still recording. */
    override fun onCleared() {
        recorder.stop()
    }
}
