package io.github.markovcd.capynib.ui.board

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.markovcd.capynib.R
import io.github.markovcd.capynib.core.board.BoardChange
import io.github.markovcd.capynib.core.board.BoardMode
import io.github.markovcd.capynib.core.board.Shelf
import io.github.markovcd.capynib.ui.ConfirmDeleteForever
import io.github.markovcd.capynib.ui.Tags

@Composable
fun BoardScreen(model: BoardViewModel, onOpen: (String) -> Unit, onNew: (String) -> Unit, onSettings: () -> Unit) {
    val state = BoardState(
        content = model.content.collectAsStateWithLifecycle().value,
        query = model.query.collectAsStateWithLifecycle().value,
        shelf = model.shelf.collectAsStateWithLifecycle().value,
        mode = model.mode.collectAsStateWithLifecycle().value,
        calendar = model.calendar.collectAsStateWithLifecycle().value,
        day = model.day.collectAsStateWithLifecycle().value,
        today = model.today,
        selection = model.selection.collectAsStateWithLifecycle().value,
        lastChange = model.lastChange.collectAsStateWithLifecycle().value,
        deleting = model.deleting.collectAsStateWithLifecycle().value,
        trashDays = model.trashDays.collectAsStateWithLifecycle().value,
        playing = model.playback.playing.collectAsStateWithLifecycle().value,
    )
    val actions = BoardActions(
        onQuery = { model.query.value = it },
        onShelf = model::showShelf,
        onMode = model::showMode,
        onMonth = model::showMonth,
        onDay = model::pickDay,
        onTag = { model.tag.value = it },
        onOpen = onOpen,
        onNew = { checklist -> onNew(model.newNote(checklist)) },
        onSettings = onSettings,
        selection = SelectionActions(
            toggle = model::toggle,
            clear = model::clearSelection,
            pin = model::pinSelected,
            color = model::colorSelected,
            archive = model::archiveSelected,
            trash = model::trashSelected,
            restore = model::restoreSelected,
            deleteForever = model::deleteSelectedForever,
        ),
        onSwiped = model::swiped,
        onPlay = model.playback::toggle,
        onConfirmDelete = model::confirmDeleteForever,
        onCancelDelete = model::cancelDeleteForever,
        onMove = model::move,
        onUndo = model::undo,
        onChangeShown = model::changeShown,
    )
    BoardLayout(state, actions)
}

@Composable
private fun BoardLayout(state: BoardState, actions: BoardActions) {
    val snackbar = remember { SnackbarHostState() }
    ChangeSnackbar(snackbar, state.lastChange, actions.onUndo, actions.onChangeShown)
    if (state.deleting.isNotEmpty()) ConfirmDeleteForever(state.deleting.size, actions.onConfirmDelete, actions.onCancelDelete)
    BackHandler(enabled = state.selection.isActive) { actions.selection.clear() }
    Scaffold(
        topBar = { BoardTopBar(state, actions) },
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                Snackbar(
                    action = {
                        TextButton(onClick = { data.performAction() }, modifier = Modifier.testTag(Tags.UNDO)) {
                            Text(data.visuals.actionLabel.orEmpty())
                        }
                    },
                ) { Text(data.visuals.message) }
            }
        },
        floatingActionButton = {
            if (state.shelf == Shelf.Notes && !state.selection.isActive) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SmallFloatingActionButton(
                        onClick = { actions.onNew(true) },
                        modifier = Modifier.testTag(Tags.NEW_LIST),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Icon(Icons.Outlined.CheckBox, contentDescription = stringResource(R.string.board_new_list))
                    }
                    FloatingActionButton(
                        onClick = { actions.onNew(false) },
                        modifier = Modifier.testTag(Tags.NEW_NOTE),
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.board_new_note))
                    }
                }
            }
        },
    ) { padding ->
        if (state.content == null) return@Scaffold
        if (state.mode == BoardMode.Calendar) {
            if (state.calendar != null) NoteCalendar(state.calendar, state, actions, Modifier.padding(padding))
            return@Scaffold
        }
        if (state.content.isEmpty) {
            Box(Modifier.fillMaxSize().padding(padding).testTag(Tags.EMPTY_BOARD), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(
                        when (state.shelf) {
                            Shelf.Notes -> R.string.board_empty
                            Shelf.Archive -> R.string.board_archive_empty
                            Shelf.Trash -> R.string.board_trash_empty
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }
        NoteGrid(state.content, state, actions, Modifier.padding(padding))
    }
}

/** Says what the last swipe or selection action did, with Undo, until the next one replaces it. */
@Composable
private fun ChangeSnackbar(snackbar: SnackbarHostState, change: BoardChange?, onUndo: (BoardChange) -> Unit, onShown: (BoardChange) -> Unit) {
    val message = change?.let {
        val text = when (it.kind) {
            BoardChange.Kind.Archive -> R.plurals.board_archived
            BoardChange.Kind.Unarchive -> R.plurals.board_unarchived
            BoardChange.Kind.Trash -> R.plurals.board_trashed
            BoardChange.Kind.Restore -> R.plurals.board_restored
        }
        pluralStringResource(text, it.ids.size, it.ids.size)
    }
    val undo = stringResource(R.string.board_undo)
    LaunchedEffect(change) {
        if (change == null || message == null) return@LaunchedEffect
        val result = snackbar.showSnackbar(message, actionLabel = undo, duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) onUndo(change)
        onShown(change)
    }
}
