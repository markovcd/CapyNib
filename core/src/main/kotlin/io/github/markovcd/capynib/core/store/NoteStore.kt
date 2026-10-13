package io.github.markovcd.capynib.core.store

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import app.cash.sqldelight.db.SqlDriver
import io.github.markovcd.capynib.core.db.CapynibDatabase
import io.github.markovcd.capynib.core.db.Note as NoteRow
import io.github.markovcd.capynib.core.memos.MemoState
import io.github.markovcd.capynib.core.note.MemoContent
import io.github.markovcd.capynib.core.note.Note
import io.github.markovcd.capynib.core.note.NoteColor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import kotlin.time.Duration.Companion.days

/**
 * The notes on this device: the source of truth the UI reads and writes, whether or not a server
 * is set up. Every local change bumps the note's version, which is how sync finds what to push.
 */
class NoteStore(
    driver: SqlDriver,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val database = CapynibDatabase(driver)
    private val queries = database.noteQueries

    fun observeVisible(): Flow<List<Note>> =
        queries.visible().asFlow().mapToList(dispatcher).map { rows -> rows.map { it.toNote() } }

    fun observe(id: String): Flow<Note?> =
        queries.byId(id).asFlow().mapToOneOrNull(dispatcher).map { row -> row?.takeUnless { it.deleted }?.toNote() }

    fun visible(): List<Note> = queries.visible().executeAsList().map { it.toNote() }

    fun get(id: String): Note? = queries.byId(id).executeAsOneOrNull()?.takeUnless { it.deleted }?.toNote()

    /** Makes a note at the top of the board. */
    fun create(now: Long, title: String = "", body: String = "", color: NoteColor = NoteColor.Default): Note = queries.transactionWithResult {
        val position = maxOf(now / 1000, queries.nextPosition().executeAsOne())
        val note = Note(newId(), title, body, color, pinned = false, archived = false, createdAt = now, updatedAt = now, position = position)
        queries.insertLocal(note.id, note.title, note.body, note.color.key, note.pinned, note.archived, now, now, position)
        note
    }

    /** Writes the user-editable fields of [note]; returns false when nothing differed or the note is gone. */
    fun save(note: Note, now: Long): Boolean = queries.transactionWithResult {
        val current = queries.byId(note.id).executeAsOneOrNull()?.takeUnless { it.deleted }?.toNote()
        if (current == null || current.sameContentAs(note)) {
            false
        } else {
            queries.updateLocal(note.title, note.body, note.color.key, note.pinned, note.archived, now, note.id)
            true
        }
    }

    /** Applies [change] to each note of [ids] that still exists, as [save] would; returns how many it changed. */
    fun saveAll(ids: Collection<String>, now: Long, change: (Note) -> Note): Int = queries.transactionWithResult {
        ids.count { id -> get(id)?.let { save(change(it), now) } ?: false }
    }

    /** Gives each note the position it is mapped to; a note that moves has a local edit for sync to send. */
    fun move(positions: Map<String, Long>) {
        queries.transaction {
            positions.forEach { (id, position) -> queries.move(position, id) }
        }
    }

    /** Moves the note to the trash, where it stays until [emptyTrash] deletes it for good. */
    fun trash(id: String, now: Long) {
        queries.trash(now, id)
    }

    fun trashAll(ids: Collection<String>, now: Long) {
        queries.transaction { ids.forEach { queries.trash(now, it) } }
    }

    /** Takes the note out of the trash, back where it was. */
    fun restore(id: String, now: Long) {
        queries.restore(now, id)
    }

    /** Deletes the note for good; it stays as a tombstone until sync has deleted its memo. */
    fun deleteForever(id: String, now: Long) {
        queries.markDeleted(now, id)
    }

    /** Deletes for good every note that has been in the trash for [days]; returns how many. */
    fun emptyTrash(now: Long, days: Int): Int = queries.transactionWithResult {
        val expired = queries.trashedBefore(now - days.days.inWholeMilliseconds).executeAsList()
        expired.forEach { queries.markDeleted(now, it) }
        expired.size
    }

    fun stored(id: String): StoredNote? = queries.byId(id).executeAsOneOrNull()?.toStored()

    fun pending(): List<StoredNote> = queries.pending().executeAsList().map { it.toStored() }

    fun onServer(): List<StoredNote> = queries.onServer().executeAsList().map { it.toStored() }

    fun markPushed(id: String, pushedVersion: Long, state: MemoState) {
        queries.markPushed(pushedVersion, state.content, state.pinned, state.archived, state.position, id)
    }

    /** Replaces the note with the server's copy, unless it was edited after [expectedVersion]; returns whether it did. */
    fun applyServer(id: String, expectedVersion: Long, content: MemoContent, state: MemoState, updatedAt: Long): Boolean =
        queries.applyServer(
            title = content.title,
            body = content.body,
            color = content.color.key,
            pinned = state.pinned,
            archived = state.archived,
            updated_at = updatedAt,
            position = state.position,
            synced_content = state.content,
            id = id,
            expected_version = expectedVersion,
        ).value == 1L

    fun insertFromServer(id: String, content: MemoContent, state: MemoState, createdAt: Long, updatedAt: Long) {
        queries.insertFromServer(
            id = id,
            title = content.title,
            body = content.body,
            color = content.color.key,
            pinned = state.pinned,
            archived = state.archived,
            created_at = createdAt,
            updated_at = updatedAt,
            position = state.position,
            synced_content = state.content,
        )
    }

    /** Records [state] as the server's copy without touching the note. */
    fun acceptServer(id: String, state: MemoState) {
        queries.acceptServer(state.content, state.pinned, state.archived, state.position, id)
    }

    /** The server no longer has this note; it will be created again on the next push. */
    fun forgetServer(id: String) {
        queries.forgetServer(id)
    }

    /**
     * Records [account] as the one the notes sync with. When they were synced with another, every
     * note starts over as never sent, so the next push puts each one on [account]'s server.
     */
    fun syncWith(account: String) {
        database.transaction {
            val previous = database.accountQueries.synced().executeAsOneOrNull()
            if (previous != null && previous != account) queries.forgetAllServers()
            database.accountQueries.set(account)
        }
    }

    fun purge(id: String) {
        queries.purge(id)
    }

    companion object {
        /** The schema a driver for this store is created with; drivers create or upgrade the file from it. */
        val schema = CapynibDatabase.Schema
    }
}

private fun NoteRow.toNote() = Note(
    id = id,
    title = title,
    body = body,
    color = NoteColor.fromKey(color),
    pinned = pinned,
    archived = archived,
    createdAt = created_at,
    updatedAt = updated_at,
    position = position,
    trashedAt = trashed_at,
)

private fun NoteRow.toStored(): StoredNote {
    val content = synced_content
    return StoredNote(
        note = toNote(),
        deleted = deleted,
        version = version,
        syncedVersion = synced_version,
        onServer = on_server,
        synced = if (content == null) null else MemoState(content, synced_pinned ?: false, synced_archived ?: false, synced_position ?: position),
    )
}
