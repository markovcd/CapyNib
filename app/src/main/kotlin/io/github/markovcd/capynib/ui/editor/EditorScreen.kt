package io.github.markovcd.capynib.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RestoreFromTrash
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
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.markovcd.capynib.R
import io.github.markovcd.capynib.core.editor.NoteEditor
import io.github.markovcd.capynib.core.note.Checklist
import io.github.markovcd.capynib.core.note.ChecklistLine
import io.github.markovcd.capynib.core.note.NoteColor
import io.github.markovcd.capynib.ui.ColorPalette
import io.github.markovcd.capynib.ui.PlainTextField
import io.github.markovcd.capynib.ui.ConfirmDeleteForever
import io.github.markovcd.capynib.ui.Tags
import io.github.markovcd.capynib.ui.theme.NoteColors

private const val TICKED_ALPHA = 0.62f
private const val RULE_ALPHA = 0.12f

/**
 * The open note. Leaving it (back, archive, delete) calls [onClose]. A note in the trash is read-only until restored.
 * With [startTyping] the body, or a checklist's first item, takes focus and the keyboard comes up.
 */
@Composable
fun EditorScreen(editor: NoteEditor, startTyping: Boolean, onClose: () -> Unit) {
    val note by editor.note.collectAsStateWithLifecycle()
    val leave = {
        editor.close()
        onClose()
    }
    BackHandler(onBack = leave)
    val checklist = Checklist.isChecklist(note.body)
    val editable = !note.inTrash
    var confirmingDelete by remember { mutableStateOf(false) }
    var showingColors by remember { mutableStateOf(false) }
    BackHandler(enabled = showingColors) { showingColors = false }
    val focus = LocalFocusManager.current
    if (confirmingDelete) {
        ConfirmDeleteForever(
            count = 1,
            onConfirm = {
                confirmingDelete = false
                editor.deleteForever()
                onClose()
            },
            onDismiss = { confirmingDelete = false },
        )
    }
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
                    if (!editable) {
                        IconButton(onClick = editor::restore, modifier = Modifier.testTag(Tags.RESTORE)) {
                            Icon(Icons.Outlined.RestoreFromTrash, contentDescription = stringResource(R.string.editor_restore))
                        }
                        IconButton(
                            onClick = { confirmingDelete = true },
                            modifier = Modifier.testTag(Tags.DELETE_FOREVER),
                        ) {
                            Icon(Icons.Outlined.DeleteForever, contentDescription = stringResource(R.string.editor_delete_forever))
                        }
                    } else {
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
                                editor.trash()
                                onClose()
                            },
                            modifier = Modifier.testTag(Tags.DELETE),
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.editor_delete))
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (editable) {
                EditorBottomBar(
                    selected = note.color,
                    checklist = checklist,
                    onColor = editor::setColor,
                    showingColors = showingColors,
                    onPalette = {
                        if (!showingColors) focus.clearFocus()
                        showingColors = !showingColors
                    },
                    onToggleCheckboxes = { if (checklist) editor.hideCheckboxes() else editor.showCheckboxes() },
                )
            }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxWidth().verticalScroll(rememberScrollState())
                .onFocusEvent { if (it.hasFocus) showingColors = false },
        ) {
            PlainTextField(
                value = note.title,
                onValueChange = editor::setTitle,
                placeholder = stringResource(R.string.editor_title_hint),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 14.dp).testTag(Tags.TITLE),
                readOnly = !editable,
            )
            if (checklist) {
                ChecklistEditor(note.body, editor, editable, startTyping)
            } else {
                val body = remember { FocusRequester() }
                LaunchedEffect(Unit) { if (startTyping && editable) body.requestFocus() }
                PlainTextField(
                    value = note.body,
                    onValueChange = editor::setBody,
                    placeholder = stringResource(R.string.editor_body_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).focusRequester(body).testTag(Tags.BODY),
                    readOnly = !editable,
                )
            }
        }
    }
}

/** Unticked items and text in their order, then the ticked items under a count, as Keep does. */
@Composable
private fun ChecklistEditor(body: String, editor: NoteEditor, editable: Boolean, startTyping: Boolean) {
    val lines = Checklist.parse(body).withIndex().toList()
    val (checked, open) = lines.partition { (it.value as? ChecklistLine.Item)?.checked == true }
    var focus by remember { mutableStateOf(open.firstOrNull()?.index?.takeIf { startTyping && editable }) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    open.forEach { (index, line) -> key(index) { ChecklistRow(index, line, editor, editable, focus) { focus = it } } }
    if (editable) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { focus = editor.addItemAfter(open.lastOrNull()?.index ?: -1) }
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
        checked.forEach { (index, line) -> key(index) { ChecklistRow(index, line, editor, editable, focus) { focus = it } } }
    }
}

@Composable
private fun ChecklistRow(index: Int, line: ChecklistLine, editor: NoteEditor, editable: Boolean, focus: Int?, onFocus: (Int?) -> Unit) {
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

/** The note's tools: the checkbox toggle and, as in Keep, the colors folded behind a palette button until it is tapped. */
@Composable
private fun EditorBottomBar(
    selected: NoteColor,
    checklist: Boolean,
    onColor: (NoteColor) -> Unit,
    showingColors: Boolean,
    onPalette: () -> Unit,
    onToggleCheckboxes: () -> Unit,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
        HorizontalDivider(color = ink.copy(alpha = RULE_ALPHA))
        AnimatedVisibility(showingColors) {
            ColorPalette(selected, onColor, Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp))
        }
        Row(Modifier.padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onToggleCheckboxes, modifier = Modifier.testTag(Tags.CHECKBOXES)) {
                Icon(Icons.Outlined.CheckBox, contentDescription = null, modifier = Modifier.size(22.dp), tint = ink)
                Text(
                    stringResource(if (checklist) R.string.editor_hide_checkboxes else R.string.editor_show_checkboxes),
                    Modifier.padding(start = 10.dp),
                    color = ink,
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onPalette, modifier = Modifier.testTag(Tags.PALETTE)) {
                Icon(Icons.Outlined.Palette, contentDescription = stringResource(R.string.editor_colors), tint = ink)
            }
        }
    }
}
