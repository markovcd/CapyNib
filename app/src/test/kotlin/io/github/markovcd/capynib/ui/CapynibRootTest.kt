package io.github.markovcd.capynib.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
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
import io.github.markovcd.capynib.core.note.Capture
import io.github.markovcd.capynib.core.note.NoteColor
import io.github.markovcd.capynib.core.sync.SyncStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
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

    /** Notes made by quick captures, as the activity hands them to the screen. */
    private val captured = Channel<String>(Channel.UNLIMITED)

    @Before
    fun start() {
        graph = TestGraphs.create(server) { if (hasBrowser) opened += it; hasBrowser }
        compose.setContent { CapynibRoot(graph, captured.receiveAsFlow()) }
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
    fun A_new_note_opens_ready_to_type() {
        compose.onNodeWithTag(Tags.NEW_NOTE).performClick()

        awaitTag(Tags.BODY)
        compose.onNodeWithTag(Tags.BODY).assertIsFocused()
    }

    @Test
    fun A_new_list_opens_ready_to_type_its_first_item() {
        compose.onNodeWithTag(Tags.NEW_LIST).performClick()

        awaitTag(Tags.item(0))
        compose.onNodeWithTag(Tags.item(0)).assertIsFocused()
    }

    @Test
    fun Opening_a_note_from_the_board_does_not_bring_up_the_keyboard() {
        val id = graph.store.create(graph.clock(), body = "just reading").id
        open(id)

        awaitTag(Tags.BODY)
        compose.onNodeWithTag(Tags.BODY).assertIsNotFocused()
    }

    @Test
    fun A_shared_link_opens_as_a_note_titled_with_the_page_ready_to_type() {
        captured.trySend(graph.capture(Capture.shared("https://example.com/capybaras", "Capybaras")!!))

        awaitTag(Tags.BODY)
        compose.onNodeWithTag(Tags.TITLE).assertTextEquals("Capybaras")
        compose.onNodeWithTag(Tags.BODY).assertTextEquals("https://example.com/capybaras").assertIsFocused()
    }

    @Test
    fun A_capture_while_settings_are_open_opens_the_new_note() {
        compose.onNodeWithTag(Tags.SETTINGS).performClick()
        awaitTag(Tags.SERVER)

        captured.trySend(graph.capture(Capture.EmptyChecklist))

        awaitTag(Tags.item(0))
        compose.onNodeWithTag(Tags.item(0)).assertIsFocused()
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
    fun Deleting_a_note_moves_it_to_the_trash_where_it_can_be_restored() {
        val id = graph.store.create(graph.clock(), title = "bye").id
        open(id)

        compose.onNodeWithTag(Tags.DELETE).performClick()

        waitForStore { graph.store.get(id)?.inTrash == true }
        awaitNoTag(Tags.card(id))

        compose.onNodeWithTag(Tags.TRASH_TOGGLE).performClick()
        awaitTag(Tags.TRASH_NOTICE)
        open(id)
        awaitNoTag(Tags.PIN)
        compose.onNodeWithTag(Tags.RESTORE).performClick()

        waitForStore { graph.store.get(id)?.inTrash == false }
        awaitTag(Tags.PIN)
    }

    @Test
    fun Deleting_forever_from_the_trash_asks_first_then_leaves_nothing_behind() {
        val id = graph.store.create(graph.clock(), title = "gone").id
        graph.store.trash(id, graph.clock())
        compose.onNodeWithTag(Tags.TRASH_TOGGLE).performClick()
        open(id)

        compose.onNodeWithTag(Tags.DELETE_FOREVER).performClick()
        compose.onNodeWithTag(Tags.CANCEL_DELETE_FOREVER).performClick()
        awaitNoTag(Tags.CANCEL_DELETE_FOREVER)
        assertEquals(true, graph.store.get(id)?.inTrash)

        compose.onNodeWithTag(Tags.DELETE_FOREVER).performClick()
        compose.onNodeWithTag(Tags.CONFIRM_DELETE_FOREVER).performClick()

        waitForStore { graph.store.get(id) == null }
        awaitTag(Tags.EMPTY_BOARD)
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
    fun A_tag_written_in_a_note_shows_on_its_card_and_picking_it_narrows_the_board() {
        val milk = graph.store.create(graph.clock(), title = "Buy milk", body = "2 litres #shopping").id
        val idea = graph.store.create(graph.clock(), title = "App idea #work").id

        awaitTag(Tags.card(milk))
        // The card is one button to accessibility, so its tags are read from its merged text.
        compose.onNodeWithTag(Tags.card(milk)).assertTextContains("#shopping", substring = false)
        compose.onNodeWithTag(Tags.tag("shopping")).performClick()

        awaitNoTag(Tags.card(idea))
        compose.onNodeWithTag(Tags.card(milk)).assertIsDisplayed()
        compose.onNodeWithTag(Tags.tag("shopping")).performClick()
        awaitTag(Tags.card(idea))
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

        // The page opens on the main thread right after this text shows; waiting on the screen lets that thread run.
        awaitText("Finish signing in with Google in the browser, then come back.")
        val page = opened.single().toHttpUrl()
        assertEquals("accounts.example.com", page.host)

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

    private fun connectWithToken() {
        compose.onNodeWithTag(Tags.SETTINGS).performClick()
        compose.onNodeWithTag(Tags.SERVER).performTextReplacement("https://memos.example.com")
        compose.onNodeWithTag(Tags.TOKEN).performTextReplacement("memos_pat_test")
        compose.onNodeWithTag(Tags.CONNECT).performScrollTo().performClick()
        awaitText("Connected to memos.example.com as tester")
    }

    @Test
    fun Check_server_says_every_call_works_and_leaves_no_memo_behind() {
        connectWithToken()
        server.put("mine", "groceries")

        compose.onNodeWithTag(Tags.CHECK_SERVER).performScrollTo().performClick()

        awaitText("Every call Capynib makes works on this server (memos 0.31.0).")
        assertEquals(listOf("mine"), server.memos.keys.toList())
    }

    @Test
    fun Check_server_names_each_call_the_server_gets_wrong() {
        server.ignoresArchive = true
        connectWithToken()

        compose.onNodeWithTag(Tags.CHECK_SERVER).performScrollTo().performClick()

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("does not do everything Capynib needs", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(Tags.SETTINGS_MESSAGE).assertTextContains(
            "• change a memo's text, pin, archive and create time: the server answered, but not with what was asked for",
            substring = true,
        )
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
    fun Settings_show_the_redirect_address_the_sign_in_site_must_allow() {
        compose.onNodeWithTag(Tags.SETTINGS).performClick()

        compose.onNodeWithTag(Tags.REDIRECT_URI).performScrollTo().assertTextEquals("http://127.0.0.1:47815/capynib")
    }

    @Test
    fun Settings_offer_only_disconnect_while_connected_and_only_the_ways_to_connect_otherwise() {
        compose.onNodeWithTag(Tags.SETTINGS).performClick()
        awaitNoTag(Tags.DISCONNECT)

        compose.onNodeWithTag(Tags.SERVER).performTextReplacement("https://memos.example.com")
        compose.onNodeWithTag(Tags.TOKEN).performTextReplacement("memos_pat_test")
        compose.onNodeWithTag(Tags.CONNECT).performScrollTo().performClick()

        awaitTag(Tags.DISCONNECT)
        for (gone in listOf(Tags.CONNECT, Tags.SIGN_IN, Tags.REDIRECT_URI, Tags.SERVER, Tags.TOKEN)) awaitNoTag(gone)

        compose.onNodeWithTag(Tags.DISCONNECT).performScrollTo().performClick()

        awaitNoTag(Tags.DISCONNECT)
        for (back in listOf(Tags.CONNECT, Tags.SIGN_IN, Tags.REDIRECT_URI, Tags.SERVER, Tags.TOKEN)) awaitTag(back)
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
    fun Settings_choose_how_many_days_the_trash_keeps_notes() {
        compose.onNodeWithTag(Tags.SETTINGS).performClick()

        compose.onNodeWithTag(Tags.TRASH_DAYS).performScrollTo().performTextReplacement("30")
        compose.waitUntil(timeoutMillis = 5_000) { graph.trash.days.value == 30 }

        compose.onNodeWithTag(Tags.TRASH_DAYS).performTextReplacement("0")
        assertEquals(30, graph.trash.days.value)

        compose.onNodeWithTag(Tags.BACK).performClick()
        compose.onNodeWithTag(Tags.TRASH_TOGGLE).performClick()
        graph.store.trash(graph.store.create(graph.clock(), title = "in the trash").id, graph.clock())
        awaitText("Notes in the trash are deleted after 30 days")
    }

    @Test
    fun Without_a_server_the_status_says_notes_are_on_this_phone() {
        assertEquals(SyncStatus.Local, graph.scheduler.status.value)
        compose.onNodeWithTag(Tags.SYNC_STATUS).assertIsDisplayed()
    }
}
