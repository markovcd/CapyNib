package io.github.markovcd.capynib.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.markovcd.capynib.R
import io.github.markovcd.capynib.core.board.BoardContent
import io.github.markovcd.capynib.core.board.Shelf
import io.github.markovcd.capynib.core.note.Note
import io.github.markovcd.capynib.core.sync.SyncStatus
import io.github.markovcd.capynib.ui.PlainTextField
import io.github.markovcd.capynib.ui.SyncStatusLabel
import io.github.markovcd.capynib.ui.Tags
import io.github.markovcd.capynib.ui.theme.color

@Composable
fun BoardScreen(model: BoardViewModel, onOpen: (String) -> Unit, onSettings: () -> Unit) {
    val content by model.content.collectAsStateWithLifecycle()
    val query by model.query.collectAsStateWithLifecycle()
    val shelf by model.shelf.collectAsStateWithLifecycle()
    val status by model.status.collectAsStateWithLifecycle()
    val trashDays by model.trashDays.collectAsStateWithLifecycle()
    BoardLayout(
        content = content,
        query = query,
        shelf = shelf,
        status = status,
        trashDays = trashDays,
        onQuery = { model.query.value = it },
        onShelf = { model.shelf.value = it },
        onTag = { model.tag.value = it },
        onOpen = onOpen,
        onNew = { checklist -> onOpen(model.newNote(checklist)) },
        onSettings = onSettings,
    )
}

@Composable
fun BoardLayout(
    content: BoardContent?,
    query: String,
    shelf: Shelf,
    status: SyncStatus,
    trashDays: Int,
    onQuery: (String) -> Unit,
    onShelf: (Shelf) -> Unit,
    onTag: (String?) -> Unit,
    onOpen: (String) -> Unit,
    onNew: (checklist: Boolean) -> Unit,
    onSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            Column {
                SearchBar(query, shelf, status, onQuery, onShelf, onSettings)
                val tags = content?.tags.orEmpty()
                if (tags.isNotEmpty()) TagBar(tags, content?.tag, onTag)
            }
        },
        floatingActionButton = {
            if (shelf == Shelf.Notes) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SmallFloatingActionButton(
                        onClick = { onNew(true) },
                        modifier = Modifier.testTag(Tags.NEW_LIST),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Icon(Icons.Outlined.CheckBox, contentDescription = stringResource(R.string.board_new_list))
                    }
                    ExtendedFloatingActionButton(
                        onClick = { onNew(false) },
                        modifier = Modifier.testTag(Tags.NEW_NOTE),
                        icon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        text = { Text(stringResource(R.string.board_new_note)) },
                    )
                }
            }
        },
    ) { padding ->
        if (content == null) return@Scaffold
        if (content.isEmpty) {
            Box(Modifier.fillMaxSize().padding(padding).testTag(Tags.EMPTY_BOARD), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(
                        when (shelf) {
                            Shelf.Notes -> R.string.board_empty
                            Shelf.Archive -> R.string.board_archive_empty
                            Shelf.Trash -> R.string.board_trash_empty
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(160.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 160.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalItemSpacing = 8.dp,
        ) {
            if (shelf == Shelf.Trash) trashNotice(trashDays)
            section(R.string.board_pinned.takeIf { content.pinned.isNotEmpty() }, Tags.PINNED, content.pinned, onOpen)
            section(R.string.board_others.takeIf { content.pinned.isNotEmpty() && content.others.isNotEmpty() }, Tags.OTHERS, content.others, onOpen)
        }
    }
}

/** The pill at the top: search, then the sync status, the archive and trash toggles, and settings. */
@Composable
private fun SearchBar(
    query: String,
    shelf: Shelf,
    status: SyncStatus,
    onQuery: (String) -> Unit,
    onShelf: (Shelf) -> Unit,
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
        SyncStatusIcon(status, onClick = onSettings)
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

private fun LazyStaggeredGridScope.trashNotice(days: Int) {
    item(span = StaggeredGridItemSpan.FullLine, key = "trash-notice") {
        Text(
            pluralStringResource(R.plurals.board_trash_notice, days, days),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp).testTag(Tags.TRASH_NOTICE),
        )
    }
}

private fun LazyStaggeredGridScope.section(heading: Int?, tag: String, notes: List<Note>, onOpen: (String) -> Unit) {
    if (heading != null) {
        item(span = StaggeredGridItemSpan.FullLine, key = "heading-$heading") {
            Text(
                stringResource(heading).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 12.dp).testTag(tag),
            )
        }
    }
    items(notes, key = { it.id }) { note -> NoteCard(note, onOpen = { onOpen(note.id) }) }
}

/** The sync state at a glance; tapping it opens settings, where it is spelled out. */
@Composable
private fun SyncStatusIcon(status: SyncStatus, onClick: () -> Unit) {
    val label = SyncStatusLabel.of(status)
    IconButton(onClick = onClick, modifier = Modifier.testTag(Tags.SYNC_STATUS)) {
        Icon(
            label.icon,
            contentDescription = stringResource(label.text, label.detail.orEmpty()),
            modifier = Modifier.size(22.dp),
            tint = label.tone.color(),
        )
    }
}
