package io.github.markovcd.capynib.core.sync

import io.github.markovcd.capynib.core.memos.ApiResult
import io.github.markovcd.capynib.core.memos.MemosApi
import io.github.markovcd.capynib.core.memos.ServerAddress
import io.github.markovcd.capynib.core.memos.ServerMemo
import io.github.markovcd.capynib.core.memos.SyncFailure
import io.github.markovcd.capynib.core.note.MemoCodec
import io.github.markovcd.capynib.core.store.NoteStore
import io.github.markovcd.capynib.core.store.StoredAttachment
import io.github.markovcd.capynib.core.store.StoredNote
import io.github.markovcd.capynib.core.store.Trash

/**
 * Two-way sync between the [store] and a memos server.
 *
 * Notes that have been in the trash for [trashDays] are deleted first. Then pull: a memo that
 * changed on the server replaces a note with no local edits; when both sides changed, the local edit
 * wins and the server's version is kept as a new note, so nothing is lost ([PullStep]). Then push every note with
 * local edits. Notes are created on the server under their own id, so a create that is retried after
 * a lost reply updates instead of duplicating. Notes last synced with another [server] or user are
 * sent to this one as new.
 *
 * A note's recordings and photos are its memo's audio and image attachments. They follow the memo's
 * list of them, and are sent once the memo exists, deleted there, and downloaded, after the notes.
 */
