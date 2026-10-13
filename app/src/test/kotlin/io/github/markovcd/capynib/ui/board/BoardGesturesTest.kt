package io.github.markovcd.capynib.ui.board

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
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

    // The store answers on a background thread, so the screen catches up a moment after a change.
    private fun waitForStore(condition: () -> Boolean) = compose.waitUntil(timeoutMillis = 5_000) { condition() }

    private fun awaitTag(tag: String) = compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    private fun awaitNoTag(tag: String) = compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() }

    /** Notes made in order, so the last is on top. */
    private fun notes(vararg titles: String): List<String> {
        val ids = titles.map { graph.store.create(graph.clock(), title = it).id }
        ids.forEach { awaitTag(Tags.card(it)) }
        return ids
    }

    private fun longPress(id: String) = compose.onNodeWithTag(Tags.card(id)).performTouchInput { longClick() }

    private fun center(id: String): Offset = compose.onNodeWithTag(Tags.card(id)).fetchSemanticsNode().boundsInRoot.center

    @Test
    fun A_long_press_picks_a_note_and_a_tap_then_picks_another_instead_of_opening_it() {
        val (a, b) = notes("a", "b")

        longPress(a)
        awaitTag(Tags.SELECTION_BAR)
        compose.onNodeWithTag(Tags.card(b)).performClick()

        compose.onNodeWithTag(Tags.card(a)).assertIsSelected()
        compose.onNodeWithTag(Tags.card(b)).assertIsSelected()
        compose.onNodeWithTag(Tags.SELECTED_COUNT).assertTextEquals("2 selected")
        compose.onNodeWithTag(Tags.CLOSE_SELECTION).performClick()
        awaitNoTag(Tags.SELECTION_BAR)
        compose.onNodeWithTag(Tags.card(a)).assertIsNotSelected()
    }

    @Test
    fun Archiving_the_picked_notes_archives_them_all_and_undo_brings_them_back() {
        val (a, b, c) = notes("a", "b", "c")
        longPress(a)
        awaitTag(Tags.SELECTION_BAR)
        compose.onNodeWithTag(Tags.card(b)).performClick()

        compose.onNodeWithTag(Tags.ARCHIVE_SELECTED).performClick()

        waitForStore { graph.store.get(a)!!.archived && graph.store.get(b)!!.archived }
        assertEquals(false, graph.store.get(c)!!.archived)
        awaitNoTag(Tags.SELECTION_BAR)
        awaitTag(Tags.UNDO)
        compose.onNodeWithTag(Tags.UNDO).performClick()
        waitForStore { !graph.store.get(a)!!.archived && !graph.store.get(b)!!.archived }
    }

    @Test
    fun Deleting_the_picked_notes_moves_them_to_the_trash() {
        val (a, b) = notes("a", "b")
        longPress(a)
        awaitTag(Tags.SELECTION_BAR)

        compose.onNodeWithTag(Tags.TRASH_SELECTED).performClick()

        waitForStore { graph.store.get(a)!!.inTrash }
        assertEquals(false, graph.store.get(b)!!.inTrash)
        awaitNoTag(Tags.card(a))
    }

    @Test
    fun The_pin_action_pins_every_picked_note_and_unpins_them_when_all_are_pinned() {
        val (a, b) = notes("a", "b")
        graph.store.save(graph.store.get(a)!!.copy(pinned = true), graph.clock())
        longPress(a)
        awaitTag(Tags.SELECTION_BAR)
        compose.onNodeWithTag(Tags.card(b)).performClick()

        compose.onNodeWithTag(Tags.PIN_SELECTED).performClick()
        waitForStore { graph.store.get(a)!!.pinned && graph.store.get(b)!!.pinned }

        compose.onNodeWithTag(Tags.PIN_SELECTED).performClick()
        waitForStore { !graph.store.get(a)!!.pinned && !graph.store.get(b)!!.pinned }
    }

    @Test
    fun Picking_a_color_colors_every_picked_note() {
        val (a, b) = notes("a", "b")
        longPress(a)
        awaitTag(Tags.SELECTION_BAR)
        compose.onNodeWithTag(Tags.card(b)).performClick()

        compose.onNodeWithTag(Tags.COLOR_SELECTED).performClick()
        awaitTag(Tags.color(NoteColor.Mint.key))
        compose.onNodeWithTag(Tags.color(NoteColor.Mint.key)).performClick()

        waitForStore { listOf(a, b).all { graph.store.get(it)!!.color == NoteColor.Mint } }
    }

    @Test
    fun Swiping_a_note_left_archives_it_and_undo_brings_it_back() {
        val (a) = notes("a")

        compose.onNodeWithTag(Tags.card(a)).performTouchInput { swipeLeft() }

        waitForStore { graph.store.get(a)!!.archived }
        awaitTag(Tags.UNDO)
        compose.onNodeWithTag(Tags.UNDO).performClick()
        waitForStore { !graph.store.get(a)!!.archived }
        awaitTag(Tags.card(a))
    }

    @Test
    fun Swiping_a_note_right_pins_it_and_again_unpins_it() {
        val (a) = notes("a")

        compose.onNodeWithTag(Tags.card(a)).performTouchInput { swipeRight() }
        waitForStore { graph.store.get(a)!!.pinned }
        awaitTag(Tags.PINNED)

        compose.onNodeWithTag(Tags.card(a)).performTouchInput { swipeRight() }
        waitForStore { !graph.store.get(a)!!.pinned }
        assertEquals(false, graph.store.get(a)!!.archived)
    }

    @Test
    fun Swiping_a_note_in_the_archive_takes_it_out() {
        val (a) = notes("a")
        graph.store.save(graph.store.get(a)!!.copy(archived = true), graph.clock())
        compose.onNodeWithTag(Tags.ARCHIVE_TOGGLE).performClick()
        awaitTag(Tags.card(a))

        compose.onNodeWithTag(Tags.card(a)).performTouchInput { swipeLeft() }

        waitForStore { !graph.store.get(a)!!.archived }
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

        waitForStore { graph.store.visible().map { it.title } == listOf("bottom", "top") }
        compose.onNodeWithTag(Tags.card(bottom)).assertIsNotSelected()
        awaitNoTag(Tags.SELECTION_BAR)
    }
}
