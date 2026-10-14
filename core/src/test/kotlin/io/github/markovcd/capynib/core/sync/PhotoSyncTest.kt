package io.github.markovcd.capynib.core.sync

import io.github.markovcd.capynib.core.memos.FakeMemosServer
import io.github.markovcd.capynib.core.note.Note
import io.github.markovcd.capynib.core.note.Photo
import io.github.markovcd.capynib.core.store.TempStores
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PhotoSyncTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val stores by lazy { TempStores(folder) }
    private val server = FakeMemosServer()
    private var now = 100L
    private val store by lazy { stores.open() }
    private val engine by lazy { SyncEngine(store, server, FakeMemosServer.ADDRESS) { now } }

    /** Adds [image] to note [note] as a photo of [type], as the editor does. */
    private fun addPhoto(note: Note, image: String, type: String = Photo.JPEG): String {
        val photo = store.newPhoto(note.id, type, ++now)
        store.addPhoto(photo, folder.newFile().apply { writeText(image) })
        return photo.id
    }

    @Test
    fun A_photo_taken_offline_reaches_the_server_attached_to_its_memo() = runTest {
        val note = store.create(++now, "Trip")
        val photo = addPhoto(note, "jpeg")

        assertNull(engine.sync().failure)

        assertEquals(mapOf(photo to "jpeg"), server.filesOf(note.id))
        assertEquals("photo-$photo.jpg" to "image/jpeg", server.memos.getValue(note.id).attachments.single().let { it.fileName to it.type })
    }

    @Test
    fun A_note_that_holds_only_a_photo_is_sent() = runTest {
        val note = store.create(++now)
        val photo = addPhoto(note, "jpeg")

        assertFalse(store.get(note.id)!!.isBlank)
        assertNull(engine.sync().failure)

        assertEquals("", server.memos.getValue(note.id).state.content)
        assertEquals(mapOf(photo to "jpeg"), server.filesOf(note.id))
    }

    @Test
    fun An_image_attached_on_the_server_reaches_the_note_as_a_photo_and_is_downloaded() = runTest {
        server.put("web", "From the web")
        server.attach("img1", "web", "photo.png", "image/png", "png".encodeToByteArray())

        assertNull(engine.sync().failure)

        val photo = store.get("web")!!.photos.single()
        assertEquals(listOf("img1" to true), listOf(photo.id to photo.downloaded))
        assertEquals("png", store.readAttachment("img1")!!.decodeToString())
        assertEquals(emptyList<String>(), store.get("web")!!.recordings.map { it.id })
    }

    @Test
    fun A_photo_deleted_here_is_deleted_on_the_server() = runTest {
        val note = store.create(++now, "Trip")
        val photo = addPhoto(note, "jpeg")
        engine.sync()

        store.deleteAttachment(photo)
        assertNull(engine.sync().failure)

        assertEquals(emptyMap<String, String>(), server.filesOf(note.id))
    }

    @Test
    fun A_photo_deleted_on_the_server_is_deleted_here_with_its_image() = runTest {
        val note = store.create(++now, "Trip")
        val photo = addPhoto(note, "jpeg")
        engine.sync()

        server.detach(photo)
        assertNull(engine.sync().failure)

        assertEquals(emptyList<Photo>(), store.get(note.id)!!.photos)
        assertFalse(store.attachmentFile(photo).exists())
    }
}
