package io.github.markovcd.capynib.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.markovcd.capynib.R
import io.github.markovcd.capynib.core.board.BoardMode
import io.github.markovcd.capynib.core.board.Selection
import io.github.markovcd.capynib.core.board.Shelf
import io.github.markovcd.capynib.ui.ColorPalette
import io.github.markovcd.capynib.ui.PlainTextField
import io.github.markovcd.capynib.ui.Tags

/**
 * The search pill and the tags under it. While notes are picked the selection bar takes the pill's place at its height,
 * and the tags stay, so the board does not move under the finger that picked a note.
 */
@Composable
internal fun BoardTopBar(state: BoardState, actions: BoardActions) {
    Column {
        if (state.selection.isActive) {
            SelectionBar(state.selection, pins = state.selection.pins(state.content?.notes.orEmpty()), state.shelf, actions.selection)
        } else {
            SearchBar(state.query, state.shelf, state.mode, actions.onQuery, actions.onShelf, actions.onMode, actions.onSettings)
        }
        val tags = state.content?.tags.orEmpty()
        if (tags.isNotEmpty()) TagBar(tags, state.content?.tag, actions.onTag)
    }
}

/** The pill at the top: search, then the calendar, archive and trash toggles, and settings. */
@Composable
private fun SearchBar(
    query: String,
    shelf: Shelf,
    mode: BoardMode,
    onQuery: (String) -> Unit,
    onShelf: (Shelf) -> Unit,
    onMode: (BoardMode) -> Unit,
    onSettings: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.statusBarsPadding().padding(start = 12.dp, end = 12.dp, top = 8.dp).fillMaxWidth().height(52.dp)
            .clip(CircleShape).background(colors.surfaceContainerHigh).padding(start = 18.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(22.dp), tint = colors.onSurfaceVariant)
        PlainTextField(
            value = query,
            onValueChange = onQuery,
            placeholder = stringResource(
                when (shelf) {
                    Shelf.Notes -> R.string.board_search_hint
                    Shelf.Archive -> R.string.board_archive_title
                    Shelf.Trash -> R.string.board_trash_title
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(start = 10.dp).testTag(Tags.SEARCH),
            singleLine = true,
        )
        ModeToggle(mode, onMode)
        ShelfToggle(Shelf.Archive, shelf, Tags.ARCHIVE_TOGGLE, Icons.Outlined.Archive, R.string.board_show_archive, onShelf)
        ShelfToggle(Shelf.Trash, shelf, Tags.TRASH_TOGGLE, Icons.Outlined.Delete, R.string.board_show_trash, onShelf)
        IconButton(onClick = onSettings, modifier = Modifier.testTag(Tags.SETTINGS)) {
            Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.board_settings), tint = colors.onSurfaceVariant)
        }
    }
}

/** The tags on the shelf; tapping one shows only its notes, tapping it again shows them all. */
@Composable
private fun TagBar(tags: List<String>, picked: String?, onTag: (String?) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag(Tags.TAG_BAR),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(tags, key = { it }) { tag ->
            FilterChip(
                selected = tag == picked,
                onClick = { onTag(if (tag == picked) null else tag) },
                label = { Text("#$tag") },
                modifier = Modifier.testTag(Tags.tag(tag)),
            )
        }
    }
}

