package io.github.markovcd.quicknib.core.sync

import io.github.markovcd.quicknib.core.memos.SyncFailure

/** Where sync stands, for the UI. Times are epoch milliseconds. */
sealed interface SyncStatus {
    /** No server is set up; notes stay on this device. */
    data object Local : SyncStatus

    data object Waiting : SyncStatus

    data object Syncing : SyncStatus

    data class Synced(val at: Long) : SyncStatus

    data class Failed(val failure: SyncFailure, val at: Long) : SyncStatus
}
