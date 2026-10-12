package io.github.markovcd.quicknib.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.markovcd.quicknib.AppGraph
import io.github.markovcd.quicknib.ui.board.BoardScreen
import io.github.markovcd.quicknib.ui.board.BoardViewModel
import io.github.markovcd.quicknib.ui.editor.EditorScreen
import io.github.markovcd.quicknib.ui.editor.EditorViewModel
import io.github.markovcd.quicknib.ui.settings.SettingsScreen
import io.github.markovcd.quicknib.ui.settings.SettingsViewModel
import io.github.markovcd.quicknib.ui.theme.QuickNibTheme

/** The app's screens: the board, an open note, or settings. */
@Composable
fun QuickNibRoot(graph: AppGraph) {
    var openNote by rememberSaveable { mutableStateOf<String?>(null) }
    var settings by rememberSaveable { mutableStateOf(false) }

    QuickNibTheme {
        val noteId = openNote
        when {
            noteId != null -> {
                val model: EditorViewModel = viewModel(key = "editor-$noteId", factory = viewModelFactory { initializer { EditorViewModel(graph, noteId) } })
                val editor = model.editor
                if (editor == null) {
                    LaunchedEffect(noteId) { openNote = null }
                } else {
                    EditorScreen(editor, onClose = { openNote = null })
                }
            }
            settings -> {
                val model: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(graph) } })
                SettingsScreen(model, onClose = { settings = false })
            }
            else -> {
                val model: BoardViewModel = viewModel(factory = viewModelFactory { initializer { BoardViewModel(graph) } })
                BoardScreen(model, onOpen = { openNote = it }, onSettings = { settings = true })
            }
        }
    }
}
