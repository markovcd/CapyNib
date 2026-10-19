package io.github.markovcd.capynib.ui.board

import io.github.markovcd.capynib.core.board.BoardChange
import io.github.markovcd.capynib.core.board.BoardMode
import io.github.markovcd.capynib.core.board.Shelf
import io.github.markovcd.capynib.core.board.Swipe
import io.github.markovcd.capynib.core.note.Recording
import java.time.LocalDate

/** What the board's controls and gestures do; [selection] is what the selection bar does. */
internal class BoardActions(
    val onQuery: (String) -> Unit,
    val onShelf: (Shelf) -> Unit,
    val onMode: (BoardMode) -> Unit,
    val onMonth: (Long) -> Unit,
    val onDay: (LocalDate) -> Unit,
    val onTag: (String?) -> Unit,
    val onOpen: (String) -> Unit,
    val onNew: (checklist: Boolean) -> Unit,
    val onSettings: () -> Unit,
    val selection: SelectionActions,
    val onSwiped: (String, Swipe) -> Unit,
    val onPlay: (Recording) -> Unit,
    val onConfirmDelete: () -> Unit,
    val onCancelDelete: () -> Unit,
    val onMove: (String, Int) -> Unit,
    val onUndo: (BoardChange) -> Unit,
    val onChangeShown: (BoardChange) -> Unit,
)
