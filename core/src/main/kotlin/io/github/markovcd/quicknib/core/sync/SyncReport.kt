package io.github.markovcd.quicknib.core.sync

import io.github.markovcd.quicknib.core.memos.SyncFailure

/** What one sync did. A sync that stopped part way still reports what it got done, and why it stopped. */
data class SyncReport(
    val received: Int = 0,
    val sent: Int = 0,
    val deletedHere: Int = 0,
    val deletedThere: Int = 0,
    val conflicts: Int = 0,
    val failure: SyncFailure? = null,
) {
    val succeeded: Boolean get() = failure == null
}
