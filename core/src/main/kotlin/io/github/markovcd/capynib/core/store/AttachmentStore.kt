package io.github.markovcd.capynib.core.store

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import io.github.markovcd.capynib.core.db.AttachmentQueries
import io.github.markovcd.capynib.core.memos.ServerAttachment
import io.github.markovcd.capynib.core.note.Photo
import io.github.markovcd.capynib.core.note.Recording
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import java.io.File
import io.github.markovcd.capynib.core.db.Attachment as AttachmentRow

/**
 * The notes' recordings and photos: a row each beside its note, its bytes in [files]. [NoteStore] owns it
 * and reads a note's rows through it.
 */
class AttachmentStore internal constructor(
    private val queries: AttachmentQueries,
    private val files: AttachmentFiles,
    private val newId: () -> String,
) {
    /** A new recording for note [noteId], not yet stored: [addRecording] stores it with its clip. */
    fun newRecording(noteId: String, durationMillis: Long, now: Long): Recording {
        val id = newId()
        return Recording(id, noteId, "recording-$id.m4a", Recording.TYPE, durationMillis, now, downloaded = true)
    }

    /** Stores [recording] with the clip at [source], which moves into the store. */
    fun addRecording(recording: Recording, source: File) {
        files.adopt(source, recording.id)
        queries.insertLocal(recording.id, recording.noteId, recording.fileName, recording.type, recording.durationMillis, recording.createdAt)
    }

    /** A new photo of [type] for note [noteId], not yet stored: [addPhoto] stores it with its image. */
    fun newPhoto(noteId: String, type: String, now: Long): Photo {
        val id = newId()
        return Photo(id, noteId, Photo.fileName(id, type), type, now, downloaded = true)
    }

    /** Stores [photo] with the image at [source], which moves into the store. */
    fun addPhoto(photo: Photo, source: File) {
        files.adopt(source, photo.id)
        queries.insertLocal(photo.id, photo.noteId, photo.fileName, photo.type, null, photo.createdAt)
    }

    /**
     * Deletes a recording's or photo's bytes; its row stays as a tombstone until sync has deleted it
     * on the server, where one whose reply was lost may be without this device knowing.
     */
    fun delete(id: String) {
        queries.markDeleted(id)
        files.delete(id)
    }

    /** The file holding a recording's or photo's bytes, once it is downloaded. */
    fun file(id: String): File = files.file(id)

    /**
     * Makes note [noteId]'s recordings and photos agree with the [kept] attachments of its memo: one
     * made elsewhere is added, to be downloaded; one sent here whose reply was lost is known to be
     * there; one deleted elsewhere is deleted here.
     */
    fun sync(noteId: String, kept: List<ServerAttachment>) {
        val gone = queries.transactionWithResult {
            kept.forEach {
                queries.insertFromServer(it.id, noteId, it.fileName, it.type, it.createdAt)
                queries.markOnServer(it.id)
            }
            queries.onServerOf(noteId).executeAsList() - kept.map { it.id }.toSet()
        }
        gone.forEach(::purge)
    }

    /** Attachments to send: each one's note is on the server to link it to. */
    fun toUpload(): List<StoredAttachment> = queries.toUpload().executeAsList().map { it.toStored() }

    fun toDownload(): List<StoredAttachment> = queries.toDownload().executeAsList().map { it.toStored() }

    /** Recordings and photos deleted here, to delete on the server too. */
    fun toDelete(): List<String> = queries.toDelete().executeAsList()

    /** The bytes of recording or photo [id], or null when they are not on this device. */
    fun read(id: String): ByteArray? = files.read(id)

    fun markSent(id: String) {
        queries.markOnServer(id)
    }

    /** Stores the [bytes] sync fetched for attachment [id]. */
    fun saveDownload(id: String, bytes: ByteArray) {
        files.write(id, bytes)
        queries.markDownloaded(id)
    }

    /** Drops recording or photo [id] and its bytes from this device. */
    fun purge(id: String) {
        queries.purge(id)
        files.delete(id)
    }

    internal fun visible(): List<AttachmentRow> = queries.visible().executeAsList()

    internal fun observeVisible(dispatcher: CoroutineDispatcher): Flow<List<AttachmentRow>> = queries.visible().asFlow().mapToList(dispatcher)

    internal fun of(noteId: String): List<AttachmentRow> = queries.ofNote(noteId).executeAsList()

    internal fun observe(noteId: String, dispatcher: CoroutineDispatcher): Flow<List<AttachmentRow>> =
        queries.ofNote(noteId).asFlow().mapToList(dispatcher)

    internal fun idsOf(noteId: String): List<String> = queries.idsOfNote(noteId).executeAsList()

    internal fun forgetServer(noteId: String) {
        queries.forgetServer(noteId)
    }

    internal fun forgetAllServers() {
        queries.forgetAllServers()
    }
}

private fun AttachmentRow.toStored() = StoredAttachment(id, note_id, file_name, type)
