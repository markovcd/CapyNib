package io.github.markovcd.capynib.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.markovcd.capynib.R
import io.github.markovcd.capynib.core.editor.NoteEditor
import io.github.markovcd.capynib.core.note.Checklist
import io.github.markovcd.capynib.core.note.ChecklistLine
import io.github.markovcd.capynib.core.note.NoteColor
import io.github.markovcd.capynib.ui.PlainTextField
import io.github.markovcd.capynib.ui.Tags
import io.github.markovcd.capynib.ui.theme.NoteColors

private const val TICKED_ALPHA = 0.62f
private const val RULE_ALPHA = 0.12f
private const val SWATCHES_PER_ROW = 6

/** The open note. Leaving it (back, archive, delete) calls [onClose]. */
@Composable
fun EditorScreen(editor: NoteEditor, onClose: () -> Unit) {
    val note by editor.note.collectAsStateWithLifecycle()
    val leave = {
        editor.close()
        onClose()
    }
    BackHandler(onBack = leave)
    val checklist = Checklist.isChecklist(note.body)
    val fill = NoteColors.background(note.color)

    Scaffold(
        containerColor = fill,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = {},
                navigationIcon = {
                    IconButton(onClick = leave, modifier = Modifier.testTag(Tags.BACK)) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.editor_back))
                    }
                },
                actions = {
                    IconButton(onClick = editor::togglePinned, modifier = Modifier.testTag(Tags.PIN)) {
                        if (note.pinned) {
                            Icon(Icons.Filled.PushPin, contentDescription = stringResource(R.string.editor_unpin))
                        } else {
                            Icon(Icons.Outlined.PushPin, contentDescription = stringResource(R.string.editor_pin))
                        }
                    }
                    IconButton(
                        onClick = {
                            editor.setArchived(!note.archived)
                            leave()
                        },
                        modifier = Modifier.testTag(Tags.ARCHIVE),
                    ) {
                        if (note.archived) {
                            Icon(Icons.Outlined.Unarchive, contentDescription = stringResource(R.string.editor_unarchive))
                        } else {
                            Icon(Icons.Outlined.Archive, contentDescription = stringResource(R.string.editor_archive))
                        }
                    }
                    IconButton(
                        onClick = {
                            editor.delete()
                            onClose()
                        },
                        modifier = Modifier.testTag(Tags.DELETE),
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.editor_delete))
                    }
                },
            )
        },
        bottomBar = {
            EditorBottomBar(
                selected = note.color,
                checklist = checklist,
                onColor = editor::setColor,
                onToggleCheckboxes = { if (checklist) editor.hideCheckboxes() else editor.showCheckboxes() },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxWidth().verticalScroll(rememberScrollState())) {
            PlainTextField(
                value = note.title,
                onValueChange = editor::setTitle,
                placeholder = stringResource(R.string.editor_title_hint),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 14.dp).testTag(Tags.TITLE),
            )
            if (checklist) {
                ChecklistEditor(note.body, editor)
            } else {
                PlainTextField(
                    value = note.body,
                    onValueChange = editor::setBody,
                    placeholder = stringResource(R.string.editor_body_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).testTag(Tags.BODY),
                )
            }
        }
    }
}

/** Unticked items and text in their order, then the ticked items under a count, as Keep does. */
@Composable
private fun ChecklistEditor(body: String, editor: NoteEditor) {
    val lines = Checklist.parse(body).withIndex().toList()
    val (checked, open) = lines.partition { (it.value as? ChecklistLine.Item)?.checked == true }
    var focus by remember { mutableStateOf<Int?>(null) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    open.forEach { (index, line) -> key(index) { ChecklistRow(index, line, editor, focus) { focus = it } } }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { focus = editor.addItemAfter(open.lastOrNull()?.index ?: -1) }
            .padding(horizontal = 18.dp).testTag(Tags.ADD_ITEM),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(22.dp), tint = muted)
        Text(stringResource(R.string.editor_add_item), style = MaterialTheme.typography.bodyLarge, color = muted)
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
        checked.forEach { (index, line) -> key(index) { ChecklistRow(index, line, editor, focus) { focus = it } } }
    }
}

@Composable
private fun ChecklistRow(index: Int, line: ChecklistLine, editor: NoteEditor, focus: Int?, onFocus: (Int?) -> Unit) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(focus) {
        if (focus == index) {
            requester.requestFocus()
            onFocus(null)
        }
    }
    val ticked = (line as? ChecklistLine.Item)?.checked == true
    Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (line is ChecklistLine.Item) {
            ItemCheckbox(line.checked, onToggle = { editor.toggleItem(index) }, modifier = Modifier.testTag(Tags.itemCheck(index)))
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
        )
        IconButton(onClick = { editor.removeItem(index) }, modifier = Modifier.size(44.dp).testTag(Tags.itemRemove(index))) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = stringResource(R.string.editor_remove_item),
                modifier = Modifier.size(18.dp).alpha(0.7f),
            )
        }
    }
}

/** A 20 dp box with soft corners, filled with the accent when ticked. */
@Composable
private fun ItemCheckbox(checked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier.size(44.dp).toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() }),
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

@Composable
private fun EditorBottomBar(selected: NoteColor, checklist: Boolean, onColor: (NoteColor) -> Unit, onToggleCheckboxes: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface
    Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
        HorizontalDivider(color = ink.copy(alpha = RULE_ALPHA))
        Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onToggleCheckboxes, modifier = Modifier.testTag(Tags.CHECKBOXES)) {
                Icon(Icons.Outlined.CheckBox, contentDescription = null, modifier = Modifier.size(22.dp), tint = ink)
                Text(
                    stringResource(if (checklist) R.string.editor_hide_checkboxes else R.string.editor_show_checkboxes),
                    Modifier.padding(start = 10.dp),
                    color = ink,
                )
            }
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
