package io.github.markovcd.quicknib.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.markovcd.quicknib.R
import io.github.markovcd.quicknib.core.editor.NoteEditor
import io.github.markovcd.quicknib.core.note.Checklist
import io.github.markovcd.quicknib.core.note.ChecklistLine
import io.github.markovcd.quicknib.core.note.NoteColor
import io.github.markovcd.quicknib.ui.Tags
import io.github.markovcd.quicknib.ui.theme.NoteColors

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

    Scaffold(
        containerColor = NoteColors.background(note.color),
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
            TextField(
                value = note.title,
                onValueChange = editor::setTitle,
                modifier = Modifier.fillMaxWidth().testTag(Tags.TITLE),
                textStyle = MaterialTheme.typography.titleLarge,
                placeholder = { Text(stringResource(R.string.editor_title_hint), style = MaterialTheme.typography.titleLarge) },
                colors = transparentFieldColors(),
            )
            if (checklist) {
                ChecklistEditor(note.body, editor)
            } else {
                TextField(
                    value = note.body,
                    onValueChange = editor::setBody,
                    modifier = Modifier.fillMaxWidth().testTag(Tags.BODY),
                    placeholder = { Text(stringResource(R.string.editor_body_hint)) },
                    colors = transparentFieldColors(),
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

    open.forEach { (index, line) -> key(index) { ChecklistRow(index, line, editor, focus) { focus = it } } }
    Row(
        Modifier.fillMaxWidth().clickable { focus = editor.addItemAfter(open.lastOrNull()?.index ?: -1) }
            .padding(horizontal = 12.dp, vertical = 12.dp).testTag(Tags.ADD_ITEM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Add, contentDescription = null)
        Text(stringResource(R.string.editor_add_item), Modifier.padding(start = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (checked.isNotEmpty()) {
        Text(
            pluralStringResource(R.plurals.editor_checked_items, checked.size, checked.size),
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (line is ChecklistLine.Item) {
            Checkbox(
                checked = line.checked,
                onCheckedChange = { editor.toggleItem(index) },
                modifier = Modifier.testTag(Tags.itemCheck(index)),
            )
        } else {
            Box(Modifier.size(48.dp))
        }
        TextField(
            value = line.text,
            onValueChange = { text ->
                editor.setItemText(index, text)
                if ('\n' in text) onFocus(index + text.count { it == '\n' })
            },
            modifier = Modifier.weight(1f).focusRequester(requester).testTag(Tags.item(index)),
            placeholder = { Text(stringResource(R.string.editor_item_hint)) },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                textDecoration = if ((line as? ChecklistLine.Item)?.checked == true) TextDecoration.LineThrough else null,
            ),
            colors = transparentFieldColors(),
        )
        IconButton(onClick = { editor.removeItem(index) }, modifier = Modifier.testTag(Tags.itemRemove(index))) {
            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.editor_remove_item))
        }
    }
}

@Composable
private fun EditorBottomBar(selected: NoteColor, checklist: Boolean, onColor: (NoteColor) -> Unit, onToggleCheckboxes: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onToggleCheckboxes, modifier = Modifier.testTag(Tags.CHECKBOXES)) {
            Icon(
                Icons.Outlined.CheckBox,
                contentDescription = stringResource(if (checklist) R.string.editor_hide_checkboxes else R.string.editor_show_checkboxes),
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)) {
            items(NoteColor.entries) { color -> ColorSwatch(color, color == selected) { onColor(color) } }
        }
    }
}

@Composable
private fun ColorSwatch(color: NoteColor, selected: Boolean, onClick: () -> Unit) {
    val outline = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val name = stringResource(R.string.editor_color, color.key)
    Box(
        Modifier.size(36.dp)
            .border(BorderStroke(if (selected) 2.dp else 1.dp, outline), CircleShape)
            .padding(2.dp)
            .background(NoteColors.background(color), CircleShape)
            .clickable(onClickLabel = name, onClick = onClick)
            .testTag(Tags.color(color.key)),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Outlined.Check, contentDescription = name, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun transparentFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
)
