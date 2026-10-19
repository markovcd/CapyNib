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
        val command = parsed.positionals.firstOrNull() ?: return usage(err, null)
        val operands = parsed.positionals.drop(1)
        val store by lazy { StoreFile.open(parsed.option("db") ?: env["CAPYNIB_DB"] ?: "capynib.db") }

        val known = COMMANDS[command] ?: return usage(err, "unknown command '$command'")
        val unknown = parsed.unknown(known.options + "db", known.flags)
        if (unknown.isNotEmpty()) return usage(err, "$command does not take ${unknown.joinToString()}")

        return when (command) {
            "list" -> list(store, parsed, out)
            "tags" -> tags(store, parsed, out)
            "show" -> withNote(store, operands, err) { note -> print(note, parsed, out) }
            "add" -> add(store, parsed, out, err)
            "edit" -> withNote(store, operands, err) { note -> edit(store, note, parsed, out, err) }
            "check" -> withNote(store, operands, err) { note -> check(store, note, operands, parsed, out, err) }
            "delete" -> withNote(store, operands, err) { note ->
                if (parsed.flag("forever")) store.deleteForever(note.id, clock()) else store.trash(note.id, clock())
                ExitCode.OK
            }
            "move" -> withNote(store, operands, err) { note -> move(store, note, operands, err) }
            "restore" -> withNote(store, operands, err) { note ->
                store.restore(note.id, clock())
                print(store.get(note.id)!!, parsed, out)
            }
            "empty-trash" -> {
                val days = trashDays(parsed) { return usage(err, it) }
                out.appendLine(store.emptyTrash(clock(), days).toString())
                ExitCode.OK
            }
            "sync" -> sync(store, parsed, out, err)
            "login" -> login(parsed, out, err)
            "server" -> checkServer(parsed, out, err)
            "colors" -> {
                NoteColor.entries.forEach { out.appendLine(it.key) }
                ExitCode.OK
            }
            else -> usage(err, "unknown command '$command'")
        }
    }

    private fun list(store: NoteStore, args: Arguments, out: Appendable): Int {
        val content = BoardContent.of(store.visible(), args.option("search").orEmpty(), shelf(args), args.option("tag"))
        val notes = content.pinned + content.others
        if (args.flag("json")) {
            out.appendLine(json.encodeToString(notes.map(NoteJson::of)))
        } else {
            notes.forEach { note ->
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
        val note = store.create(clock(), args.option("title").orEmpty().replace('\n', ' '), body, color)
        if (args.flag("pinned")) store.save(note.copy(pinned = true), clock())
        val saved = store.get(note.id)!!
        if (args.flag("json")) out.appendLine(json.encodeToString(NoteJson.of(saved))) else out.appendLine(saved.id)
        return ExitCode.OK
    }

    private fun edit(store: NoteStore, note: Note, args: Arguments, out: Appendable, err: Appendable): Int {
        val color = if (args.option("color") == null) note.color else color(args, err) ?: return ExitCode.USAGE
        val edited = note.copy(
            title = args.option("title")?.replace('\n', ' ') ?: note.title,
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
        val shelf = when {
            note.inTrash -> Shelf.Trash
            note.archived -> Shelf.Archive
            else -> Shelf.Notes
        }
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
        val provider = when (val providers = runBlocking { browserSignIn.providers() }) {
            is ApiResult.Ok -> providers.value.firstOrNull() ?: run {
                err.appendLine("login: $server offers no sign-in provider; make an access token in memos instead")
                return ExitCode.LOGIN_FAILED
            }
            is ApiResult.Failed -> {
                err.appendLine("login failed: ${providers.failure.message}")
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

    private data class Command(val options: Set<String> = emptySet(), val flags: Set<String> = emptySet())

    private companion object {
        val VALUED = setOf("db", "title", "body", "color", "search", "tag", "server", "trash-days")
        val COMMANDS = mapOf(
            "list" to Command(setOf("search", "tag"), setOf("archived", "trashed", "json")),
            "tags" to Command(flags = setOf("archived", "trashed", "json")),
            "show" to Command(flags = setOf("json")),
            "add" to Command(setOf("title", "body", "color"), setOf("checklist", "pinned", "json")),
            "edit" to Command(setOf("title", "body", "color"), setOf("pin", "unpin", "archive", "unarchive", "json")),
            "check" to Command(flags = setOf("json")),
            "delete" to Command(flags = setOf("forever")),
            "move" to Command(),
            "restore" to Command(flags = setOf("json")),
            "empty-trash" to Command(setOf("trash-days")),
            "sync" to Command(setOf("server", "trash-days"), setOf("json")),
            "login" to Command(setOf("server"), setOf("json")),
            "server" to Command(setOf("server"), setOf("json")),
            "colors" to Command(),
        )
        val USAGE = """
            |usage: capynib [--db FILE] COMMAND
            |  list [--archived|--trashed] [--search TEXT] [--tag TAG] [--json]
            |  tags [--archived|--trashed] [--json]  the tags on the notes, with the tags they are inside
            |  show ID [--json]
            |  add [--title T] [--body B] [--color C] [--checklist] [--pinned] [--json]
            |  edit ID [--title T] [--body B] [--color C] [--pin|--unpin] [--archive|--unarchive] [--json]
            |  check ID LINE [--json]        tick or untick a checklist item
            |  move ID PLACE                 put at PLACE (0 is first) among the pinned or the other notes
            |  delete ID [--forever]         move to the trash, or delete for good
            |  restore ID [--json]           take out of the trash
            |  empty-trash [--trash-days N]  delete notes N days (default 7) in the trash; prints how many
            |  sync [--server URL] [--trash-days N] [--json]  token from CAPYNIB_TOKEN
            |  login [--server URL] [--json] sign in in the browser; prints a new access token
            |  server [--server URL] [--json] try every call sync makes on its own test memo; token from CAPYNIB_TOKEN
            |  colors
            |The database defaults to CAPYNIB_DB, then ./capynib.db.
            |
        """.trimMargin()
    }
}
