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
        store.markPushed(note.id, 1, io.github.markovcd.capynib.core.memos.MemoState("", false, false, note.position))
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

        assertEquals(Note("old", "Kept", "body", NoteColor.Sand, pinned = false, archived = false, createdAt = 5, updatedAt = 6, position = 0), store.get("old"))
        store.trash("old", now = 7)
        assertEquals(7L, store.get("old")!!.trashedAt)
    }

    @Test
    fun A_database_from_before_notes_could_be_moved_keeps_them_newest_first() {
        val path = folder.newFile("v2.db").absolutePath
        DriverManager.getConnection("jdbc:sqlite:$path").use { db ->
            db.createStatement().use { sql ->
                sql.executeUpdate(V1_SCHEMA)
                sql.executeUpdate("ALTER TABLE note ADD COLUMN trashed_at INTEGER")
                sql.executeUpdate("INSERT INTO note(id, title, body, color, pinned, archived, created_at, updated_at) VALUES ('older', 'a', '', 'sand', 0, 0, 5000, 5000)")
                sql.executeUpdate(
                    "INSERT INTO note(id, title, body, color, pinned, archived, created_at, updated_at, version, synced_version, on_server, synced_content, synced_pinned, synced_archived) " +
                        "VALUES ('newer', 'b', '', 'sand', 0, 0, 9000, 9000, 1, 1, 1, '# b', 0, 0)",
                )
                sql.executeUpdate("PRAGMA user_version = 2")
            }
        }

        val store = TempStores.open(path, { "new" })

        assertEquals(listOf("newer" to 9L, "older" to 5L), store.visible().map { it.id to it.position })
        assertEquals(9L, store.stored("newer")!!.synced!!.position)
        assertEquals(listOf("older"), store.pending().map { it.note.id })
    }

    @Test
    fun A_new_note_goes_on_top_even_of_a_note_moved_above_the_present() {
        val store = stores.open()
        val moved = store.create(now = 1_000)
        store.move(mapOf(moved.id to 5_000L))

        val new = store.create(now = 2_000)

        assertEquals(listOf(new.id, moved.id), store.visible().map { it.id })
    }

    @Test
    fun A_moved_note_waits_to_be_sent_and_a_note_left_in_place_does_not() {
        val store = stores.open()
        val moved = store.create(now = 1_000)
        val still = store.create(now = 2_000)
        listOf(moved, still).forEach { store.markPushed(it.id, 1, io.github.markovcd.capynib.core.memos.MemoState("", false, false, it.position)) }

        store.move(mapOf(moved.id to 9L, still.id to still.position))

        assertEquals(listOf(moved.id), store.pending().map { it.note.id })
        assertEquals(1_000L, store.get(moved.id)!!.updatedAt)
    }

    @Test
    fun Saving_several_notes_at_once_changes_only_those_that_differ() {
        val store = stores.open()
        val pinned = store.create(now = 1)
        val plain = store.create(now = 2)
        store.save(pinned.copy(pinned = true), now = 3)

        val changed = store.saveAll(listOf(pinned.id, plain.id, "gone"), now = 4) { it.copy(pinned = true) }

        assertEquals(1, changed)
        assertEquals(listOf(true, true), listOf(pinned.id, plain.id).map { store.get(it)!!.pinned })
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
