package io.github.markovcd.capynib.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** memos-versions.txt, the memos releases the gate runs the live tests against. */
class MemosReleasesTest {
    private val releases = File("../memos-versions.txt").readLines().filter { it.isNotBlank() && !it.startsWith("#") }

    @Test
    fun Every_release_is_a_version_and_an_image_pinned_to_a_digest() {
        val found = releases.filterNot { Regex("""^\d+\.\d+\.\d+ neosmemo/memos:\S+@sha256:[0-9a-f]{64}$""").matches(it) }
        assertEquals("first: ${found.take(3)}", 0, found.size)
        assertEquals(true, releases.isNotEmpty())
    }
}
