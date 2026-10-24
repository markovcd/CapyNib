package io.github.markovcd.capynib.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.markovcd.capynib.R
import io.github.markovcd.capynib.audio.PlayingClip
import io.github.markovcd.capynib.core.note.Recording
import io.github.markovcd.capynib.ui.theme.Ink

/**
 * A recording as a small player: play or stop, how far along it is, and its length. [onRemove] adds
 * a remove button; [compact] draws it for a card. A clip not yet downloaded shows a cloud until sync fetches it.
 */
@Composable
fun RecordingPlayer(recording: Recording, playing: PlayingClip?, onPlay: () -> Unit, onRemove: (() -> Unit)?, compact: Boolean, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    val mine = playing?.takeIf { it.id == recording.id }
    val length = mine?.durationMillis ?: recording.durationMillis
    val button = if (compact) 32.dp else 44.dp
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ink.copy(alpha = Ink.FILL))
            .heightIn(min = button).padding(end = if (onRemove == null) 12.dp else 0.dp).testTag(Tags.recording(recording.id)),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPlay, enabled = recording.downloaded, modifier = Modifier.size(button).testTag(Tags.recordingPlay(recording.id))) {
            val icon = when {
                !recording.downloaded -> Icons.Outlined.CloudDownload
                mine != null -> Icons.Filled.Stop
                else -> Icons.Filled.PlayArrow
            }
            val label = when {
                !recording.downloaded -> R.string.recording_not_downloaded
                mine != null -> R.string.recording_stop
                else -> R.string.recording_play
            }
            Icon(icon, contentDescription = stringResource(label), modifier = Modifier.size(if (compact) 20.dp else 24.dp), tint = ink)
        }
        LinearProgressIndicator(
            progress = { if (mine == null || mine.durationMillis <= 0) 0f else mine.positionMillis.toFloat() / mine.durationMillis },
            modifier = Modifier.weight(1f),
            color = ink,
            trackColor = ink.copy(alpha = Ink.FILL * 2),
            drawStopIndicator = {},
        )
        Text(
            when {
                mine != null -> clipTime(mine.positionMillis)
                length != null -> clipTime(length)
                else -> ""
            },
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
            modifier = Modifier.alpha(0.8f).testTag(Tags.recordingTime(recording.id)),
        )
        if (onRemove != null) {
            IconButton(onClick = onRemove, modifier = Modifier.size(button).testTag(Tags.recordingRemove(recording.id))) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.recording_remove),
                    modifier = Modifier.size(18.dp).alpha(0.7f),
                )
            }
        }
    }
}
