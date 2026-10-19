package io.github.markovcd.capynib.ui.board

import io.github.markovcd.capynib.audio.PlayingClip
import io.github.markovcd.capynib.core.board.BoardChange
import io.github.markovcd.capynib.core.board.BoardContent
import io.github.markovcd.capynib.core.board.BoardMode
import io.github.markovcd.capynib.core.board.CalendarMonth
import io.github.markovcd.capynib.core.board.Selection
import io.github.markovcd.capynib.core.board.Shelf
import java.time.LocalDate

/** Everything the board shows; [content] is null until the store first answers. */
internal data class BoardState(
    val content: BoardContent?,
    val query: String,
    val shelf: Shelf,
    val mode: BoardMode,
    val calendar: CalendarMonth?,
    val day: LocalDate,
    val today: LocalDate,
    val selection: Selection,
    val lastChange: BoardChange?,
    val deleting: Set<String>,
    val trashDays: Int,
    val playing: PlayingClip?,
)
