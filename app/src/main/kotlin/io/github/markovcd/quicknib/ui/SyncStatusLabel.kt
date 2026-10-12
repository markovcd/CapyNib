package io.github.markovcd.quicknib.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.markovcd.quicknib.R
import io.github.markovcd.quicknib.core.memos.SyncFailure
import io.github.markovcd.quicknib.core.sync.SyncStatus

/** How a sync status is shown: an icon, and words that may take the failure's message. */
data class SyncStatusLabel(val icon: ImageVector, @param:StringRes val text: Int, val detail: String? = null) {
    companion object {
        fun of(status: SyncStatus): SyncStatusLabel = when (status) {
            SyncStatus.Local -> SyncStatusLabel(Icons.Outlined.PhoneAndroid, R.string.sync_local)
            SyncStatus.Waiting -> SyncStatusLabel(Icons.Outlined.CloudQueue, R.string.sync_waiting)
            SyncStatus.Syncing -> SyncStatusLabel(Icons.Outlined.Sync, R.string.sync_syncing)
            is SyncStatus.Synced -> SyncStatusLabel(Icons.Outlined.CloudDone, R.string.sync_synced)
            is SyncStatus.Failed -> when (status.failure) {
                is SyncFailure.Unreachable -> SyncStatusLabel(Icons.Outlined.CloudOff, R.string.sync_offline)
                is SyncFailure.Unauthorized -> SyncStatusLabel(Icons.Outlined.ErrorOutline, R.string.sync_unauthorized)
                is SyncFailure.Server -> SyncStatusLabel(Icons.Outlined.ErrorOutline, R.string.sync_error, status.failure.message)
            }
        }
    }
}
