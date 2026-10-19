package io.github.markovcd.capynib.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.markovcd.capynib.R
import io.github.markovcd.capynib.core.editor.ChecklistDrag
import io.github.markovcd.capynib.core.editor.NoteEditor
import io.github.markovcd.capynib.core.note.Checklist
import io.github.markovcd.capynib.core.note.ChecklistLine
import io.github.markovcd.capynib.ui.PlainTextField
import io.github.markovcd.capynib.ui.Tags

private const val TICKED_ALPHA = 0.62f

/**
 * Unticked items and text in their order, then the ticked items under a count, as Keep does.
 * A line dragged by its handle moves as [ChecklistDrag] says.
 */
@Composable
internal fun ChecklistEditor(body: String, editor: NoteEditor, editable: Boolean, startTyping: Boolean, fill: Color) {
    val lines = Checklist.parse(body)
    val (open, checked) = Checklist.sections(body)
    var focus by remember { mutableStateOf(open.firstOrNull()?.takeIf { startTyping && editable }) }
    var dragged by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableMapOf<Int, Int>() }
    val focusManager = LocalFocusManager.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    val onDragStart = { index: Int ->
        focusManager.clearFocus()
        dragged = index
        dragOffset = 0f
    }
    val onDrag = { delta: Float ->
        val from = dragged
        if (from != null) {
            // Reads the body from the editor: several moves can land before the next recomposition.
            val moved = ChecklistDrag.move(editor.note.value.body, from, dragOffset + delta) { heights.getOrDefault(it, 0) }
            moved.swaps.forEach { (a, b) -> editor.swapItems(a, b) }
            dragged = moved.index
            dragOffset = moved.offset
        }
    }
    val onDragEnd = { dragged = null }

    val itemRow: @Composable (Int) -> Unit = { index ->
        key(index) {
            ChecklistRow(
                index, lines[index], editor, editable, focus, { focus = it }, onDragStart, onDrag, onDragEnd,
                Modifier.onSizeChanged { heights[index] = it.height }.then(
                    if (dragged == index) {
                        Modifier.zIndex(1f).graphicsLayer {
                            translationY = dragOffset
                            shadowElevation = 8.dp.toPx()
                        }.background(fill)
                    } else {
                        Modifier
                    },
                ),
            )
        }
    }

    open.forEach { itemRow(it) }
    if (editable) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { focus = editor.addItemAfter(open.lastOrNull() ?: -1) }
                .padding(horizontal = 18.dp).testTag(Tags.ADD_ITEM),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(22.dp), tint = muted)
            Text(stringResource(R.string.editor_add_item), style = MaterialTheme.typography.bodyLarge, color = muted)
        }
    }
    if (checked.isNotEmpty()) {
        HorizontalDivider(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 6.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = RULE_ALPHA),
        )
        Text(
            pluralStringResource(R.plurals.editor_checked_items, checked.size, checked.size),
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            color = muted,
        )
        checked.forEach { itemRow(it) }
    }
}

@Composable
private fun ChecklistRow(
    index: Int,
    line: ChecklistLine,
    editor: NoteEditor,
    editable: Boolean,
    focus: Int?,
    onFocus: (Int?) -> Unit,
    onDragStart: (Int) -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier,
) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(focus) {
        if (focus == index) {
            requester.requestFocus()
            onFocus(null)
        }
    }
    // The gesture outlives this row's line moving away from under the finger, so it reads the latest of both.
    val currentIndex by rememberUpdatedState(index)
    val currentDragStart by rememberUpdatedState(onDragStart)
    val currentDrag by rememberUpdatedState(onDrag)
    val currentDragEnd by rememberUpdatedState(onDragEnd)
    val ticked = (line as? ChecklistLine.Item)?.checked == true
    Row(modifier.heightIn(min = 48.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (editable) {
            Box(
                Modifier.size(width = 28.dp, height = 44.dp).testTag(Tags.itemDrag(index)).pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { currentDragStart(currentIndex) },
                        onDragEnd = { currentDragEnd() },
                        onDragCancel = { currentDragEnd() },
                    ) { change, amount ->
                        change.consume()
                        currentDrag(amount)
                    }
                },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.DragIndicator,
                    contentDescription = stringResource(R.string.editor_move_item),
                    modifier = Modifier.size(20.dp).alpha(0.7f),
                )
            }
        }
        if (line is ChecklistLine.Item) {
            ItemCheckbox(line.checked, editable, onToggle = { editor.toggleItem(index) }, modifier = Modifier.testTag(Tags.itemCheck(index)))
        } else {
            Box(Modifier.size(44.dp))
        }
        PlainTextField(
            value = line.text,
            onValueChange = { text ->
                editor.setItemText(index, text)
                if ('\n' in text) onFocus(index + text.count { it == '\n' })
            },
            placeholder = stringResource(R.string.editor_item_hint),
            style = MaterialTheme.typography.bodyLarge.copy(textDecoration = if (ticked) TextDecoration.LineThrough else null),
            modifier = Modifier.weight(1f).padding(start = 4.dp).focusRequester(requester).testTag(Tags.item(index))
                .then(if (ticked) Modifier.alpha(TICKED_ALPHA) else Modifier),
            readOnly = !editable,
        )
        if (editable) {
            IconButton(onClick = { editor.removeItem(index) }, modifier = Modifier.size(44.dp).testTag(Tags.itemRemove(index))) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.editor_remove_item),
                    modifier = Modifier.size(18.dp).alpha(0.7f),
                )
            }
        }
    }
}

/** A 20 dp box with soft corners, filled with the accent when ticked. */
@Composable
private fun ItemCheckbox(checked: Boolean, enabled: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier.size(44.dp).toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() }),
        contentAlignment = Alignment.Center,
    ) {
        val box = Modifier.size(20.dp)
        if (checked) {
            Box(box.background(colors.primary, shape), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.onPrimary)
            }
        } else {
            Box(box.border(2.dp, colors.onSurfaceVariant, shape))
        }
    }
}
