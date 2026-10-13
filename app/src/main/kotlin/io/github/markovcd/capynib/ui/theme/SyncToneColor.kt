package io.github.markovcd.capynib.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import io.github.markovcd.capynib.ui.SyncTone

/** The color a sync status icon is drawn in. */
@Composable
@ReadOnlyComposable
fun SyncTone.color(): Color = when (this) {
    SyncTone.Quiet -> MaterialTheme.colorScheme.onSurfaceVariant
    SyncTone.Accent -> MaterialTheme.colorScheme.primary
    SyncTone.Error -> MaterialTheme.colorScheme.error
}
