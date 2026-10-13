package io.github.markovcd.capynib.core.sync

import io.github.markovcd.capynib.core.memos.ApiResult
import io.github.markovcd.capynib.core.memos.MemosApi
import io.github.markovcd.capynib.core.memos.ServerMemo
import io.github.markovcd.capynib.core.memos.SyncFailure
import io.github.markovcd.capynib.core.note.MemoCodec
import io.github.markovcd.capynib.core.store.NoteStore
import io.github.markovcd.capynib.core.store.StoredNote
import io.github.markovcd.capynib.core.store.Trash

/**
 * Two-way sync between the [store] and a memos server.
 *
 * Notes that have been in the trash for [trashDays] are deleted first. Then pull: a memo that
 * changed on the server replaces a note with no local edits; when both sides changed, the local edit
 * wins and the server's version is kept as a new note, so nothing is lost. A move is not an edit
 * that conflicts: a note moved on one side and edited on the other keeps both. Then push every note with
 * local edits. Notes are created on the server under their own id, so a create that is retried after
 * a lost reply updates instead of duplicating.
 */
class SyncEngine(
    private val store: NoteStore,
    private val api: MemosApi,
    private val trashDays: Int = Trash.DEFAULT_DAYS,
    private val clock: () -> Long,
) {
    suspend fun sync(): SyncReport {
        store.emptyTrash(clock(), trashDays)
        val serverMemos = when (val listed = api.listAll()) {
            is ApiResult.Ok -> listed.value
            is ApiResult.Failed -> return SyncReport(failure = listed.failure)
            ApiResult.NotFound, ApiResult.AlreadyExists -> error("listAll answers Ok or Failed")
        }
        val pulled = pull(serverMemos)
        return push(pulled)
    }

    private fun pull(serverMemos: List<ServerMemo>): SyncReport {
        var report = SyncReport()
        val byId = serverMemos.associateBy { it.id }

        for (memo in serverMemos) {
            val local = store.stored(memo.id)
            report = when {
                local == null -> {
                    store.insertFromServer(memo.id, MemoCodec.decode(memo.state.content), memo.state, memo.createdAt, memo.updatedAt)
                    report.copy(received = report.received + 1)
                }
                local.onServer && local.synced == memo.state -> report
                // Deleted here, or created here and the reply to the create was lost: either way the
                // memo is this note, and the push sends the local state over it.
                local.deleted || !local.onServer -> {
                    store.acceptServer(memo.id, memo.state)
                    report
                }
                !local.hasLocalEdits || local.note.toMemoState() == memo.state -> {
                    if (store.applyServer(memo.id, local.version, MemoCodec.decode(memo.state.content), memo.state, memo.updatedAt)) {
                        report.copy(received = report.received + 1)
                    } else {
                        // Edited while this sync ran: treat as both sides changed on the next sync.
                        report
                    }
                }
                local.synced != null && memo.state.sameContentAs(local.synced) -> {
                    // Only moved there: the local edit goes out at the server's place, unless it was moved here too.
                    if (local.note.position == local.synced.position) store.move(mapOf(memo.id to memo.state.position))
                    store.acceptServer(memo.id, memo.state)
                    report
                }
                local.synced != null && local.note.toMemoState().sameContentAs(local.synced) -> {
                    // Only moved here: the server's edit is taken, and the move goes out after it.
                    if (store.applyServer(memo.id, local.version, MemoCodec.decode(memo.state.content), memo.state, memo.updatedAt)) {
                        store.move(mapOf(memo.id to local.note.position))
                        report.copy(received = report.received + 1)
                    } else {
                        report
                    }
                }
                else -> {
                    keepServerCopy(memo)
                    store.acceptServer(memo.id, memo.state)
                    report.copy(conflicts = report.conflicts + 1)
                }
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
