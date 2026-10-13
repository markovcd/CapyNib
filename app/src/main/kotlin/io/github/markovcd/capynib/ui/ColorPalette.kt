package io.github.markovcd.capynib.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.markovcd.capynib.R
import io.github.markovcd.capynib.core.note.NoteColor
import io.github.markovcd.capynib.ui.theme.NoteColors

private const val SWATCHES_PER_ROW = 6

/** Every note color as a swatch, in rows; [selected] wears a tick, and none does when it is null. */
@Composable
fun ColorPalette(selected: NoteColor?, onColor: (NoteColor) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        NoteColor.entries.chunked(SWATCHES_PER_ROW).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { color ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        ColorSwatch(color, color == selected) { onColor(color) }
                    }
                }
            }
        }
    }
}

/** A 36 dp circle of the color; the chosen one wears a ring and a tick, default is crossed through. */
@Composable
private fun ColorSwatch(color: NoteColor, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val name = stringResource(R.string.editor_color, color.key)
    val isDefault = color == NoteColor.Default
    Box(
        Modifier.size(48.dp).selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = name }.testTag(Tags.color(color.key)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(44.dp).then(if (selected) Modifier.border(2.dp, colors.onSurface, CircleShape) else Modifier).padding(4.dp)
                .background(NoteColors.background(color), CircleShape)
                .border(if (isDefault) 1.5.dp else 1.dp, if (isDefault) colors.outline else colors.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.onSurface)
            } else if (isDefault) {
                val slash = colors.outline
                Canvas(Modifier.size(20.dp)) {
                    drawLine(slash, Offset(size.width * 0.25f, size.height * 0.75f), Offset(size.width * 0.75f, size.height * 0.25f), 1.8.dp.toPx(), StrokeCap.Round)
                }
            }
        }
    }
}