class SyncEngine(
    private val store: NoteStore,
    private val api: MemosApi,
    private val server: ServerAddress,
    private val trashDays: Int = Trash.DEFAULT_DAYS,
    private val clock: () -> Long,
) {
    suspend fun sync(): SyncReport {
        store.emptyTrash(clock(), trashDays)
        when (val user = api.currentUser()) {
            is ApiResult.Ok -> store.syncWith("${user.value}@$server")
            is ApiResult.Failed -> return SyncReport(failure = user.failure)
            ApiResult.NotFound, ApiResult.AlreadyExists -> return SyncReport(failure = SyncFailure.Server(404, "$server names no signed-in user"))
        }
        val serverMemos = when (val listed = api.listAll()) {
            is ApiResult.Ok -> listed.value
            is ApiResult.Failed -> return SyncReport(failure = listed.failure)
            ApiResult.NotFound, ApiResult.AlreadyExists -> error("listAll answers Ok or Failed")
        }
        val pulled = pull(serverMemos)
        val pushed = push(pulled)
        if (pushed.failure != null) return pushed
        return pushed.copy(failure = pushAttachments() ?: downloadAttachments())
    }

    private fun pull(serverMemos: List<ServerMemo>): SyncReport {
        var report = SyncReport()
        val byId = serverMemos.associateBy { it.id }

        for (memo in serverMemos) {
            report = when (val step = PullStep.of(store.stored(memo.id), memo.state)) {
                PullStep.Insert -> {
                    store.insertFromServer(memo.id, MemoCodec.decode(memo.state.content), memo.state, memo.createdAt, memo.updatedAt)
                    report.copy(received = report.received + 1)
                }
                PullStep.InStep -> report
                PullStep.Claim -> {
                    store.acceptServer(memo.id, memo.state)
                    report
                }
                is PullStep.Take ->
                    if (store.applyServer(memo.id, step.version, MemoCodec.decode(memo.state.content), memo.state, memo.updatedAt)) {
                        step.position?.let { store.move(mapOf(memo.id to it)) }
                        report.copy(received = report.received + 1)
                    } else {
                        // Edited while this sync ran: treat as both sides changed on the next sync.
                        report
                    }
                is PullStep.Keep -> {
                    step.position?.let { store.move(mapOf(memo.id to it)) }
                    store.acceptServer(memo.id, memo.state)
                    report
                }
                PullStep.Conflict -> {
                    keepServerCopy(memo)
                    store.acceptServer(memo.id, memo.state)
                    report.copy(conflicts = report.conflicts + 1)
                }
            }
            if (store.stored(memo.id)?.deleted == false) {
                store.syncAttachments(memo.id, memo.attachments.filter { StoredAttachment.isKept(it.type) })
            }
        }

        for (local in store.onServer()) {
            if (local.note.id in byId) continue
            report = if (local.hasLocalEdits && !local.deleted) {
                store.forgetServer(local.note.id)
                report
            } else {
                store.purge(local.note.id)
                if (local.deleted) report else report.copy(deletedHere = report.deletedHere + 1)
            }
        }
        return report
    }

    private fun keepServerCopy(memo: ServerMemo) {
        val content = MemoCodec.decode(memo.state.content)
        val copy = store.create(clock(), content.title, content.body, content.color)
        store.save(copy.copy(pinned = memo.state.pinned, archived = memo.state.archived), clock())
    }

    private suspend fun push(pulled: SyncReport): SyncReport {
        var report = pulled
        for (local in store.pending()) {
            when {
                local.deleted -> {
                    pushDelete(local)?.let { return report.copy(failure = it) }
                    if (local.onServer) report = report.copy(deletedThere = report.deletedThere + 1)
                }
                // An empty note is never sent; one that was emptied waits until it has content again.
                local.note.isBlank -> Unit
                else -> {
                    pushNote(local)?.let { return report.copy(failure = it) }
                    report = report.copy(sent = report.sent + 1)
                }
            }
        }
        return report
    }

    private suspend fun pushAttachments(): SyncFailure? {
        for (id in store.attachmentsToDelete()) {
            when (val result = api.detach(id)) {
                is ApiResult.Ok, ApiResult.NotFound -> store.purgeAttachment(id)
                is ApiResult.Failed -> return result.failure
                ApiResult.AlreadyExists -> error("detach never answers AlreadyExists")
            }
        }
        for (attachment in store.attachmentsToUpload()) {
            val bytes = store.readAttachment(attachment.id)
            if (bytes == null) {
                // Its file is gone from this device, so there is nothing to send.
                store.purgeAttachment(attachment.id)
                continue
            }
            when (val result = api.attach(attachment.id, attachment.noteId, attachment.fileName, attachment.type, bytes)) {
                is ApiResult.Ok, ApiResult.AlreadyExists -> store.markAttachmentSent(attachment.id)
                // The memo went meanwhile; the next pull forgets it and sends both again.
                ApiResult.NotFound -> Unit
                is ApiResult.Failed -> return result.failure
            }
        }
        return null
    }

    private suspend fun downloadAttachments(): SyncFailure? {
        for (attachment in store.attachmentsToDownload()) {
            when (val result = api.download(attachment.id, attachment.fileName)) {
                is ApiResult.Ok -> store.saveDownload(attachment.id, result.value)
                ApiResult.NotFound -> store.purgeAttachment(attachment.id)
                is ApiResult.Failed -> return result.failure
                ApiResult.AlreadyExists -> error("download never answers AlreadyExists")
            }
        }
        return null
    }

    private suspend fun pushDelete(local: StoredNote): SyncFailure? =
        if (!local.onServer) {
            store.purge(local.note.id)
            null
        } else {
            when (val result = api.delete(local.note.id)) {
                is ApiResult.Ok, ApiResult.NotFound -> {
                    store.purge(local.note.id)
                    null
                }
                is ApiResult.Failed -> result.failure
                ApiResult.AlreadyExists -> error("delete never answers AlreadyExists")
            }
        }

    private suspend fun pushNote(local: StoredNote): SyncFailure? =
        local.note.toMemoState().let { state ->
            val first = if (local.onServer) api.update(local.note.id, state) else api.create(local.note.id, state)
            val result = when (first) {
                ApiResult.NotFound -> api.create(local.note.id, state)
                ApiResult.AlreadyExists -> api.update(local.note.id, state)
                else -> first
            }
            when (result) {
                is ApiResult.Ok -> {
                    store.markPushed(local.note.id, local.version, result.value.state)
                    null
                }
                is ApiResult.Failed -> result.failure
                ApiResult.NotFound, ApiResult.AlreadyExists ->
                    SyncFailure.Server(0, "memo ${local.note.id} could neither be created nor updated")
            }
        }
}
