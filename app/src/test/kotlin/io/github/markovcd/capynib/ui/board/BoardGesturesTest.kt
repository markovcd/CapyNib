package io.github.markovcd.capynib.ui.board

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import io.github.markovcd.capynib.AppGraph
import io.github.markovcd.capynib.TestGraphs
import io.github.markovcd.capynib.core.note.NoteColor
import io.github.markovcd.capynib.ui.CapynibRoot
import io.github.markovcd.capynib.ui.Tags
import io.github.markovcd.capynib.ui.awaitNoTag
import io.github.markovcd.capynib.ui.awaitTag
import io.github.markovcd.capynib.ui.waitForStore
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Long press, drag and swipe on the board's cards, over a real store. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class BoardGesturesTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var graph: AppGraph

    @Before
    fun start() {
        graph = TestGraphs.create()
        compose.setContent { CapynibRoot(graph) }
    }

    /** Notes made in order, so the last is on top. */
    private fun notes(vararg titles: String): List<String> {
        val ids = titles.map { graph.store.create(graph.clock(), title = it).id }
        ids.forEach { compose.awaitTag(Tags.card(it)) }
        return ids
    }

    private fun longPress(id: String) = compose.onNodeWithTag(Tags.card(id)).performTouchInput { longClick() }

    private fun center(id: String): Offset = compose.onNodeWithTag(Tags.card(id)).fetchSemanticsNode().boundsInRoot.center

    @Test
    fun A_long_press_picks_a_note_and_a_tap_then_picks_another_instead_of_opening_it() {
        val (a, b) = notes("a", "b")

        longPress(a)
        compose.awaitTag(Tags.SELECTION_BAR)
        compose.onNodeWithTag(Tags.card(b)).performClick()

        compose.onNodeWithTag(Tags.card(a)).assertIsSelected()
        compose.onNodeWithTag(Tags.card(b)).assertIsSelected()
        compose.onNodeWithTag(Tags.SELECTED_COUNT).assertTextEquals("2 selected")
        compose.onNodeWithTag(Tags.CLOSE_SELECTION).performClick()
        compose.awaitNoTag(Tags.SELECTION_BAR)
        compose.onNodeWithTag(Tags.card(a)).assertIsNotSelected()
    }

    @Test
    fun A_long_press_leaves_the_board_where_it_was_so_the_pressed_note_stays_picked() {
        val (a, b) = listOf("a", "b").map { graph.store.create(graph.clock(), title = it, body = "#travel").id }
        compose.awaitTag(Tags.card(a))
        compose.awaitTag(Tags.TAG_BAR)
        val before = center(a)

        longPress(a)
        compose.awaitTag(Tags.SELECTION_BAR)
        compose.waitForIdle()

        assertEquals(before, center(a))
        compose.onNodeWithTag(Tags.TAG_BAR).assertExists()
        compose.onNodeWithTag(Tags.card(a)).assertIsSelected()
        compose.onNodeWithTag(Tags.card(b)).assertIsNotSelected()
    }

    @Test
    fun Archiving_the_picked_notes_archives_them_all_and_undo_brings_them_back() {
        val (a, b, c) = notes("a", "b", "c")
        longPress(a)
        compose.awaitTag(Tags.SELECTION_BAR)
        compose.onNodeWithTag(Tags.card(b)).performClick()

        compose.onNodeWithTag(Tags.ARCHIVE_SELECTED).performClick()

        compose.waitForStore { graph.store.get(a)!!.archived && graph.store.get(b)!!.archived }
        assertEquals(false, graph.store.get(c)!!.archived)
        compose.awaitNoTag(Tags.SELECTION_BAR)
        compose.awaitTag(Tags.UNDO)
        compose.onNodeWithTag(Tags.UNDO).performClick()
        compose.waitForStore { !graph.store.get(a)!!.archived && !graph.store.get(b)!!.archived }
    }

    @Test
    fun Deleting_the_picked_notes_moves_them_to_the_trash() {
        val (a, b) = notes("a", "b")
        longPress(a)
        compose.awaitTag(Tags.SELECTION_BAR)

        compose.onNodeWithTag(Tags.TRASH_SELECTED).performClick()

        compose.waitForStore { graph.store.get(a)!!.inTrash }
        assertEquals(false, graph.store.get(b)!!.inTrash)
        compose.awaitNoTag(Tags.card(a))
    }

    @Test
    fun The_pin_action_pins_every_picked_note_and_unpins_them_when_all_are_pinned() {
        val (a, b) = notes("a", "b")
        graph.store.save(graph.store.get(a)!!.copy(pinned = true), graph.clock())
        longPress(a)
        compose.awaitTag(Tags.SELECTION_BAR)
        compose.onNodeWithTag(Tags.card(b)).performClick()
        compose.awaitTag(Tags.OTHERS)

        compose.onNodeWithTag(Tags.PIN_SELECTED).performClick()
        compose.waitForStore { graph.store.get(a)!!.pinned && graph.store.get(b)!!.pinned }
        // The bar acts on the board it shows, so the next tap waits for both notes to show as pinned.
        compose.awaitNoTag(Tags.OTHERS)

        compose.onNodeWithTag(Tags.PIN_SELECTED).performClick()
        compose.waitForStore { !graph.store.get(a)!!.pinned && !graph.store.get(b)!!.pinned }
    }

    @Test
    fun Picking_a_color_colors_every_picked_note() {
        val (a, b) = notes("a", "b")
        longPress(a)
        compose.awaitTag(Tags.SELECTION_BAR)
        compose.onNodeWithTag(Tags.card(b)).performClick()

        compose.onNodeWithTag(Tags.COLOR_SELECTED).performClick()
        compose.awaitTag(Tags.color(NoteColor.Mint.key))
        compose.onNodeWithTag(Tags.color(NoteColor.Mint.key)).performClick()

        compose.waitForStore { listOf(a, b).all { graph.store.get(it)!!.color == NoteColor.Mint } }
    }

    @Test
    fun Swiping_a_note_left_archives_it_and_undo_brings_it_back() {
        val (a) = notes("a")

        compose.onNodeWithTag(Tags.card(a)).performTouchInput { swipeLeft() }

        compose.waitForStore { graph.store.get(a)!!.archived }
        compose.awaitTag(Tags.UNDO)
        compose.onNodeWithTag(Tags.UNDO).performClick()
        compose.waitForStore { !graph.store.get(a)!!.archived }
        compose.awaitTag(Tags.card(a))
    }

    @Test
    fun Swiping_a_note_right_pins_it_and_again_unpins_it() {
        val (a) = notes("a")

        compose.onNodeWithTag(Tags.card(a)).performTouchInput { swipeRight() }
        compose.waitForStore { graph.store.get(a)!!.pinned }
        compose.awaitTag(Tags.PINNED)

        compose.onNodeWithTag(Tags.card(a)).performTouchInput { swipeRight() }
        compose.waitForStore { !graph.store.get(a)!!.pinned }
        assertEquals(false, graph.store.get(a)!!.archived)
    }

    @Test
    fun A_note_swiped_into_the_archive_stays_there_when_the_archive_is_opened() {
        // The other note keeps the board, and its grid, on screen while the archive opens.
        val (a, _) = notes("a", "b")
        compose.onNodeWithTag(Tags.card(a)).performTouchInput { swipeLeft() }
        compose.waitForStore { graph.store.get(a)!!.archived }
        compose.awaitNoTag(Tags.card(a))

        compose.onNodeWithTag(Tags.ARCHIVE_TOGGLE).performClick()
        compose.awaitTag(Tags.card(a))
        compose.waitForIdle()

        assertEquals(true, graph.store.get(a)!!.archived)
    }

    @Test
    fun Swiping_a_note_right_in_the_archive_takes_it_out_and_left_moves_it_to_the_trash() {
        val (a, b) = notes("a", "b")
        graph.store.saveAll(setOf(a, b), graph.clock()) { it.copy(archived = true) }
        compose.onNodeWithTag(Tags.ARCHIVE_TOGGLE).performClick()
        compose.awaitTag(Tags.card(a))
        compose.awaitTag(Tags.card(b))

        compose.onNodeWithTag(Tags.card(a)).performTouchInput { swipeRight() }
        compose.onNodeWithTag(Tags.card(b)).performTouchInput { swipeLeft() }

        compose.waitForStore { !graph.store.get(a)!!.archived && graph.store.get(b)!!.inTrash }
        assertEquals(false, graph.store.get(a)!!.pinned)
        assertEquals(false, graph.store.get(a)!!.inTrash)
    }

    @Test
    fun Dragging_a_note_after_a_long_press_moves_it_without_picking_it() {
        val (bottom, top) = notes("bottom", "top")
        val by = center(top) - center(bottom)

        compose.onNodeWithTag(Tags.card(bottom)).performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            repeat(10) { moveBy(by / 10f) }
            up()
        }

        compose.waitForStore { graph.store.visible().map { it.title } == listOf("bottom", "top") }
        compose.onNodeWithTag(Tags.card(bottom)).assertIsNotSelected()
        compose.awaitNoTag(Tags.SELECTION_BAR)
    }

    /** Notes made and moved to the trash, with the trash open. */
    private fun trashed(vararg titles: String): List<String> {
        val ids = titles.map { graph.store.create(graph.clock(), title = it).id }
        graph.store.trashAll(ids, graph.clock())
        compose.onNodeWithTag(Tags.TRASH_TOGGLE).performClick()
        ids.forEach { compose.awaitTag(Tags.card(it)) }
        return ids
    }

    @Test
    fun Restoring_the_picked_notes_in_the_trash_takes_them_out_and_undo_puts_them_back() {
        val (a, b, c) = trashed("a", "b", "c")
        longPress(a)
        compose.awaitTag(Tags.SELECTION_BAR)
        compose.onNodeWithTag(Tags.card(b)).performClick()

        compose.onNodeWithTag(Tags.RESTORE_SELECTED).performClick()

        compose.waitForStore { !graph.store.get(a)!!.inTrash && !graph.store.get(b)!!.inTrash }
        assertEquals(true, graph.store.get(c)!!.inTrash)
        compose.awaitTag(Tags.UNDO)
        compose.onNodeWithTag(Tags.UNDO).performClick()
        compose.waitForStore { graph.store.get(a)!!.inTrash && graph.store.get(b)!!.inTrash }
    }

    @Test
    fun Deleting_the_picked_notes_forever_asks_first_and_cancel_keeps_them() {
        val (a, b) = trashed("a", "b")
        longPress(a)
        compose.awaitTag(Tags.SELECTION_BAR)

        compose.onNodeWithTag(Tags.DELETE_FOREVER_SELECTED).performClick()
        compose.awaitTag(Tags.CANCEL_DELETE_FOREVER)
        compose.onNodeWithTag(Tags.CANCEL_DELETE_FOREVER).performClick()
        compose.awaitNoTag(Tags.CANCEL_DELETE_FOREVER)
        assertEquals(true, graph.store.get(a)!!.inTrash)

        compose.onNodeWithTag(Tags.DELETE_FOREVER_SELECTED).performClick()
        compose.awaitTag(Tags.CONFIRM_DELETE_FOREVER)
        compose.onNodeWithTag(Tags.CONFIRM_DELETE_FOREVER).performClick()

        compose.waitForStore { graph.store.get(a) == null }
        assertEquals(true, graph.store.get(b)!!.inTrash)
        compose.awaitNoTag(Tags.card(a))
    }

    @Test
    fun Swiping_a_note_right_in_the_trash_restores_it_and_undo_puts_it_back() {
        val (a) = trashed("a")

        compose.onNodeWithTag(Tags.card(a)).performTouchInput { swipeRight() }

        compose.waitForStore { !graph.store.get(a)!!.inTrash }
        assertEquals(false, graph.store.get(a)!!.pinned)
        compose.awaitTag(Tags.UNDO)
        compose.onNodeWithTag(Tags.UNDO).performClick()
        compose.waitForStore { graph.store.get(a)!!.inTrash }
    }

    @Test
    fun Swiping_a_note_left_in_the_trash_asks_before_deleting_it_forever() {
        val (a) = trashed("a")

        compose.onNodeWithTag(Tags.card(a)).performTouchInput { swipeLeft() }
        compose.awaitTag(Tags.CANCEL_DELETE_FOREVER)
        compose.onNodeWithTag(Tags.CANCEL_DELETE_FOREVER).performClick()
        compose.awaitNoTag(Tags.CANCEL_DELETE_FOREVER)
        assertEquals(true, graph.store.get(a)!!.inTrash)

        compose.onNodeWithTag(Tags.card(a)).performTouchInput { swipeLeft() }
        compose.awaitTag(Tags.CONFIRM_DELETE_FOREVER)
        compose.onNodeWithTag(Tags.CONFIRM_DELETE_FOREVER).performClick()

        compose.waitForStore { graph.store.get(a) == null }
    }
}
