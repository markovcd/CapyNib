package io.github.markovcd.capynib.ui

import androidx.compose.runtime.Composable
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
 * quick capture arriving through [captured], opens ready to type.
 */
@Composable
fun CapynibRoot(graph: AppGraph, captured: Flow<String> = emptyFlow()) {
    var openNote by rememberSaveable { mutableStateOf<String?>(null) }
    var typing by rememberSaveable { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(false) }
    val start = { id: String, new: Boolean ->
        openNote = id
        typing = new
    }
    LaunchedEffect(captured) {
        captured.collect { id ->
            settings = false
            start(id, true)
        }
    }

    CapynibTheme {
        val noteId = openNote
        when {
            noteId != null -> key(noteId) {
                val model: EditorViewModel = viewModel(key = "editor-$noteId", factory = viewModelFactory { initializer { EditorViewModel(graph, noteId) } })
                val editor = model.editor
                if (editor == null) {
                    LaunchedEffect(noteId) { openNote = null }
                } else {
                    EditorScreen(editor, startTyping = typing, onClose = { openNote = null })
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
