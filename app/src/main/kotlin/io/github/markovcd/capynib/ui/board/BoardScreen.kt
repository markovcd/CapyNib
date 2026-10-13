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
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.markovcd.capynib.R
import io.github.markovcd.capynib.core.board.BoardContent
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
    val archive by model.archive.collectAsStateWithLifecycle()
    val status by model.status.collectAsStateWithLifecycle()
    BoardLayout(
        content = content,
        query = query,
        archive = archive,
        status = status,
        onQuery = { model.query.value = it },
        onToggleArchive = { model.archive.value = !archive },
        onOpen = onOpen,
        onNew = { checklist -> onOpen(model.newNote(checklist)) },
        onSettings = onSettings,
    )
}

@Composable
fun BoardLayout(
    content: BoardContent?,
    query: String,
    archive: Boolean,
    status: SyncStatus,
    onQuery: (String) -> Unit,
    onToggleArchive: () -> Unit,
    onOpen: (String) -> Unit,
    onNew: (checklist: Boolean) -> Unit,
    onSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            SearchBar(query, archive, status, onQuery, onToggleArchive, onSettings)
        },
        floatingActionButton = {
            if (!archive) {
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
                    stringResource(if (archive) R.string.board_archive_empty else R.string.board_empty),
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
            section(R.string.board_pinned.takeIf { content.pinned.isNotEmpty() }, Tags.PINNED, content.pinned, onOpen)
            section(R.string.board_others.takeIf { content.pinned.isNotEmpty() && content.others.isNotEmpty() }, Tags.OTHERS, content.others, onOpen)
        }
    }
}

/** The pill at the top: search, then the sync status, the archive toggle and settings. */
@Composable
private fun SearchBar(
    query: String,
    archive: Boolean,
    status: SyncStatus,
    onQuery: (String) -> Unit,
    onToggleArchive: () -> Unit,
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
            placeholder = stringResource(if (archive) R.string.board_archive_title else R.string.board_search_hint),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(start = 10.dp).testTag(Tags.SEARCH),
            singleLine = true,
        )
        SyncStatusIcon(status, onClick = onSettings)
        IconButton(onClick = onToggleArchive, modifier = Modifier.testTag(Tags.ARCHIVE_TOGGLE)) {
            if (archive) {
                Icon(Icons.Outlined.Lightbulb, contentDescription = stringResource(R.string.board_show_notes), tint = colors.onSurfaceVariant)
            } else {
                Icon(Icons.Outlined.Archive, contentDescription = stringResource(R.string.board_show_archive), tint = colors.onSurfaceVariant)
            }
        }
        IconButton(onClick = onSettings, modifier = Modifier.testTag(Tags.SETTINGS)) {
            Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.board_settings), tint = colors.onSurfaceVariant)
        }
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
