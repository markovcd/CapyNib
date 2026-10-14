package io.github.markovcd.capynib.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.github.markovcd.capynib.AppGraph
import io.github.markovcd.capynib.TestGraphs
import io.github.markovcd.capynib.audio.FakeMicrophone
import io.github.markovcd.capynib.audio.FakePlayback
import io.github.markovcd.capynib.capture.CapturedNote
import io.github.markovcd.capynib.core.note.Capture
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
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
    private val restoration by lazy { StateRestorationTester(compose) }

    /** Notes made by quick captures, as the activity hands them to the screen. */
    private val captured = Channel<CapturedNote>(Channel.UNLIMITED)

    @Before
    fun start() {
        graph = TestGraphs.create(microphone = microphone, playback = playback)
        restoration.setContent { CapynibRoot(graph, captured.receiveAsFlow()) }
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
        assertEquals("hum", graph.store.readAttachment(recording.id)!!.decodeToString())
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
    fun An_audio_note_capture_opens_a_new_note_already_recording_and_leaving_keeps_the_clip() {
        allowMicrophone()

        captured.trySend(CapturedNote(graph.capture(Capture.EmptyRecording), record = true))

        awaitTag(Tags.STOP_RECORDING)
        compose.onNodeWithTag(Tags.BACK).performClick()
        waitForStore { graph.store.visible().singleOrNull()?.recordings?.size == 1 }
    }

    @Test
    fun Deleting_a_note_while_it_records_stops_the_microphone_and_keeps_the_clip_in_the_note() {
        allowMicrophone()
        val id = graph.capture(Capture(body = "Shopping"))
        captured.trySend(CapturedNote(id, record = true))
        awaitTag(Tags.STOP_RECORDING)

        compose.onNodeWithTag(Tags.DELETE).performClick()

        awaitTag(Tags.NEW_NOTE)
        assertEquals(false, microphone.recording)
        waitForStore { graph.store.get(id)?.let { it.inTrash && it.recordings.size == 1 } == true }
    }

    @Test
    fun Turning_the_phone_while_a_note_records_keeps_recording() {
        allowMicrophone()
        captured.trySend(CapturedNote(graph.capture(Capture.EmptyRecording), record = true))
        awaitTag(Tags.STOP_RECORDING)

        compose.activityRule.scenario.recreate()

        assertEquals(true, microphone.recording)
    }

    @Test
    fun A_capture_arriving_while_a_note_records_keeps_the_clip_in_that_note() {
        allowMicrophone()
        val recorded = graph.capture(Capture.EmptyRecording)
        captured.trySend(CapturedNote(recorded, record = true))
        awaitTag(Tags.STOP_RECORDING)

        captured.trySend(CapturedNote(graph.capture(Capture.EmptyNote), record = false))

        awaitTag(Tags.RECORD)
        waitForStore { graph.store.get(recorded)?.recordings?.size == 1 }
        assertEquals(false, microphone.recording)
    }

    @Test
    fun A_note_opened_recording_records_once() {
        allowMicrophone()
        captured.trySend(CapturedNote(graph.capture(Capture.EmptyRecording), record = true))
        awaitTag(Tags.STOP_RECORDING)
        compose.onNodeWithTag(Tags.STOP_RECORDING).performClick()
        waitForStore { graph.store.visible().singleOrNull()?.recordings?.size == 1 }

        restoration.emulateSavedInstanceStateRestore()

        awaitTag(Tags.RECORD)
        assertEquals(0, compose.onAllNodesWithTag(Tags.STOP_RECORDING).fetchSemanticsNodes().size)
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
