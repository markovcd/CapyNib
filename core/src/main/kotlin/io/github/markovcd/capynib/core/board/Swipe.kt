package io.github.markovcd.capynib.core.board

/**
 * What swiping a card does. Left takes a note a step toward deletion and right a step back, except
 * on the notes shelf, where right pins. [keepsCard] when the note stays where it is, so the card
 * slides back; deleting forever asks first.
 */
enum class Swipe(val keepsCard: Boolean, val change: BoardChange.Kind?) {
    Pin(keepsCard = true, change = null),
    Archive(keepsCard = false, change = BoardChange.Kind.Archive),
    Unarchive(keepsCard = false, change = BoardChange.Kind.Unarchive),
    Trash(keepsCard = false, change = BoardChange.Kind.Trash),
    Restore(keepsCard = false, change = BoardChange.Kind.Restore),
    DeleteForever(keepsCard = true, change = null),
    ;

    companion object {
        fun of(shelf: Shelf, right: Boolean): Swipe = when (shelf) {
            Shelf.Notes -> if (right) Pin else Archive
            Shelf.Archive -> if (right) Unarchive else Trash
            Shelf.Trash -> if (right) Restore else DeleteForever
        }
    }
}
