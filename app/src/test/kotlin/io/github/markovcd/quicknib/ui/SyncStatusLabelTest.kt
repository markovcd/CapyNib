package io.github.markovcd.quicknib.ui

import io.github.markovcd.quicknib.R
import io.github.markovcd.quicknib.core.memos.SyncFailure
import io.github.markovcd.quicknib.core.sync.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncStatusLabelTest {
    @Test
    fun Each_status_has_its_own_words() {
        val cases = mapOf(
            SyncStatus.Local to R.string.sync_local,
            SyncStatus.Waiting to R.string.sync_waiting,
            SyncStatus.Syncing to R.string.sync_syncing,
            SyncStatus.Synced(0) to R.string.sync_synced,
            SyncStatus.Failed(SyncFailure.Unreachable("x"), 0) to R.string.sync_offline,
            SyncStatus.Failed(SyncFailure.Unauthorized("x"), 0) to R.string.sync_unauthorized,
            SyncStatus.Failed(SyncFailure.Server(500, "boom"), 0) to R.string.sync_error,
        )
        assertEquals(cases.values.toList(), cases.keys.map { SyncStatusLabel.of(it).text })
    }

    @Test
    fun Syncing_and_synced_show_in_the_accent_and_refusals_as_errors() {
        val cases = mapOf(
            SyncStatus.Local to SyncTone.Quiet,
            SyncStatus.Waiting to SyncTone.Quiet,
            SyncStatus.Syncing to SyncTone.Accent,
            SyncStatus.Synced(0) to SyncTone.Accent,
            SyncStatus.Failed(SyncFailure.Unreachable("x"), 0) to SyncTone.Quiet,
            SyncStatus.Failed(SyncFailure.Unauthorized("x"), 0) to SyncTone.Error,
            SyncStatus.Failed(SyncFailure.Server(500, "boom"), 0) to SyncTone.Error,
        )
        assertEquals(cases.values.toList(), cases.keys.map { SyncStatusLabel.of(it).tone })
    }

    @Test
    fun A_server_error_carries_its_message() {
        assertEquals("boom", SyncStatusLabel.of(SyncStatus.Failed(SyncFailure.Server(500, "boom"), 0)).detail)
    }
}
