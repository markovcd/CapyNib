package io.github.markovcd.capynib.core.board

import io.github.markovcd.capynib.core.note.Note
import io.github.markovcd.capynib.core.note.NoteColor
import org.junit.Assert.assertEquals
import org.junit.Test

class BoardContentTest {
    private fun note(id: String, title: String = id, body: String = "", pinned: Boolean = false, archived: Boolean = false, trashedAt: Long? = null) =
        Note(id, title, body, NoteColor.Default, pinned, archived, 0, 0, trashedAt)

    private fun BoardContent.ids() = pinned.map { it.id } to others.map { it.id }

    private val notes = listOf(
        note("pinned", pinned = true),
        note("plain", body = "Buy MILK"),
        note("archived", archived = true),
        note("blank", title = ""),
        note("trashed early", trashedAt = 1),
        note("trashed archived", archived = true, pinned = true, trashedAt = 2),
    )

    @Test
    fun Pinned_notes_get_their_own_section_and_archived_trashed_and_empty_notes_are_left_out() {
        assertEquals(listOf("pinned") to listOf("plain"), BoardContent.of(notes).ids())
    }

    @Test
    fun The_archive_shows_only_archived_notes() {
        assertEquals(emptyList<String>() to listOf("archived"), BoardContent.of(notes, shelf = Shelf.Archive).ids())
    }

    @Test
    fun The_trash_shows_only_trashed_notes_most_recently_deleted_first() {
        assertEquals(emptyList<String>() to listOf("trashed archived", "trashed early"), BoardContent.of(notes, shelf = Shelf.Trash).ids())
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

    private val tagged = listOf(
        note("trip", title = "Lisbon #travel/2026", pinned = true),
        note("socks", body = "- [ ] socks #packing #Travel"),
        note("milk", body = "milk #shopping"),
        note("old", body = "#archived-tag", archived = true),
    )

    @Test
    fun The_board_offers_every_tag_on_the_shelf_with_its_parents_sorted_ignoring_case() {
        assertEquals(listOf("packing", "shopping", "Travel", "travel", "travel/2026"), BoardContent.of(tagged).tags)
        assertEquals(listOf("archived-tag"), BoardContent.of(tagged, shelf = Shelf.Archive).tags)
    }

    @Test
    fun Picking_a_tag_shows_the_notes_that_have_it_or_a_tag_inside_it() {
        assertEquals(listOf("trip") to emptyList<String>(), BoardContent.of(tagged, tag = "travel").ids())
        assertEquals(emptyList<String>() to listOf("socks"), BoardContent.of(tagged, tag = "Travel").ids())
        assertEquals(emptyList<String>() to listOf("milk"), BoardContent.of(tagged, query = "milk", tag = "shopping").ids())
        assertEquals(emptyList<String>() to emptyList<String>(), BoardContent.of(tagged, query = "socks", tag = "shopping").ids())
    }

    @Test
    fun A_tag_no_note_on_the_shelf_has_narrows_nothing() {
        val content = BoardContent.of(tagged, shelf = Shelf.Archive, tag = "shopping")
        assertEquals(null, content.tag)
        assertEquals(emptyList<String>() to listOf("old"), content.ids())
    }
}
