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

    /** Every note shown, top to bottom. */
    val notes: List<Note> get() = pinned + others

    /**
     * The positions that put note [id] at index [to] of its section, pinned or others, for
     * [NoteStore.move][io.github.markovcd.capynib.core.store.NoteStore.move]. Only notes whose
     * position changes are in it: the moved one, and those below it when there is no room.
     */
    fun move(id: String, to: Int): Map<String, Long> {
        val section = listOf(pinned, others).firstOrNull { notes -> notes.any { it.id == id } } ?: return emptyMap()
        val from = section.indexOfFirst { it.id == id }
        val target = to.coerceIn(0, section.lastIndex)
        if (from == target) return emptyMap()
        val order = section.toMutableList().apply { add(target, removeAt(from)) }
        val positions = order.map { it.position }.toMutableList()
        positions[target] = when {
            target == 0 -> positions[1] + 1
            target == order.lastIndex -> positions[target - 1] - 1
            positions[target - 1] - positions[target + 1] >= 2 -> (positions[target - 1] + positions[target + 1]) / 2
            else -> positions[target - 1] - 1
        }
        for (i in target + 1..order.lastIndex) {
            if (positions[i] < positions[i - 1]) break
            positions[i] = positions[i - 1] - 1
        }
        return order.indices.filter { positions[it] != order[it].position }.associate { order[it].id to positions[it] }
    }

    companion object {
        /**
         * The notes on [shelf], narrowed to those whose title or text contains [query] and that have
         * [tag]. A tag no note on the shelf has narrows nothing. Keeps the order given, except that
         * the trash shows the most recently deleted first.
         */
        fun of(notes: List<Note>, query: String = "", shelf: Shelf = Shelf.Notes, tag: String? = null): BoardContent {
            val needle = query.trim()
            val onShelf = notes.filter { Shelf.of(it) == shelf && !it.isBlank }
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
    }
}
