package io.github.markovcd.capynib.core.board

import io.github.markovcd.capynib.core.note.Note
import io.github.markovcd.capynib.core.note.NoteColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionTest {
    private fun note(id: String, pinned: Boolean = false) = Note(id, id, "", NoteColor.Default, pinned, archived = false, 0, 0, 0)

    @Test
    fun Picking_a_note_twice_drops_it_and_an_empty_selection_is_inactive() {
        val picked = Selection().toggle("a").toggle("b").toggle("a")

        assertEquals(setOf("b"), picked.ids)
        assertFalse(picked.toggle("b").isActive)
    }

    @Test
    fun The_pin_action_unpins_only_when_every_picked_note_is_pinned() {
        val notes = listOf(note("a", pinned = true), note("b"), note("c", pinned = true))

        assertTrue(Selection(setOf("a", "b")).pins(notes))
        assertFalse(Selection(setOf("a", "c")).pins(notes))
    }

    @Test
    fun A_picked_note_that_leaves_the_board_is_no_longer_picked() {
        val board = BoardContent.of(listOf(note("a"), note("b").copy(archived = true)))

        assertEquals(setOf("a"), Selection(setOf("a", "b")).within(board).ids)
    }
}
