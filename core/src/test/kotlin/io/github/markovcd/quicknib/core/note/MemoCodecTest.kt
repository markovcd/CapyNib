package io.github.markovcd.quicknib.core.note

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class MemoCodecTest {
    private fun roundTrip(content: MemoContent) = MemoCodec.decode(MemoCodec.encode(content))

    @Test
    fun A_titled_colored_note_is_written_as_a_heading_body_and_color_tag() {
        val written = MemoCodec.encode(MemoContent("Shopping", "- [ ] milk\n- [x] eggs", NoteColor.Sand))

        assertEquals("# Shopping\n\n- [ ] milk\n- [x] eggs\n\n#color/sand", written)
    }

    @Test
    fun A_plain_note_is_written_as_its_body_alone() {
        assertEquals("call mum", MemoCodec.encode(MemoContent("", "call mum", NoteColor.Default)))
    }

    @Test
    fun A_memo_written_in_the_memos_web_ui_reads_as_an_untitled_default_note() {
        assertEquals(
            MemoContent("", "Some thought\n#tag in the middle", NoteColor.Default),
            MemoCodec.decode("Some thought\n#tag in the middle"),
        )
    }

    @Test
    fun A_color_tag_with_no_blank_line_before_it_still_reads_as_the_color() {
        assertEquals(MemoContent("", "text", NoteColor.Mint), MemoCodec.decode("text\n#color/mint"))
    }

    @Test
    fun An_unknown_color_tag_stays_in_the_body() {
        assertEquals(MemoContent("", "text\n\n#color/neon", NoteColor.Default), MemoCodec.decode("text\n\n#color/neon"))
    }

    @Test
    fun Every_note_reads_back_as_the_same_note() {
        val cases = listOf(
            MemoContent("", "", NoteColor.Default),
            MemoContent("T", "", NoteColor.Default),
            MemoContent("", "", NoteColor.Coral),
            MemoContent("T", "", NoteColor.Coral),
            MemoContent("", "# not a title", NoteColor.Default),
            MemoContent("", "#color/sand", NoteColor.Default),
            MemoContent("", "#color/sand", NoteColor.Fog),
            MemoContent("", "\\escaped", NoteColor.Default),
            MemoContent("", "\\a\nmiddle\n\\b", NoteColor.Default),
            MemoContent("", "# first\n#color/mint", NoteColor.Default),
            MemoContent("Title", "# heading line in body", NoteColor.Default),
            MemoContent("", "\n", NoteColor.Default),
            MemoContent("", "\nlead and trail\n", NoteColor.Blossom),
            MemoContent("T", "\n\nx", NoteColor.Default),
            MemoContent("# hash title", "body", NoteColor.Default),
        )
        val failures = cases.filter { roundTrip(it) != it }.map { "$it -> ${roundTrip(it)}" }
        assertEquals("first failures: ${failures.take(3)}", 0, failures.size)
    }

    @Test
    fun Random_notes_read_back_as_the_same_notes() {
        val random = Random(42)
        val pieces = listOf("", " ", "#", "# ", "\\", "#color/", "#color/sand", "#color/default", "- [ ] ", "- [x] ", "a", "\n")
        fun text(maxPieces: Int) = (0 until random.nextInt(maxPieces)).joinToString("") { pieces.random(random) }
        val failures = (1..5000).mapNotNull {
            val content = MemoContent(
                title = text(4).replace("\n", ""),
                body = text(12),
                color = NoteColor.entries.random(random),
            )
            val back = roundTrip(content)
            if (back == content) null else "$content -> $back"
        }
        assertEquals("first failures: ${failures.take(3)}", 0, failures.size)
    }
}
