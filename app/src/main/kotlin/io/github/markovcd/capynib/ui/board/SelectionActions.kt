package io.github.markovcd.capynib.ui.board

import io.github.markovcd.capynib.core.note.NoteColor

/** What the selection's bar and a long press do. */
class SelectionActions(
    val toggle: (String) -> Unit,
    val clear: () -> Unit,
    val pin: () -> Unit,
    val color: (NoteColor) -> Unit,
    val archive: () -> Unit,
    val trash: () -> Unit,
)
