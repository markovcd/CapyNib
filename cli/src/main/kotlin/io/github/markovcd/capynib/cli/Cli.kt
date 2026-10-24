package io.github.markovcd.capynib.cli

import io.github.markovcd.capynib.core.board.BoardContent
import io.github.markovcd.capynib.core.board.Shelf
import io.github.markovcd.capynib.core.memos.ApiResult
import io.github.markovcd.capynib.core.memos.BrowserSignIn
import io.github.markovcd.capynib.core.memos.LoopbackReceiver
import io.github.markovcd.capynib.core.memos.HttpMemosApi
import io.github.markovcd.capynib.core.memos.MemosApi
import io.github.markovcd.capynib.core.memos.ServerAddress
import io.github.markovcd.capynib.core.memos.ServerCheck
import io.github.markovcd.capynib.core.memos.SignInResult
import io.github.markovcd.capynib.core.note.Checklist
import io.github.markovcd.capynib.core.note.ChecklistLine
import io.github.markovcd.capynib.core.note.Note
import io.github.markovcd.capynib.core.note.NoteColor
import io.github.markovcd.capynib.core.store.NoteStore
import io.github.markovcd.capynib.core.store.Trash
import io.github.markovcd.capynib.core.sync.SyncEngine
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The `capynib` command line: the same notes and sync as the app, driven without a screen.
 * Answers go to [run]'s `out`, problems to `err`; the exit code is one of [ExitCode].
 */
