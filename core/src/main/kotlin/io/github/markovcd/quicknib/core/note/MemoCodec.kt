package io.github.markovcd.quicknib.core.note

/**
 * Writes a note's title, body and color as memo content, and reads them back.
 *
 * ```
 * # Title
 *
 * body lines, with - [ ] items
 *
 * #color/sand
 * ```
 *
 * The title is the memo's first-line H1 (memos shows it as the title too) and the color is a
 * trailing tag, so both survive editing in the memos web UI. A body line that would be misread as
 * either (or that starts with a backslash) is written with a leading backslash, which keeps
 * `decode(encode(x)) == x` for every note.
 */
object MemoCodec {
    private const val TITLE_PREFIX = "# "
    private const val COLOR_PREFIX = "#color/"
    private const val ESCAPE = '\\'

    fun encode(content: MemoContent): String {
        val hasTitle = content.title.isNotEmpty()
        val hasColor = content.color != NoteColor.Default
        val lines = if (content.body.isEmpty()) mutableListOf() else content.body.split('\n').toMutableList()
        lines.indices.forEach { i ->
            val first = i == 0 && !hasTitle
            val last = i == lines.lastIndex && !hasColor
            if ((first && needsEscapeAsFirst(lines[i])) || (last && needsEscapeAsLast(lines[i]))) {
                lines[i] = ESCAPE + lines[i]
            }
        }
        val parts = buildList {
            if (hasTitle) add(TITLE_PREFIX + content.title)
            if (lines.isNotEmpty()) add(lines.joinToString("\n"))
            if (hasColor) add(COLOR_PREFIX + content.color.key)
        }
        return parts.joinToString("\n\n")
    }

    fun decode(markdown: String): MemoContent {
        val lines = if (markdown.isEmpty()) mutableListOf() else markdown.split('\n').toMutableList()

        var title = ""
        if (lines.isNotEmpty() && lines.first().startsWith(TITLE_PREFIX)) {
            title = lines.removeAt(0).removePrefix(TITLE_PREFIX)
            if (lines.isNotEmpty() && lines.first().isEmpty()) lines.removeAt(0)
        }

        var color = NoteColor.Default
        val colorFromLast = lines.lastOrNull()?.let(::colorOf)
        if (colorFromLast != null) {
            color = colorFromLast
            lines.removeAt(lines.lastIndex)
            if (lines.isNotEmpty() && lines.last().isEmpty()) lines.removeAt(lines.lastIndex)
        }

        val unescapeFirst = title.isEmpty() && lines.firstOrNull()?.startsWith(ESCAPE) == true
        if (unescapeFirst) lines[0] = lines[0].substring(1)
        val lastIsAlsoFirst = lines.size == 1 && unescapeFirst
        if (!lastIsAlsoFirst && color == NoteColor.Default && lines.lastOrNull()?.startsWith(ESCAPE) == true) {
            lines[lines.lastIndex] = lines.last().substring(1)
        }

        return MemoContent(title, lines.joinToString("\n"), color)
    }

    private fun needsEscapeAsFirst(line: String) = line.startsWith(TITLE_PREFIX) || line.startsWith(ESCAPE)

    private fun needsEscapeAsLast(line: String) = colorOf(line) != null || line.startsWith(ESCAPE)

    private fun colorOf(line: String): NoteColor? =
        if (line.startsWith(COLOR_PREFIX)) {
            NoteColor.parseKey(line.removePrefix(COLOR_PREFIX))?.takeUnless { it == NoteColor.Default }
        } else {
            null
        }
}
