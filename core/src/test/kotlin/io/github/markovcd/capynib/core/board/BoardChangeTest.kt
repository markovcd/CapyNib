package io.github.markovcd.capynib.core.board

import io.github.markovcd.capynib.core.store.TempStores
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BoardChangeTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val store by lazy { TempStores(folder).open() }

    private fun shelves(vararg ids: String) = ids.map { id -> store.get(id)!!.let { it.archived to it.inTrash } }

    @Test
    fun Archiving_several_notes_and_undoing_it_puts_each_back_where_it_was() {
        val a = store.create(1, body = "a").id
        val b = store.create(2, body = "b").id
        val change = BoardChange(BoardChange.Kind.Archive, setOf(a, b))

        change.apply(store, 3)
        assertEquals(listOf(true to false, true to false), shelves(a, b))

        change.undo(store, 4)
        assertEquals(listOf(false to false, false to false), shelves(a, b))
    }

    @Test
    fun Moving_notes_to_the_trash_and_undoing_it_restores_them() {
        val a = store.create(1, body = "a").id
        val change = BoardChange(BoardChange.Kind.Trash, setOf(a))

        change.apply(store, 2)
        assertEquals(listOf(false to true), shelves(a))

        change.undo(store, 3)
        assertEquals(listOf(false to false), shelves(a))
    }

    @Test
    fun Restoring_notes_from_the_trash_and_undoing_it_puts_them_back_in_the_trash() {
        val a = store.create(1, body = "a").id
        store.trash(a, 2)
        val change = BoardChange(BoardChange.Kind.Restore, setOf(a))

        change.apply(store, 3)
        assertEquals(listOf(false to false), shelves(a))

        change.undo(store, 4)
        assertEquals(listOf(false to true), shelves(a))
    }

    @Test
    fun Applying_a_change_twice_is_the_same_as_once() {
        val a = store.create(1, body = "a").id
        val change = BoardChange(BoardChange.Kind.Unarchive, setOf(a))
        store.save(store.get(a)!!.copy(archived = true), 2)

        change.apply(store, 3)
        change.apply(store, 4)

        assertEquals(listOf(false to false), shelves(a))
    }
}
