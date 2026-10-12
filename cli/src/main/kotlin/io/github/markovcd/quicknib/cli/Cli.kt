package io.github.markovcd.quicknib.cli

import io.github.markovcd.quicknib.core.board.BoardContent
import io.github.markovcd.quicknib.core.memos.HttpMemosApi
import io.github.markovcd.quicknib.core.memos.MemosApi
import io.github.markovcd.quicknib.core.memos.ServerAddress
import io.github.markovcd.quicknib.core.note.Checklist
import io.github.markovcd.quicknib.core.note.ChecklistLine
import io.github.markovcd.quicknib.core.note.Note
import io.github.markovcd.quicknib.core.note.NoteColor
import io.github.markovcd.quicknib.core.store.NoteStore
import io.github.markovcd.quicknib.core.sync.SyncEngine
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The `quicknib` command line: the same notes and sync as the app, driven without a screen.
 * Answers go to [run]'s `out`, problems to `err`; the exit code is one of [ExitCode].
 */
class Cli(
    private val env: Map<String, String>,
    private val clock: () -> Long,
    private val api: (ServerAddress, String) -> MemosApi = ::HttpMemosApi,
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
        val store by lazy { StoreFile.open(parsed.option("db") ?: env["QUICKNIB_DB"] ?: "quicknib.db") }

        val known = COMMANDS[command] ?: return usage(err, "unknown command '$command'")
        val unknown = parsed.unknown(known.options + "db", known.flags)
        if (unknown.isNotEmpty()) return usage(err, "$command does not take ${unknown.joinToString()}")

        return when (command) {
            "list" -> list(store, parsed, out)
            "show" -> withNote(store, operands, err) { note -> print(note, parsed, out) }
            "add" -> add(store, parsed, out, err)
            "edit" -> withNote(store, operands, err) { note -> edit(store, note, parsed, out, err) }
            "check" -> withNote(store, operands, err) { note -> check(store, note, operands, parsed, out, err) }
            "delete" -> withNote(store, operands, err) { note ->
                store.delete(note.id, clock())
                ExitCode.OK
            }
            "sync" -> sync(store, parsed, out, err)
            "colors" -> {
                NoteColor.entries.forEach { out.appendLine(it.key) }
                ExitCode.OK
            }
            else -> usage(err, "unknown command '$command'")
        }
    }

    private fun list(store: NoteStore, args: Arguments, out: Appendable): Int {
        val content = BoardContent.of(store.visible(), args.option("search").orEmpty(), args.flag("archived"))
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
        val serverText = args.option("server") ?: env["QUICKNIB_SERVER"]
            ?: return usage(err, "sync needs --server URL or QUICKNIB_SERVER")
        val server = ServerAddress.parse(serverText) ?: return usage(err, "sync: '$serverText' is not a server address")
        val token = env["QUICKNIB_TOKEN"]?.takeIf { it.isNotBlank() }
            ?: return usage(err, "sync needs the memos access token in QUICKNIB_TOKEN")
        val report = runBlocking { SyncEngine(store, api(server, token), clock).sync() }
        if (args.flag("json")) {
            out.appendLine(json.encodeToString(SyncJson.of(report)))
        } else {
            out.appendLine("received ${report.received}, sent ${report.sent}, deleted here ${report.deletedHere}, deleted there ${report.deletedThere}, conflicts ${report.conflicts}")
        }
        report.failure?.let { err.appendLine("sync failed: ${it.message}") }
        return if (report.succeeded) ExitCode.OK else ExitCode.SYNC_FAILED
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
        val VALUED = setOf("db", "title", "body", "color", "search", "server")
        val COMMANDS = mapOf(
            "list" to Command(setOf("search"), setOf("archived", "json")),
            "show" to Command(flags = setOf("json")),
            "add" to Command(setOf("title", "body", "color"), setOf("checklist", "pinned", "json")),
            "edit" to Command(setOf("title", "body", "color"), setOf("pin", "unpin", "archive", "unarchive", "json")),
            "check" to Command(flags = setOf("json")),
            "delete" to Command(),
            "sync" to Command(setOf("server"), setOf("json")),
            "colors" to Command(),
        )
        val USAGE = """
            |usage: quicknib [--db FILE] COMMAND
            |  list [--archived] [--search TEXT] [--json]
            |  show ID [--json]
            |  add [--title T] [--body B] [--color C] [--checklist] [--pinned] [--json]
            |  edit ID [--title T] [--body B] [--color C] [--pin|--unpin] [--archive|--unarchive] [--json]
            |  check ID LINE [--json]        tick or untick a checklist item
            |  delete ID
            |  sync [--server URL] [--json]  token from QUICKNIB_TOKEN
            |  colors
            |The database defaults to QUICKNIB_DB, then ./quicknib.db.
            |
        """.trimMargin()
    }
}
