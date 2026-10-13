package io.github.markovcd.capynib.cli

import io.github.markovcd.capynib.core.memos.BrowserSignIn
import io.github.markovcd.capynib.core.memos.FakeMemosServer
import io.github.markovcd.capynib.core.memos.LiveMemos
import io.github.markovcd.capynib.core.store.Trash
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.concurrent.thread

class CliTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val db by lazy { folder.newFile("cli.db").absolutePath }
    private val server = FakeMemosServer()
    private var env = mapOf<String, String>()
    private var now = 1_000L

    private data class Ran(val code: Int, val out: String, val err: String)

    private fun run(vararg args: String): Ran {
        val out = StringBuilder()
        val err = StringBuilder()
        val code = Cli(env, { ++now }, { _, _ -> server }).run(listOf("--db", db) + args, out, err)
        return Ran(code, out.toString(), err.toString())
    }

    private fun json(text: String) = Json.parseToJsonElement(text)

    /** Runs `login` against [memos], with a browser that signs in on whatever page `err` names. */
    private fun login(memos: MockWebServer, vararg args: String): Ran {
        val out = StringBuilder()
        val err = Browser()
        val cli = Cli(env, { ++now }, { _, _ -> server }) { BrowserSignIn(it, "test", port = 0) }
        val code = cli.run(listOf("login", "--server", memos.url("/").toString()) + args, out, err)
        return Ran(code, out.toString(), err.toString())
    }

    /** Reads what the command line writes to stderr and, on seeing a sign-in page, comes back from it signed in. */
    private class Browser : Appendable {
        private val text = StringBuilder()

        override fun append(csq: CharSequence?): Appendable = apply {
            text.append(csq)
            csq?.toString()?.takeIf { it.startsWith("https://") }?.toHttpUrl()?.let { page ->
                val back = page.queryParameter("redirect_uri")!!.toHttpUrl().newBuilder()
                    .addQueryParameter("code", "abc")
                    .addQueryParameter("state", page.queryParameter("state"))
                    .build()
                thread { OkHttpClient().newCall(Request.Builder().url(back).build()).execute().close() }
            }
        }

        override fun append(csq: CharSequence?, start: Int, end: Int): Appendable = append(csq?.subSequence(start, end))

        override fun append(c: Char): Appendable = apply { text.append(c) }

        override fun toString() = text.toString()
    }

    private fun MockWebServer.reply(body: String) = enqueue(MockResponse.Builder().body(body).build())

    private val googleOnly =
        """{"identityProviders":[{"name":"identity-providers/google","type":"OAUTH2","title":"Google",""" +
            """"config":{"oauth2Config":{"clientId":"c1","authUrl":"https://accounts.example.com/auth","scopes":["email"]}}}]}"""

    @Test
    fun A_note_added_from_the_command_line_is_listed() {
        val id = run("add", "--title", "Groceries", "--body", "milk\neggs", "--checklist", "--color", "sand").out.trim()

        val listed = json(run("list", "--json").out).jsonArray.single().jsonObject

        assertEquals(id, listed["id"]!!.jsonPrimitive.content)
        assertEquals("sand", listed["color"]!!.jsonPrimitive.content)
        assertEquals("- [ ] milk\n- [ ] eggs", listed["body"]!!.jsonPrimitive.content)
    }

    @Test
    fun Checking_an_item_ticks_it() {
        val id = run("add", "--body", "- [ ] milk\n- [ ] eggs").out.trim()

        val shown = run("check", id, "1")

        assertEquals(ExitCode.OK, shown.code)
        assertEquals("0 [ ] milk\n1 [x] eggs\n", shown.out)
    }

    @Test
    fun Checking_a_line_that_is_not_an_item_says_which_lines_are() {
        val id = run("add", "--body", "intro\n- [ ] milk").out.trim()

        val ran = run("check", id, "0")

        assertEquals(ExitCode.USAGE, ran.code)
        assertTrue(ran.err, ran.err.contains("items: [1]"))
    }

    @Test
    fun Edits_pin_archive_and_delete_a_note() {
        val id = run("add", "--body", "x").out.trim()

        run("edit", id, "--pin", "--color", "mint")
        assertEquals(true, json(run("show", id, "--json").out).jsonObject["pinned"]!!.jsonPrimitive.content.toBoolean())

        run("edit", id, "--archive")
        assertEquals("", run("list").out)
        assertTrue(run("list", "--archived").out.startsWith(id))

        run("delete", id, "--forever")
        assertEquals(ExitCode.NOT_FOUND, run("show", id).code)
    }

    @Test
    fun Delete_moves_a_note_to_the_trash_and_restore_brings_it_back() {
        val id = run("add", "--body", "x").out.trim()

        run("delete", id)
        assertEquals("", run("list").out)
        assertTrue(run("list", "--trashed").out.startsWith(id))

        assertEquals(ExitCode.OK, run("restore", id).code)
        assertTrue(run("list").out.startsWith(id))
        assertEquals("", run("list", "--trashed").out)
    }

    @Test
    fun Empty_trash_deletes_only_notes_a_week_in_the_trash() {
        val old = run("add", "--body", "old").out.trim()
        val recent = run("add", "--body", "recent").out.trim()
        run("delete", old)
        now += Trash.KEPT_FOR.inWholeMilliseconds
        run("delete", recent)

        assertEquals("1", run("empty-trash").out.trim())

        assertEquals(ExitCode.NOT_FOUND, run("show", old).code)
        assertTrue(json(run("show", recent, "--json").out).jsonObject["trashedAt"]!!.jsonPrimitive.content.toLong() > 0)
    }

    @Test
    fun Search_narrows_the_list() {
        run("add", "--title", "Groceries")
        run("add", "--title", "Ideas")

        assertEquals(listOf("Groceries"), json(run("list", "--search", "groc", "--json").out).jsonArray.map { it.jsonObject["title"]!!.jsonPrimitive.content })
    }

    @Test
    fun Help_prints_the_usage_and_succeeds() {
        val ran = run("--help")

        assertEquals(ExitCode.OK, ran.code)
        assertTrue(ran.out.startsWith("usage: capynib"))
    }

    @Test
    fun Mistakes_are_usage_errors_that_say_what_was_wrong() {
        assertEquals(ExitCode.USAGE, run().code)
        assertTrue(run("frobnicate").err.startsWith("unknown command 'frobnicate'"))
        assertTrue(run("add", "--color", "neon").err.startsWith("unknown color 'neon'"))
        assertTrue(run("list", "--bogus").err.startsWith("list does not take --bogus"))
        assertTrue(run("add", "--title").err.startsWith("--title needs a value"))
    }

    @Test
    fun Sync_reads_the_token_from_the_environment_never_from_arguments() {
        val ran = run("sync", "--server", "https://memos.example.com")

        assertEquals(ExitCode.USAGE, ran.code)
        assertTrue(ran.err, ran.err.startsWith("sync needs the memos access token in CAPYNIB_TOKEN"))
    }

    @Test
    fun Sync_sends_notes_and_reports_as_json() {
        env = mapOf("CAPYNIB_TOKEN" to "t", "CAPYNIB_SERVER" to "https://memos.example.com")
        run("add", "--body", "hello")

        val ran = run("sync", "--json")

        assertEquals(ExitCode.OK, ran.code)
        assertEquals(1, json(ran.out).jsonObject["sent"]!!.jsonPrimitive.content.toInt())
        assertEquals("hello", server.memos.values.single().state.content)
    }

    @Test
    fun A_failed_sync_exits_with_its_own_code() {
        env = mapOf("CAPYNIB_TOKEN" to "t", "CAPYNIB_SERVER" to "https://memos.example.com")
        server.offline = true

        val ran = run("sync")

        assertEquals(ExitCode.SYNC_FAILED, ran.code)
        assertTrue(ran.err, ran.err.startsWith("sync failed: fake server is offline"))
    }

    @Test
    fun Two_databases_share_notes_through_a_real_memos_server() {
        val live = LiveMemos.connect()
        val realEnv = mapOf("CAPYNIB_TOKEN" to live.token, "CAPYNIB_SERVER" to live.server.toString())
        fun real(dbFile: String, vararg args: String): Ran {
            val out = StringBuilder()
            val err = StringBuilder()
            return Ran(Cli(realEnv, { ++now }).run(listOf("--db", dbFile) + args, out, err), out.toString(), err.toString())
        }
        val phone = folder.newFile("phone.db").absolutePath
        val laptop = folder.newFile("laptop.db").absolutePath

        val id = real(phone, "add", "--title", "Trip", "--body", "- [ ] tickets", "--color", "fog").out.trim()
        assertEquals(ExitCode.OK, real(phone, "sync").code)
        assertEquals(ExitCode.OK, real(laptop, "sync").code)
        real(laptop, "check", id, "0")
        assertEquals(ExitCode.OK, real(laptop, "sync").code)
        assertEquals(ExitCode.OK, real(phone, "sync").code)

        assertEquals("Trip\n0 [x] tickets\n", real(phone, "show", id).out)
    }

    @Test
    fun Login_prints_a_new_access_token_after_signing_in_in_the_browser() {
        MockWebServer().use { memos ->
            memos.start()
            memos.reply(googleOnly)
            memos.reply("""{"user":{"name":"users/arek","username":"arek"},"accessToken":"session-jwt"}""")
            memos.reply("""{"token":"memos_pat_new"}""")

            val ran = login(memos, "--json")

            assertEquals(ran.err, ExitCode.OK, ran.code)
            assertEquals("""{"username":"arek","accessToken":"memos_pat_new"}""", ran.out.trim())
            assertTrue(ran.err, ran.err.startsWith("Open this page to sign in with Google:\nhttps://accounts.example.com/auth?"))
        }
    }

    @Test
    fun Login_names_the_redirect_address_the_sign_in_provider_must_allow() {
        MockWebServer().use { memos ->
            memos.start()
            memos.reply(googleOnly)
            memos.reply("""{"user":{"name":"users/arek","username":"arek"},"accessToken":"session-jwt"}""")
            memos.reply("""{"token":"memos_pat_new"}""")

            val ran = login(memos)

            assertTrue(ran.err, ran.err.contains("Google must allow the redirect address http://127.0.0.1:47815/capynib\n"))
        }
    }

    @Test
    fun Login_to_a_server_without_sign_in_providers_fails_with_its_own_code() {
        MockWebServer().use { memos ->
            memos.start()
            memos.reply("""{"identityProviders":[]}""")

            val ran = login(memos)

            assertEquals(ExitCode.LOGIN_FAILED, ran.code)
            assertTrue(ran.err, ran.err.contains("offers no sign-in provider"))
        }
    }
}
