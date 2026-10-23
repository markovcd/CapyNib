package io.github.markovcd.capynib.ui.board

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
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
import io.github.markovcd.capynib.R
import io.github.markovcd.capynib.core.board.BoardContent
import io.github.markovcd.capynib.core.board.Shelf
import io.github.markovcd.capynib.core.board.Swipe
import io.github.markovcd.capynib.core.note.Note
import io.github.markovcd.capynib.ui.Tags
import kotlinx.coroutines.launch

/** How close to the board's top or bottom edge a dragged note scrolls it. */
private val SCROLL_EDGE = 48.dp

/**
 * The cards of [content]. A long press picks a note for the selection; moving the finger after it drags the
 * note to a new place instead, when [BoardContent.canMove]. A swipe left archives
 * a note, or takes it out of the archive; on the notes shelf a swipe right pins or unpins it.
 */
@Composable
internal fun NoteGrid(content: BoardContent, state: BoardState, actions: BoardActions, modifier: Modifier) {
    val selection = state.selection
    val shelf = state.shelf
    val drag = remember { BoardDrag() }
    val grid = rememberLazyStaggeredGridState()
    val scope = rememberCoroutineScope()
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    LaunchedEffect(content) { drag.settle() }

    val current by rememberUpdatedState(content)
    val picked by rememberUpdatedState(selection)
    val canMove by rememberUpdatedState(content.canMove(shelf, state.query))
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
                        if (pickedByPress) actions.selection.toggle(id)
                    },
                    onDragStart = { id, at ->
                        if (!canMove) return@detectPressOnCard false
                        if (pickedByPress) actions.selection.toggle(id)
                        drag.start(id, at, current.sectionOf(id).map { it.id })
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
                    onDragEnd = { drag.drop()?.let { (id, index) -> actions.onMove(id, index) } },
                )
            }
            .onGloballyPositioned { coordinates = it }
            .testTag(Tags.BOARD),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 160.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp,
    ) {
        if (shelf == Shelf.Trash) trashNotice(state.trashDays)
        val card = CardBehavior(
            drag,
            selection,
            swipeable = !selection.isActive,
            shelf,
            actions.onOpen,
            actions.selection.toggle,
            actions.onSwiped,
            state.playing,
            actions.onPlay,
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
