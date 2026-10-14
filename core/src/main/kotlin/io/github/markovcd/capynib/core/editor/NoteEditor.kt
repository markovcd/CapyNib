package io.github.markovcd.capynib.core.editor

import io.github.markovcd.capynib.core.note.Checklist
import io.github.markovcd.capynib.core.note.Note
import io.github.markovcd.capynib.core.note.NoteColor
import io.github.markovcd.capynib.core.store.NoteStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Everything the note screen does, without the screen. Each change shows at once in [note], is
 * written to the store in the background, and then [onSaved] is called, which asks for a sync.
 * A change that arrives from the server while the note is open is shown, unless an edit of the
 * user's is still being written. Writes run in [writeScope], which outlives the screen, so leaving
 * the note never drops the last keystrokes.
 */
class NoteEditor(
    private val store: NoteStore,
    initial: Note,
    scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val clock: () -> Long,
    private val writeScope: CoroutineScope = scope,
    private val onSaved: () -> Unit,
) {
    private val current = MutableStateFlow(initial)
    private val writeLock = Mutex()
    private val unsaved = MutableStateFlow(0)

    val note: StateFlow<Note> = current.asStateFlow()

    val isChecklist: Boolean get() = Checklist.isChecklist(current.value.body)

    init {
        scope.launch {
            store.observe(initial.id).filterNotNull().collect { stored ->
                val changed = !stored.sameContentAs(current.value) || stored.trashedAt != current.value.trashedAt
                if (unsaved.value == 0 && changed) current.value = stored
            }
        }
    }

    fun setTitle(title: String) = change { it.copy(title = title.replace('\n', ' ')) }

    fun setBody(body: String) = change { it.copy(body = body) }

    fun setColor(color: NoteColor) = change { it.copy(color = color) }

    fun togglePinned() = change { it.copy(pinned = !it.pinned) }

    fun setArchived(archived: Boolean) = change { it.copy(archived = archived) }

    fun showCheckboxes() = change { it.copy(body = Checklist.toChecklist(it.body)) }

    fun hideCheckboxes() = change { it.copy(body = Checklist.toText(it.body)) }

    fun toggleItem(index: Int) = change { it.copy(body = Checklist.toggle(it.body, index)) }

    fun setItemText(index: Int, text: String) {
        // Typing Enter inside an item splits it into two items.
        val parts = text.split('\n')
        change { note ->
            var body = Checklist.setText(note.body, index, parts.first())
            parts.drop(1).forEachIndexed { offset, part -> body = Checklist.insertItemAfter(body, index + offset, part) }
            note.copy(body = body)
        }
    }

    /** Adds an empty item after [index] (or first, for -1) and returns the new item's index. */
    fun addItemAfter(index: Int): Int {
        change { it.copy(body = Checklist.insertItemAfter(it.body, index)) }
        return index + 1
    }

    fun removeItem(index: Int) = change { it.copy(body = Checklist.remove(it.body, index)) }

    /** Drags the line at [from] to [to], as [Checklist.move] does. */
    fun moveItem(from: Int, to: Int) = change { it.copy(body = Checklist.move(it.body, from, to)) }

    /** Moves the note to the trash. */
    fun trash() {
        val id = current.value.id
        val now = clock()
        current.value = current.value.copy(trashedAt = now)
        write { store.trash(id, now) }
    }

    /** Takes the note out of the trash. */
    fun restore() {
        val id = current.value.id
        current.value = current.value.copy(trashedAt = null)
        write { store.restore(id, clock()) }
    }

    fun deleteForever() {
        val id = current.value.id
        write { store.deleteForever(id, clock()) }
    }

    /** Leaves the note; a note with nothing written in it is deleted for good. Returns whether it was. */
    fun close(): Boolean {
        if (!current.value.isBlank) return false
        deleteForever()
        return true
    }

    /** Suspends until every change made so far is in the store. */
    suspend fun flush() {
        unsaved.first { it == 0 }
    }

    /** A note in the trash is read-only until it is restored. */
    private fun change(edit: (Note) -> Note) {
        if (current.value.inTrash) return
        val edited = edit(current.value)
        if (edited == current.value) return
        current.value = edited
        write { store.save(current.value, clock()) }
    }

    private fun write(action: () -> Unit) {
        unsaved.update { it + 1 }
        writeScope.launch {
            writeLock.withLock { withContext(io) { action() } }
            unsaved.update { it - 1 }
            onSaved()
        }
    }
}
