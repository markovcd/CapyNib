package io.github.markovcd.quicknib.ui.board

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.markovcd.quicknib.core.note.Checklist
import io.github.markovcd.quicknib.core.note.ChecklistLine
import io.github.markovcd.quicknib.core.note.Note
import io.github.markovcd.quicknib.ui.Tags
import io.github.markovcd.quicknib.ui.theme.NoteColors

private const val PREVIEW_LINES = 10

/** A note on the board: its color, title, and the start of its text or checklist. */
@Composable
fun NoteCard(note: Note, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth().testTag(Tags.card(note.id)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = NoteColors.background(note.color)),
        border = if (NoteColors.needsOutline(note.color)) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (note.title.isNotBlank()) {
                Text(note.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Checklist.parse(note.body).take(PREVIEW_LINES).forEach { line ->
                when (line) {
                    is ChecklistLine.Text -> if (line.text.isNotEmpty()) {
                        Text(line.text, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    is ChecklistLine.Item -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (line.checked) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(" ", Modifier.width(4.dp))
                        Text(
                            line.text,
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
