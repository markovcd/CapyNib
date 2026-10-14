package io.github.markovcd.capynib.ui.board

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import io.github.markovcd.capynib.AppGraph
import io.github.markovcd.capynib.TestGraphs
import io.github.markovcd.capynib.ui.CapynibRoot
import io.github.markovcd.capynib.ui.Tags
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The board's calendar: days marked by the notes made on them, and a picked day's notes as cards. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class BoardCalendarTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var graph: AppGraph

    private val zone = ZoneId.systemDefault()

    @Before
    fun start() {
        graph = TestGraphs.create()
        compose.setContent { CapynibRoot(graph) }
    }

    private fun awaitTag(tag: String) = compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    private fun awaitNoTag(tag: String) = compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() }

    // The dots sit inside the day's clickable circle, which merges them away in the default tree.
    private fun awaitMarks(date: LocalDate) =
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithTag(Tags.dayMarks(date), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    /** Waits for the card of note [id] in the picked day's list, scrolled into view below the month. */
    private fun awaitListed(id: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            runCatching { compose.onNodeWithTag(Tags.CALENDAR).performScrollToKey(id) }.isSuccess
        }
        awaitTag(Tags.card(id))
    }

    private fun today(): LocalDate = Instant.ofEpochMilli(graph.clock()).atZone(zone).toLocalDate()

    /** A note whose memo is dated noon on [date]. */
    private fun madeOn(date: LocalDate, title: String): String {
        val id = graph.store.create(graph.clock(), title = title).id
        graph.store.move(mapOf(id to date.atTime(12, 0).atZone(zone).toEpochSecond()))
        return id
    }

    private fun showCalendar() {
        compose.onNodeWithTag(Tags.CALENDAR_TOGGLE).performClick()
        awaitTag(Tags.CALENDAR)
    }

    @Test
    fun The_calendar_opens_on_today_and_lists_the_notes_made_today() {
        val earlier = today().withDayOfMonth(1).minusMonths(1)
        val now = graph.store.create(graph.clock(), title = "now").id
        val before = madeOn(earlier, "before")
        awaitTag(Tags.card(before))

        showCalendar()

        compose.onNodeWithTag(Tags.day(today())).assertIsSelected()
        awaitMarks(today())
        awaitListed(now)
        compose.onNodeWithTag(Tags.card(before)).assertDoesNotExist()
    }

    @Test
    fun Picking_a_day_in_another_month_lists_the_notes_made_that_day_and_a_tap_opens_one() {
        val earlier = today().withDayOfMonth(1).minusMonths(1)
        val now = graph.store.create(graph.clock(), title = "now").id
        val before = madeOn(earlier, "before")
        awaitTag(Tags.card(before))
        showCalendar()

        compose.onNodeWithTag(Tags.PREVIOUS_MONTH).performClick()
        awaitMarks(earlier)
        compose.onNodeWithTag(Tags.day(earlier)).performClick()

        awaitListed(before)
        awaitNoTag(Tags.card(now))
        compose.onNodeWithTag(Tags.card(before)).performClick()
        awaitTag(Tags.TITLE)
        compose.onNodeWithTag(Tags.TITLE).assertTextEquals("before")
    }

    @Test
    fun A_day_nothing_was_made_on_has_no_marks_and_says_so() {
        val later = today().withDayOfMonth(1).plusMonths(1)
        val now = graph.store.create(graph.clock(), title = "now").id
        awaitTag(Tags.card(now))
        showCalendar()

        compose.onNodeWithTag(Tags.NEXT_MONTH).performClick()
        awaitTag(Tags.day(later))
        compose.onNodeWithTag(Tags.day(later)).performClick()

        awaitTag(Tags.DAY_EMPTY)
        compose.onNodeWithTag(Tags.dayMarks(later), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun The_toggle_switches_back_to_the_cards() {
        val now = graph.store.create(graph.clock(), title = "now").id
        awaitTag(Tags.card(now))
        showCalendar()

        compose.onNodeWithTag(Tags.CALENDAR_TOGGLE).performClick()

        awaitNoTag(Tags.CALENDAR)
        awaitTag(Tags.BOARD)
    }
}
