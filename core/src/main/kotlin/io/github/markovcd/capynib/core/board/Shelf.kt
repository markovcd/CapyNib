package io.github.markovcd.capynib.core.board

import io.github.markovcd.capynib.core.note.Note

/** Which notes the board shows: the notes in use, the archive, or the trash. */
enum class Shelf {
    Notes,
    Archive,
    Trash,
    ;

    companion object {
        /** The shelf [note] is on: the trash while it is there, whether or not it was archived. */
        fun of(note: Note): Shelf = when {
            note.inTrash -> Trash
            note.archived -> Archive
            else -> Notes
        }
    }
}
