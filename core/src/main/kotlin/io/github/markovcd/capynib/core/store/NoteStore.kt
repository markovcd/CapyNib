package io.github.markovcd.capynib.core.store

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import app.cash.sqldelight.db.SqlDriver
import io.github.markovcd.capynib.core.db.CapynibDatabase
import io.github.markovcd.capynib.core.db.Note as NoteRow
import io.github.markovcd.capynib.core.db.Recording as RecordingRow
import io.github.markovcd.capynib.core.memos.MemoState
import io.github.markovcd.capynib.core.memos.ServerAttachment
import io.github.markovcd.capynib.core.note.MemoContent
import io.github.markovcd.capynib.core.note.Note
import io.github.markovcd.capynib.core.note.NoteColor
import io.github.markovcd.capynib.core.note.Recording
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.io.File
import java.util.UUID
import kotlin.time.Duration.Companion.days

/**
 * The notes on this device: the source of truth the UI reads and writes, whether or not a server
 * is set up. Every local change bumps the note's version, which is how sync finds what to push.
 * A note's recordings are kept beside it, their bytes in [files].
 */
class NoteStore(
    driver: SqlDriver,
    private val files: RecordingFiles,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val database = CapynibDatabase(driver)
    private val queries = database.noteQueries
    private val recordings = database.recordingQueries

    fun observeVisible(): Flow<List<Note>> =
        combine(
            queries.visible().asFlow().mapToList(dispatcher),
            recordings.visible().asFlow().mapToList(dispatcher),
        ) { rows, clips ->
            val byNote = clips.map { it.toRecording() }.groupBy { it.noteId }
            rows.map { it.toNote(byNote[it.id].orEmpty()) }
        }

    fun observe(id: String): Flow<Note?> =
        combine(
            queries.byId(id).asFlow().mapToOneOrNull(dispatcher),
            recordings.ofNote(id).asFlow().mapToList(dispatcher),
        ) { row, clips -> row?.takeUnless { it.deleted }?.toNote(clips.map { it.toRecording() }) }

    fun visible(): List<Note> {
        val byNote = recordings.visible().executeAsList().map { it.toRecording() }.groupBy { it.noteId }
        return queries.visible().executeAsList().map { it.toNote(byNote[it.id].orEmpty()) }
    }

    fun get(id: String): Note? = queries.byId(id).executeAsOneOrNull()?.takeUnless { it.deleted }?.let { it.toNote(recordingsOf(it.id)) }

    /** A new recording for note [noteId], not yet stored: [addRecording] stores it with its clip. */
    fun newRecording(noteId: String, durationMillis: Long, now: Long): Recording {
        val id = newId()
        return Recording(id, noteId, "recording-$id.m4a", Recording.TYPE, durationMillis, now, downloaded = true)
    }

    /** Stores [recording] with the clip at [source], which moves into the store. */
    fun addRecording(recording: Recording, source: File) {
        files.adopt(source, recording.id)
        recordings.insertLocal(recording.id, recording.noteId, recording.fileName, recording.type, recording.durationMillis, recording.createdAt)
    }

    /**
     * Deletes the recording's bytes; its row stays as a tombstone until sync has deleted it on the
     * server, where a recording whose reply was lost may be without this device knowing.
     */
    fun deleteRecording(id: String) {
        recordings.markDeleted(id)
        files.delete(id)
    }

    /** The file holding recording [id]'s bytes, once it is [Recording.downloaded]. */
    fun recordingFile(id: String): File = files.file(id)

    /** Makes a note at the top of the board. */
    fun create(now: Long, title: String = "", body: String = "", color: NoteColor = NoteColor.Default): Note = queries.transactionWithResult {
        val position = maxOf(now / 1000, queries.nextPosition().executeAsOne())
        val note = Note(newId(), title, body, color, pinned = false, archived = false, createdAt = now, updatedAt = now, position = position)
        queries.insertLocal(note.id, note.title, note.body, note.color.key, note.pinned, note.archived, now, now, position)
        note
    }

    /** Writes the user-editable fields of [note]; returns false when nothing differed or the note is gone. */
    fun save(note: Note, now: Long): Boolean = queries.transactionWithResult {
        val current = queries.byId(note.id).executeAsOneOrNull()?.takeUnless { it.deleted }?.toNote(emptyList())
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

    fun stored(id: String): StoredNote? = queries.byId(id).executeAsOneOrNull()?.let { it.toStored(recordingsOf(it.id)) }

    fun pending(): List<StoredNote> = queries.pending().executeAsList().map { it.toStored(recordingsOf(it.id)) }

    fun onServer(): List<StoredNote> = queries.onServer().executeAsList().map { it.toStored(recordingsOf(it.id)) }

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

    /** The server no longer has this note; it will be created again on the next push, its recordings with it. */
    fun forgetServer(id: String) {
        queries.transaction {
            queries.forgetServer(id)
            recordings.forgetServer(id)
        }
    }

    /**
     * Makes note [noteId]'s recordings agree with the [attachments] of its memo: one made elsewhere
     * is added, to be downloaded; one sent here whose reply was lost is known to be there; one
     * deleted elsewhere is deleted here.
     */
    fun syncRecordings(noteId: String, attachments: List<ServerAttachment>) {
        val gone = recordings.transactionWithResult {
            attachments.forEach {
                recordings.insertFromServer(it.id, noteId, it.fileName, it.type, it.createdAt)
                recordings.markOnServer(it.id)
            }
            recordings.onServerOf(noteId).executeAsList() - attachments.map { it.id }.toSet()
        }
        gone.forEach(::purgeRecording)
    }

    /** Recordings to send: each one's note is on the server to link it to. */
    fun recordingsToUpload(): List<Recording> = recordings.toUpload().executeAsList().map { it.toRecording() }

    fun recordingsToDownload(): List<Recording> = recordings.toDownload().executeAsList().map { it.toRecording() }

    /** Recordings deleted here, to delete on the server too. */
    fun recordingsToDelete(): List<String> = recordings.toDelete().executeAsList()

    /** Recording [id]'s bytes, or null when they are not on this device. */
    fun readRecording(id: String): ByteArray? = files.read(id)

    fun markRecordingSent(id: String) {
        recordings.markOnServer(id)
    }

    /** Stores the [bytes] sync fetched for recording [id]. */
    fun saveDownload(id: String, bytes: ByteArray) {
        files.write(id, bytes)
        recordings.markDownloaded(id)
    }

    /** Drops recording [id] and its bytes from this device. */
    fun purgeRecording(id: String) {
        recordings.purge(id)
        files.delete(id)
    }

    /**
     * Records [account] as the one the notes sync with. When they were synced with another, every
     * note starts over as never sent, so the next push puts each one on [account]'s server.
     */
    fun syncWith(account: String) {
        database.transaction {
            val previous = database.accountQueries.synced().executeAsOneOrNull()
            if (previous != null && previous != account) {
                queries.forgetAllServers()
                recordings.forgetAllServers()
            }
            database.accountQueries.set(account)
        }
    }

    /** Drops note [id] and its recordings from this device. */
    fun purge(id: String) {
        val clips = queries.transactionWithResult {
            queries.purge(id)
            recordings.idsOfNote(id).executeAsList()
        }
        clips.forEach(::purgeRecording)
    }

    private fun recordingsOf(noteId: String): List<Recording> = recordings.ofNote(noteId).executeAsList().map { it.toRecording() }

    companion object {
        /** The schema a driver for this store is created with; drivers create or upgrade the file from it. */
        val schema = CapynibDatabase.Schema
    }
}

private fun NoteRow.toNote(recordings: List<Recording>) = Note(
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
    recordings = recordings,
)

private fun RecordingRow.toRecording() = Recording(id, note_id, file_name, type, duration_ms, created_at, downloaded)

private fun NoteRow.toStored(recordings: List<Recording>): StoredNote {
    val content = synced_content
    return StoredNote(
        note = toNote(recordings),
        deleted = deleted,
        version = version,
        syncedVersion = synced_version,
        onServer = on_server,
        synced = if (content == null) null else MemoState(content, synced_pinned ?: false, synced_archived ?: false, synced_position ?: position),
    )
}
