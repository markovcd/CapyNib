package io.github.markovcd.capynib.ui.board

import io.github.markovcd.capynib.audio.PlayingClip
import io.github.markovcd.capynib.core.board.Selection
import io.github.markovcd.capynib.core.board.Shelf
import io.github.markovcd.capynib.core.board.Swipe
import io.github.markovcd.capynib.core.note.Recording

/** What a card does when tapped and swiped on [shelf], how a drag draws it, and which recording plays. */
internal class CardBehavior(
    val drag: BoardDrag,
    val selection: Selection,
    val swipeable: Boolean,
    val shelf: Shelf,
    val onOpen: (String) -> Unit,
    val onToggle: (String) -> Unit,
    val onSwiped: (String, Swipe) -> Unit,
    val playing: PlayingClip?,
    val onPlay: (Recording) -> Unit,
)
