package io.github.markovcd.capynib.core.note

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistTest {
    @Test
    fun Item_lines_read_as_items_and_other_lines_as_text() {
        assertEquals(
            listOf(
                ChecklistLine.Text("Groceries"),
                ChecklistLine.Item("milk", checked = false),
                ChecklistLine.Item("eggs", checked = true),
                ChecklistLine.Item("bread", checked = true),
            ),
            Checklist.parse("Groceries\n- [ ] milk\n- [x] eggs\n- [X] bread"),
        )
    }

    @Test
    fun An_empty_body_has_no_lines() {
        assertEquals(emptyList<ChecklistLine>(), Checklist.parse(""))
    }

    @Test
    fun A_parsed_body_renders_back_to_the_same_text() {
        val body = "intro\n- [ ] one\n- [x] two\n\n- [ ] "
        assertEquals(body, Checklist.render(Checklist.parse(body)))
    }

    @Test
    fun Ticking_an_item_checks_it_and_ticking_again_unchecks_it() {
        val ticked = Checklist.toggle("- [ ] milk\n- [ ] eggs", 1)
        assertEquals("- [ ] milk\n- [x] eggs", ticked)
        assertEquals("- [ ] milk\n- [ ] eggs", Checklist.toggle(ticked, 1))
    }

    @Test
    fun Turning_text_into_a_checklist_makes_each_non_blank_line_an_item() {
        assertEquals("- [ ] milk\n- [ ] eggs", Checklist.toChecklist("milk\n\neggs"))
    }

    @Test
    fun Turning_an_empty_note_into_a_checklist_gives_one_empty_item() {
        assertEquals("- [ ] ", Checklist.toChecklist(""))
    }

    @Test
    fun Hiding_checkboxes_keeps_the_item_text() {
        assertEquals("milk\neggs", Checklist.toText("- [ ] milk\n- [x] eggs"))
    }

    @Test
    fun A_new_item_goes_right_after_the_one_it_was_added_from() {
        assertEquals("- [ ] a\n- [ ] new\n- [ ] b", Checklist.insertItemAfter("- [ ] a\n- [ ] b", 0, "new"))
        assertEquals("- [ ] first\n- [ ] a", Checklist.insertItemAfter("- [ ] a", -1, "first"))
    }

    @Test
    fun Removing_an_item_leaves_the_others() {
        assertEquals("- [ ] a\n- [ ] c", Checklist.remove("- [ ] a\n- [ ] b\n- [ ] c", 1))
    }

    @Test
    fun Editing_an_item_keeps_its_check() {
        assertEquals("- [x] oat milk", Checklist.setText("- [x] milk", 0, "oat milk"))
    }

    @Test
    fun A_body_is_a_checklist_when_any_line_is_an_item() {
        assertTrue(Checklist.isChecklist("text\n- [ ] item"))
        assertFalse(Checklist.isChecklist("text\n- not an item"))
    }
}
