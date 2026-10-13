package io.github.markovcd.capynib.core.board

import io.github.markovcd.capynib.core.store.NoteStore

/**
 * A change made from the board to several notes at once, by a swipe or a selection's action, that
 * the snackbar can undo. [apply] makes it and [undo] takes it back; both leave alone a note
 * already as asked.
 */
data class BoardChange(val kind: Kind, val ids: Set<String>) {
    enum class Kind { Archive, Unarchive, Trash }

    fun apply(store: NoteStore, now: Long) {
        when (kind) {
            Kind.Archive -> store.saveAll(ids, now) { it.copy(archived = true) }
            Kind.Unarchive -> store.saveAll(ids, now) { it.copy(archived = false) }
            Kind.Trash -> store.trashAll(ids, now)
        }
    }

    fun undo(store: NoteStore, now: Long) {
        when (kind) {
            Kind.Archive -> store.saveAll(ids, now) { it.copy(archived = false) }
            Kind.Unarchive -> store.saveAll(ids, now) { it.copy(archived = true) }
            Kind.Trash -> ids.forEach { store.restore(it, now) }
        }
    }
}
