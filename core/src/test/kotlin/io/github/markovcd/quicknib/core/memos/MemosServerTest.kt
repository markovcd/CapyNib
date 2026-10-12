package io.github.markovcd.quicknib.core.memos

import io.github.markovcd.quicknib.core.note.NoteColor
import io.github.markovcd.quicknib.core.store.TempStores
import io.github.markovcd.quicknib.core.sync.SyncEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Sync against a real memos server. */
class MemosServerTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val stores by lazy { TempStores(folder) }
    private var now = 1_000L

    @Test
    fun Notes_written_on_one_device_arrive_on_another_through_memos() = runTest {
        val live = LiveMemos.connect()
        val phone = stores.open("phone-")
        val tablet = stores.open("tablet-")
        val note = phone.create(++now, "Groceries", "- [ ] milk\n- [x] eggs", NoteColor.Sage)
        phone.save(note.copy(pinned = true), ++now)

        assertEquals(null, SyncEngine(phone, live.api()) { now }.sync().failure)
        assertEquals(null, SyncEngine(tablet, live.api()) { now }.sync().failure)

        val arrived = tablet.get(note.id)!!
        assertEquals(listOf("Groceries", "- [ ] milk\n- [x] eggs", NoteColor.Sage, true), listOf(arrived.title, arrived.body, arrived.color, arrived.pinned))
    }

    @Test
    fun Edits_archives_and_deletes_reach_the_server() = runTest {
        val live = LiveMemos.connect()
        val api = live.api()
        val store = stores.open("e-")
        val engine = SyncEngine(store, api) { now }
        val kept = store.create(++now, "", "first draft")
        val gone = store.create(++now, "", "delete me")
        engine.sync()

        store.save(store.get(kept.id)!!.copy(body = "second draft", archived = true), ++now)
        store.delete(gone.id, ++now)
        assertEquals(null, engine.sync().failure)

        val memos = (api.listAll() as ApiResult.Ok).value
        assertEquals(listOf(MemoState("second draft", pinned = false, archived = true)), memos.map { it.state })
    }

    @Test
    fun The_server_keeps_note_content_byte_for_byte() = runTest {
        val api = LiveMemos.connect().api()
        val content = "# Title\n\n  indented\n- [ ] \n\ttab\n\n#color/fog"

        api.create("bytes-1", MemoState(content, pinned = false, archived = false))

        assertEquals(content, (api.listAll() as ApiResult.Ok).value.single().state.content)
    }

    @Test
    fun A_wrong_token_is_reported_as_unauthorized() = runTest {
        val live = LiveMemos.connect()

        val result = HttpMemosApi(live.server, "memos_pat_wrong").listAll()

        assertTrue(result.toString(), (result as ApiResult.Failed).failure is SyncFailure.Unauthorized)
    }
}
