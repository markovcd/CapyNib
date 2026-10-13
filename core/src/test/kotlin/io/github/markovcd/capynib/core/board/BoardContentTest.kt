package io.github.markovcd.capynib.core.board

import io.github.markovcd.capynib.core.note.Note
import io.github.markovcd.capynib.core.note.NoteColor
import org.junit.Assert.assertEquals
import org.junit.Test

class BoardContentTest {
    private fun note(
        id: String,
        title: String = id,
        body: String = "",
        pinned: Boolean = false,
        archived: Boolean = false,
        trashedAt: Long? = null,
        position: Long = 0,
    ) = Note(id, title, body, NoteColor.Default, pinned, archived, 0, 0, position, trashedAt)

    /** The board of notes [ids] at [positions], top first, and the order after [BoardContent.move] of [id] to [to]. */
    private fun moved(positions: List<Long>, id: String, to: Int): Pair<List<String>, Map<String, Long>> {
        val board = positions.mapIndexed { i, position -> note("n$i", position = position) }
        val changes = BoardContent.of(board).move(id, to)
        val after = board.map { it.copy(position = changes[it.id] ?: it.position) }.sortedByDescending { it.position }
        return after.map { it.id } to changes
    }

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

    @Test
    fun A_note_moved_into_a_gap_takes_a_position_in_it_and_nothing_else_moves() {
        val (order, changes) = moved(listOf(30, 20, 10), "n2", 1)

        assertEquals(listOf("n0", "n2", "n1"), order)
        assertEquals(setOf("n2"), changes.keys)
    }

    @Test
    fun A_note_moved_between_neighbors_with_no_room_pushes_down_only_those_in_the_way() {
        val (order, changes) = moved(listOf(5, 4, 3, 2, 0), "n0", 2)

        assertEquals(listOf("n1", "n2", "n0", "n3", "n4"), order)
        assertEquals(setOf("n0", "n3"), changes.keys)
    }

    @Test
    fun A_note_moved_to_the_top_or_bottom_goes_past_the_end_note() {
        assertEquals(listOf("n2", "n0", "n1"), moved(listOf(3, 2, 1), "n2", 0).first)
        assertEquals(listOf("n1", "n2", "n0"), moved(listOf(3, 2, 1), "n0", 2).first)
    }

    @Test
    fun Notes_sharing_a_position_still_end_in_the_order_asked_for() {
        assertEquals(listOf("n1", "n0", "n2"), moved(listOf(7, 7, 7), "n0", 1).first)
    }

    @Test
    fun A_note_moves_only_within_its_own_section() {
        val board = BoardContent.of(listOf(note("pin", pinned = true, position = 1), note("a", position = 3), note("b", position = 2)))

        assertEquals(mapOf("b" to 4L), board.move("b", 0))
        assertEquals(emptyMap<String, Long>(), board.move("pin", 5))
        assertEquals(emptyMap<String, Long>(), board.move("missing", 0))
    }
}
