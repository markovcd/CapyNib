package io.github.markovcd.quicknib.core.note

/**
 * A note as the user sees it. [body] is plain text in which `- [ ] ` and `- [x] ` lines
 * are checklist items (see [Checklist]). Times are epoch milliseconds.
 */
data class Note(
    val id: String,
    val title: String,
    val body: String,
    val color: NoteColor,
    val pinned: Boolean,
    val archived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
) {
    /** Nothing written: no title, and no line (checklist items included) with any text. */
    val isBlank: Boolean get() = title.isBlank() && Checklist.parse(body).all { it.text.isBlank() }

    /** Same user-visible content, ignoring id and times. */
    fun sameContentAs(other: Note): Boolean =
        title == other.title && body == other.body && color == other.color && pinned == other.pinned && archived == other.archived
}
