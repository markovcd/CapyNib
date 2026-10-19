package io.github.markovcd.capynib.core.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class ChecklistDragTest {
    private val rows = 100

    private fun moved(body: String, from: Int, offset: Float) = ChecklistDrag.move(body, from, offset) { rows }

    @Test
    fun A_line_dragged_past_half_its_neighbor_swaps_with_it() {
        assertEquals(ChecklistDrag.Moved(listOf(0 to 1), 1, -49f), moved("- [ ] a\n- [ ] b", 0, 51f))
        assertEquals(ChecklistDrag.Moved(listOf(1 to 0), 0, 49f), moved("- [ ] a\n- [ ] b", 1, -51f))
    }

    @Test
    fun A_line_dragged_half_its_neighbor_or_less_stays() {
        assertEquals(ChecklistDrag.Moved(emptyList(), 0, 50f), moved("- [ ] a\n- [ ] b", 0, 50f))
    }

    @Test
    fun A_long_drag_passes_every_line_it_is_over_and_keeps_what_is_left_of_the_offset() {
        assertEquals(
            ChecklistDrag.Moved(listOf(0 to 1, 1 to 2), 2, 30f),
            moved("- [ ] a\n- [ ] b\n- [ ] c\n- [ ] d", 0, 230f),
        )
    }

    @Test
    fun A_line_is_measured_against_the_neighbor_it_passes() {
        val heights = mapOf(0 to 40, 1 to 300)

        assertEquals(ChecklistDrag.Moved(emptyList(), 0, 140f), ChecklistDrag.move("- [ ] a\n- [ ] b", 0, 140f) { heights.getValue(it) })
    }

    @Test
    fun An_open_line_stops_at_the_last_open_line_however_far_it_is_dragged() {
        assertEquals(ChecklistDrag.Moved(listOf(0 to 1), 1, 400f), moved("- [ ] a\ntext\n- [x] c", 0, 500f))
    }

    @Test
    fun A_ticked_item_swaps_over_the_open_lines_between_it_and_the_next_ticked_item() {
        assertEquals(ChecklistDrag.Moved(listOf(0 to 2), 2, -40f), moved("- [x] a\n- [ ] b\n- [x] c", 0, 60f))
    }

    @Test
    fun A_line_the_body_no_longer_has_does_not_move() {
        assertEquals(ChecklistDrag.Moved(emptyList(), 5, 300f), moved("- [ ] a\n- [ ] b", 5, 300f))
    }
}
