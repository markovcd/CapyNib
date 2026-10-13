package io.github.markovcd.capynib.core.note

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteTagsTest {
    private fun note(title: String = "", body: String = "", color: NoteColor = NoteColor.Default) =
        Note("n", title, body, color, pinned = false, archived = false, createdAt = 0, updatedAt = 0, position = 0)

    /** The cases of memos v0.31's `TestExtractTags` and `TestExtractTagsMemosTagV1` (`markdown/markdown_test.go`). */
    private val memosCases = listOf(
        "Just plain text" to listOf(),
        "Text with #tag" to listOf("tag"),
        "Text with #tag1 and #tag2" to listOf("tag1", "tag2"),
        "#work is important. #Work #WORK" to listOf("work", "Work", "WORK"),
        "Tags: #work-notes #2024_plans" to listOf("work-notes", "2024_plans"),
        "This is important #urgent." to listOf("urgent"),
        "## Heading\n\n# Title\n\nText with #realtag" to listOf("realtag"),
        "Issue #123" to listOf("123"),
        "- Item 1 #todo\n- Item 2 #done" to listOf("todo", "done"),
        "https://github.com/dmtrKovalenko/fff#pi-agent-extension\n\nProject #memo-tag" to listOf("memo-tag"),
        "[release #notes](https://example.com/releases#release-notes) Outside #memo-tag" to listOf("memo-tag"),
        "[reference #anchor][docs]\n\n[docs]: https://example.com/docs#reference-anchor\n\nOutside #memo-tag" to listOf("memo-tag"),
        "![preview #image](https://example.com/image#preview)\n\nOutside #memo-tag" to listOf("memo-tag"),
        "Text #测试。 More text" to listOf("测试"),
        "#work/测试/项目" to listOf("work", "work/测试", "work/测试/项目"),
        "#first#second" to listOf("first", "second"),
        "##tag" to listOf("tag"),
        "## tag" to listOf(),
        "#\ufe0f\u20e3" to listOf(),
        "##\ufe0f\u20e3" to listOf("#\ufe0f\u20e3"),
        "#Work #work" to listOf("Work", "work"),
        "#café #cafe\u0301" to listOf("café", "cafe\u0301"),
        "'#tag' #users' #'missing #rock''roll #O‘Brien #A\u200d'B" to listOf("tag", "users", "rock", "O", "A"),
        "#AB #A\u200dB #\u0301AB" to listOf("AB"),
        "#book/fiction/history" to listOf("book", "book/fiction", "book/fiction/history"),
        "#book/fiction #book" to listOf("book", "book/fiction"),
        "#R&D" to listOf("R&D"),
        "#R&amp;D" to listOf("R"),
        "~#a!&&amp;)*" to listOf("a"),
        "#R&bogus;D #Q&amp;&bogus;D" to listOf("R&bogus", "Q"),
        "&#35;tag &#x23;other" to listOf(),
        "&num;tag" to listOf(),
        "\\#tag #foo\\+bar" to listOf("foo"),
        "`#inline`\n\n```text\n#fenced\n```\n\n    #indented" to listOf(),
        ">\t #tag" to listOf("tag"),
        "before  \n#tag" to listOf("tag"),
        "[#label](https://example.com/#destination) ![#alt](image#fragment)" to listOf(),
        "[#use][bad]\n\n[bad]:(" to listOf("use"),
        "[#hidden][valid]\n\n[valid]: path(foo)" to listOf(),
        "[#hidden][valid]\n\n[valid]: <(>" to listOf(),
        "<https://localhost/#local> <ftp://example.com/#ftp> <custom:foo#custom> <https://foo.example_/#underscore>" to listOf(),
        "https://example.com/path#url /path#plain" to listOf("plain"),
        "ftp://example.com/#ftp HTTP://example.com/#upper https://localhost/#local" to listOf("ftp", "upper", "local"),
        "https://example.COM/#hidden www.example.123/#also-hidden https://a_b.foo.example/路径#still-hidden https://foo_bar.example/#visible" to listOf("visible"),
        "https://點看.com/#hidden www.點看.com/#also-hidden https://a_b.點看.com/#still-hidden https://foo_bar.點看/#visible https://點看.com_/#suffix hellohttps://點看.com/#joined" to
            listOf("visible", "suffix", "joined"),
        "http://example.com#hidden http://example.com./#also-hidden https://foo.example_/#visible\n-\thttps://foo.example_/#padded\n\n(https://foo.example_/#parenthesized) *https://foo.example_/#emphasized*" to
            listOf("visible", "padded", "parenthesized", "emphasized"),
        "before\n\n\ufeffhttps://example.com/#shown" to listOf("shown"),
        "[text https://點看.com/#inside [more https://example.com/#ascii" to listOf(),
        "[ https://example.com/#hidden] [x https://example.com/#also-hidden] ![ https://example.com/#image-hidden]" to listOf(),
        "https://點看.com/\u00a0#nbsp https://點看.com/\u2003#em-space https://點看.com/\u000b#vertical-tab https://點看.com/\u2028#line-separator https://點看.com/\u2029#paragraph-separator https://點看.com/\u0085#nel-hidden" to
            listOf("nbsp", "em-space", "vertical-tab", "line-separator", "paragraph-separator"),
        "<span data-tag=\"#attribute\">#text</span>" to listOf("text"),
        "$20,000 #budget and $30,000" to listOf("budget"),
        "text $ #plain" to listOf("plain"),
        "```text\n$$\n```\n\n#plain" to listOf("plain"),
        "**#strong** ~~#strike~~" to listOf("strong", "strike"),
        "#_" to listOf("_"),
        "#foo_bar" to listOf("foo_bar"),
        "#_foo_" to listOf(),
        "\$#math$ #plain" to listOf("plain"),
        "foo#mail@example.com foo!#second@example.com <foo#hidden@example.com> foo@example.com/#after" to listOf("after"),
        "#foo/bar@example.com" to listOf("foo"),
        "[#label]:(" to listOf("label"),
        "\ufeffhttps://example.com/#hidden #shown" to listOf("shown"),
    )

    /**
     * memos cases this parser knowingly reads differently: what memos reads as math, underscore
     * emphasis or an email address, and a URL after a byte order mark, is plain text here (ADR 0007).
     */
    private val readDifferently = setOf(
        "#_foo_",
        "\$#math$ #plain",
        "foo#mail@example.com foo!#second@example.com <foo#hidden@example.com> foo@example.com/#after",
        "#foo/bar@example.com",
        "\ufeffhttps://example.com/#hidden #shown",
    )

    /** The cases that disagree with memos, among those expected to agree or, with [agreeing] false, to disagree. */
    private fun disagreements(agreeing: Boolean) = memosCases
        .filter { (markdown, _) -> (markdown in readDifferently) != agreeing }
        .filter { (markdown, tags) -> (NoteTags.inMemo(markdown) == tags) != agreeing }
        .map { (markdown, tags) -> "'$markdown' gave ${NoteTags.inMemo(markdown)}, memos gives $tags" }

    @Test
    fun Tags_are_the_ones_memos_lists_for_the_same_memo() {
        val wrong = disagreements(agreeing = true)
        assertEquals("${wrong.size} of ${memosCases.size} disagree with memos", emptyList<String>(), wrong.take(5))
    }

    @Test
    fun Every_case_listed_as_read_differently_still_is() {
        assertEquals("these agree with memos now; take them off the list", emptyList<String>(), disagreements(agreeing = false))
    }

    @Test
    fun A_note_has_the_tags_in_its_title_and_body_in_order_each_once() {
        assertEquals(listOf("trip", "packing", "kids"), NoteTags.of(note("Holiday #trip", "- [ ] socks #packing\n- [x] toys #kids #packing")))
    }

    @Test
    fun The_color_is_not_one_of_a_notes_tags() {
        assertEquals(listOf("work"), NoteTags.of(note(body = "#work", color = NoteColor.Sand)))
    }

    @Test
    fun A_note_written_with_a_nested_tag_shows_it_once_but_belongs_to_its_parents_too() {
        val note = note(body = "#books/fiction")
        assertEquals(listOf("books/fiction"), NoteTags.of(note))
        assertTrue(NoteTags.has(note, "books"))
        assertTrue(NoteTags.has(note, "books/fiction"))
        assertFalse(NoteTags.has(note, "book"))
        assertFalse(NoteTags.has(note, "fiction"))
    }

    @Test
    fun A_body_line_written_like_a_title_is_text_and_a_color_written_in_the_body_is_a_tag() {
        assertEquals(listOf<String>(), NoteTags.of(note(body = "# not a heading here")))
        assertEquals(listOf("color/sand"), NoteTags.of(note(body = "#color/sand\nmore")))
    }
}
