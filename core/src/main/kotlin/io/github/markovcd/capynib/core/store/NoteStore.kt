package io.github.markovcd.capynib.core.store

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import app.cash.sqldelight.db.SqlDriver
import io.github.markovcd.capynib.core.db.CapynibDatabase
import io.github.markovcd.capynib.core.db.Note as NoteRow
import io.github.markovcd.capynib.core.db.Attachment as AttachmentRow
import io.github.markovcd.capynib.core.memos.MemoState
import io.github.markovcd.capynib.core.memos.ServerAttachment
import io.github.markovcd.capynib.core.note.MemoContent
import io.github.markovcd.capynib.core.note.Note
import io.github.markovcd.capynib.core.note.NoteColor
import io.github.markovcd.capynib.core.note.Photo
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
 * A note's recordings and photos are kept beside it as its attachments, their bytes in [files].
 */
class NoteStore(
    driver: SqlDriver,
    private val files: AttachmentFiles,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val database = CapynibDatabase(driver)
    private val queries = database.noteQueries
    private val attachments = database.attachmentQueries

    fun observeVisible(): Flow<List<Note>> =
        combine(
            queries.visible().asFlow().mapToList(dispatcher),
            attachments.visible().asFlow().mapToList(dispatcher),
        ) { rows, files ->
            val byNote = files.groupBy { it.note_id }
            rows.map { it.toNote(byNote[it.id].orEmpty()) }
        }

    fun observe(id: String): Flow<Note?> =
        combine(
            queries.byId(id).asFlow().mapToOneOrNull(dispatcher),
            attachments.ofNote(id).asFlow().mapToList(dispatcher),
        ) { row, files -> row?.takeUnless { it.deleted }?.toNote(files) }

    fun visible(): List<Note> {
        val byNote = attachments.visible().executeAsList().groupBy { it.note_id }
        return queries.visible().executeAsList().map { it.toNote(byNote[it.id].orEmpty()) }
    }

    fun get(id: String): Note? = queries.byId(id).executeAsOneOrNull()?.takeUnless { it.deleted }?.let { it.toNote(attachmentsOf(it.id)) }

    /** A new recording for note [noteId], not yet stored: [addRecording] stores it with its clip. */
    fun newRecording(noteId: String, durationMillis: Long, now: Long): Recording {
        val id = newId()
        return Recording(id, noteId, "recording-$id.m4a", Recording.TYPE, durationMillis, now, downloaded = true)
    }

    /** Stores [recording] with the clip at [source], which moves into the store. */
    fun addRecording(recording: Recording, source: File) {
        files.adopt(source, recording.id)
        attachments.insertLocal(recording.id, recording.noteId, recording.fileName, recording.type, recording.durationMillis, recording.createdAt)
    }

    /** A new photo of [type] for note [noteId], not yet stored: [addPhoto] stores it with its image. */
    fun newPhoto(noteId: String, type: String, now: Long): Photo {
        val id = newId()
        return Photo(id, noteId, Photo.fileName(id, type), type, now, downloaded = true)
    }

    /** Stores [photo] with the image at [source], which moves into the store. */
    fun addPhoto(photo: Photo, source: File) {
        files.adopt(source, photo.id)
        attachments.insertLocal(photo.id, photo.noteId, photo.fileName, photo.type, null, photo.createdAt)
    }

    /**
     * Deletes a recording's or photo's bytes; its row stays as a tombstone until sync has deleted it
     * on the server, where one whose reply was lost may be without this device knowing.
     */
    fun deleteAttachment(id: String) {
        attachments.markDeleted(id)
        files.delete(id)
    }

    /** The file holding a recording's or photo's bytes, once it is downloaded. */
    fun attachmentFile(id: String): File = files.file(id)

    /** Makes a note at the top of the board. */
    fun create(now: Long, title: String = "", body: String = "", color: NoteColor = NoteColor.Default): Note = queries.transactionWithResult {
        val position = maxOf(now / 1000, queries.nextPosition().executeAsOne())
        val note = Note(newId(), title, body, color, pinned = false, archived = false, createdAt = now, updatedAt = now, position = position)
        queries.insertLocal(note.id, note.title, note.body, note.color.key, note.pinned, note.archived, now, now, position)
        note
    }

    /** Writes the user-editable fields of [note]; returns false when nothing differed or the note is gone. */
    fun save(note: Note, now: Long): Boolean = queries.transactionWithResult {
        val current = queries.byId(note.id).executeAsOneOrNull()?.takeUnless { it.deleted }?.toNote(emptyList<AttachmentRow>())
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

    fun stored(id: String): StoredNote? = queries.byId(id).executeAsOneOrNull()?.let { it.toStored(attachmentsOf(it.id)) }

    fun pending(): List<StoredNote> = queries.pending().executeAsList().map { it.toStored(attachmentsOf(it.id)) }

    fun onServer(): List<StoredNote> = queries.onServer().executeAsList().map { it.toStored(attachmentsOf(it.id)) }

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

    /** The server no longer has this note; it will be created again on the next push, its attachments with it. */
    fun forgetServer(id: String) {
        queries.transaction {
            queries.forgetServer(id)
            attachments.forgetServer(id)
        }
    }

    /**
     * Makes note [noteId]'s recordings and photos agree with the [kept] attachments of its memo: one
     * made elsewhere is added, to be downloaded; one sent here whose reply was lost is known to be
     * there; one deleted elsewhere is deleted here.
     */
    fun syncAttachments(noteId: String, kept: List<ServerAttachment>) {
        val gone = attachments.transactionWithResult {
            kept.forEach {
                attachments.insertFromServer(it.id, noteId, it.fileName, it.type, it.createdAt)
                attachments.markOnServer(it.id)
            }
            attachments.onServerOf(noteId).executeAsList() - kept.map { it.id }.toSet()
        }
        gone.forEach(::purgeAttachment)
    }

    /** Attachments to send: each one's note is on the server to link it to. */
    fun attachmentsToUpload(): List<StoredAttachment> = attachments.toUpload().executeAsList().map { it.toStored() }

    fun attachmentsToDownload(): List<StoredAttachment> = attachments.toDownload().executeAsList().map { it.toStored() }

    /** Recordings and photos deleted here, to delete on the server too. */
    fun attachmentsToDelete(): List<String> = attachments.toDelete().executeAsList()

    /** The bytes of recording or photo [id], or null when they are not on this device. */
    fun readAttachment(id: String): ByteArray? = files.read(id)

    fun markAttachmentSent(id: String) {
        attachments.markOnServer(id)
    }

    /** Stores the [bytes] sync fetched for attachment [id]. */
    fun saveDownload(id: String, bytes: ByteArray) {
        files.write(id, bytes)
        attachments.markDownloaded(id)
    }

    /** Drops recording or photo [id] and its bytes from this device. */
    fun purgeAttachment(id: String) {
        attachments.purge(id)
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
                attachments.forgetAllServers()
            }
            database.accountQueries.set(account)
        }
    }

    /** Drops note [id] and its recordings and photos from this device. */
    fun purge(id: String) {
        val files = queries.transactionWithResult {
            queries.purge(id)
            attachments.idsOfNote(id).executeAsList()
        }
        files.forEach(::purgeAttachment)
    }

    private fun attachmentsOf(noteId: String): List<AttachmentRow> = attachments.ofNote(noteId).executeAsList()

    companion object {
        /** The schema a driver for this store is created with; drivers create or upgrade the file from it. */
        val schema = CapynibDatabase.Schema
    }
}

/** The note, with each of its [files] among its photos or its recordings. */
private fun NoteRow.toNote(files: List<AttachmentRow>): Note {
    val (photos, recordings) = files.partition { Photo.isPhoto(it.type) }
    return toNote(recordings.map { it.toRecording() }, photos.map { it.toPhoto() })
}

private fun NoteRow.toNote(recordings: List<Recording>, photos: List<Photo>) = Note(
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
    photos = photos,
)

private fun AttachmentRow.toRecording() = Recording(id, note_id, file_name, type, duration_ms, created_at, downloaded)

private fun AttachmentRow.toPhoto() = Photo(id, note_id, file_name, type, created_at, downloaded)

private fun AttachmentRow.toStored() = StoredAttachment(id, note_id, file_name, type)

private fun NoteRow.toStored(files: List<AttachmentRow>): StoredNote {
    val content = synced_content
    return StoredNote(
        note = toNote(files),
        deleted = deleted,
        version = version,
        syncedVersion = synced_version,
        onServer = on_server,
        synced = if (content == null) null else MemoState(content, synced_pinned ?: false, synced_archived ?: false, synced_position ?: position),
    )
}
