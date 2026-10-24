package io.github.markovcd.capynib.ui

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.markovcd.capynib.AppGraph
import io.github.markovcd.capynib.TestGraphs
import io.github.markovcd.capynib.capture.CapturedNote
import io.github.markovcd.capynib.core.note.Capture
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Quick captures handed to the app open as notes, whatever screen is showing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class CapynibRootTest {
    @get:Rule
    val compose = createComposeRule()

    /** Notes made by quick captures, as the activity hands them to the screen. */
    private val captured = Channel<CapturedNote>(Channel.UNLIMITED)

    private lateinit var graph: AppGraph

    @Before
    fun start() {
        graph = TestGraphs.create()
        compose.setContent { CapynibRoot(graph, captured.receiveAsFlow()) }
    }

    @Test
    fun A_shared_link_opens_as_a_note_titled_with_the_page_ready_to_type() {
        captured.trySend(CapturedNote(graph.capture(Capture.shared("https://example.com/capybaras", "Capybaras")!!)))

        compose.awaitTag(Tags.BODY)
        compose.onNodeWithTag(Tags.TITLE).assertTextEquals("Capybaras")
        compose.onNodeWithTag(Tags.BODY).assertTextEquals("https://example.com/capybaras").assertIsFocused()
    }

    @Test
    fun A_capture_while_settings_are_open_opens_the_new_note() {
        compose.onNodeWithTag(Tags.SETTINGS).performClick()
        compose.awaitTag(Tags.SERVER)

        captured.trySend(CapturedNote(graph.capture(Capture.EmptyChecklist)))

        compose.awaitTag(Tags.item(0))
        compose.onNodeWithTag(Tags.item(0)).assertIsFocused()
    }
}
