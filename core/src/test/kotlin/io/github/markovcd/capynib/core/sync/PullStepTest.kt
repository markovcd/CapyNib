package io.github.markovcd.capynib.core.sync

import io.github.markovcd.capynib.core.memos.MemoState
import io.github.markovcd.capynib.core.note.Note
import io.github.markovcd.capynib.core.note.NoteColor
import io.github.markovcd.capynib.core.store.StoredNote
import org.junit.Assert.assertEquals
import org.junit.Test

class PullStepTest {
    private fun note(title: String, position: Long = 100) =
        Note("n", title, "", NoteColor.Default, pinned = false, archived = false, createdAt = 0, updatedAt = 0, position = position)

    /** The note as the server last had it, edited here to [now] when that differs. */
    private fun local(synced: Note?, now: Note = synced ?: note("new"), deleted: Boolean = false) = StoredNote(
        note = now,
        deleted = deleted,
        version = if (now == synced) 1 else 2,
        syncedVersion = 1,
        onServer = synced != null,
        synced = synced?.toMemoState(),
    )

    private fun step(local: StoredNote?, server: Note) = PullStep.of(local, server.toMemoState())

    private val was = note("was")

    @Test
    fun A_memo_with_no_note_of_its_id_becomes_a_new_note() {
        assertEquals(PullStep.Insert, step(null, was))
    }

    @Test
    fun A_memo_unchanged_since_the_last_sync_needs_nothing() {
        assertEquals(PullStep.InStep, step(local(was), was))
        assertEquals(PullStep.InStep, step(local(was, now = note("edited")), was))
    }

    @Test
    fun A_note_deleted_here_or_whose_create_reply_was_lost_claims_the_memo() {
        assertEquals(PullStep.Claim, step(local(was, deleted = true), note("changed there")))
        assertEquals(PullStep.Claim, step(local(synced = null, now = note("mine")), note("mine")))
    }

    @Test
    fun A_note_with_no_edits_here_takes_the_server_version() {
        assertEquals(PullStep.Take(version = 1, position = null), step(local(was), note("changed there")))
    }

    @Test
    fun A_note_edited_the_same_way_on_both_sides_takes_the_server_version() {
        assertEquals(PullStep.Take(version = 2, position = null), step(local(was, now = note("same")), note("same")))
    }

    @Test
    fun A_note_edited_here_and_moved_there_keeps_its_edit_at_the_server_place() {
        assertEquals(PullStep.Keep(position = 300), step(local(was, now = note("edited")), note("was", position = 300)))
    }

    @Test
    fun A_note_moved_on_both_sides_keeps_its_own_place() {
        assertEquals(PullStep.Keep(position = null), step(local(was, now = note("edited", position = 200)), note("was", position = 300)))
    }

    @Test
    fun A_note_moved_here_and_edited_there_takes_the_server_edit_at_its_own_place() {
        assertEquals(PullStep.Take(version = 2, position = 200), step(local(was, now = note("was", position = 200)), note("changed there")))
    }

    @Test
    fun A_note_edited_on_both_sides_is_a_conflict() {
        assertEquals(PullStep.Conflict, step(local(was, now = note("edited here")), note("edited there")))
    }
}
