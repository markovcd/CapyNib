package io.github.markovcd.capynib.core.board

import org.junit.Assert.assertEquals
import org.junit.Test

class CardDragTest {
    private val order = listOf("a", "b", "c", "d")

    @Test
    fun A_card_dragged_down_over_another_takes_its_place_and_those_between_move_up() {
        assertEquals(listOf("b", "c", "a", "d"), CardDrag.over(order, "a", over = "c"))
    }

    @Test
    fun A_card_dragged_up_over_another_takes_its_place_and_those_between_move_down() {
        assertEquals(listOf("a", "d", "b", "c"), CardDrag.over(order, "d", over = "b"))
    }

    @Test
    fun A_card_over_itself_or_a_card_of_another_section_moves_nothing() {
        assertEquals(order, CardDrag.over(order, "b", over = "b"))
        assertEquals(order, CardDrag.over(order, "b", over = "pinned"))
    }
}
