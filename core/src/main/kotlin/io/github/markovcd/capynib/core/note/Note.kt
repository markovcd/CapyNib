package io.github.markovcd.capynib.core.note

/**
 * A note as the user sees it. [body] is plain text in which `- [ ] ` and `- [x] ` lines
 * are checklist items (see [Checklist]). Times are epoch milliseconds; [trashedAt] is set while
 * the note is in the trash. [position] orders the board, highest first; it is the memo's create
 * time in epoch seconds (ADR 0008). [recordings] are the voice clips in it and
 * [photos] its images, each oldest first.
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
    val position: Long,
    val trashedAt: Long? = null,
    val recordings: List<Recording> = emptyList(),
    val photos: List<Photo> = emptyList(),
) {
    /** Nothing in it: no title, no line (checklist items included) with any text, no recording and no photo. */
    val isBlank: Boolean get() = title.isBlank() && Checklist.parse(body).all { it.text.isBlank() } && recordings.isEmpty() && photos.isEmpty()

    val inTrash: Boolean get() = trashedAt != null

    /** Same user-visible content, ignoring id, times, recordings and photos. */
    fun sameContentAs(other: Note): Boolean =
        title == other.title && body == other.body && color == other.color && pinned == other.pinned && archived == other.archived
}
