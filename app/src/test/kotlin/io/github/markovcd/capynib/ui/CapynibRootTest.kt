package io.github.markovcd.capynib.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import io.github.markovcd.capynib.AppGraph
import io.github.markovcd.capynib.TestGraphs
import io.github.markovcd.capynib.core.memos.FakeMemosServer
import io.github.markovcd.capynib.core.note.NoteColor
import io.github.markovcd.capynib.core.sync.SyncStatus
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

/** The app's screens driven the way a person uses them, over a real store and a fake server. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class CapynibRootTest {
    @get:Rule
    val compose = createComposeRule()

    private val server = FakeMemosServer()
    private lateinit var graph: AppGraph

    /** Pages the app asked the browser to open. */
    private val opened = CopyOnWriteArrayList<String>()

    /** Whether the phone has a browser to open them in. */
    @Volatile private var hasBrowser = true

    @Before
    fun start() {
        graph = TestGraphs.create(server) { if (hasBrowser) opened += it; hasBrowser }
        compose.setContent { CapynibRoot(graph) }
    }

    // The store answers on a background thread, so the screen catches up a moment after a change.
    private fun waitForStore(condition: () -> Boolean) = compose.waitUntil(timeoutMillis = 5_000) { condition() }

    private fun awaitTag(tag: String) = compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    private fun awaitNoTag(tag: String) = compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() }

    private fun awaitText(text: String) = compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun open(id: String) {
        awaitTag(Tags.card(id))
        compose.onNodeWithTag(Tags.card(id)).performClick()
    }

    @Test
    fun An_empty_board_says_where_notes_will_appear() {
        awaitTag(Tags.EMPTY_BOARD)
    }

    @Test
    fun A_new_note_is_typed_saved_and_shown_on_the_board() {
        compose.onNodeWithTag(Tags.NEW_NOTE).performClick()
        compose.onNodeWithTag(Tags.TITLE).performTextInput("Groceries")
        compose.onNodeWithTag(Tags.BODY).performTextInput("milk")
        waitForStore { graph.store.visible().singleOrNull()?.body == "milk" }

        compose.onNodeWithTag(Tags.BACK).performClick()

        awaitText("Groceries")
        assertEquals("Groceries", graph.store.visible().single().title)
    }

    @Test
    fun A_note_left_empty_is_thrown_away() {
        compose.onNodeWithTag(Tags.NEW_NOTE).performClick()
        compose.onNodeWithTag(Tags.BACK).performClick()

        waitForStore { graph.store.visible().isEmpty() }
        awaitTag(Tags.EMPTY_BOARD)
    }

    @Test
    fun A_new_list_takes_items_that_can_be_ticked() {
        compose.onNodeWithTag(Tags.NEW_LIST).performClick()
        compose.onNodeWithTag(Tags.item(0)).performTextInput("milk")
        compose.onNodeWithTag(Tags.ADD_ITEM).performClick()
        compose.onNodeWithTag(Tags.item(1)).performTextInput("eggs")
        compose.onNodeWithTag(Tags.itemCheck(1)).performClick()

        waitForStore { graph.store.visible().singleOrNull()?.body == "- [ ] milk\n- [x] eggs" }
    }

    @Test
    fun Picking_a_color_colors_the_note() {
        val id = graph.store.create(graph.clock(), body = "paint me").id
        open(id)

        compose.onNodeWithTag(Tags.color(NoteColor.Mint.key)).performClick()

        waitForStore { graph.store.get(id)?.color == NoteColor.Mint }
    }

    @Test
    fun Showing_checkboxes_turns_a_note_into_a_list() {
        val id = graph.store.create(graph.clock(), body = "milk\neggs").id
        open(id)

        compose.onNodeWithTag(Tags.CHECKBOXES).performClick()
        compose.onNodeWithTag(Tags.itemCheck(0)).performClick()

        waitForStore { graph.store.get(id)?.body == "- [x] milk\n- [ ] eggs" }
    }

    @Test
    fun Pinned_notes_are_shown_first_and_archived_ones_only_in_the_archive() {
        val plain = graph.store.create(graph.clock(), title = "plain").id
        val pinned = graph.store.create(graph.clock(), title = "pin me").id
        val archived = graph.store.create(graph.clock(), title = "put away").id

        open(pinned)
        compose.onNodeWithTag(Tags.PIN).performClick()
        compose.onNodeWithTag(Tags.BACK).performClick()
        open(archived)
        compose.onNodeWithTag(Tags.ARCHIVE).performClick()

        waitForStore { graph.store.get(archived)?.archived == true && graph.store.get(pinned)?.pinned == true }
        awaitTag(Tags.PINNED)
        awaitTag(Tags.card(plain))
        awaitNoTag(Tags.card(archived))

        compose.onNodeWithTag(Tags.ARCHIVE_TOGGLE).performClick()
        awaitTag(Tags.card(archived))
    }

    @Test
    fun Deleting_a_note_removes_it() {
        val id = graph.store.create(graph.clock(), title = "bye").id
        open(id)

        compose.onNodeWithTag(Tags.DELETE).performClick()

        waitForStore { graph.store.get(id) == null }
        awaitNoTag(Tags.card(id))
    }

    @Test
    fun Search_narrows_the_board() {
        val milk = graph.store.create(graph.clock(), title = "Buy milk").id
        val idea = graph.store.create(graph.clock(), title = "App idea").id

        awaitTag(Tags.card(idea))
        compose.onNodeWithTag(Tags.SEARCH).performTextInput("milk")

        awaitNoTag(Tags.card(idea))
        compose.onNodeWithTag(Tags.card(milk)).assertIsDisplayed()
    }

    @Test
    fun Connecting_a_server_syncs_the_notes_written_before() {
        graph.store.create(graph.clock(), body = "written offline")
        compose.onNodeWithTag(Tags.SETTINGS).performClick()

        compose.onNodeWithTag(Tags.SERVER).performTextReplacement("https://memos.example.com")
        compose.onNodeWithTag(Tags.TOKEN).performTextReplacement("memos_pat_test")
        compose.onNodeWithTag(Tags.CONNECT).performScrollTo().performClick()

        compose.waitUntil(timeoutMillis = 5_000) { server.memos.values.any { it.state.content == "written offline" } }
        awaitText("Connected to memos.example.com as tester")
    }

    @Test
    fun Signing_in_with_the_browser_keeps_the_access_token_the_server_makes() {
        compose.onNodeWithTag(Tags.SETTINGS).performClick()
        compose.onNodeWithTag(Tags.SERVER).performTextReplacement("https://memos.example.com")
        compose.onNodeWithTag(Tags.SIGN_IN).performScrollTo().performClick()

        compose.waitUntil(timeoutMillis = 5_000) { opened.isNotEmpty() }
        val page = opened.single().toHttpUrl()
        assertEquals("accounts.example.com", page.host)
        awaitText("Finish signing in with Google in the browser, then come back.")

        // The provider sends the browser back to the app with a code.
        val back = page.queryParameter("redirect_uri")!!.toHttpUrl().newBuilder()
            .addQueryParameter("code", "abc")
            .addQueryParameter("state", page.queryParameter("state"))
            .build()
        OkHttpClient().newCall(Request.Builder().url(back).build()).execute().close()

        awaitText("Connected to memos.example.com as tester")
        assertEquals("memos_pat_from_browser", graph.settings.settings.value.token)
        compose.waitUntil(timeoutMillis = 5_000) { graph.scheduler.status.value is SyncStatus.Synced }
    }

    @Test
    fun Signing_in_without_a_browser_says_so_and_saves_nothing() {
        hasBrowser = false
        compose.onNodeWithTag(Tags.SETTINGS).performClick()
        compose.onNodeWithTag(Tags.SERVER).performTextReplacement("https://memos.example.com")
        compose.onNodeWithTag(Tags.SIGN_IN).performScrollTo().performClick()

        awaitText("No browser to sign in with. Use an access token.")
        assertEquals(false, graph.settings.settings.value.isConfigured)
    }

    @Test
    fun Settings_offer_only_disconnect_while_connected_and_only_the_ways_to_connect_otherwise() {
        compose.onNodeWithTag(Tags.SETTINGS).performClick()
        awaitNoTag(Tags.DISCONNECT)

        compose.onNodeWithTag(Tags.SERVER).performTextReplacement("https://memos.example.com")
        compose.onNodeWithTag(Tags.TOKEN).performTextReplacement("memos_pat_test")
        compose.onNodeWithTag(Tags.CONNECT).performScrollTo().performClick()

        awaitTag(Tags.DISCONNECT)
        for (gone in listOf(Tags.CONNECT, Tags.SIGN_IN, Tags.SERVER, Tags.TOKEN)) awaitNoTag(gone)

        compose.onNodeWithTag(Tags.DISCONNECT).performScrollTo().performClick()

        awaitNoTag(Tags.DISCONNECT)
        for (back in listOf(Tags.CONNECT, Tags.SIGN_IN, Tags.SERVER, Tags.TOKEN)) awaitTag(back)
    }

    @Test
    fun A_bad_server_address_is_refused_before_anything_is_saved() {
        compose.onNodeWithTag(Tags.SETTINGS).performClick()

        compose.onNodeWithTag(Tags.SERVER).performTextReplacement("not a url")
        compose.onNodeWithTag(Tags.TOKEN).performTextReplacement("t")
        compose.onNodeWithTag(Tags.CONNECT).performScrollTo().performClick()

        compose.onNodeWithTag(Tags.SETTINGS_MESSAGE).assertTextContains("That is not a server address")
        assertEquals(false, graph.settings.settings.value.isConfigured)
    }

    @Test
    fun Without_a_server_the_status_says_notes_are_on_this_phone() {
        assertEquals(SyncStatus.Local, graph.scheduler.status.value)
        compose.onNodeWithTag(Tags.SYNC_STATUS).assertIsDisplayed()
    }
}
