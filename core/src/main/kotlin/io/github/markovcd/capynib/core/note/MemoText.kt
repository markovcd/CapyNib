package io.github.markovcd.capynib.core.note

/**
 * The stretches of a memo's Markdown that memos reads tags from: its literal text, leaving out code
 * (fenced, indented and inline), links and images, autolinks, written URLs and HTML tags.
 *
 * This is the Markdown memos parses cut down to what a note's plain text meets; math, underscore
 * emphasis, bare email addresses and HTML blocks are read as plain text (ADR 0007).
 */
internal object MemoText {
    private val fence = Regex("""^ {0,3}(`{3,}|~{3,})(.*)$""")
    private val listItem = Regex("""^ {0,3}([-+*]|\d{1,9}[.)])([ \t]|$)""")
    private val quoteMarker = Regex("""^ {0,3}>[ \t]?""")
    private val definition = Regex("""^ {0,3}\[((?:[^\]\\]|\\.)+)]:[ \t]*(<[^<>\n]*>|\S+)""")
    private val autolink = Regex("""<[A-Za-z][A-Za-z0-9+.-]{1,31}:[^<>\x00-\x20]*>|<[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*>""")
    private val htmlTag = Regex("""</?[A-Za-z][A-Za-z0-9-]*(?:\s[^<>]*)?/?>""")
    private val writtenUrl = Regex("""(?:https?://|www\.)(?:[\p{L}\p{N}_-]+\.)*[\p{L}\p{N}-]+\.[\p{L}\p{N}-]+[^<\x09-\x0d\p{Z}]*""")

    fun literalRuns(markdown: String): List<String> {
        val lines = markdown.split('\n')
        val labels = lines.mapNotNull { line -> definition.find(line)?.takeIf { destinationIsValid(it.groupValues[2]) }?.groupValues?.get(1)?.let(::normalizeLabel) }.toSet()
        return paragraphs(lines).flatMap { inline(it, labels) }
    }

    /** Groups the lines that hold inline text into paragraphs, dropping code blocks and link definitions. */
    private fun paragraphs(lines: List<String>): List<String> {
        val paragraphs = mutableListOf<String>()
        val open = mutableListOf<String>()
        fun close() {
            if (open.isNotEmpty()) paragraphs += open.joinToString("\n")
            open.clear()
        }

        var fenceOpener: String? = null
        var inList = false
        var afterBlank = true
        for (line in lines) {
            val content = unquote(line)
            val closing = fenceOpener
            if (closing != null) {
                val match = fence.matchEntire(content)
                if (match != null && match.groupValues[1].startsWith(closing) && match.groupValues[1].all { it == closing[0] } && match.groupValues[2].isBlank()) {
                    fenceOpener = null
                }
                continue
            }
            val opening = fence.matchEntire(content)
            if (opening != null && !(opening.groupValues[1][0] == '`' && '`' in opening.groupValues[2])) {
                close()
                fenceOpener = opening.groupValues[1]
                continue
            }
            if (content.isBlank()) {
                close()
                afterBlank = true
                continue
            }
            if (listItem.containsMatchIn(content)) {
                inList = true
            } else if (afterBlank && !content[0].isWhitespace()) {
                inList = false
            }
            afterBlank = false
            when {
                open.isEmpty() && !inList && indentation(content) >= 4 -> Unit
                open.isEmpty() && definition.find(content)?.let { destinationIsValid(it.groupValues[2]) } == true -> Unit
                content.trimStart().startsWith('#') && isHeading(content.trimStart()) -> {
                    close()
                    paragraphs += content
                }
                else -> open += content
            }
        }
        close()
        return paragraphs
    }

