package io.github.markovcd.capynib.ui.board

import io.github.markovcd.capynib.core.board.Selection

/** What a card does when tapped and swiped, and how a drag draws it. */
internal class CardBehavior(
    val drag: BoardDrag,
    val selection: Selection,
    val swipeable: Boolean,
    val rightSwipe: RightSwipe,
    val onOpen: (String) -> Unit,
    val onToggle: (String) -> Unit,
    val onSwiped: (String) -> Unit,
    val onSwipedToPin: (String) -> Unit,
)
