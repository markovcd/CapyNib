package io.github.markovcd.capynib.core.note

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptureTest {
    @Test
    fun A_shared_link_takes_the_page_title_as_its_title() {
        assertEquals(
            Capture(title = "Capybaras of the world", body = "https://example.com/capybaras"),
            Capture.shared("https://example.com/capybaras", "Capybaras of the world"),
        )
    }

    @Test
    fun Shared_text_without_a_subject_is_the_body() {
        assertEquals(Capture(body = "buy milk"), Capture.shared("  buy milk\n", null))
    }

    @Test
    fun A_subject_the_text_already_holds_is_not_repeated_as_the_title() {
        assertEquals(Capture(body = "Capybaras https://example.com"), Capture.shared("Capybaras https://example.com", "Capybaras"))
        assertEquals(Capture(body = "same"), Capture.shared("same", "same"))
    }

    @Test
    fun A_subject_over_several_lines_becomes_a_one_line_title() {
        assertEquals("Two lines", Capture.shared("body", "Two\n  lines")?.title)
    }

    @Test
    fun A_share_with_only_a_subject_makes_a_titled_note() {
        assertEquals(Capture(title = "Just a title"), Capture.shared(null, "Just a title"))
    }

    @Test
    fun A_share_with_nothing_in_it_captures_nothing() {
        assertNull(Capture.shared(null, null))
        assertNull(Capture.shared("  ", "\n"))
    }

    @Test
    fun A_captured_checklist_starts_with_one_empty_item() {
        assertEquals(true, Checklist.isChecklist(Capture.EmptyChecklist.body))
        assertEquals(listOf(ChecklistLine.Item("", checked = false)), Checklist.parse(Capture.EmptyChecklist.body))
    }
}
