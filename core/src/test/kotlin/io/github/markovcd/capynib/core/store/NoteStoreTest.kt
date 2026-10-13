package io.github.markovcd.capynib.core.store

import io.github.markovcd.capynib.core.note.Note
import io.github.markovcd.capynib.core.note.NoteColor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.sql.DriverManager
import kotlin.time.Duration.Companion.days

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
        store.markPushed(note.id, 1, io.github.markovcd.capynib.core.memos.MemoState("", false, false))
        assertEquals(emptyList<StoredNote>(), store.pending())

        store.save(note.copy(body = "x"), now = 2)

        assertEquals(listOf(note.id), store.pending().map { it.note.id })
    }

    @Test
    fun A_deleted_note_disappears_but_stays_for_sync_to_delete() {
        val store = stores.open()
        val note = store.create(now = 1)

        store.deleteForever(note.id, now = 2)

        assertNull(store.get(note.id))
        assertEquals(emptyList<String>(), store.visible().map { it.id })
        assertTrue(store.stored(note.id)!!.deleted)
    }

    @Test
    fun A_trashed_note_is_kept_until_it_has_been_in_the_trash_the_days_asked_for() {
        val store = stores.open()
        val note = store.create(now = 1)
        store.trash(note.id, now = 10)
        val threeDays = 3.days.inWholeMilliseconds

        assertEquals(0, store.emptyTrash(now = 10 + threeDays - 1, days = 3))
        assertEquals(10L, store.get(note.id)!!.trashedAt)

        assertEquals(1, store.emptyTrash(now = 10 + threeDays, days = 3))
        assertNull(store.get(note.id))
        assertTrue(store.stored(note.id)!!.deleted)
    }

    @Test
    fun A_restored_note_comes_back_where_it_was() {
        val store = stores.open()
        val note = store.create(now = 1, body = "x")
        store.save(note.copy(archived = true), now = 2)

        store.trash(note.id, now = 3)
        store.restore(note.id, now = 4)

        val back = store.get(note.id)!!
        assertEquals(listOf(null, true), listOf(back.trashedAt, back.archived))
        assertEquals(0, store.emptyTrash(now = 4 + 1.days.inWholeMilliseconds, days = 1))
    }

    @Test
    fun Trashing_a_note_again_keeps_when_it_first_went_to_the_trash() {
        val store = stores.open()
        val note = store.create(now = 1)

        store.trash(note.id, now = 2)
        store.trash(note.id, now = 3)

        assertEquals(2L, store.get(note.id)!!.trashedAt)
    }

    @Test
    fun A_database_from_before_the_trash_opens_with_its_notes() {
        val path = folder.newFile("v1.db").absolutePath
        DriverManager.getConnection("jdbc:sqlite:$path").use { db ->
            db.createStatement().use { sql ->
                sql.executeUpdate(V1_SCHEMA)
                sql.executeUpdate("INSERT INTO note(id, title, body, color, pinned, archived, created_at, updated_at) VALUES ('old', 'Kept', 'body', 'sand', 0, 0, 5, 6)")
                sql.executeUpdate("PRAGMA user_version = 1")
            }
        }

        val store = TempStores.open(path, { "new" })

        assertEquals(Note("old", "Kept", "body", NoteColor.Sand, pinned = false, archived = false, createdAt = 5, updatedAt = 6), store.get("old"))
        store.trash("old", now = 7)
        assertEquals(7L, store.get("old")!!.trashedAt)
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

    private companion object {
        /** The note table as the first released schema (`databases/1.db`) made it. */
        const val V1_SCHEMA = """
            CREATE TABLE note (
                id TEXT NOT NULL PRIMARY KEY,
                title TEXT NOT NULL,
                body TEXT NOT NULL,
                color TEXT NOT NULL,
                pinned INTEGER NOT NULL,
                archived INTEGER NOT NULL,
                deleted INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                version INTEGER NOT NULL DEFAULT 1,
                synced_version INTEGER NOT NULL DEFAULT 0,
                on_server INTEGER NOT NULL DEFAULT 0,
                synced_content TEXT,
                synced_pinned INTEGER,
                synced_archived INTEGER
            )
        """
    }
}
