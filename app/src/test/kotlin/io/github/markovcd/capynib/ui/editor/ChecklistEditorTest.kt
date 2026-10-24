package io.github.markovcd.capynib.ui.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import io.github.markovcd.capynib.AppGraph
import io.github.markovcd.capynib.TestGraphs
import io.github.markovcd.capynib.ui.CapynibRoot
import io.github.markovcd.capynib.ui.Tags
import io.github.markovcd.capynib.ui.awaitTag
import io.github.markovcd.capynib.ui.openNote
import io.github.markovcd.capynib.ui.waitForStore
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/** A list's items typed, ticked and dragged into a new order, over a real store. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ChecklistEditorTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var graph: AppGraph

    @Before
    fun start() {
        graph = TestGraphs.create()
        compose.setContent { CapynibRoot(graph) }
    }

    /**
     * Drags line [index] by its handle down by [rows] rows (up when negative), in small steps as a finger does.
     * A row is the distance from line [index]'s handle to that of line [neighbor], drawn right next to it.
     */
    private fun dragItem(index: Int, rows: Float, neighbor: Int = index + 1) {
        val row = abs(
            compose.onNodeWithTag(Tags.itemDrag(neighbor)).fetchSemanticsNode().boundsInRoot.center.y -
                compose.onNodeWithTag(Tags.itemDrag(index)).fetchSemanticsNode().boundsInRoot.center.y,
        )
        compose.onNodeWithTag(Tags.itemDrag(index)).performTouchInput {
            down(center)
            repeat(20) { moveBy(Offset(0f, row * rows / 20)) }
            up()
        }
    }

    @Test
    fun A_new_list_opens_ready_to_type_its_first_item() {
        compose.onNodeWithTag(Tags.NEW_LIST).performClick()

        compose.awaitTag(Tags.item(0))
        compose.onNodeWithTag(Tags.item(0)).assertIsFocused()
    }

    @Test
    fun A_new_list_takes_items_that_can_be_ticked() {
        compose.onNodeWithTag(Tags.NEW_LIST).performClick()
        compose.onNodeWithTag(Tags.item(0)).performTextInput("milk")
        compose.onNodeWithTag(Tags.ADD_ITEM).performClick()
        compose.onNodeWithTag(Tags.item(1)).performTextInput("eggs")
        compose.onNodeWithTag(Tags.itemCheck(1)).performClick()

        compose.waitForStore { graph.store.visible().singleOrNull()?.body == "- [ ] milk\n- [x] eggs" }
    }

    @Test
    fun Showing_checkboxes_turns_a_note_into_a_list() {
        val id = graph.store.create(graph.clock(), body = "milk\neggs").id
        compose.openNote(id)

        compose.onNodeWithTag(Tags.CHECKBOXES).performClick()
        compose.onNodeWithTag(Tags.itemCheck(0)).performClick()

        compose.waitForStore { graph.store.get(id)?.body == "- [x] milk\n- [ ] eggs" }
    }

    @Test
    fun An_item_dragged_by_its_handle_moves_to_where_it_is_dropped() {
        val id = graph.store.create(graph.clock(), body = "- [ ] a\n- [ ] b\n- [ ] c").id
        compose.openNote(id)
        compose.awaitTag(Tags.itemDrag(2))

        dragItem(0, rows = 2.2f)
        compose.waitForStore { graph.store.get(id)?.body == "- [ ] b\n- [ ] c\n- [ ] a" }

        dragItem(2, rows = -1.2f, neighbor = 1)
        compose.waitForStore { graph.store.get(id)?.body == "- [ ] b\n- [ ] a\n- [ ] c" }
    }

    @Test
    fun A_dragged_item_leaves_the_ticked_items_where_they_are() {
        val id = graph.store.create(graph.clock(), body = "- [ ] a\n- [x] done\n- [ ] b").id
        compose.openNote(id)
        compose.awaitTag(Tags.itemDrag(2))

        dragItem(0, rows = 1.2f, neighbor = 2)

        compose.waitForStore { graph.store.get(id)?.body == "- [ ] b\n- [x] done\n- [ ] a" }
    }

    @Test
    fun An_item_dragged_past_a_ticked_one_keeps_following_the_finger() {
        val id = graph.store.create(graph.clock(), body = "- [ ] a\n- [x] done\n- [ ] b\n- [ ] c").id
        compose.openNote(id)
        compose.awaitTag(Tags.itemDrag(3))

        dragItem(0, rows = 2.2f, neighbor = 2)

        compose.waitForStore { graph.store.get(id)?.body == "- [ ] b\n- [x] done\n- [ ] c\n- [ ] a" }
    }
}
