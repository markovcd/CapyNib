package io.github.markovcd.capynib.core.sync

import io.github.markovcd.capynib.core.memos.FakeMemosServer
import io.github.markovcd.capynib.core.memos.MemoState
import io.github.markovcd.capynib.core.memos.SyncFailure
import io.github.markovcd.capynib.core.note.NoteColor
import io.github.markovcd.capynib.core.store.TempStores
import io.github.markovcd.capynib.core.store.Trash
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.time.Duration.Companion.days

class SyncEngineTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val stores by lazy { TempStores(folder) }
    private val server = FakeMemosServer()
    private var now = 100L
    private val store by lazy { stores.open() }
    private val engine by lazy { SyncEngine(store, server) { now } }

    private fun note(title: String = "", body: String = "", color: NoteColor = NoteColor.Default) =
        store.create(++now, title, body, color)

    @Test
    fun A_note_written_offline_reaches_the_server_on_the_first_sync() = runTest {
        val note = note("Shopping", "- [ ] milk", NoteColor.Sand)

        val report = engine.sync()

        assertEquals(1, report.sent)
        assertEquals(MemoState("# Shopping\n\n- [ ] milk\n\n#color/sand", false, false), server.memos.getValue(note.id).state)
        assertEquals(emptyList<String>(), store.pending().map { it.note.id })
    }

    @Test
    fun A_memo_made_on_the_server_appears_as_a_note() = runTest {
        server.put("web1", "# From the web\n\nhello\n\n#color/mint", pinned = true)

        val report = engine.sync()

        assertEquals(1, report.received)
        val note = store.get("web1")!!
        assertEquals(listOf("From the web", "hello", NoteColor.Mint, true), listOf(note.title, note.body, note.color, note.pinned))
        assertEquals(emptyList<String>(), store.pending().map { it.note.id })
    }

    @Test
    fun Typing_more_into_a_synced_note_updates_its_memo() = runTest {
        val note = note(body = "draft")
        engine.sync()

        store.save(store.get(note.id)!!.copy(body = "draft, finished"), ++now)
        engine.sync()

        assertEquals("draft, finished", server.memos.getValue(note.id).state.content)
    }

    @Test
    fun An_edit_made_on_the_server_replaces_an_unedited_note() = runTest {
        val note = note(body = "v1")
        engine.sync()

        server.put(note.id, "v2 from web")
        engine.sync()

        assertEquals("v2 from web", store.get(note.id)!!.body)
    }

    @Test
    fun When_both_sides_changed_the_local_edit_wins_and_the_server_version_is_kept_as_a_new_note() = runTest {
        val note = note(body = "v1")
        engine.sync()
        server.put(note.id, "server edit")
        store.save(store.get(note.id)!!.copy(body = "local edit"), ++now)

        val report = engine.sync()

        assertEquals(1, report.conflicts)
        assertEquals("local edit", server.memos.getValue(note.id).state.content)
        assertEquals(setOf("local edit", "server edit"), store.visible().map { it.body }.toSet())
        assertEquals(setOf("local edit", "server edit"), server.memos.values.map { it.state.content }.toSet())
    }

    @Test
    fun A_note_deleted_here_is_deleted_on_the_server() = runTest {
        val note = note(body = "bye")
        engine.sync()

        store.deleteForever(note.id, ++now)
        val report = engine.sync()

        assertEquals(1, report.deletedThere)
        assertTrue(note.id !in server.memos)
        assertNull(store.stored(note.id))
    }

    @Test
    fun A_note_deleted_before_it_was_ever_sent_never_reaches_the_server() = runTest {
        val note = note(body = "oops")
        store.deleteForever(note.id, ++now)

        engine.sync()

        assertEquals(0, server.memos.size)
        assertNull(store.stored(note.id))
    }

    @Test
    fun A_note_in_the_trash_is_archived_on_the_server_and_deleted_there_a_week_later() = runTest {
        val note = note(body = "bye")
        engine.sync()

        store.trash(note.id, ++now)
        engine.sync()
        assertEquals(MemoState("bye", false, true), server.memos.getValue(note.id).state)

        now += Trash.DEFAULT_DAYS.days.inWholeMilliseconds
        val report = engine.sync()

        assertEquals(1, report.deletedThere)
        assertTrue(note.id !in server.memos)
        assertNull(store.stored(note.id))
    }

    @Test
    fun A_sync_keeps_trashed_notes_for_the_days_it_is_given() = runTest {
        val note = note(body = "bye")
        val engine = SyncEngine(store, server, trashDays = 2) { now }
        store.trash(note.id, now)

        now += 1.days.inWholeMilliseconds
        engine.sync()
        assertTrue(store.get(note.id)!!.inTrash)

        now += 1.days.inWholeMilliseconds
        engine.sync()
        assertNull(store.stored(note.id))
    }

    @Test
    fun A_note_restored_from_the_trash_is_back_on_the_server_as_it_was() = runTest {
        val plain = note(body = "plain")
        val archived = note(body = "archived")
        store.save(archived.copy(archived = true), ++now)
        engine.sync()

        for (id in listOf(plain.id, archived.id)) store.trash(id, ++now)
        engine.sync()
        for (id in listOf(plain.id, archived.id)) store.restore(id, ++now)
        engine.sync()

        assertEquals(listOf(false, true), listOf(plain.id, archived.id).map { server.memos.getValue(it).state.archived })
        assertEquals(emptyList<String>(), store.pending().map { it.note.id })
    }

    @Test
    fun A_trashed_note_whose_memo_is_unarchived_elsewhere_leaves_the_trash() = runTest {
        val note = note(body = "wanted after all")
        store.trash(note.id, ++now)
        engine.sync()

        server.put(note.id, "wanted after all", archived = false)
        engine.sync()

        val back = store.get(note.id)!!
        assertEquals(listOf(null, false), listOf(back.trashedAt, back.archived))
    }

    @Test
    fun A_trashed_note_edited_on_the_server_stays_in_the_trash_and_keeps_its_own_archive_state() = runTest {
        val note = note(body = "v1")
        store.trash(note.id, ++now)
        engine.sync()

        server.put(note.id, "v2 from web", archived = true)
        engine.sync()

        val stored = store.get(note.id)!!
        assertEquals(listOf("v2 from web", false), listOf(stored.body, stored.archived))
        assertTrue(stored.inTrash)
        assertEquals(emptyList<String>(), store.pending().map { it.note.id })
    }

    @Test
    fun A_memo_deleted_on_the_server_disappears_here() = runTest {
        val note = note(body = "x")
        engine.sync()

        server.memos.remove(note.id)
        val report = engine.sync()

        assertEquals(1, report.deletedHere)
        assertNull(store.stored(note.id))
    }

    @Test
    fun A_memo_deleted_on_the_server_while_edited_here_comes_back_with_the_edit() = runTest {
        val note = note(body = "x")
        engine.sync()
        server.memos.remove(note.id)
        store.save(store.get(note.id)!!.copy(body = "still wanted"), ++now)

        engine.sync()

        assertEquals("still wanted", server.memos.getValue(note.id).state.content)
    }

    @Test
    fun Edits_made_while_the_server_is_down_are_sent_when_it_is_back() = runTest {
        val note = note(body = "offline")
        server.offline = true

        val failed = engine.sync()

        assertTrue(failed.failure is SyncFailure.Unreachable)
        assertEquals(listOf(note.id), store.pending().map { it.note.id })

        server.offline = false
        engine.sync()

        assertEquals("offline", server.memos.getValue(note.id).state.content)
        assertEquals(emptyList<String>(), store.pending().map { it.note.id })
    }

    @Test
    fun A_create_whose_reply_was_lost_does_not_make_a_duplicate() = runTest {
        val note = note(body = "once")
        server.loseNextCreateReply = true
        engine.sync()
        store.save(store.get(note.id)!!.copy(body = "once more"), ++now)

        engine.sync()

        assertEquals(1, server.memos.size)
        assertEquals(1, store.visible().size)
        assertEquals("once more", server.memos.getValue(note.id).state.content)
    }

    @Test
    fun An_empty_note_is_never_sent() = runTest {
        note()

        engine.sync()

        assertEquals(0, server.memos.size)
    }

    @Test
    fun Pinning_and_archiving_reach_the_server_and_come_back() = runTest {
        val note = note(body = "keep")
        store.save(note.copy(pinned = true, archived = true), ++now)
        engine.sync()

        assertEquals(MemoState("keep", true, true), server.memos.getValue(note.id).state)

        server.put(note.id, "keep", pinned = false, archived = false)
        engine.sync()

        val back = store.get(note.id)!!
        assertEquals(listOf(false, false), listOf(back.pinned, back.archived))
    }

    @Test
    fun A_sync_with_nothing_changed_sends_nothing() = runTest {
        note(body = "x")
        engine.sync()
        val callsAfterFirst = server.calls

        val report = engine.sync()

        assertEquals(SyncReport(), report)
        assertEquals("only the listing", callsAfterFirst + 1, server.calls)
    }

    @Test
    fun Two_devices_see_each_others_notes() = runTest {
        val phone = engine
        val tabletStore = stores.open("t")
        val tablet = SyncEngine(tabletStore, server) { now }
        note(title = "from phone")
        tabletStore.create(++now, "from tablet", "")

        phone.sync()
        tablet.sync()
        phone.sync()

        assertEquals(setOf("from phone", "from tablet"), store.visible().map { it.title }.toSet())
        assertEquals(setOf("from phone", "from tablet"), tabletStore.visible().map { it.title }.toSet())
    }
}