    /** Splits a paragraph into literal runs around the inline constructs whose text holds no tags. */
    private fun inline(paragraph: String, labels: Set<String>): List<String> {
        val runs = mutableListOf<String>()
        val run = StringBuilder()
        fun cut(to: Int): Int {
            if (run.isNotEmpty()) runs += run.toString()
            run.clear()
            return to
        }

        var i = 0
        while (i < paragraph.length) {
            val c = paragraph[i]
            i = when {
                c == '\\' && i + 1 < paragraph.length && TagLexer.isAsciiPunctuation(paragraph[i + 1]) -> {
                    run.append(c).append(paragraph[i + 1])
                    i + 2
                }
                c == '`' -> {
                    val ticks = runLength(paragraph, i, '`')
                    val closer = codeSpanEnd(paragraph, i + ticks, ticks)
                    if (closer != null) {
                        cut(closer)
                    } else {
                        run.append(paragraph, i, i + ticks)
                        i + ticks
                    }
                }
                c == '<' -> {
                    val end = (autolink.matchAt(paragraph, i) ?: htmlTag.matchAt(paragraph, i))?.range?.last
                    if (end != null) {
                        cut(end + 1)
                    } else {
                        run.append(c)
                        i + 1
                    }
                }
                c == '[' || c == '!' && paragraph.startsWith("[", i + 1) -> {
                    val end = linkEnd(paragraph, if (c == '!') i + 1 else i, labels)
                    if (end != null) {
                        cut(end)
                    } else {
                        run.append(c)
                        i + 1
                    }
                }
                (c == 'h' || c == 'w') && isUrlBoundary(paragraph, i) -> {
                    val end = writtenUrl.matchAt(paragraph, i)?.takeIf { hasValidDomain(it.value) }?.range?.last
                    if (end != null) {
                        cut(end + 1)
                    } else {
                        run.append(c)
                        i + 1
                    }
                }
                else -> {
                    run.append(c)
                    i + 1
                }
            }
        }
        cut(paragraph.length)
        return runs
    }

    private fun runLength(text: String, from: Int, c: Char): Int {
        var end = from
        while (end < text.length && text[end] == c) end++
        return end - from
    }

    /** Where a code span opened by [ticks] backticks ends: after a run of exactly that many. */
    private fun codeSpanEnd(text: String, from: Int, ticks: Int): Int? {
        var i = from
        while (i < text.length) {
            if (text[i] == '`') {
                val length = runLength(text, i, '`')
                if (length == ticks) return i + length
                i += length
            } else {
                i++
            }
        }
        return null
    }

    /** Where a link or image whose text opens at [open] ends, or null when the brackets are not a link. */
    private fun linkEnd(text: String, open: Int, labels: Set<String>): Int? {
        val close = matching(text, open, '[', ']') ?: return null
        val linkText = text.substring(open + 1, close)
        val after = close + 1
        return when {
            text.startsWith("(", after) -> matching(text, after, '(', ')')?.plus(1)
            text.startsWith("[", after) -> {
                val labelEnd = matching(text, after, '[', ']') ?: return null
                val label = text.substring(after + 1, labelEnd).ifEmpty { linkText }
                (labelEnd + 1).takeIf { normalizeLabel(label) in labels }
            }
            else -> after.takeIf { normalizeLabel(linkText) in labels }
        }
    }

    private fun matching(text: String, open: Int, opener: Char, closer: Char): Int? {
        var depth = 0
        var i = open
        while (i < text.length) {
            when (text[i]) {
                '\\' -> i++
                opener -> depth++
                closer -> if (--depth == 0) return i
            }
            i++
        }
        return null
    }

    private fun isUrlBoundary(text: String, at: Int): Boolean {
        if (at == 0) return true
        val previous = text[at - 1]
        return previous == ' ' || previous in '\t'..'\r' || previous == '*' || previous == '_' || previous == '~' || previous == '('
    }

    /** memos links a written URL only when the last two parts of its domain have no underscore. */
    private fun hasValidDomain(url: String): Boolean {
        val start = when {
            url.startsWith("http://") -> 7
            url.startsWith("https://") -> 8
            else -> 4
        }
        var end = start
        while (end < url.length) {
            val codePoint = url.codePointAt(end)
            if (!Character.isLetter(codePoint) && !Character.isDigit(codePoint) && codePoint != '_'.code && codePoint != '-'.code && codePoint != '.'.code) break
            end += Character.charCount(codePoint)
        }
        val parts = url.substring(start, end).split('.')
        return parts.size >= 2 && parts.takeLast(2).none { '_' in it }
    }

    private fun destinationIsValid(destination: String): Boolean =
        destination.startsWith('<') || destination.count { it == '(' } == destination.count { it == ')' }

    private fun normalizeLabel(label: String) = label.trim().replace(Regex("""\s+"""), " ").lowercase()

    private fun isHeading(line: String): Boolean {
        val hashes = runLength(line, 0, '#')
        return hashes in 1..6 && (hashes == line.length || line[hashes] == ' ' || line[hashes] == '\t')
    }

    private fun indentation(line: String): Int {
        var columns = 0
        for (c in line) {
            columns = when (c) {
                ' ' -> columns + 1
                '\t' -> columns + 4 - columns % 4
                else -> return columns
            }
        }
        return columns
    }

    private fun unquote(line: String): String {
        var rest = line
        while (true) {
            val marker = quoteMarker.find(rest) ?: return rest
            rest = rest.substring(marker.range.last + 1)
        }
    }
}
