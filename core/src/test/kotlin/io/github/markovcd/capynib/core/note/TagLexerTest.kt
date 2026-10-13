package io.github.markovcd.capynib.core.note

import org.junit.Assert.assertEquals
import org.junit.Test

/** The cases of memos v0.31's own lexer tests (`markdown/parser/tag_test.go`), which this lexer must agree with. */
class TagLexerTest {
    /** The first tag in [input] as (tag, source spelling, rest), or null when there is none. */
    private fun firstTag(input: String): Triple<String, String, String>? =
        TagLexer.find(input).firstOrNull()?.let { Triple(it.value, input.substring(it.start, it.end), input.substring(it.end)) }

    private fun check(cases: List<Pair<String, String?>>) {
        val wrong = cases.filter { (input, tag) -> firstTag(input)?.first != tag }
            .map { (input, tag) -> "'$input' gave ${firstTag(input)?.first}, memos gives $tag" }
        assertEquals("${wrong.size} of ${cases.size} disagree with memos", emptyList<String>(), wrong.take(5))
    }

    @Test
    fun A_tag_is_a_hash_and_a_word_of_letters_digits_hyphens_and_underscores() = check(
        listOf(
            "#tag" to "tag",
            "#work-notes" to "work-notes",
            "#science&tech" to "science&tech",
            "#2024_plans" to "2024_plans",
            "#123" to "123",
            "#tag " to "tag",
            "#tag." to "tag",
            "#important task" to "important",
            "## Heading" to null,
            "# heading" to null,
            "#" to null,
            "# " to null,
            "#tag@special" to "tag",
            "#WorkNotes" to "WorkNotes",
        ),
    )

    @Test
    fun Slashes_nest_tags_and_must_sit_between_two_parts() = check(
        listOf(
            "#tag1/subtag" to "tag1/subtag",
            "#tag1/subtag/subtag2" to "tag1/subtag/subtag2",
            "#work/notes " to "work/notes",
            "#project/2024." to "project/2024",
            "#work-log/2024/q1" to "work-log/2024/q1",
            "#book/" to "book",
            "#/book" to null,
            "#book//fiction" to "book",
            "#book/fiction/" to "book/fiction",
            "#foo/\u0301bar" to "foo/bar",
        ),
    )

    @Test
    fun Letters_of_any_script_and_fully_qualified_emoji_make_tags() = check(
        listOf(
            "#测试" to "测试",
            "#测试 some text" to "测试",
            "#测试。" to "测试",
            "#测试test123" to "测试test123",
            "#テスト" to "テスト",
            "#테스트" to "테스트",
            "#test🚀" to "test🚀",
            "#test👁️" to "test👁️",
            "#family👨‍👩‍👧‍👦" to "family👨‍👩‍👧‍👦",
            "#*\ufe0f\u20e3" to "*\ufe0f\u20e3",
            "#‼️" to "‼️",
            "#🫯" to "🫯",
            "#♥" to null,
            "#♥️" to "♥️",
            "#🏻" to null,
            "#\ufe0f\u20e3" to null,
            "##\ufe0f\u20e3" to "#\ufe0f\u20e3",
            "#first#\ufe0f\u20e3" to "first#\ufe0f\u20e3",
            "＃tag" to null,
            "﹟tag" to null,
            "#\u1c89" to "\u1c89",
            "#l·l" to "l·l",
            "#foo‿bar" to "foo‿bar",
            "#price€" to "price",
            "#€budget" to null,
            "#v²" to "v",
        ),
    )

    @Test
    fun Plus_hyphen_and_ampersand_extend_a_tag_but_a_character_reference_ends_it() = check(
        listOf(
            "#C++" to "C++",
            "#---" to "---",
            "#&&" to "&&",
            "#R&amp;D" to "R",
            "#R&D" to "R&D",
            "&#35;tag" to null,
            "&#x23;tag" to null,
            "#" + "a".repeat(101) to "a".repeat(101),
        ),
    )

    @Test
    fun An_apostrophe_joins_only_between_two_word_characters() = check(
        listOf(
            "#tag's" to "tag's",
            "#сім'я" to "сім'я",
            "#O’Brien" to "O’Brien",
            "#OʼBrien" to "OʼBrien",
            "#cafe\u0301's" to "cafe\u0301's",
            "#'tag" to null,
            "#users'" to "users",
            "#rock''roll" to "rock",
            "#O‘Brien" to "O",
            "#foo-'bar" to "foo-",
            "#foo'1\ufe0f\u20e3" to "foo",
            "#A\u200d'B" to "A",
        ),
    )

    @Test
    fun Invisible_joiners_and_leading_combining_marks_are_left_out_of_the_tag() = check(
        listOf(
            "#\u200dfoo" to "foo",
            "#A\u200dB" to "AB",
            "#A\u200cB" to "AB",
            "#A\ufe0fB" to "AB",
            "#\u200d\u200c" to null,
            "#\u0301foo" to "foo",
            "#\u0301" to null,
            "#cafe\u0301" to "cafe\u0301",
        ),
    )

    @Test
    fun A_tag_keeps_its_source_spelling_and_where_it_ends() {
        assertEquals(Triple("users", "#users", "'"), firstTag("#users'"))
        assertEquals(Triple("AB", "#A\u200dB", ""), firstTag("#A\u200dB"))
        assertEquals(Triple("A", "#A\u200d", "'B"), firstTag("#A\u200d'B"))
        assertEquals(Triple("R", "#R", "&amp;D"), firstTag("#R&amp;D"))
    }

    @Test
    fun Every_tag_in_a_line_is_found() {
        assertEquals(listOf("tag1", "tag2"), TagLexer.find("#tag1 #tag2").map { it.value })
    }
}
