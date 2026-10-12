package io.github.markovcd.quicknib.core.board

import io.github.markovcd.quicknib.core.note.Note

/** The notes the board shows, split the way Keep shows them. */
data class BoardContent(val pinned: List<Note>, val others: List<Note>) {
    val isEmpty: Boolean get() = pinned.isEmpty() && others.isEmpty()

    companion object {
        /**
         * Notes in view: archived ones in [archive], the rest otherwise, narrowed to those whose
         * title or text contains [query]. Keeps the order given.
         */
        fun of(notes: List<Note>, query: String = "", archive: Boolean = false): BoardContent {
            val needle = query.trim()
            val shown = notes.filter { note ->
                note.archived == archive && !note.isBlank &&
                    (needle.isEmpty() || note.title.contains(needle, ignoreCase = true) || note.body.contains(needle, ignoreCase = true))
            }
            return if (archive) {
                BoardContent(emptyList(), shown)
            } else {
                BoardContent(shown.filter { it.pinned }, shown.filterNot { it.pinned })
            }
        }
    }
}
