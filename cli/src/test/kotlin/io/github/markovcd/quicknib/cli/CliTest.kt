package io.github.markovcd.quicknib.cli

import io.github.markovcd.quicknib.core.memos.FakeMemosServer
import io.github.markovcd.quicknib.core.memos.LiveMemos
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

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
        val code = Cli(env, { ++now }) { _, _ -> server }.run(listOf("--db", db) + args, out, err)
        return Ran(code, out.toString(), err.toString())
    }

    private fun json(text: String) = Json.parseToJsonElement(text)

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

        run("delete", id)
        assertEquals(ExitCode.NOT_FOUND, run("show", id).code)
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
        assertTrue(ran.out.startsWith("usage: quicknib"))
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
        assertTrue(ran.err, ran.err.startsWith("sync needs the memos access token in QUICKNIB_TOKEN"))
    }

    @Test
    fun Sync_sends_notes_and_reports_as_json() {
        env = mapOf("QUICKNIB_TOKEN" to "t", "QUICKNIB_SERVER" to "https://memos.example.com")
        run("add", "--body", "hello")

        val ran = run("sync", "--json")

        assertEquals(ExitCode.OK, ran.code)
        assertEquals(1, json(ran.out).jsonObject["sent"]!!.jsonPrimitive.content.toInt())
        assertEquals("hello", server.memos.values.single().state.content)
    }

    @Test
    fun A_failed_sync_exits_with_its_own_code() {
        env = mapOf("QUICKNIB_TOKEN" to "t", "QUICKNIB_SERVER" to "https://memos.example.com")
        server.offline = true

        val ran = run("sync")

        assertEquals(ExitCode.SYNC_FAILED, ran.code)
        assertTrue(ran.err, ran.err.startsWith("sync failed: fake server is offline"))
    }

    @Test
    fun Two_databases_share_notes_through_a_real_memos_server() {
        val live = LiveMemos.connect()
        val realEnv = mapOf("QUICKNIB_TOKEN" to live.token, "QUICKNIB_SERVER" to live.server.toString())
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
}
