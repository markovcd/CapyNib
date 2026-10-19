package io.github.markovcd.capynib.core.sync

import io.github.markovcd.capynib.core.memos.FakeMemosServer
import io.github.markovcd.capynib.core.note.Note
import io.github.markovcd.capynib.core.store.TempStores
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RecordingSyncTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val stores by lazy { TempStores(folder) }
    private val server = FakeMemosServer()
    private var now = 100L
    private val store by lazy { stores.open() }
    private val engine by lazy { SyncEngine(store, server, FakeMemosServer.ADDRESS) { now } }

    /** Records [clip] into note [note], as the editor does. */
    private fun record(note: Note, clip: String, durationMillis: Long = 1_000): String {
        val recording = store.attachments.newRecording(note.id, durationMillis, ++now)
        store.attachments.addRecording(recording, folder.newFile().apply { writeText(clip) })
        return recording.id
    }

    private fun clipsOf(id: String): Map<String, String?> =
        store.get(id)!!.recordings.associate { it.id to store.attachments.read(it.id)?.decodeToString() }

    @Test
    fun A_recording_made_offline_reaches_the_server_attached_to_its_memo() = runTest {
        val note = store.create(++now, "Idea")
        val clip = record(note, "hum")

        assertNull(engine.sync().failure)

        assertEquals(mapOf(clip to "hum"), server.filesOf(note.id))
        assertEquals("recording-$clip.m4a" to "audio/mp4", server.memos.getValue(note.id).attachments.single().let { it.fileName to it.type })
        assertEquals(emptyList<String>(), store.attachments.toUpload().map { it.id })
    }

    @Test
    fun A_note_that_holds_only_a_recording_is_sent() = runTest {
        val note = store.create(++now)
        val clip = record(note, "hum")

        assertFalse(store.get(note.id)!!.isBlank)
        assertNull(engine.sync().failure)

        assertEquals("", server.memos.getValue(note.id).state.content)
        assertEquals(mapOf(clip to "hum"), server.filesOf(note.id))
    }

    @Test
    fun A_recording_attached_on_the_server_reaches_the_note_and_its_clip_is_downloaded() = runTest {
        server.put("web", "From the web")
        server.attach("rec1", "web", "voice.webm", "audio/webm", "spoken".encodeToByteArray())

        assertNull(engine.sync().failure)

        assertEquals(mapOf("rec1" to "spoken"), clipsOf("web"))
        assertEquals(true, store.get("web")!!.recordings.single().downloaded)
    }

    @Test
    fun An_attachment_that_is_neither_audio_nor_an_image_is_left_alone() = runTest {
        server.put("web", "From the web")
        server.attach("doc1", "web", "plan.pdf", "application/pdf", byteArrayOf(1, 2))

        assertNull(engine.sync().failure)

        assertEquals(emptyMap<String, String?>(), clipsOf("web"))
        assertEquals(emptyList<String>(), store.get("web")!!.photos.map { it.id })
        assertEquals(listOf("doc1"), server.memos.getValue("web").attachments.map { it.id })
    }

    @Test
    fun A_recording_deleted_here_is_deleted_on_the_server() = runTest {
        val note = store.create(++now, "Idea")
        val clip = record(note, "hum")
        engine.sync()

        store.attachments.delete(clip)
        assertNull(engine.sync().failure)

        assertEquals(emptyMap<String, String>(), server.filesOf(note.id))
        assertEquals(emptyList<String>(), store.attachments.toDelete())
    }

    @Test
    fun A_recording_deleted_on_the_server_is_deleted_here_with_its_clip() = runTest {
        val note = store.create(++now, "Idea")
        val clip = record(note, "hum")
        engine.sync()

        server.detach(clip)
        assertNull(engine.sync().failure)

        assertEquals(emptyMap<String, String?>(), clipsOf(note.id))
        assertFalse(store.attachments.file(clip).exists())
    }

    @Test
    fun A_recording_whose_reply_was_lost_is_not_sent_twice() = runTest {
        val note = store.create(++now, "Idea")
        val clip = record(note, "hum")
        server.loseNextAttachReply = true
        engine.sync()

        assertNull(engine.sync().failure)

        assertEquals(mapOf(clip to "hum"), server.filesOf(note.id))
        assertEquals(emptyList<String>(), store.attachments.toUpload().map { it.id })
    }

    @Test
    fun A_recording_waits_offline_and_goes_out_with_the_next_sync() = runTest {
        val note = store.create(++now, "Idea")
        engine.sync()
        val clip = record(note, "hum")
        server.offline = true

        assertEquals(false, engine.sync().succeeded)
        server.offline = false
        assertNull(engine.sync().failure)

        assertEquals(mapOf(clip to "hum"), server.filesOf(note.id))
    }

    @Test
    fun A_note_deleted_forever_takes_its_recordings_with_it() = runTest {
        val note = store.create(++now, "Idea")
        val clip = record(note, "hum")
        engine.sync()

        store.deleteForever(note.id, ++now)
        assertNull(engine.sync().failure)

        assertEquals(emptyMap<String, ByteArray>(), server.files)
        assertFalse(store.attachments.file(clip).exists())
    }

    @Test
    fun A_note_deleted_on_the_server_takes_its_recordings_with_it_here() = runTest {
        val note = store.create(++now, "Idea")
        val clip = record(note, "hum")
        engine.sync()

        server.delete(note.id)
        assertNull(engine.sync().failure)

        assertNull(store.get(note.id))
        assertFalse(store.attachments.file(clip).exists())
    }

    @Test
    fun Recordings_go_to_another_account_with_their_notes() = runTest {
        val note = store.create(++now, "Idea")
        val clip = record(note, "hum")
        engine.sync()

        val other = FakeMemosServer().apply { user = "someone-else" }
        assertNull(SyncEngine(store, other, FakeMemosServer.ADDRESS) { now }.sync().failure)

        assertEquals(mapOf(clip to "hum"), other.filesOf(note.id))
    }

    @Test
    fun A_conflict_copy_leaves_the_recordings_on_the_note() = runTest {
        val note = store.create(++now, "Idea")
        val clip = record(note, "hum")
        engine.sync()
        store.save(store.get(note.id)!!.copy(title = "Mine"), ++now)
        server.put(note.id, "# Theirs")

        assertEquals(1, engine.sync().conflicts)

        assertEquals(listOf(clip), store.get(note.id)!!.recordings.map { it.id })
        assertEquals(listOf(clip), server.memos.getValue(note.id).attachments.map { it.id })
    }
}
