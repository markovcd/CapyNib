package io.github.markovcd.capynib

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import io.github.markovcd.capynib.core.note.NoteColor
import io.github.markovcd.capynib.ui.CapynibRoot
import io.github.markovcd.capynib.ui.Tags
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the store listing's phone screenshots into the folder `capynib.screenshots` names:
 * `./gradlew :app:testDebugUnitTest --tests '*StoreScreenshotsTest' -Pcapynib.screenshots=true`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class StoreScreenshotsTest {
    @get:Rule
    val compose = createComposeRule()

    private val folder = System.getProperty("capynib.screenshots").orEmpty()
    private val graph = TestGraphs.create()

    @Test
    fun The_board() {
        shoot("1.png") { awaitTag(Tags.card(it)) }
    }

    @Test
    fun A_checklist() {
        shoot("2.png") {
            awaitTag(Tags.card(it))
            compose.onNodeWithTag(Tags.card(it)).performClick()
            awaitTag(Tags.TITLE)
        }
    }

    @Test
    fun The_board_at_night() {
        RuntimeEnvironment.setQualifiers("+night")
        shoot("3.png") { awaitTag(Tags.card(it)) }
    }

    /** Seeds the notes, shows the app, runs [show] with the checklist's id, and saves the screen. */
    private fun shoot(name: String, show: (String) -> Unit) {
        assumeTrue("skip when capynib.screenshots names no folder", folder.isNotEmpty())
        val checklist = seed()
        compose.setContent { CapynibRoot(graph, emptyFlow()) }
        show(checklist)
        compose.waitForIdle()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(folder).mkdirs()
        File(folder, name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun awaitTag(tag: String) =
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    /** A board someone has used for a week; returns the groceries checklist's id. */
    private fun seed(): String {
        var now = 1_760_000_000_000
        fun note(title: String, body: String, color: NoteColor, pinned: Boolean = false): String {
            now += 60_000
            val note = graph.store.create(now, title, body, color)
            if (pinned) graph.store.save(note.copy(pinned = true), now)
            return note.id
        }
        note("Book club", "Thursday 7pm at Ana's.\nBring the second half read! #friends", NoteColor.Peach)
        note("Bike", "- [x] New chain\n- [ ] Pump the tires\n- [ ] Fix the bell", NoteColor.Sage)
        note("", "The capybara is the largest living rodent. Very relaxed animals.", NoteColor.Default)
        note("Gift ideas", "Mum: a good teapot\nTom: climbing chalk bag #family", NoteColor.Blossom)
        note("Wi-Fi at the cabin", "network: lakeside\nask Jan for the password", NoteColor.Fog)
        note("Ideas", "A notes app with a capybara on the icon", NoteColor.Sand, pinned = true)
        return note(
            "Groceries",
            "- [ ] Oat milk\n- [ ] Tomatoes\n- [ ] Basil\n- [x] Bread\n- [x] Coffee beans",
            NoteColor.Mint,
            pinned = true,
        )
    }
}
