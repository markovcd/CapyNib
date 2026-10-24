package io.github.markovcd.capynib.ui.board

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.github.markovcd.capynib.AppGraph
import io.github.markovcd.capynib.TestGraphs
import io.github.markovcd.capynib.ui.CapynibRoot
import io.github.markovcd.capynib.ui.Tags
import io.github.markovcd.capynib.ui.awaitNoTag
import io.github.markovcd.capynib.ui.awaitTag
import io.github.markovcd.capynib.ui.openNote
import io.github.markovcd.capynib.ui.waitForStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The board of cards: its sections, the archive, search and tags, over a real store. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class BoardTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var graph: AppGraph

    @Before
    fun start() {
        graph = TestGraphs.create()
        compose.setContent { CapynibRoot(graph) }
    }

    @Test
    fun An_empty_board_says_where_notes_will_appear() {
        compose.awaitTag(Tags.EMPTY_BOARD)
    }

    @Test
    fun The_new_note_button_is_an_unlabeled_icon_a_size_up_from_the_new_list_button() {
        val note = compose.onNodeWithTag(Tags.NEW_NOTE).fetchSemanticsNode()
        val list = compose.onNodeWithTag(Tags.NEW_LIST).fetchSemanticsNode()

        assertEquals(null, note.config.getOrNull(SemanticsProperties.Text))
        assertEquals(listOf("New note"), note.config.getOrNull(SemanticsProperties.ContentDescription))
        assertTrue(note.size.width > list.size.width && note.size.height > list.size.height)
    }

    @Test
    fun Pinned_notes_are_shown_first_and_archived_ones_only_in_the_archive() {
        val plain = graph.store.create(graph.clock(), title = "plain").id
        val pinned = graph.store.create(graph.clock(), title = "pin me").id
        val archived = graph.store.create(graph.clock(), title = "put away").id

        compose.openNote(pinned)
        compose.onNodeWithTag(Tags.PIN).performClick()
        compose.onNodeWithTag(Tags.BACK).performClick()
        compose.openNote(archived)
        compose.onNodeWithTag(Tags.ARCHIVE).performClick()

        compose.waitForStore { graph.store.get(archived)?.archived == true && graph.store.get(pinned)?.pinned == true }
        compose.awaitTag(Tags.PINNED)
        compose.awaitTag(Tags.card(plain))
        compose.awaitNoTag(Tags.card(archived))

        compose.onNodeWithTag(Tags.ARCHIVE_TOGGLE).performClick()
        compose.awaitTag(Tags.card(archived))
    }

    @Test
    fun Search_narrows_the_board() {
        val milk = graph.store.create(graph.clock(), title = "Buy milk").id
        val idea = graph.store.create(graph.clock(), title = "App idea").id

        compose.awaitTag(Tags.card(idea))
        compose.onNodeWithTag(Tags.SEARCH).performTextInput("milk")

        compose.awaitNoTag(Tags.card(idea))
        compose.onNodeWithTag(Tags.card(milk)).assertIsDisplayed()
    }

    @Test
    fun A_tag_written_in_a_note_shows_on_its_card_and_picking_it_narrows_the_board() {
        val milk = graph.store.create(graph.clock(), title = "Buy milk", body = "2 litres #shopping").id
        val idea = graph.store.create(graph.clock(), title = "App idea #work").id

        compose.awaitTag(Tags.card(milk))
        // The card is one button to accessibility, so its tags are read from its merged text.
        compose.onNodeWithTag(Tags.card(milk)).assertTextContains("#shopping", substring = false)
        compose.onNodeWithTag(Tags.tag("shopping")).performClick()

        compose.awaitNoTag(Tags.card(idea))
        compose.onNodeWithTag(Tags.card(milk)).assertIsDisplayed()
        compose.onNodeWithTag(Tags.tag("shopping")).performClick()
        compose.awaitTag(Tags.card(idea))
    }
}
