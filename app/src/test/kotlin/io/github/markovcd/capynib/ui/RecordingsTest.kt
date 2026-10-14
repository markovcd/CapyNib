package io.github.markovcd.capynib.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.github.markovcd.capynib.AppGraph
import io.github.markovcd.capynib.TestGraphs
import io.github.markovcd.capynib.audio.FakeMicrophone
import io.github.markovcd.capynib.audio.FakePlayback
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Recording clips into notes and playing them back, over a real store and a fake microphone and speaker. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class RecordingsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val microphone = FakeMicrophone()
    private val playback = FakePlayback(durationMillis = 4_000, positionMillis = 1_000)
    private lateinit var graph: AppGraph

    @Before
    fun start() {
        graph = TestGraphs.create(microphone = microphone, playback = playback)
        compose.setContent { CapynibRoot(graph) }
    }

    private fun allowMicrophone() = shadowOf(compose.activity.application).grantPermissions(Manifest.permission.RECORD_AUDIO)

    private fun waitForStore(condition: () -> Boolean) = compose.waitUntil(timeoutMillis = 5_000) { condition() }

    private fun awaitTag(tag: String) = compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    private fun awaitNoTag(tag: String) = compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() }

    /** Opens a new note and records one clip into it; returns the note's id. */
    private fun recordIntoNewNote(): String {
        compose.onNodeWithTag(Tags.NEW_NOTE).performClick()
        awaitTag(Tags.RECORD)
        compose.onNodeWithTag(Tags.RECORD).performClick()
        awaitTag(Tags.STOP_RECORDING)
        compose.onNodeWithTag(Tags.STOP_RECORDING).performClick()
        waitForStore { graph.store.visible().singleOrNull()?.recordings?.size == 1 }
        return graph.store.visible().single().id
    }

    @Test
    fun A_recorded_clip_is_kept_in_the_note_with_its_sound() {
        allowMicrophone()

        val id = recordIntoNewNote()

        val recording = graph.store.get(id)!!.recordings.single()
        assertEquals("hum", graph.store.readRecording(recording.id)!!.decodeToString())
        awaitTag(Tags.recordingPlay(recording.id))
        compose.onNodeWithTag(Tags.RECORD).assertIsDisplayed()
    }

    @Test
    fun A_note_holding_only_a_recording_stays_on_the_board_with_a_player_on_its_card() {
        allowMicrophone()
        val id = recordIntoNewNote()
        val recording = graph.store.get(id)!!.recordings.single()

        compose.onNodeWithTag(Tags.BACK).performClick()

        awaitTag(Tags.card(id))
        awaitTag(Tags.recordingPlay(recording.id))
        assertEquals(listOf(id), graph.store.visible().map { it.id })
    }

    @Test
    fun A_cards_recording_plays_from_the_board_and_shows_how_far_along_it_is() {
        val note = graph.store.create(graph.clock(), title = "Voice")
        val recording = graph.store.newRecording(note.id, durationMillis = 7_000, now = graph.clock())
        graph.store.addRecording(recording, kotlin.io.path.createTempFile().toFile().apply { writeText("hum") })
        awaitTag(Tags.recordingPlay(recording.id))
        // The card merges its text into one node; the time is found inside it.
        val time = compose.onNodeWithTag(Tags.recordingTime(recording.id), useUnmergedTree = true)
        time.assertTextEquals("0:07")

        compose.onNodeWithTag(Tags.recordingPlay(recording.id)).performClick()

        assertEquals(recording.id, playback.playing.value?.id)
        time.assertTextEquals("0:01")
    }

    @Test
    fun A_removed_recording_leaves_the_note() {
        allowMicrophone()
        val id = recordIntoNewNote()
        compose.onNodeWithTag(Tags.TITLE).performTextInput("Kept")
        val recording = graph.store.get(id)!!.recordings.single()

        compose.onNodeWithTag(Tags.recordingRemove(recording.id)).performClick()

        awaitNoTag(Tags.recording(recording.id))
        waitForStore { graph.store.get(id)!!.recordings.isEmpty() }
    }

    @Test
    fun Recording_waits_for_the_microphone_to_be_allowed() {
        compose.onNodeWithTag(Tags.NEW_NOTE).performClick()
        awaitTag(Tags.RECORD)

        compose.onNodeWithTag(Tags.RECORD).performClick()

        val activity = shadowOf(compose.activity)
        assertEquals(Manifest.permission.RECORD_AUDIO, activity.lastRequestedPermission.requestedPermissions.single())
        assertEquals(0, compose.onAllNodesWithTag(Tags.STOP_RECORDING).fetchSemanticsNodes().size)

        allowMicrophone()
        @Suppress("DEPRECATION") // The activity result registry still hears a permission answer through this callback.
        compose.activity.onRequestPermissionsResult(
            activity.lastRequestedPermission.requestCode,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            intArrayOf(PackageManager.PERMISSION_GRANTED),
        )

        awaitTag(Tags.STOP_RECORDING)
    }

    @Test
    fun A_microphone_that_will_not_start_records_nothing() {
        allowMicrophone()
        microphone.broken = true
        compose.onNodeWithTag(Tags.NEW_NOTE).performClick()
        awaitTag(Tags.RECORD)

        compose.onNodeWithTag(Tags.RECORD).performClick()

        compose.waitForIdle()
        assertEquals(0, compose.onAllNodesWithTag(Tags.STOP_RECORDING).fetchSemanticsNodes().size)
        compose.onNodeWithTag(Tags.RECORD).assertIsDisplayed()
    }
}
