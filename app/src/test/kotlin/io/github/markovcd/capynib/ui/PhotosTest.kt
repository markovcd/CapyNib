package io.github.markovcd.capynib.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.github.markovcd.capynib.AppGraph
import io.github.markovcd.capynib.TestGraphs
import io.github.markovcd.capynib.capture.CapturedNote
import io.github.markovcd.capynib.core.note.Capture
import io.github.markovcd.capynib.core.note.Note
import io.github.markovcd.capynib.core.note.Photo
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream

/** Taking and choosing photos into notes, viewing them, and the camera widget's note, over a real store and a stand-in camera app. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PhotosTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var graph: AppGraph
    private val captured = Channel<CapturedNote>(Channel.UNLIMITED)

    @Before
    fun start() {
        graph = TestGraphs.create()
        compose.setContent { CapynibRoot(graph, captured.receiveAsFlow()) }
    }

    /** Waits for [tag] inside a card, which merges what it holds into one node. */
    private fun awaitInCard(tag: String) =
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    private fun photosFolder() = File(compose.activity.cacheDir, "photos")

    /**
     * Plays the camera app the editor started: writes [image] into the file it was lent and says it took
     * a photo, or, for null, says it took none.
     */
    private fun answerCamera(image: String?) {
        val started = shadowOf(compose.activity).nextStartedActivityForResult
        assertEquals(MediaStore.ACTION_IMAGE_CAPTURE, started.intent.action)
        val lent = started.intent.getParcelableExtra(MediaStore.EXTRA_OUTPUT, Uri::class.java)!!
        if (image != null) File(photosFolder(), lent.lastPathSegment!!).writeText(image)
        shadowOf(compose.activity).receiveResult(started.intent, if (image != null) Activity.RESULT_OK else Activity.RESULT_CANCELED, null)
    }

    private fun openNewNote() {
        compose.onNodeWithTag(Tags.NEW_NOTE).performClick()
        compose.awaitTag(Tags.ADD_PHOTO)
    }

    private fun takePhoto(image: String?) {
        compose.onNodeWithTag(Tags.ADD_PHOTO).performClick()
        compose.awaitTag(Tags.TAKE_PHOTO)
        compose.onNodeWithTag(Tags.TAKE_PHOTO).performClick()
        answerCamera(image)
    }

    private fun onlyPhoto(): Photo = graph.store.visible().single().photos.single()

    @Test
    fun A_taken_photo_is_kept_at_the_top_of_the_note_and_on_its_card() {
        openNewNote()

        takePhoto("jpeg")

        compose.waitForStore { graph.store.visible().singleOrNull()?.photos?.size == 1 }
        val photo = onlyPhoto()
        assertEquals(Photo.JPEG, photo.type)
        assertEquals("jpeg", graph.store.attachments.read(photo.id)!!.decodeToString())
        compose.awaitTag(Tags.photo(photo.id))
        compose.onNodeWithTag(Tags.BACK).performClick()
        compose.awaitTag(Tags.card(photo.noteId))
        awaitInCard(Tags.photo(photo.id))
    }

    @Test
    fun A_photo_the_camera_did_not_take_leaves_nothing_behind() {
        openNewNote()
        compose.onNodeWithTag(Tags.TITLE).performTextInput("Trip")

        takePhoto(null)

        compose.waitForIdle()
        compose.waitForStore { graph.store.visible().singleOrNull()?.title == "Trip" }
        assertEquals(emptyList<Photo>(), graph.store.visible().single().photos)
        assertEquals(emptyList<String>(), photosFolder().list().orEmpty().toList())
    }

    @Test
    fun A_chosen_image_is_copied_into_the_note() {
        openNewNote()
        val image = File(compose.activity.filesDir, "chosen.jpg").apply { writeText("picked") }

        compose.onNodeWithTag(Tags.ADD_PHOTO).performClick()
        compose.awaitTag(Tags.CHOOSE_PHOTO)
        compose.onNodeWithTag(Tags.CHOOSE_PHOTO).performClick()
        val started = shadowOf(compose.activity).nextStartedActivityForResult
        shadowOf(compose.activity).receiveResult(started.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(image)))

        compose.waitForStore { graph.store.visible().singleOrNull()?.photos?.size == 1 }
        assertEquals("picked", graph.store.attachments.read(onlyPhoto().id)!!.decodeToString())
        assertEquals("picked", image.readText())
    }

    @Test
    fun Tapping_a_photo_shows_it_full_screen_where_it_can_be_removed() {
        openNewNote()
        compose.onNodeWithTag(Tags.TITLE).performTextInput("Trip")
        takePhoto("jpeg")
        compose.waitForStore { graph.store.visible().singleOrNull()?.photos?.size == 1 }
        val photo = onlyPhoto()
        compose.awaitTag(Tags.photo(photo.id))

        compose.onNodeWithTag(Tags.photo(photo.id)).performClick()
        compose.awaitTag(Tags.viewedPhoto(photo.id))
        compose.onNodeWithTag(Tags.REMOVE_PHOTO).performClick()

        compose.awaitNoTag(Tags.PHOTO_VIEWER)
        compose.waitForStore { graph.store.visible().single().photos.isEmpty() }
        compose.onNodeWithTag(Tags.TITLE).assertExists()
    }

    @Test
    fun The_camera_app_is_lent_a_photo_file_through_the_apps_file_provider() {
        val real = AppGraph(compose.activity.application, databaseName = null, cipher = TestGraphs.ReversingCipher)
        val file = real.newPhotoFile()

        val lent = real.photoUri(file)

        assertEquals("content" to "${compose.activity.packageName}.photos", lent.scheme to lent.authority)
        assertEquals(file.name, lent.lastPathSegment)
    }

    @Test
    fun The_camera_widget_opens_the_camera_and_keeps_the_photo_as_a_new_note() {
        captured.trySend(CapturedNote(graph.capture(Capture.EmptyPhoto), photo = true))
        compose.waitForIdle()

        answerCamera("jpeg")

        compose.waitForStore { graph.store.visible().singleOrNull()?.photos?.size == 1 }
        compose.awaitTag(Tags.photo(onlyPhoto().id))
        compose.onNodeWithTag(Tags.ADD_PHOTO).assertExists()
    }

    @Test
    fun A_camera_widget_note_closed_without_a_photo_is_thrown_away() {
        captured.trySend(CapturedNote(graph.capture(Capture.EmptyPhoto), photo = true))
        compose.waitForIdle()

        answerCamera(null)

        compose.awaitTag(Tags.NEW_NOTE)
        compose.waitForStore { graph.store.visible().isEmpty() }
    }

    /** An image another app holds at [uri], as its content provider would hand it over. */
    private fun heldByAnotherApp(uri: Uri, image: String): Uri {
        shadowOf(graph.resolver).registerInputStream(uri, image.byteInputStream())
        return uri
    }

    @Test
    fun Images_shared_from_another_app_open_as_a_new_note_holding_copies_of_them() {
        val shared = listOf(
            heldByAnotherApp(Uri.parse("content://com.example.gallery/images/1"), "first"),
            heldByAnotherApp(Uri.parse("content://com.example.gallery/images/2"), "second"),
        )

        val id = runBlocking { graph.capture(Capture(body = "Zoo"), shared) }!!
        captured.trySend(CapturedNote(id))

        val note = graph.store.visible().single()
        assertEquals("Zoo", note.body)
        assertEquals(listOf("first", "second"), note.photos.map { graph.store.attachments.read(it.id)!!.decodeToString() })
        compose.awaitTag(Tags.photo(note.photos.first().id))
    }

    @Test
    fun A_shared_image_that_cannot_be_read_makes_no_note() {
        val unreadable = Uri.parse("content://com.example.gallery/images/gone")
        shadowOf(graph.resolver).registerInputStream(
            unreadable,
            object : InputStream() {
                override fun read(): Int = throw FileNotFoundException("gone")
            },
        )

        assertNull(runBlocking { graph.capture(Capture.EmptyNote, listOf(unreadable)) })

        assertEquals(emptyList<Note>(), graph.store.visible())
        assertEquals(emptyList<String>(), photosFolder().list().orEmpty().toList())
    }
}
