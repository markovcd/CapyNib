package io.github.markovcd.capynib.ui.board

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.markovcd.capynib.R
import io.github.markovcd.capynib.audio.PlayingClip
import io.github.markovcd.capynib.core.board.BoardChange
import io.github.markovcd.capynib.core.board.BoardContent
import io.github.markovcd.capynib.core.board.BoardMode
import io.github.markovcd.capynib.core.board.CalendarMonth
import io.github.markovcd.capynib.core.board.Selection
import io.github.markovcd.capynib.core.board.Shelf
import io.github.markovcd.capynib.core.board.Swipe
import io.github.markovcd.capynib.core.note.Note
import io.github.markovcd.capynib.core.note.Recording
import io.github.markovcd.capynib.core.note.NoteColor
import io.github.markovcd.capynib.core.sync.SyncStatus
import io.github.markovcd.capynib.ui.ColorPalette
import io.github.markovcd.capynib.ui.ConfirmDeleteForever
import io.github.markovcd.capynib.ui.PlainTextField
import io.github.markovcd.capynib.ui.SyncStatusLabel
import io.github.markovcd.capynib.ui.Tags
import io.github.markovcd.capynib.ui.theme.color
import kotlinx.coroutines.launch
import java.time.LocalDate

/** How close to the board's top or bottom edge a dragged note scrolls it. */
private val SCROLL_EDGE = 48.dp

@Composable
fun BoardScreen(model: BoardViewModel, onOpen: (String) -> Unit, onNew: (String) -> Unit, onSettings: () -> Unit) {
    val content by model.content.collectAsStateWithLifecycle()
    val query by model.query.collectAsStateWithLifecycle()
    val shelf by model.shelf.collectAsStateWithLifecycle()
    val mode by model.mode.collectAsStateWithLifecycle()
    val calendar by model.calendar.collectAsStateWithLifecycle()
    val day by model.day.collectAsStateWithLifecycle()
    val selection by model.selection.collectAsStateWithLifecycle()
    val lastChange by model.lastChange.collectAsStateWithLifecycle()
    val deleting by model.deleting.collectAsStateWithLifecycle()
    val status by model.status.collectAsStateWithLifecycle()
    val trashDays by model.trashDays.collectAsStateWithLifecycle()
    val playing by model.playback.playing.collectAsStateWithLifecycle()
    BoardLayout(
        content = content,
        query = query,
        shelf = shelf,
        mode = mode,
        calendar = calendar,
        day = day,
        today = model.today,
        selection = selection,
        lastChange = lastChange,
        deleting = deleting,
        status = status,
        trashDays = trashDays,
        onQuery = { model.query.value = it },
        onShelf = model::showShelf,
        onMode = model::showMode,
        onMonth = model::showMonth,
        onDay = model::pickDay,
        onTag = { model.tag.value = it },
        onOpen = onOpen,
        onNew = { checklist -> onNew(model.newNote(checklist)) },
        onSettings = onSettings,
        actions = SelectionActions(
            toggle = model::toggle,
            clear = model::clearSelection,
            pin = model::pinSelected,
            color = model::colorSelected,
            archive = model::archiveSelected,
            trash = model::trashSelected,
            restore = model::restoreSelected,
            deleteForever = model::deleteSelectedForever,
        ),
        onSwiped = model::swiped,
        playing = playing,
        onPlay = model.playback::toggle,
        onConfirmDelete = model::confirmDeleteForever,
        onCancelDelete = model::cancelDeleteForever,
        onMove = model::move,
        onUndo = model::undo,
        onChangeShown = model::changeShown,
    )
}

