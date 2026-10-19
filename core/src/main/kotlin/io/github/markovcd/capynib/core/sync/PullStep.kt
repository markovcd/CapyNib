package io.github.markovcd.capynib.core.sync

import io.github.markovcd.capynib.core.memos.MemoState
import io.github.markovcd.capynib.core.store.StoredNote

/**
 * What a pull does with one memo from the server, given the note of the same id on this device. A move is not
 * an edit that conflicts: a note moved on one side and edited on the other keeps both.
 */
internal sealed interface PullStep {
    /** No note has its id: the memo becomes a new note. */
    data object Insert : PullStep

    /** The note is as the server last had it. */
    data object InStep : PullStep

    /** Deleted here, or created here and the reply to the create was lost: either way the memo is this note, and the push sends the local state over it. */
    data object Claim : PullStep

    /** The server's version replaces the note, unless it changed since [version] was read; then it goes to [position] when one is given. */
    data class Take(val version: Long, val position: Long?) : PullStep

    /** Only moved there: the local edit goes out, at the server's [position] unless the note was moved here too. */
    data class Keep(val position: Long?) : PullStep

    /** Both sides changed: the server's version is kept as a conflict copy and the local edit goes out. */
    data object Conflict : PullStep

    companion object {
        fun of(local: StoredNote?, memo: MemoState): PullStep {
            val synced = local?.synced
            return when {
                local == null -> Insert
                local.onServer && synced == memo -> InStep
                local.deleted || !local.onServer -> Claim
                !local.hasLocalEdits || local.note.toMemoState() == memo -> Take(local.version, position = null)
                synced != null && memo.sameContentAs(synced) -> Keep(memo.position.takeIf { local.note.position == synced.position })
                // Only moved here: the server's edit is taken, and the move goes out after it.
                synced != null && local.note.toMemoState().sameContentAs(synced) -> Take(local.version, local.note.position)
                else -> Conflict
            }
        }
    }
}