class Cli(
    private val env: Map<String, String>,
    private val clock: () -> Long,
    private val api: (ServerAddress, String) -> MemosApi = ::HttpMemosApi,
    private val signIn: (ServerAddress) -> BrowserSignIn = { BrowserSignIn(it, "Capynib command line") },
) {
    private val json = Json { prettyPrint = false }

    fun run(args: List<String>, out: Appendable, err: Appendable): Int {
        val parsed = when (val result = Arguments.parse(args, VALUED)) {
            is Arguments.Result.Parsed -> result.arguments
            is Arguments.Result.Error -> return usage(err, result.message)
        }
        if (parsed.flag("help")) {
            out.append(USAGE)
            return ExitCode.OK
        }
        val name = parsed.positionals.firstOrNull() ?: return usage(err, null)
        val command = COMMANDS[name] ?: return usage(err, "unknown command '$name'")
        val unknown = parsed.unknown(command.options + "db", command.flags)
        if (unknown.isNotEmpty()) return usage(err, "$name does not take ${unknown.joinToString()}")

        val call = Call(parsed, parsed.positionals.drop(1), out, err) { StoreFile.open(parsed.option("db") ?: env["CAPYNIB_DB"] ?: "capynib.db") }
        return command.run(this, call)
    }

    private fun delete(store: NoteStore, note: Note, args: Arguments): Int {
        if (args.flag("forever")) store.deleteForever(note.id, clock()) else store.trash(note.id, clock())
        return ExitCode.OK
    }

    private fun restore(store: NoteStore, note: Note, args: Arguments, out: Appendable): Int {
        store.restore(note.id, clock())
        return print(store.get(note.id)!!, args, out)
    }

    private fun emptyTrash(store: () -> NoteStore, args: Arguments, out: Appendable, err: Appendable): Int {
        val days = trashDays(args) { return usage(err, it) }
        out.appendLine(store().emptyTrash(clock(), days).toString())
        return ExitCode.OK
    }

    private fun colors(out: Appendable): Int {
        NoteColor.entries.forEach { out.appendLine(it.key) }
        return ExitCode.OK
    }

    private fun list(store: NoteStore, args: Arguments, out: Appendable): Int {
        val content = BoardContent.of(store.visible(), args.option("search").orEmpty(), shelf(args), args.option("tag"))
        if (args.flag("json")) {
            out.appendLine(json.encodeToString(content.notes.map(NoteJson::of)))
        } else {
            content.notes.forEach { note ->
                val pin = if (note.pinned) "*" else " "
                out.appendLine("${note.id}  $pin ${note.color.key.padEnd(8)} ${note.title.ifEmpty { note.body.lineSequence().first() }}")
            }
        }
        return ExitCode.OK
    }

    private fun tags(store: NoteStore, args: Arguments, out: Appendable): Int {
        val tags = BoardContent.of(store.visible(), shelf = shelf(args), tag = null).tags
        if (args.flag("json")) out.appendLine(json.encodeToString(tags)) else tags.forEach(out::appendLine)
        return ExitCode.OK
    }

    private fun shelf(args: Arguments) = when {
        args.flag("trashed") -> Shelf.Trash
        args.flag("archived") -> Shelf.Archive
        else -> Shelf.Notes
    }

    private fun add(store: NoteStore, args: Arguments, out: Appendable, err: Appendable): Int {
        val color = color(args, err) ?: return ExitCode.USAGE
        var body = args.option("body").orEmpty()
        if (args.flag("checklist")) body = Checklist.toChecklist(body)
        val note = store.create(clock(), args.option("title").orEmpty(), body, color)
        if (args.flag("pinned")) store.save(note.copy(pinned = true), clock())
        val saved = store.get(note.id)!!
        if (args.flag("json")) out.appendLine(json.encodeToString(NoteJson.of(saved))) else out.appendLine(saved.id)
        return ExitCode.OK
    }

    private fun edit(store: NoteStore, note: Note, args: Arguments, out: Appendable, err: Appendable): Int {
        val color = if (args.option("color") == null) note.color else color(args, err) ?: return ExitCode.USAGE
        val edited = note.copy(
            title = args.option("title") ?: note.title,
            body = args.option("body") ?: note.body,
            color = color,
            pinned = when {
                args.flag("pin") -> true
                args.flag("unpin") -> false
                else -> note.pinned
            },
            archived = when {
                args.flag("archive") -> true
                args.flag("unarchive") -> false
                else -> note.archived
            },
        )
        store.save(edited, clock())
        return print(store.get(note.id)!!, args, out)
    }

    private fun move(store: NoteStore, note: Note, operands: List<String>, err: Appendable): Int {
        val shelf = Shelf.of(note)
        val content = BoardContent.of(store.visible(), shelf = shelf)
        val section = if (note.pinned) content.pinned else content.others
        val index = operands.getOrNull(1)?.toIntOrNull()
        if (shelf == Shelf.Trash || index == null || index !in section.indices) {
            err.appendLine("move: ${note.id} goes to a place from 0 to ${section.lastIndex} among the ${if (note.pinned) "pinned" else "other"} notes, not '${operands.getOrNull(1)}'")
            return ExitCode.USAGE
        }
        store.move(content.move(note.id, index))
        return ExitCode.OK
    }

    private fun check(store: NoteStore, note: Note, operands: List<String>, args: Arguments, out: Appendable, err: Appendable): Int {
        val lines = Checklist.parse(note.body)
        val index = operands.getOrNull(1)?.toIntOrNull()
        if (index == null || lines.getOrNull(index) !is ChecklistLine.Item) {
            err.appendLine("check: line ${operands.getOrNull(1)} of ${note.id} is not a checklist item (items: ${lines.indices.filter { lines[it] is ChecklistLine.Item }})")
            return ExitCode.USAGE
        }
        store.save(note.copy(body = Checklist.toggle(note.body, index)), clock())
        return print(store.get(note.id)!!, args, out)
    }

    private fun sync(store: NoteStore, args: Arguments, out: Appendable, err: Appendable): Int {
        val server = server("sync", args) { return usage(err, it) }
        val token = token("sync") { return usage(err, it) }
        val days = trashDays(args) { return usage(err, it) }
        val report = runBlocking { SyncEngine(store, api(server, token), server, days, clock).sync() }
        if (args.flag("json")) {
            out.appendLine(json.encodeToString(SyncJson.of(report)))
        } else {
            out.appendLine("received ${report.received}, sent ${report.sent}, deleted here ${report.deletedHere}, deleted there ${report.deletedThere}, conflicts ${report.conflicts}")
        }
        report.failure?.let { err.appendLine("sync failed: ${it.message}") }
        return if (report.succeeded) ExitCode.OK else ExitCode.SYNC_FAILED
    }

    private fun checkServer(args: Arguments, out: Appendable, err: Appendable): Int {
        val server = server("server", args) { return usage(err, it) }
        val token = token("server") { return usage(err, it) }
        val report = when (val result = runBlocking { ServerCheck(api(server, token)).run() }) {
            is ApiResult.Ok -> result.value
            is ApiResult.Failed -> {
                err.appendLine("server check failed: ${result.failure.message}")
                return ExitCode.SERVER_CHECK_FAILED
            }
        }
        if (args.flag("json")) {
            out.appendLine(json.encodeToString(ServerJson.of(report)))
        } else {
            out.appendLine("memos ${report.version ?: "(version not reported)"}")
            report.results.forEach { out.appendLine(if (it.works) "ok    ${it.capability.what}" else "FAIL  ${it.capability.what}: ${it.problem}") }
        }
        report.leftover?.let { err.appendLine("the check's memo $it could not be deleted; delete it in memos") }
        return if (report.works) ExitCode.OK else ExitCode.SERVER_CHECK_FAILED
    }

    private fun login(args: Arguments, out: Appendable, err: Appendable): Int {
        val server = server("login", args) { return usage(err, it) }
        val browserSignIn = signIn(server)
        val provider = when (val chosen = runBlocking { browserSignIn.provider() }) {
            is ApiResult.Ok -> chosen.value ?: run {
                err.appendLine("login: $server offers no sign-in provider; make an access token in memos instead")
                return ExitCode.LOGIN_FAILED
            }
            is ApiResult.Failed -> {
                err.appendLine("login failed: ${chosen.failure.message}")
                return ExitCode.LOGIN_FAILED
            }
        }
        val result = runBlocking {
            browserSignIn.signIn(provider) { page ->
                err.appendLine("Open this page to sign in with ${provider.title}:").appendLine(page)
                err.appendLine("${provider.title} must allow the redirect address ${LoopbackReceiver.REDIRECT_URI}")
            }
        }
        return when (result) {
            is SignInResult.SignedIn -> {
                if (args.flag("json")) {
                    out.appendLine(json.encodeToString(LoginJson(result.username, result.accessToken)))
                } else {
                    out.appendLine(result.accessToken)
                }
                ExitCode.OK
            }
            is SignInResult.Refused -> {
                err.appendLine("login refused: ${result.reason}")
                ExitCode.LOGIN_FAILED
            }
            is SignInResult.Failed -> {
                err.appendLine("login failed: ${result.failure.message}")
                ExitCode.LOGIN_FAILED
            }
        }
    }

    private inline fun trashDays(args: Arguments, problem: (String) -> Nothing): Int {
        val text = args.option("trash-days") ?: return Trash.DEFAULT_DAYS
        return Trash.parseDays(text) ?: problem("--trash-days takes a whole number of days from ${Trash.DAYS.first} to ${Trash.DAYS.last}, not '$text'")
    }

    private inline fun token(command: String, problem: (String) -> Nothing): String =
        env["CAPYNIB_TOKEN"]?.takeIf { it.isNotBlank() } ?: problem("$command needs the memos access token in CAPYNIB_TOKEN")

    private inline fun server(command: String, args: Arguments, problem: (String) -> Nothing): ServerAddress {
        val text = args.option("server") ?: env["CAPYNIB_SERVER"] ?: problem("$command needs --server URL or CAPYNIB_SERVER")
        return ServerAddress.parse(text) ?: problem("$command: '$text' is not a server address")
    }

    private fun print(note: Note, args: Arguments, out: Appendable): Int {
        if (args.flag("json")) {
            out.appendLine(json.encodeToString(NoteJson.of(note)))
        } else {
            if (note.title.isNotEmpty()) out.appendLine(note.title)
            Checklist.parse(note.body).forEachIndexed { i, line ->
                out.appendLine(
                    when (line) {
                        is ChecklistLine.Text -> line.text
                        is ChecklistLine.Item -> "$i ${if (line.checked) "[x]" else "[ ]"} ${line.text}"
                    },
                )
            }
        }
        return ExitCode.OK
    }

    private fun withNote(store: NoteStore, operands: List<String>, err: Appendable, action: (Note) -> Int): Int {
        val id = operands.firstOrNull() ?: return usage(err, "a note id is needed")
        val note = store.get(id)
        if (note == null) {
            err.appendLine("no note with id '$id'")
            return ExitCode.NOT_FOUND
        }
        return action(note)
    }

    private fun color(args: Arguments, err: Appendable): NoteColor? {
        val key = args.option("color") ?: return NoteColor.Default
        return NoteColor.parseKey(key) ?: run {
            err.appendLine("unknown color '$key'; colors: ${NoteColor.entries.joinToString { it.key }}")
            null
        }
    }

    private fun usage(err: Appendable, problem: String?): Int {
        if (problem != null) err.appendLine(problem)
        err.append(USAGE)
        return ExitCode.USAGE
    }

    /** One run of a command: its arguments, the operands after its name, the two writers, and the notes, opened on first use. */
    internal class Call(val args: Arguments, val operands: List<String>, val out: Appendable, val err: Appendable, openStore: () -> NoteStore) {
        val store by lazy(openStore)
    }

    /** A command's options and flags, its line of the usage after its name, and what it does. */
    internal class Command(val options: Set<String> = emptySet(), val flags: Set<String> = emptySet(), val usage: String, val run: Cli.(Call) -> Int)

    internal companion object {
        val VALUED = setOf("db", "title", "body", "color", "search", "tag", "server", "trash-days")
        val COMMANDS = mapOf(
            "list" to Command(setOf("search", "tag"), setOf("archived", "trashed", "json"), usage = "[--archived|--trashed] [--search TEXT] [--tag TAG] [--json]") { list(it.store, it.args, it.out) },
            "tags" to Command(flags = setOf("archived", "trashed", "json"), usage = "[--archived|--trashed] [--json]  the tags on the notes, with the tags they are inside") { tags(it.store, it.args, it.out) },
            "show" to Command(flags = setOf("json"), usage = "ID [--json]") { c -> withNote(c.store, c.operands, c.err) { print(it, c.args, c.out) } },
            "add" to Command(setOf("title", "body", "color"), setOf("checklist", "pinned", "json"), usage = "[--title T] [--body B] [--color C] [--checklist] [--pinned] [--json]") { add(it.store, it.args, it.out, it.err) },
            "edit" to Command(setOf("title", "body", "color"), setOf("pin", "unpin", "archive", "unarchive", "json"), usage = "ID [--title T] [--body B] [--color C] [--pin|--unpin] [--archive|--unarchive] [--json]") { c ->
                withNote(c.store, c.operands, c.err) { edit(c.store, it, c.args, c.out, c.err) }
            },
            "check" to Command(flags = setOf("json"), usage = "ID LINE [--json]        tick or untick a checklist item") { c -> withNote(c.store, c.operands, c.err) { check(c.store, it, c.operands, c.args, c.out, c.err) } },
            "move" to Command(usage = "ID PLACE                 put at PLACE (0 is first) among the pinned or the other notes") { c -> withNote(c.store, c.operands, c.err) { move(c.store, it, c.operands, c.err) } },
            "delete" to Command(flags = setOf("forever"), usage = "ID [--forever]         move to the trash, or delete for good") { c -> withNote(c.store, c.operands, c.err) { delete(c.store, it, c.args) } },
            "restore" to Command(flags = setOf("json"), usage = "ID [--json]           take out of the trash") { c -> withNote(c.store, c.operands, c.err) { restore(c.store, it, c.args, c.out) } },
            "empty-trash" to Command(setOf("trash-days"), usage = "[--trash-days N]  delete notes N days (default 7) in the trash; prints how many") { c -> emptyTrash({ c.store }, c.args, c.out, c.err) },
            "sync" to Command(setOf("server", "trash-days"), setOf("json"), usage = "[--server URL] [--trash-days N] [--json]  token from CAPYNIB_TOKEN") { sync(it.store, it.args, it.out, it.err) },
            "login" to Command(setOf("server"), setOf("json"), usage = "[--server URL] [--json] sign in in the browser; prints a new access token") { login(it.args, it.out, it.err) },
            "server" to Command(setOf("server"), setOf("json"), usage = "[--server URL] [--json] try every call sync makes on its own test memo; token from CAPYNIB_TOKEN") { checkServer(it.args, it.out, it.err) },
            "colors" to Command(usage = "") { colors(it.out) },
        )
        val USAGE = (
            listOf("usage: capynib [--db FILE] COMMAND") +
                COMMANDS.map { (name, command) -> "  $name ${command.usage}".trimEnd() } +
                listOf("The database defaults to CAPYNIB_DB, then ./capynib.db.", "")
            ).joinToString("\n")
    }
}
