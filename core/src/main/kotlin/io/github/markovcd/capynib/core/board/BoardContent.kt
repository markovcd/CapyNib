package io.github.markovcd.capynib.core.board

import io.github.markovcd.capynib.core.note.Note
import io.github.markovcd.capynib.core.note.NoteTags

/**
 * The notes the board shows, split the way Keep shows them. [tags] are every tag on the shelf, to
 * pick from; [tag] is the one the notes are narrowed to, if any.
 */
data class BoardContent(
    val pinned: List<Note>,
    val others: List<Note>,
    val tags: List<String>,
    val tag: String?,
) {
    val isEmpty: Boolean get() = pinned.isEmpty() && others.isEmpty()

    companion object {
        /**
         * The notes on [shelf], narrowed to those whose title or text contains [query] and that have
         * [tag]. A tag no note on the shelf has narrows nothing. Keeps the order given, except that
         * the trash shows the most recently deleted first.
         */
        fun of(notes: List<Note>, query: String = "", shelf: Shelf = Shelf.Notes, tag: String? = null): BoardContent {
            val needle = query.trim()
            val onShelf = notes.filter { it.shelf == shelf && !it.isBlank }
            val tags = onShelf.flatMap { note -> NoteTags.of(note).flatMap(NoteTags::withParents) }
                .distinct()
                .sortedWith(String.CASE_INSENSITIVE_ORDER.then(naturalOrder()))
            val picked = tag?.takeIf { it in tags }
            val shown = onShelf.filter { note ->
                (needle.isEmpty() || note.title.contains(needle, ignoreCase = true) || note.body.contains(needle, ignoreCase = true)) &&
                    (picked == null || NoteTags.has(note, picked))
            }
            return when (shelf) {
                Shelf.Notes -> BoardContent(shown.filter { it.pinned }, shown.filterNot { it.pinned }, tags, picked)
                Shelf.Archive -> BoardContent(emptyList(), shown, tags, picked)
                Shelf.Trash -> BoardContent(emptyList(), shown.sortedByDescending { it.trashedAt }, tags, picked)
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
