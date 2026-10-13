package io.github.markovcd.capynib.core.board

import io.github.markovcd.capynib.core.note.Note

/** The notes picked on the board by a long press, which the selection's actions apply to together. */
data class Selection(val ids: Set<String> = emptySet()) {
    val isActive: Boolean get() = ids.isNotEmpty()

    fun toggle(id: String): Selection = Selection(if (id in ids) ids - id else ids + id)

    /** Only the picked notes still in [content], so a note that left the board is not acted on. */
    fun within(content: BoardContent): Selection {
        val shown = (content.pinned + content.others).mapTo(mutableSetOf()) { it.id }
        return if (shown.containsAll(ids)) this else Selection(ids intersect shown)
    }

    /** What the pin action sets: unpins when every picked note is pinned, as Keep does, and pins otherwise. */
    fun pins(notes: List<Note>): Boolean = !notes.filter { it.id in ids }.all { it.pinned }
}
