package io.github.markovcd.capynib.core.board

import org.junit.Assert.assertEquals
import org.junit.Test

class SwipeTest {
    private fun seen(shelf: Shelf, pinned: Boolean = false) = Swipe.of(shelf, right = false, pinned) to Swipe.of(shelf, right = true, pinned)

    @Test
    fun On_the_board_left_archives_and_right_pins() {
        assertEquals(Swipe.Archive to Swipe.Pin, seen(Shelf.Notes))
    }

    @Test
    fun On_the_board_either_way_unpins_a_pinned_note() {
        assertEquals(Swipe.Pin to Swipe.Pin, seen(Shelf.Notes, pinned = true))
    }

    @Test
    fun In_the_archive_left_trashes_and_right_unarchives() {
        assertEquals(Swipe.Trash to Swipe.Unarchive, seen(Shelf.Archive))
    }

    @Test
    fun In_the_trash_left_deletes_forever_and_right_restores() {
        assertEquals(Swipe.DeleteForever to Swipe.Restore, seen(Shelf.Trash))
    }

    @Test
    fun Deleting_forever_keeps_the_card_until_it_is_confirmed() {
        assertEquals(true, Swipe.DeleteForever.keepsCard)
        assertEquals(null, Swipe.DeleteForever.change)
    }
}
