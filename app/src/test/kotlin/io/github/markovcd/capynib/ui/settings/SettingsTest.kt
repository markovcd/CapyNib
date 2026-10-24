package io.github.markovcd.capynib.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import io.github.markovcd.capynib.AppGraph
import io.github.markovcd.capynib.TestGraphs
import io.github.markovcd.capynib.core.memos.FakeMemosServer
import io.github.markovcd.capynib.core.sync.SyncStatus
import io.github.markovcd.capynib.ui.CapynibRoot
import io.github.markovcd.capynib.ui.Tags
import io.github.markovcd.capynib.ui.awaitNoTag
import io.github.markovcd.capynib.ui.awaitTag
import io.github.markovcd.capynib.ui.awaitText
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

/** Connecting to a server, signing in, checking it and the trash setting, over a fake server. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class SettingsTest {
    @get:Rule
    val compose = createComposeRule()

    private val server = FakeMemosServer()

    /** Pages the app asked the browser to open. */
    private val opened = CopyOnWriteArrayList<String>()

    /** Whether the phone has a browser to open them in. */
    @Volatile private var hasBrowser = true

    private lateinit var graph: AppGraph

    @Before
    fun start() {
        graph = TestGraphs.create(server) { if (hasBrowser) opened += it; hasBrowser }
        compose.setContent { CapynibRoot(graph) }
    }

    private fun connectWithToken() {
        compose.onNodeWithTag(Tags.SETTINGS).performClick()
        compose.onNodeWithTag(Tags.SERVER).performTextReplacement("https://memos.example.com")
        compose.onNodeWithTag(Tags.TOKEN).performTextReplacement("memos_pat_test")
        compose.onNodeWithTag(Tags.CONNECT).performScrollTo().performClick()
        compose.awaitText("Connected to memos.example.com as tester")
    }

    @Test
    fun Connecting_a_server_syncs_the_notes_written_before() {
        graph.store.create(graph.clock(), body = "written offline")
        compose.onNodeWithTag(Tags.SETTINGS).performClick()

        compose.onNodeWithTag(Tags.SERVER).performTextReplacement("https://memos.example.com")
        compose.onNodeWithTag(Tags.TOKEN).performTextReplacement("memos_pat_test")
        compose.onNodeWithTag(Tags.CONNECT).performScrollTo().performClick()

        compose.waitUntil(timeoutMillis = 5_000) { server.memos.values.any { it.state.content == "written offline" } }
        compose.awaitText("Connected to memos.example.com as tester")
    }

    @Test
    fun Signing_in_with_the_browser_keeps_the_access_token_the_server_makes() {
        compose.onNodeWithTag(Tags.SETTINGS).performClick()
        compose.onNodeWithTag(Tags.SERVER).performTextReplacement("https://memos.example.com")
        compose.onNodeWithTag(Tags.SIGN_IN).performScrollTo().performClick()

        // The page opens on the main thread right after this text shows; waiting on the screen lets that thread run.
        compose.awaitText("Finish signing in with Google in the browser, then come back.")
        val page = opened.single().toHttpUrl()
        assertEquals("accounts.example.com", page.host)

        // The provider sends the browser back to the app with a code.
        val back = page.queryParameter("redirect_uri")!!.toHttpUrl().newBuilder()
            .addQueryParameter("code", "abc")
            .addQueryParameter("state", page.queryParameter("state"))
            .build()
        OkHttpClient().newCall(Request.Builder().url(back).build()).execute().close()

        compose.awaitText("Connected to memos.example.com as tester")
        assertEquals("memos_pat_from_browser", graph.settings.settings.value.token)
        compose.waitUntil(timeoutMillis = 5_000) { graph.scheduler.status.value is SyncStatus.Synced }
    }

    @Test
    fun Check_server_says_every_call_works_and_leaves_no_memo_behind() {
        connectWithToken()
        server.put("mine", "groceries")

        compose.onNodeWithTag(Tags.CHECK_SERVER).performScrollTo().performClick()

        compose.awaitText("Every call Capynib makes works on this server (memos 0.31.0).")
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

        compose.awaitText("No browser to sign in with. Use an access token.")
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
        compose.awaitNoTag(Tags.DISCONNECT)

        compose.onNodeWithTag(Tags.SERVER).performTextReplacement("https://memos.example.com")
        compose.onNodeWithTag(Tags.TOKEN).performTextReplacement("memos_pat_test")
        compose.onNodeWithTag(Tags.CONNECT).performScrollTo().performClick()

        compose.awaitTag(Tags.DISCONNECT)
        for (gone in listOf(Tags.CONNECT, Tags.SIGN_IN, Tags.REDIRECT_URI, Tags.SERVER, Tags.TOKEN)) compose.awaitNoTag(gone)

        compose.onNodeWithTag(Tags.DISCONNECT).performScrollTo().performClick()

        compose.awaitNoTag(Tags.DISCONNECT)
        for (back in listOf(Tags.CONNECT, Tags.SIGN_IN, Tags.REDIRECT_URI, Tags.SERVER, Tags.TOKEN)) compose.awaitTag(back)
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
        compose.awaitText("Notes in the trash are deleted after 30 days")
    }

    @Test
    fun The_sync_status_is_shown_in_settings_and_not_on_the_board() {
        assertEquals(SyncStatus.Local, graph.scheduler.status.value)
        compose.onNodeWithTag(Tags.SYNC_STATUS).assertDoesNotExist()

        compose.onNodeWithTag(Tags.SETTINGS).performClick()
        compose.awaitText("Notes are on this phone only")
        compose.onNodeWithTag(Tags.SYNC_STATUS).performScrollTo().assertIsDisplayed()
    }
}
