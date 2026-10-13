package io.github.markovcd.capynib.core.memos

import io.github.markovcd.capynib.core.board.BoardContent
import io.github.markovcd.capynib.core.note.MemoCodec
import io.github.markovcd.capynib.core.note.MemoContent
import io.github.markovcd.capynib.core.note.NoteColor
import io.github.markovcd.capynib.core.note.NoteTags
import io.github.markovcd.capynib.core.store.TempStores
import io.github.markovcd.capynib.core.sync.SyncEngine
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
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
    fun Edits_archives_trash_and_deletes_reach_the_server() = runTest {
        val live = LiveMemos.connect()
        val api = live.api()
        val store = stores.open("e-")
        val engine = SyncEngine(store, api) { now }
        val kept = store.create(++now, "", "first draft")
        val trashed = store.create(++now, "", "trash me")
        val gone = store.create(++now, "", "delete me")
        engine.sync()

        store.save(store.get(kept.id)!!.copy(body = "second draft", archived = true), ++now)
        store.trash(trashed.id, ++now)
        store.deleteForever(gone.id, ++now)
        assertEquals(null, engine.sync().failure)

        val memos = (api.listAll() as ApiResult.Ok).value
        assertEquals(
            setOf(MemoState("second draft", pinned = false, archived = true, kept.position), MemoState("trash me", pinned = false, archived = true, trashed.position)),
            memos.map { it.state }.toSet(),
        )
    }

    @Test
    fun A_note_moved_on_one_device_is_in_the_same_place_on_another_through_memos() = runTest {
        val live = LiveMemos.connect()
        val phone = stores.open("phone-")
        val tablet = stores.open("tablet-")
        val bottom = phone.create(++now, "", "bottom")
        phone.create(++now, "", "middle")
        phone.create(++now, "", "top")
        SyncEngine(phone, live.api()) { now }.sync()

        phone.move(BoardContent.of(phone.visible()).move(bottom.id, 0))
        assertEquals(null, SyncEngine(phone, live.api()) { now }.sync().failure)
        assertEquals(null, SyncEngine(tablet, live.api()) { now }.sync().failure)

        assertEquals(listOf("bottom", "top", "middle"), tablet.visible().map { it.body })
    }

    @Test
    fun The_server_keeps_note_content_byte_for_byte() = runTest {
        val api = LiveMemos.connect().api()
        val content = "# Title\n\n  indented\n- [ ] \n\ttab\n\n#color/fog"

        api.create("bytes-1", MemoState(content, pinned = false, archived = false, position = 1_000_000))

        assertEquals(content, (api.listAll() as ApiResult.Ok).value.single().state.content)
    }

    @Test
    fun A_notes_tags_are_the_ones_memos_lists_for_its_memo() = runTest {
        val live = LiveMemos.connect()
        val version = (live.api().serverVersion() as ApiResult.Ok).value.orEmpty()
        val (major, minor) = version.split('.').take(2).map { it.toIntOrNull() ?: 0 } + listOf(0, 0)
        assumeTrue(
            "skip on memos $version: TagLexer ports the lexer of 0.31, and older releases end a tag at an apostrophe (ADR 0007)",
            major > 0 || minor >= 31,
        )
        val store = stores.open("tags-")
        val notes = listOf(
            store.create(++now, "Lisbon #travel", "- [ ] socks #packing\n- [x] passport #packing/documents", NoteColor.Sand),
            store.create(++now, "", "see https://example.com/page#section and `#code` but #real-tag."),
            store.create(++now, "Shopping", "milk, #café, #C++ and #O'Brien's #测试", NoteColor.Mint),
            store.create(++now, "", "[link #text](https://example.com#frag) #after\n\n```\n#fenced\n```"),
        )
        assertEquals(null, SyncEngine(store, live.api()) { now }.sync().failure)

        val listed = live.admin("memos?pageSize=1000", Request.Builder().get())["memos"]!!.jsonArray.map { it.jsonObject }
        val serverTags = listed.associate { memo ->
            memo["content"]!!.jsonPrimitive.content to memo["tags"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }
        }
        for (note in notes) {
            val content = MemoCodec.encode(MemoContent(note.title, note.body, note.color))
            val expected = serverTags.getValue(content).filterNot { it == "color" || it.startsWith("color/") }
            assertEquals(content, expected, NoteTags.of(note).flatMap(NoteTags::withParents).distinct())
        }
    }

    @Test
    fun Every_call_sync_makes_works_and_the_check_leaves_the_users_memos_as_they_were() = runTest {
        val api = LiveMemos.connect().api()
        val mine = MemoState("mine", pinned = false, archived = false, position = 1_760_000_000)
        api.create("kept-1", mine)

        val report = (ServerCheck(api).run() as ApiResult.Ok).value

        assertEquals(report.failed.toString(), true, report.works)
        assertEquals(null, report.leftover)
        assertEquals(listOf(mine), (api.listAll() as ApiResult.Ok).value.map { it.state })
    }

    @Test
    fun A_wrong_token_is_reported_as_unauthorized() = runTest {
        val live = LiveMemos.connect()

        val result = HttpMemosApi(live.server, "memos_pat_wrong").listAll()

        assertTrue(result.toString(), (result as ApiResult.Failed).failure is SyncFailure.Unauthorized)
    }
}
