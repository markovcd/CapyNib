package io.github.markovcd.capynib.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.markovcd.capynib.AppGraph
import io.github.markovcd.capynib.capture.CapturedNote
import io.github.markovcd.capynib.ui.board.BoardScreen
import io.github.markovcd.capynib.ui.board.BoardViewModel
import io.github.markovcd.capynib.ui.editor.EditorScreen
import io.github.markovcd.capynib.ui.editor.EditorViewModel
import io.github.markovcd.capynib.ui.settings.SettingsScreen
import io.github.markovcd.capynib.ui.settings.SettingsViewModel
import io.github.markovcd.capynib.ui.theme.CapynibTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * The app's screens: the board, an open note, or settings. A note just made, on the board or by a
 * quick capture arriving through [captured], opens ready to type, or recording or taking a photo when the
 * capture asked for it.
 */
@Composable
fun CapynibRoot(graph: AppGraph, captured: Flow<CapturedNote> = emptyFlow()) {
    var openNote by rememberSaveable { mutableStateOf<String?>(null) }
    var typing by rememberSaveable { mutableStateOf(false) }
    var recordOnOpen by rememberSaveable { mutableStateOf(false) }
    var cameraOnOpen by rememberSaveable { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(false) }
    val start = { id: String, new: Boolean ->
        openNote = id
        typing = new
        recordOnOpen = false
        cameraOnOpen = false
    }
    LaunchedEffect(captured) {
        captured.collect { note ->
            settings = false
            start(note.id, !note.record && !note.photo)
            recordOnOpen = note.record
            cameraOnOpen = note.photo
        }
    }

    CapynibTheme {
        CompositionLocalProvider(LocalPhotos provides graph.photos) {
            val noteId = openNote
            when {
                noteId != null -> key(noteId) {
                    val model: EditorViewModel = viewModel(key = "editor-$noteId", factory = viewModelFactory { initializer { EditorViewModel(graph, noteId) } })
                    val editor = model.editor
                    if (editor == null) {
                        LaunchedEffect(noteId) { openNote = null }
                    } else {
                        EditorScreen(
                            editor,
                            model.recorder,
                            model.photos,
                            model.playback,
                            model.clock,
                            startTyping = typing,
                            startRecording = recordOnOpen,
                            onRecordingStarted = { recordOnOpen = false },
                            startCamera = cameraOnOpen,
                            onCameraStarted = { cameraOnOpen = false },
                            onClose = { openNote = null },
                        )
                    }
                }
                settings -> {
                    val model: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(graph) } })
                    SettingsScreen(model, onClose = { settings = false })
                }
                else -> {
                    val model: BoardViewModel = viewModel(factory = viewModelFactory { initializer { BoardViewModel(graph) } })
                    BoardScreen(model, onOpen = { start(it, false) }, onNew = { start(it, true) }, onSettings = { settings = true })
                }
            }
        }
    }
}
