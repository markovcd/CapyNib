package io.github.markovcd.capynib.ui.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.github.markovcd.capynib.AppGraph
import io.github.markovcd.capynib.TestGraphs
import io.github.markovcd.capynib.core.note.NoteColor
import io.github.markovcd.capynib.ui.CapynibRoot
import io.github.markovcd.capynib.ui.Tags
import io.github.markovcd.capynib.ui.awaitNoTag
import io.github.markovcd.capynib.ui.awaitTag
import io.github.markovcd.capynib.ui.awaitText
import io.github.markovcd.capynib.ui.openNote
import io.github.markovcd.capynib.ui.waitForStore
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Writing, coloring and deleting a note in the editor, over a real store. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class EditorTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var graph: AppGraph

    @Before
    fun start() {
        graph = TestGraphs.create()
        compose.setContent { CapynibRoot(graph) }
    }

    @Test
    fun A_new_note_is_typed_saved_and_shown_on_the_board() {
        compose.onNodeWithTag(Tags.NEW_NOTE).performClick()
        compose.onNodeWithTag(Tags.TITLE).performTextInput("Groceries")
        compose.onNodeWithTag(Tags.BODY).performTextInput("milk")
        compose.waitForStore { graph.store.visible().singleOrNull()?.body == "milk" }

        compose.onNodeWithTag(Tags.BACK).performClick()

        compose.awaitText("Groceries")
        assertEquals("Groceries", graph.store.visible().single().title)
    }

    @Test
    fun A_new_note_opens_ready_to_type() {
        compose.onNodeWithTag(Tags.NEW_NOTE).performClick()

        compose.awaitTag(Tags.BODY)
        compose.onNodeWithTag(Tags.BODY).assertIsFocused()
    }

    @Test
    fun Opening_a_note_from_the_board_does_not_bring_up_the_keyboard() {
        val id = graph.store.create(graph.clock(), body = "just reading").id
        compose.openNote(id)

        compose.awaitTag(Tags.BODY)
        compose.onNodeWithTag(Tags.BODY).assertIsNotFocused()
    }

    @Test
    fun A_note_left_empty_is_thrown_away() {
        compose.onNodeWithTag(Tags.NEW_NOTE).performClick()
        compose.onNodeWithTag(Tags.BACK).performClick()

        compose.waitForStore { graph.store.visible().isEmpty() }
        compose.awaitTag(Tags.EMPTY_BOARD)
    }

    @Test
    fun Picking_a_color_colors_the_note() {
        val id = graph.store.create(graph.clock(), body = "paint me").id
        compose.openNote(id)

        compose.onNodeWithTag(Tags.PALETTE).performClick()
        compose.onNodeWithTag(Tags.color(NoteColor.Mint.key)).performClick()

        compose.waitForStore { graph.store.get(id)?.color == NoteColor.Mint }
    }

    @Test
    fun The_colors_stay_folded_behind_the_palette_button_until_it_is_tapped() {
        compose.openNote(graph.store.create(graph.clock(), body = "paint me").id)
        val mint = Tags.color(NoteColor.Mint.key)
        compose.onNodeWithTag(mint).assertDoesNotExist()

        compose.onNodeWithTag(Tags.PALETTE).performClick()
        compose.onNodeWithTag(mint).assertIsDisplayed()

        compose.onNodeWithTag(Tags.PALETTE).performClick()
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithTag(mint).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun Tapping_into_the_note_folds_the_colors_away() {
        compose.openNote(graph.store.create(graph.clock(), body = "paint me").id)
        val mint = Tags.color(NoteColor.Mint.key)
        compose.onNodeWithTag(Tags.PALETTE).performClick()
        compose.onNodeWithTag(mint).assertIsDisplayed()

        compose.onNodeWithTag(Tags.BODY).performClick()

        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithTag(mint).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun Deleting_a_note_moves_it_to_the_trash_where_it_can_be_restored() {
        val id = graph.store.create(graph.clock(), title = "bye").id
        compose.openNote(id)

        compose.onNodeWithTag(Tags.DELETE).performClick()

        compose.waitForStore { graph.store.get(id)?.inTrash == true }
        compose.awaitNoTag(Tags.card(id))

        compose.onNodeWithTag(Tags.TRASH_TOGGLE).performClick()
        compose.awaitTag(Tags.TRASH_NOTICE)
        compose.openNote(id)
        compose.awaitNoTag(Tags.PIN)
        compose.onNodeWithTag(Tags.RESTORE).performClick()

        compose.waitForStore { graph.store.get(id)?.inTrash == false }
        compose.awaitTag(Tags.PIN)
    }

    @Test
    fun Deleting_forever_from_the_trash_asks_first_then_leaves_nothing_behind() {
        val id = graph.store.create(graph.clock(), title = "gone").id
        graph.store.trash(id, graph.clock())
        compose.onNodeWithTag(Tags.TRASH_TOGGLE).performClick()
        compose.openNote(id)

        compose.onNodeWithTag(Tags.DELETE_FOREVER).performClick()
        compose.onNodeWithTag(Tags.CANCEL_DELETE_FOREVER).performClick()
        compose.awaitNoTag(Tags.CANCEL_DELETE_FOREVER)
        assertEquals(true, graph.store.get(id)?.inTrash)

        compose.onNodeWithTag(Tags.DELETE_FOREVER).performClick()
        compose.onNodeWithTag(Tags.CONFIRM_DELETE_FOREVER).performClick()

        compose.waitForStore { graph.store.get(id) == null }
        compose.awaitTag(Tags.EMPTY_BOARD)
    }
}
