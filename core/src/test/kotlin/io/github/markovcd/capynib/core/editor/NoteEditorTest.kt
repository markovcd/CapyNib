package io.github.markovcd.capynib.core.editor

import io.github.markovcd.capynib.core.note.NoteColor
import io.github.markovcd.capynib.core.store.NoteStore
import io.github.markovcd.capynib.core.store.TempStores
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NoteEditorTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var saves = 0
    private lateinit var store: NoteStore

    private fun TestScope.editor(body: String = ""): NoteEditor {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        store = TempStores(folder).open(dispatcher = dispatcher)
        val note = store.create(0, body = body)
        return NoteEditor(store, note, backgroundScope, dispatcher, { testScheduler.currentTime }) { saves++ }
    }

    @Test
    fun Every_keystroke_is_saved_and_asks_for_a_sync() = runTest {
        val editor = editor()

        editor.setTitle("Ti")
        editor.setTitle("Title")
        editor.setBody("text")
        editor.flush()

        assertEquals(listOf("Title", "text"), store.get(editor.note.value.id)!!.let { listOf(it.title, it.body) })
        assertEquals(3, saves)
    }

    @Test
    fun A_title_stays_on_one_line() = runTest {
        val editor = editor()

        editor.setTitle("two\nlines")

        assertEquals("two lines", editor.note.value.title)
    }

    @Test
    fun Color_pin_and_archive_are_saved() = runTest {
        val editor = editor("x")

        editor.setColor(NoteColor.Dusk)
        editor.togglePinned()
        editor.setArchived(true)
        editor.flush()

        val saved = store.get(editor.note.value.id)!!
        assertEquals(listOf(NoteColor.Dusk, true, true), listOf(saved.color, saved.pinned, saved.archived))
    }

    @Test
    fun Showing_checkboxes_turns_lines_into_items_that_can_be_ticked() = runTest {
        val editor = editor("milk\neggs")

        editor.showCheckboxes()
        editor.toggleItem(1)

        assertTrue(editor.isChecklist)
        assertEquals("- [ ] milk\n- [x] eggs", editor.note.value.body)

        editor.hideCheckboxes()
        assertEquals("milk\neggs", editor.note.value.body)
    }

    @Test
    fun Enter_in_an_item_starts_a_new_item_after_it() = runTest {
        val editor = editor("- [ ] milk")

        editor.setItemText(0, "milk\nbread")

        assertEquals("- [ ] milk\n- [ ] bread", editor.note.value.body)
    }

    @Test
    fun Items_can_be_added_and_removed() = runTest {
        val editor = editor("- [ ] a")

        val added = editor.addItemAfter(0)
        editor.setItemText(added, "b")
        editor.removeItem(0)

        assertEquals("- [ ] b", editor.note.value.body)
    }

    @Test
    fun Leaving_an_empty_note_throws_it_away() = runTest {
        val editor = editor("- [ ] ")

        assertTrue(editor.close())
        editor.flush()
        runCurrent()

        assertNull(store.get(editor.note.value.id))
    }

    @Test
    fun Deleting_moves_the_note_to_the_trash() = runTest {
        val editor = editor("x")

        editor.trash()
        editor.flush()

        assertEquals(0L, store.get(editor.note.value.id)!!.trashedAt)
    }

    @Test
    fun A_note_in_the_trash_cannot_be_edited_until_it_is_restored() = runTest {
        val editor = editor("- [ ] milk")
        editor.trash()

        editor.setBody("changed")
        editor.toggleItem(0)
        editor.flush()
        assertEquals("- [ ] milk", store.get(editor.note.value.id)!!.body)

        editor.restore()
        editor.toggleItem(0)
        editor.flush()
        val restored = store.get(editor.note.value.id)!!
        assertEquals(listOf(null, "- [x] milk"), listOf(restored.trashedAt, restored.body))
    }

    @Test
    fun Deleting_forever_leaves_nothing_to_restore() = runTest {
        val editor = editor("x")
        editor.trash()

        editor.deleteForever()
        editor.flush()

        assertNull(store.get(editor.note.value.id))
    }

    @Test
    fun Leaving_a_note_with_text_keeps_it() = runTest {
        val editor = editor()
        editor.setBody("keep me")

        assertFalse(editor.close())
    }

    @Test
    fun A_change_from_the_server_shows_in_an_open_note() = runTest {
        val editor = editor("old")
        editor.flush()
        val stored = store.stored(editor.note.value.id)!!

        store.applyServer(
            stored.note.id,
            stored.version,
            io.github.markovcd.capynib.core.note.MemoContent("", "from server", NoteColor.Default),
            io.github.markovcd.capynib.core.memos.MemoState("from server", pinned = false, archived = false),
            updatedAt = 5,
        )
        runCurrent()

        assertEquals("from server", editor.note.value.body)
    }
}
