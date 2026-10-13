package io.github.markovcd.capynib.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.markovcd.capynib.R

/** Asks before [count] notes are deleted for good, since nothing can bring them back. */
@Composable
fun ConfirmDeleteForever(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(pluralStringResource(R.plurals.delete_forever_question, count, count)) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag(Tags.CONFIRM_DELETE_FOREVER)) {
                Text(stringResource(R.string.editor_delete_forever))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag(Tags.CANCEL_DELETE_FOREVER)) {
                Text(stringResource(R.string.editor_cancel))
            }
        },
    )
}
