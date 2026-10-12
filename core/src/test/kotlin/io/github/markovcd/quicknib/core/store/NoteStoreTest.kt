package io.github.markovcd.quicknib.core.store

import io.github.markovcd.quicknib.core.note.NoteColor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NoteStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val stores by lazy { TempStores(folder) }

    @Test
    fun A_saved_note_reads_back_after_reopening_the_file() {
        val path = folder.newFile("reopen.db").absolutePath
        val first = TempStores.open(path, { "a" })
        val note = first.create(now = 10)
        first.save(note.copy(title = "Title", body = "- [ ] item", color = NoteColor.Mint, pinned = true), now = 20)

        val reopened = TempStores.open(path, { "b" })

        assertEquals(
            note.copy(title = "Title", body = "- [ ] item", color = NoteColor.Mint, pinned = true, updatedAt = 20),
            reopened.get("a"),
        )
    }

    @Test
    fun Pinned_notes_come_first_then_newest_first() {
        val store = stores.open()
        val old = store.create(now = 1)
        val pinnedOld = store.create(now = 2)
        val new = store.create(now = 3)
        store.save(pinnedOld.copy(pinned = true), now = 4)

        assertEquals(listOf(pinnedOld.id, new.id, old.id), store.visible().map { it.id })
    }

    @Test
    fun Saving_an_unchanged_note_is_not_an_edit() {
        val store = stores.open()
        val note = store.create(now = 1)

        assertFalse(store.save(note, now = 2))
        assertEquals(1L, store.stored(note.id)!!.version)
    }

    @Test
    fun Every_edit_leaves_the_note_waiting_to_be_sent() {
        val store = stores.open()
        val note = store.create(now = 1)
        store.markPushed(note.id, 1, io.github.markovcd.quicknib.core.memos.MemoState("", false, false))
        assertEquals(emptyList<StoredNote>(), store.pending())

        store.save(note.copy(body = "x"), now = 2)

        assertEquals(listOf(note.id), store.pending().map { it.note.id })
    }

    @Test
    fun A_deleted_note_disappears_but_stays_for_sync_to_delete() {
        val store = stores.open()
        val note = store.create(now = 1)

        store.delete(note.id, now = 2)

        assertNull(store.get(note.id))
        assertEquals(emptyList<String>(), store.visible().map { it.id })
        assertTrue(store.stored(note.id)!!.deleted)
    }

    @Test
    fun Watching_the_notes_sees_each_change() = runTest {
        val store = stores.open()
        val note = store.create(now = 1)
        store.save(note.copy(title = "seen"), now = 2)

        assertEquals("seen", store.observeVisible().first().single().title)
        assertEquals("seen", store.observe(note.id).first()?.title)
    }

    @Test
    fun An_unknown_color_from_a_newer_version_reads_as_default() {
        assertEquals(NoteColor.Default, NoteColor.fromKey("ultraviolet"))
    }
}
