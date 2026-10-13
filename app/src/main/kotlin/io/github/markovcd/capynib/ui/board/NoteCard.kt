package io.github.markovcd.capynib.ui.board

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.markovcd.capynib.core.note.Checklist
import io.github.markovcd.capynib.core.note.ChecklistLine
import io.github.markovcd.capynib.core.note.Note
import io.github.markovcd.capynib.ui.Tags
import io.github.markovcd.capynib.ui.theme.NoteColors

private const val PREVIEW_LINES = 10
private const val TICKED_ALPHA = 0.62f

/** A note on the board: its color, title, and the start of its text or checklist. */
@Composable
fun NoteCard(note: Note, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth().testTag(Tags.card(note.id)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = NoteColors.background(note.color),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = if (NoteColors.needsOutline(note.color)) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (note.title.isNotBlank()) {
                Text(note.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Checklist.parse(note.body).take(PREVIEW_LINES).forEach { line ->
                when (line) {
                    is ChecklistLine.Text -> if (line.text.isNotEmpty()) {
                        Text(line.text, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    is ChecklistLine.Item -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CardCheckbox(line.checked)
                        Text(
                            line.text,
                            modifier = if (line.checked) Modifier.alpha(TICKED_ALPHA) else Modifier,
                            style = MaterialTheme.typography.bodyMedium,
                            textDecoration = if (line.checked) TextDecoration.LineThrough else null,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** A small, read-only box: the card only shows whether an item is ticked. */
@Composable
private fun CardCheckbox(checked: Boolean) {
    val ink = MaterialTheme.colorScheme.onSurface
    Box(
        Modifier.size(14.dp).border(1.5.dp, ink, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(11.dp), tint = ink)
    }
}
