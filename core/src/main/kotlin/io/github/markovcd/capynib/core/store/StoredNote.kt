package io.github.markovcd.capynib.core.store

import io.github.markovcd.capynib.core.memos.MemoState
import io.github.markovcd.capynib.core.note.Note

/** A note with what sync knows about it. [synced] is the memo as last seen on the server. */
data class StoredNote(
    val note: Note,
    val deleted: Boolean,
    val version: Long,
    val syncedVersion: Long,
    val onServer: Boolean,
    val synced: MemoState?,
) {
    val hasLocalEdits: Boolean get() = version != syncedVersion
}
