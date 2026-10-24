package io.github.markovcd.capynib.ui

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick

/** How long a test waits for the screen to show what it expects before it fails. */
private const val WAIT_MILLIS = 5_000L

/** Waits for [condition]. The store answers on a background thread, so the screen catches up a moment after a change. */
fun ComposeTestRule.waitForStore(condition: () -> Boolean) = waitUntil(timeoutMillis = WAIT_MILLIS) { condition() }

fun ComposeTestRule.awaitTag(tag: String) = waitUntil(timeoutMillis = WAIT_MILLIS) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

fun ComposeTestRule.awaitNoTag(tag: String) = waitUntil(timeoutMillis = WAIT_MILLIS) { onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() }

fun ComposeTestRule.awaitText(text: String) = waitUntil(timeoutMillis = WAIT_MILLIS) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

/** Opens note [id] by tapping its card once the board shows it. */
fun ComposeTestRule.openNote(id: String) {
    awaitTag(Tags.card(id))
    onNodeWithTag(Tags.card(id)).performClick()
}
