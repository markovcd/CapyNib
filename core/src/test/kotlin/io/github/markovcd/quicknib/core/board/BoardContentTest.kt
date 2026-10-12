package io.github.markovcd.quicknib.core.board

import io.github.markovcd.quicknib.core.note.Note
import io.github.markovcd.quicknib.core.note.NoteColor
import org.junit.Assert.assertEquals
import org.junit.Test

class BoardContentTest {
    private fun note(id: String, title: String = id, body: String = "", pinned: Boolean = false, archived: Boolean = false) =
        Note(id, title, body, NoteColor.Default, pinned, archived, 0, 0)

    private fun BoardContent.ids() = pinned.map { it.id } to others.map { it.id }

    private val notes = listOf(
        note("pinned", pinned = true),
        note("plain", body = "Buy MILK"),
        note("archived", archived = true),
        note("blank", title = ""),
    )

    @Test
    fun Pinned_notes_get_their_own_section_and_archived_and_empty_notes_are_left_out() {
        assertEquals(listOf("pinned") to listOf("plain"), BoardContent.of(notes).ids())
    }

    @Test
    fun The_archive_shows_only_archived_notes() {
        assertEquals(emptyList<String>() to listOf("archived"), BoardContent.of(notes, archive = true).ids())
    }

    @Test
    fun Search_matches_title_or_text_ignoring_case() {
        assertEquals(emptyList<String>() to listOf("plain"), BoardContent.of(notes, query = " milk").ids())
        assertEquals(listOf("pinned") to emptyList<String>(), BoardContent.of(notes, query = "PINN").ids())
    }

    @Test
    fun A_checklist_with_only_empty_items_counts_as_empty() {
        assertEquals(true, BoardContent.of(listOf(note("c", title = "", body = "- [ ] \n- [ ] "))).isEmpty)
    }
}