@Composable
fun BoardLayout(
    content: BoardContent?,
    query: String,
    shelf: Shelf,
    mode: BoardMode,
    calendar: CalendarMonth?,
    day: LocalDate,
    today: LocalDate,
    selection: Selection,
    lastChange: BoardChange?,
    deleting: Set<String>,
    status: SyncStatus,
    trashDays: Int,
    onQuery: (String) -> Unit,
    onShelf: (Shelf) -> Unit,
    onMode: (BoardMode) -> Unit,
    onMonth: (Long) -> Unit,
    onDay: (LocalDate) -> Unit,
    onTag: (String?) -> Unit,
    onOpen: (String) -> Unit,
    onNew: (checklist: Boolean) -> Unit,
    onSettings: () -> Unit,
    actions: SelectionActions,
    onSwiped: (String, Swipe) -> Unit,
    playing: PlayingClip?,
    onPlay: (Recording) -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onMove: (String, Int) -> Unit,
    onUndo: (BoardChange) -> Unit,
    onChangeShown: (BoardChange) -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    ChangeSnackbar(snackbar, lastChange, onUndo, onChangeShown)
    if (deleting.isNotEmpty()) ConfirmDeleteForever(deleting.size, onConfirmDelete, onCancelDelete)
    BackHandler(enabled = selection.isActive) { actions.clear() }
    Scaffold(
        // The selection bar takes the search bar's place at its height, and the tags stay, so the board
        // does not move under the finger that picked a note.
        topBar = {
            Column {
                if (selection.isActive) {
                    val notes = content?.let { it.pinned + it.others }.orEmpty()
                    SelectionBar(selection, pins = selection.pins(notes), shelf, actions)
                } else {
                    SearchBar(query, shelf, mode, status, onQuery, onShelf, onMode, onSettings)
                }
                val tags = content?.tags.orEmpty()
                if (tags.isNotEmpty()) TagBar(tags, content?.tag, onTag)
            }
        },
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                Snackbar(
                    action = {
                        TextButton(onClick = { data.performAction() }, modifier = Modifier.testTag(Tags.UNDO)) {
                            Text(data.visuals.actionLabel.orEmpty())
                        }
                    },
                ) { Text(data.visuals.message) }
            }
        },
        floatingActionButton = {
            if (shelf == Shelf.Notes && !selection.isActive) {
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
        if (mode == BoardMode.Calendar) {
            if (calendar != null) NoteCalendar(calendar, day, today, selection, onMonth, onDay, onOpen, actions.toggle, playing, onPlay, Modifier.padding(padding))
            return@Scaffold
        }
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
        NoteGrid(content, query, shelf, selection, trashDays, onOpen, actions, onSwiped, playing, onPlay, onMove, Modifier.padding(padding))
    }
}

/**
 * The cards. A long press picks a note for the selection; moving the finger after it drags the
 * note to a new place instead, on the notes shelf when no search or tag narrows it. A swipe left archives
 * a note, or takes it out of the archive; on the notes shelf a swipe right pins or unpins it.
 */
@Composable
private fun NoteGrid(
    content: BoardContent,
    query: String,
    shelf: Shelf,
    selection: Selection,
    trashDays: Int,
    onOpen: (String) -> Unit,
    actions: SelectionActions,
    onSwiped: (String, Swipe) -> Unit,
    playing: PlayingClip?,
    onPlay: (Recording) -> Unit,
    onMove: (String, Int) -> Unit,
    modifier: Modifier,
) {
    val drag = remember { BoardDrag() }
    val grid = rememberLazyStaggeredGridState()
    val scope = rememberCoroutineScope()
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    LaunchedEffect(content) { drag.settle() }

    val current by rememberUpdatedState(content)
    val picked by rememberUpdatedState(selection)
    val movable = shelf == Shelf.Notes && query.isBlank() && content.tag == null
    val canMove by rememberUpdatedState(movable)
    val edge = with(LocalDensity.current) { SCROLL_EDGE.toPx() }

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(160.dp),
        state = grid,
        modifier = modifier.fillMaxSize()
            .pointerInput(Unit) {
                var pickedByPress = false
                detectPressOnCard(
                    toRoot = { point -> coordinates?.let { it.localToRoot(point) } ?: point },
                    noteAt = drag::noteAt,
                    onLongPress = { id ->
                        pickedByPress = id !in picked.ids
                        if (pickedByPress) actions.toggle(id)
                    },
                    onDragStart = { id, at ->
                        if (!canMove) return@detectPressOnCard false
                        if (pickedByPress) actions.toggle(id)
                        val section = if (current.pinned.any { it.id == id }) current.pinned else current.others
                        drag.start(id, at, section.map { it.id })
                        true
                    },
                    onDrag = { at ->
                        drag.drag(at)
                        val bounds = coordinates?.let { Rect(it.positionInRoot(), it.size.toSize()) }
                        if (bounds != null) {
                            val by = when {
                                at.y < bounds.top + edge -> -edge / 4
                                at.y > bounds.bottom - edge -> edge / 4
                                else -> 0f
                            }
                            if (by != 0f) scope.launch { grid.scrollBy(by) }
                        }
                    },
                    onDragEnd = { drag.drop()?.let { (id, index) -> onMove(id, index) } },
                )
            }
            .onGloballyPositioned { coordinates = it }
            .testTag(Tags.BOARD),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 160.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp,
    ) {
        if (shelf == Shelf.Trash) trashNotice(trashDays)
        val card = CardBehavior(
            drag,
            selection,
            swipeable = !selection.isActive,
            shelf,
            onOpen,
            actions.toggle,
            onSwiped,
            playing,
            onPlay,
        )
        section(R.string.board_pinned.takeIf { content.pinned.isNotEmpty() }, Tags.PINNED, drag.shown(content.pinned), card)
        section(R.string.board_others.takeIf { content.pinned.isNotEmpty() && content.others.isNotEmpty() }, Tags.OTHERS, drag.shown(content.others), card)
    }
}

