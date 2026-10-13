package io.github.markovcd.capynib.core.board

import io.github.markovcd.capynib.core.note.Note

/** The notes the board shows, split the way Keep shows them. */
data class BoardContent(val pinned: List<Note>, val others: List<Note>) {
    val isEmpty: Boolean get() = pinned.isEmpty() && others.isEmpty()

    companion object {
        /**
         * The notes on [shelf], narrowed to those whose title or text contains [query]. Keeps the
         * order given, except that the trash shows the most recently deleted first.
         */
        fun of(notes: List<Note>, query: String = "", shelf: Shelf = Shelf.Notes): BoardContent {
            val needle = query.trim()
            val shown = notes.filter { note ->
                note.shelf == shelf && !note.isBlank &&
                    (needle.isEmpty() || note.title.contains(needle, ignoreCase = true) || note.body.contains(needle, ignoreCase = true))
            }
            return when (shelf) {
                Shelf.Notes -> BoardContent(shown.filter { it.pinned }, shown.filterNot { it.pinned })
                Shelf.Archive -> BoardContent(emptyList(), shown)
                Shelf.Trash -> BoardContent(emptyList(), shown.sortedByDescending { it.trashedAt })
            }
        }

        private val Note.shelf: Shelf
            get() = when {
                inTrash -> Shelf.Trash
                archived -> Shelf.Archive
                else -> Shelf.Notes
            }
    }
}
