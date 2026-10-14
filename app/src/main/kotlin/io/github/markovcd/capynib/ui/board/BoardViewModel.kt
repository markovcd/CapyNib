package io.github.markovcd.capynib.ui.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.markovcd.capynib.AppGraph
import io.github.markovcd.capynib.audio.Playback
import io.github.markovcd.capynib.core.board.BoardChange
import io.github.markovcd.capynib.core.board.BoardContent
import io.github.markovcd.capynib.core.board.BoardMode
import io.github.markovcd.capynib.core.board.CalendarMonth
import io.github.markovcd.capynib.core.board.Selection
import io.github.markovcd.capynib.core.board.Shelf
import io.github.markovcd.capynib.core.board.Swipe
import io.github.markovcd.capynib.core.note.Capture
import io.github.markovcd.capynib.core.note.NoteColor
import io.github.markovcd.capynib.core.sync.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale

class BoardViewModel(private val graph: AppGraph) : ViewModel() {
    val query = MutableStateFlow("")
    val shelf = MutableStateFlow(Shelf.Notes)
    val tag = MutableStateFlow<String?>(null)
    private val picked = MutableStateFlow(Selection())
    private val zone = ZoneId.systemDefault()
    private val firstDay = WeekFields.of(Locale.getDefault()).firstDayOfWeek

    val mode = MutableStateFlow(BoardMode.Cards)

    val today: LocalDate get() = Instant.ofEpochMilli(graph.clock()).atZone(zone).toLocalDate()

    /** The day whose notes the calendar lists; today until another is picked. */
    val day = MutableStateFlow(today)

    /** The month the calendar shows. */
    val month = MutableStateFlow(YearMonth.from(day.value))

    val content: StateFlow<BoardContent?> =
        combine(graph.store.observeVisible(), query, shelf, tag) { notes, query, shelf, tag -> BoardContent.of(notes, query, shelf, tag) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The board's notes on [month]'s calendar. */
    val calendar: StateFlow<CalendarMonth?> =
        combine(content, month) { content, month -> content?.let { CalendarMonth.of(it.pinned + it.others, month, zone, firstDay) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The picked notes still on the board; a shelf change or a note leaving it drops them. */
    val selection: StateFlow<Selection> =
        combine(picked, content) { picked, content -> if (content == null) Selection() else picked.within(content) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Selection())

    /** The last change a swipe or the selection made, for the snackbar to offer to undo. */
    val lastChange = MutableStateFlow<BoardChange?>(null)

    /** The notes waiting for a yes before they are deleted forever; empty when nothing is asked. */
    val deleting = MutableStateFlow<Set<String>>(emptySet())

    init {
        // Without a server nothing else empties the trash.
        viewModelScope.launch(Dispatchers.IO) { graph.emptyTrash() }
    }

    val status: StateFlow<SyncStatus> = graph.scheduler.status

    val trashDays: StateFlow<Int> = graph.trash.days

    val playback: Playback = graph.playback

    /** Makes an empty note, or a list with one empty item, and returns its id to open it. */
    fun newNote(checklist: Boolean): String = graph.capture(if (checklist) Capture.EmptyChecklist else Capture.EmptyNote)

    fun showShelf(shelf: Shelf) {
        picked.value = Selection()
        this.shelf.value = shelf
    }

    fun showMode(mode: BoardMode) {
        picked.value = Selection()
        this.mode.value = mode
    }

    /** Shows the month [by] months after the one shown, or before it when negative. */
    fun showMonth(by: Long) {
        month.value = month.value.plusMonths(by)
    }

    fun pickDay(date: LocalDate) {
        day.value = date
    }

    fun toggle(id: String) {
        picked.value = selection.value.toggle(id)
    }

    fun clearSelection() {
        picked.value = Selection()
    }

    /** Pins the picked notes, or unpins them when all are pinned. */
    fun pinSelected() {
        val ids = selection.value.ids
        val pins = selection.value.pins(content.value?.let { it.pinned + it.others }.orEmpty())
        write { graph.store.saveAll(ids, graph.clock()) { it.copy(pinned = pins) } }
    }

    fun colorSelected(color: NoteColor) {
        val ids = selection.value.ids
        write { graph.store.saveAll(ids, graph.clock()) { it.copy(color = color) } }
    }

    /** Archives the picked notes, or takes them out of the archive when it is shown. */
    fun archiveSelected() {
        change(BoardChange(archiveKind(), selection.value.ids))
        clearSelection()
    }

    fun trashSelected() {
        change(BoardChange(BoardChange.Kind.Trash, selection.value.ids))
        clearSelection()
    }

    fun restoreSelected() {
        change(BoardChange(BoardChange.Kind.Restore, selection.value.ids))
        clearSelection()
    }

    /** Asks to delete the picked notes for good. */
    fun deleteSelectedForever() {
        deleting.value = selection.value.ids
    }

    /** Deletes for good the notes [deleting] asked about. */
    fun confirmDeleteForever() {
        val ids = deleting.value
        deleting.value = emptySet()
        picked.value = Selection(picked.value.ids - ids)
        write { ids.forEach { graph.store.deleteForever(it, graph.clock()) } }
    }

    fun cancelDeleteForever() {
        deleting.value = emptySet()
    }

    fun swiped(id: String, swipe: Swipe) {
        when (swipe) {
            Swipe.Pin -> write { graph.store.saveAll(setOf(id), graph.clock()) { it.copy(pinned = !it.pinned) } }
            Swipe.DeleteForever -> deleting.value = setOf(id)
            else -> swipe.change?.let { change(BoardChange(it, setOf(id))) }
        }
    }

    fun undo(change: BoardChange) {
        write { change.undo(graph.store, graph.clock()) }
    }

    /** The snackbar for [change] is gone, so it is not offered again. */
    fun changeShown(change: BoardChange) {
        lastChange.compareAndSet(change, null)
    }

    /** Puts note [id] at index [to] of its section, as dropped after a drag. */
    fun move(id: String, to: Int) {
        val positions = content.value?.move(id, to).orEmpty()
        if (positions.isNotEmpty()) write { graph.store.move(positions) }
    }

    private fun archiveKind() = if (shelf.value == Shelf.Archive) BoardChange.Kind.Unarchive else BoardChange.Kind.Archive

    private fun change(change: BoardChange) {
        if (change.ids.isEmpty()) return
        lastChange.value = change
        write { change.apply(graph.store, graph.clock()) }
    }

    private fun write(block: () -> Unit) {
        block()
        graph.scheduler.requestSync()
    }
}