/** The section's notes in the order a drag shows them, or as given when no drag is in it. */
private fun BoardDrag.shown(notes: List<Note>): List<Note> {
    val ids = order ?: return notes
    if (ids.size != notes.size) return notes
    val byId = notes.associateBy { it.id }
    return ids.mapNotNull { byId[it] }.takeIf { it.size == notes.size } ?: notes
}

/**
 * Watches every press on the board before the cards see it. A press held still for a long press
 * picks the card under it; moving after that drags it, when [onDragStart] allows. Once the long press
 * is reached the rest of the gesture is the board's, so the card does not also open.
 */
private suspend fun PointerInputScope.detectPressOnCard(
    toRoot: (Offset) -> Offset,
    noteAt: (Offset) -> String?,
    onLongPress: (String) -> Unit,
    onDragStart: (String, Offset) -> Boolean,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    val id = noteAt(toRoot(down.position)) ?: return@awaitEachGesture
    val slop = viewConfiguration.touchSlop
    val letGo = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        do {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
        } while (change != null && change.pressed && (change.position - down.position).getDistance() <= slop)
        true
    }
    if (letGo != null) return@awaitEachGesture
    onLongPress(id)

    var dragging = false
    while (!dragging) {
        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
        change.consume()
        if (!change.pressed) return@awaitEachGesture
        if ((change.position - down.position).getDistance() > slop) dragging = onDragStart(id, toRoot(change.position))
    }
    try {
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
            change.consume()
            if (!change.pressed) break
            onDrag(toRoot(change.position))
        }
    } finally {
        onDragEnd()
    }
}

/** The pill at the top: search, then the sync status, the calendar, archive and trash toggles, and settings. */
@Composable
private fun SearchBar(
    query: String,
    shelf: Shelf,
    mode: BoardMode,
    status: SyncStatus,
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
        SyncStatusIcon(status, onClick = onSettings)
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

/** Says what the last swipe or selection action did, with Undo, until the next one replaces it. */
@Composable
private fun ChangeSnackbar(snackbar: SnackbarHostState, change: BoardChange?, onUndo: (BoardChange) -> Unit, onShown: (BoardChange) -> Unit) {
    val message = change?.let {
        val text = when (it.kind) {
            BoardChange.Kind.Archive -> R.plurals.board_archived
            BoardChange.Kind.Unarchive -> R.plurals.board_unarchived
            BoardChange.Kind.Trash -> R.plurals.board_trashed
            BoardChange.Kind.Restore -> R.plurals.board_restored
        }
        pluralStringResource(text, it.ids.size, it.ids.size)
    }
    val undo = stringResource(R.string.board_undo)
    LaunchedEffect(change) {
        if (change == null || message == null) return@LaunchedEffect
        val result = snackbar.showSnackbar(message, actionLabel = undo, duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) onUndo(change)
        onShown(change)
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

private fun LazyStaggeredGridScope.section(heading: Int?, tag: String, notes: List<Note>, card: CardBehavior) {
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
    items(notes, key = { it.id }) { note ->
        val dragged = card.drag.dragged == note.id
        DisposableEffect(note.id) { onDispose { card.drag.forget(note.id) } }
        // Not saved: the grid restores a returning card's saved state, and a card swiped off one shelf
        // would come back dismissed on the next and be swiped again.
        val threshold = SwipeToDismissBoxDefaults.positionalThreshold
        val swipe = remember(note.id) { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, threshold) }
        val scope = rememberCoroutineScope()
        // One callback per card: the box calls it again whenever it changes while still dismissed.
        val onDismiss: (SwipeToDismissBoxValue) -> Unit = remember(note.id, card.shelf) {
            { direction ->
                val done = Swipe.of(card.shelf, right = direction == SwipeToDismissBoxValue.StartToEnd)
                card.onSwiped(note.id, done)
                if (done.keepsCard) scope.launch { swipe.reset() }
            }
        }
        SwipeToDismissBox(
            state = swipe,
            backgroundContent = {},
            modifier = Modifier
                .animateItem(placementSpec = if (dragged) null else spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow, IntOffset.VisibilityThreshold))
                .zIndex(if (dragged) 1f else 0f)
                .onGloballyPositioned { card.drag.place(note.id, Rect(it.positionInRoot(), it.size.toSize())) }
                .graphicsLayer {
                    val offset = card.drag.offsetOf(note.id)
                    translationX = offset.x
                    translationY = offset.y
                    if (dragged) shadowElevation = 8.dp.toPx()
                },
            gesturesEnabled = card.swipeable && !dragged,
            onDismiss = onDismiss,
        ) {
            NoteCard(
                note,
                selected = note.id in card.selection.ids,
                playing = card.playing,
                onPlay = card.onPlay,
                onOpen = { if (card.selection.isActive) card.onToggle(note.id) else card.onOpen(note.id) },
            )
        }
    }
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