/** In place of the search pill while notes are picked: how many, and what to do with them all. */
@Composable
private fun SelectionBar(selection: Selection, pins: Boolean, shelf: Shelf, actions: SelectionActions) {
    val colors = MaterialTheme.colorScheme
    var palette by remember { mutableStateOf(false) }
    Row(
        Modifier.statusBarsPadding().padding(start = 12.dp, end = 12.dp, top = 8.dp).fillMaxWidth().height(52.dp)
            .clip(CircleShape).background(colors.secondaryContainer).padding(horizontal = 4.dp).testTag(Tags.SELECTION_BAR),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = actions.clear, modifier = Modifier.testTag(Tags.CLOSE_SELECTION)) {
            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.board_close_selection), tint = colors.onSecondaryContainer)
        }
        Text(
            stringResource(R.string.board_selected, selection.ids.size),
            modifier = Modifier.weight(1f).padding(start = 4.dp).testTag(Tags.SELECTED_COUNT),
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSecondaryContainer,
        )
        if (shelf == Shelf.Trash) {
            IconButton(onClick = actions.restore, modifier = Modifier.testTag(Tags.RESTORE_SELECTED)) {
                Icon(Icons.Outlined.RestoreFromTrash, contentDescription = stringResource(R.string.editor_restore), tint = colors.onSecondaryContainer)
            }
            IconButton(onClick = actions.deleteForever, modifier = Modifier.testTag(Tags.DELETE_FOREVER_SELECTED)) {
                Icon(Icons.Outlined.DeleteForever, contentDescription = stringResource(R.string.editor_delete_forever), tint = colors.onSecondaryContainer)
            }
        } else {
            IconButton(onClick = actions.pin, modifier = Modifier.testTag(Tags.PIN_SELECTED)) {
                if (pins) {
                    Icon(Icons.Outlined.PushPin, contentDescription = stringResource(R.string.editor_pin), tint = colors.onSecondaryContainer)
                } else {
                    Icon(Icons.Filled.PushPin, contentDescription = stringResource(R.string.editor_unpin), tint = colors.onSecondaryContainer)
                }
            }
            Box {
                IconButton(onClick = { palette = true }, modifier = Modifier.testTag(Tags.COLOR_SELECTED)) {
                    Icon(Icons.Outlined.Palette, contentDescription = stringResource(R.string.board_color_selected), tint = colors.onSecondaryContainer)
                }
                DropdownMenu(expanded = palette, onDismissRequest = { palette = false }) {
                    ColorPalette(
                        selected = null,
                        onColor = { color ->
                            palette = false
                            actions.color(color)
                        },
                        modifier = Modifier.width(300.dp).padding(horizontal = 8.dp),
                    )
                }
            }
            IconButton(onClick = actions.archive, modifier = Modifier.testTag(Tags.ARCHIVE_SELECTED)) {
                if (shelf == Shelf.Archive) {
                    Icon(Icons.Outlined.Unarchive, contentDescription = stringResource(R.string.editor_unarchive), tint = colors.onSecondaryContainer)
                } else {
                    Icon(Icons.Outlined.Archive, contentDescription = stringResource(R.string.editor_archive), tint = colors.onSecondaryContainer)
                }
            }
            IconButton(onClick = actions.trash, modifier = Modifier.testTag(Tags.TRASH_SELECTED)) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.editor_delete), tint = colors.onSecondaryContainer)
            }
        }
    }
}

/** Shows [target], or the notes again (a light bulb) while [target] is shown. */
@Composable
private fun ShelfToggle(target: Shelf, shelf: Shelf, tag: String, icon: ImageVector, label: Int, onShelf: (Shelf) -> Unit) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    IconButton(onClick = { onShelf(if (shelf == target) Shelf.Notes else target) }, modifier = Modifier.testTag(tag)) {
        if (shelf == target) {
            Icon(Icons.Outlined.Lightbulb, contentDescription = stringResource(R.string.board_show_notes), tint = tint)
        } else {
            Icon(icon, contentDescription = stringResource(label), tint = tint)
        }
    }
}

/** Shows the calendar, or the cards again while the calendar is shown. */
@Composable
private fun ModeToggle(mode: BoardMode, onMode: (BoardMode) -> Unit) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    val calendar = mode == BoardMode.Calendar
    IconButton(onClick = { onMode(if (calendar) BoardMode.Cards else BoardMode.Calendar) }, modifier = Modifier.testTag(Tags.CALENDAR_TOGGLE)) {
        if (calendar) {
            Icon(Icons.Outlined.GridView, contentDescription = stringResource(R.string.board_show_cards), tint = tint)
        } else {
            Icon(Icons.Outlined.CalendarMonth, contentDescription = stringResource(R.string.board_show_calendar), tint = tint)
        }
    }
}
