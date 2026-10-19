package io.github.markovcd.capynib.ui.editor

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.markovcd.capynib.R
import io.github.markovcd.capynib.core.note.Note
import io.github.markovcd.capynib.ui.Tags

/** The open note's back button and actions; a note in the trash can only be restored or deleted forever. */
@Composable
internal fun EditorTopBar(
    note: Note,
    onBack: () -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
    onPin: () -> Unit,
    onArchive: () -> Unit,
    onTrash: () -> Unit,
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        title = {},
        navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.testTag(Tags.BACK)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.editor_back))
            }
        },
        actions = {
            if (note.inTrash) {
                IconButton(onClick = onRestore, modifier = Modifier.testTag(Tags.RESTORE)) {
                    Icon(Icons.Outlined.RestoreFromTrash, contentDescription = stringResource(R.string.editor_restore))
                }
                IconButton(
                    onClick = onDeleteForever,
                    modifier = Modifier.testTag(Tags.DELETE_FOREVER),
                ) {
                    Icon(Icons.Outlined.DeleteForever, contentDescription = stringResource(R.string.editor_delete_forever))
                }
            } else {
                IconButton(onClick = onPin, modifier = Modifier.testTag(Tags.PIN)) {
                    if (note.pinned) {
                        Icon(Icons.Filled.PushPin, contentDescription = stringResource(R.string.editor_unpin))
                    } else {
                        Icon(Icons.Outlined.PushPin, contentDescription = stringResource(R.string.editor_pin))
                    }
                }
                IconButton(
                    onClick = onArchive,
                    modifier = Modifier.testTag(Tags.ARCHIVE),
                ) {
                    if (note.archived) {
                        Icon(Icons.Outlined.Unarchive, contentDescription = stringResource(R.string.editor_unarchive))
                    } else {
                        Icon(Icons.Outlined.Archive, contentDescription = stringResource(R.string.editor_archive))
                    }
                }
                IconButton(
                    onClick = onTrash,
                    modifier = Modifier.testTag(Tags.DELETE),
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.editor_delete))
                }
            }
        },
    )
}
