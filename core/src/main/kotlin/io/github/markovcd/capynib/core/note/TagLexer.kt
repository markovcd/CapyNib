package io.github.markovcd.capynib.core.note

/**
 * Finds `#tag`s in a stretch of literal text the way memos v0.31 does (`markdown/parser/tag.go`),
 * so a note offline shows the tags its memo will have. Offsets are in chars.
 */
internal object TagLexer {
    private const val MAX_REFERENCE_NAME = 32

    fun find(text: String): List<TagMatch> {
        val matches = mutableListOf<TagMatch>()
        var pos = 0
        while (pos < text.length) {
            val emoji = TagCharacters.emojiLength(text, pos)
            val reference = referenceLength(text, pos)
            when {
                emoji > 0 -> pos += emoji
                reference > 0 -> pos += reference
                text[pos] == '\\' && pos + 1 < text.length && isAsciiPunctuation(text[pos + 1]) -> pos += 2
                text[pos] != '#' -> pos += Character.charCount(text.codePointAt(pos))
                else -> {
                    val identifier = identifier(text, pos + 1)
                    if (identifier == null) {
                        pos++
                    } else {
                        val end = pos + 1 + identifier.length
                        matches += TagMatch(pos, end, identifier.value)
                        pos = end
                    }
                }
            }
        }
        return matches
    }

    /** What a scan read: [length] chars of source spelling [value], which leaves out ignorable code points. */
    private class Scan(val length: Int, val value: String)

    private fun identifier(text: String, from: Int): Scan? {
        val first = segment(text, from) ?: return null
        var length = first.length
        val value = StringBuilder(first.value)
        while (from + length < text.length && text[from + length] == '/') {
            val next = segment(text, from + length + 1) ?: break
            value.append('/').append(next.value)
            length += 1 + next.length
        }
        return Scan(length, value.toString())
    }

    private fun segment(text: String, from: Int): Scan? {
        var pos = from
        val value = StringBuilder()
        var afterXid = false

        starter@ while (pos < text.length) {
            if (text[pos] == '&' && referenceLength(text, pos) > 0) return null
            val emoji = TagCharacters.emojiLength(text, pos)
            if (emoji > 0) {
                value.append(text, pos, pos + emoji)
                pos += emoji
                break@starter
            }
            val codePoint = text.codePointAt(pos)
            if (isLoneSurrogate(codePoint)) return null
            val size = Character.charCount(codePoint)
            when {
                TagCharacters.isDefaultIgnorable(codePoint) -> pos += size
                TagCharacters.isXidContinue(codePoint) && TagCharacters.isCombiningMark(codePoint) -> pos += size
                isSegmentStarter(codePoint) -> {
                    value.appendCodePoint(codePoint)
                    pos += size
                    afterXid = TagCharacters.isXidContinue(codePoint)
                    break@starter
                }
                else -> return null
            }
        }
        if (value.isEmpty()) return null

        rest@ while (pos < text.length) {
            if (text[pos] == '&' && referenceLength(text, pos) > 0) break@rest
            val emoji = TagCharacters.emojiLength(text, pos)
            if (emoji > 0) {
                value.append(text, pos, pos + emoji)
                pos += emoji
                afterXid = false
                continue@rest
            }
            val codePoint = text.codePointAt(pos)
            if (isLoneSurrogate(codePoint)) break@rest
            val size = Character.charCount(codePoint)
            when {
                TagCharacters.isDefaultIgnorable(codePoint) -> {
                    pos += size
                    afterXid = false
                }
                isApostrophe(codePoint) && afterXid && startsNonCombiningXid(text, pos + size) -> {
                    value.appendCodePoint(codePoint)
                    pos += size
                    afterXid = false
                }
                isSegmentContinuation(codePoint) -> {
                    value.appendCodePoint(codePoint)
                    pos += size
                    afterXid = TagCharacters.isXidContinue(codePoint)
                }
                else -> break@rest
            }
        }
        return Scan(pos - from, value.toString())
    }

    private fun isSegmentStarter(codePoint: Int) =
        isExtension(codePoint) || TagCharacters.isXidContinue(codePoint) && !TagCharacters.isCombiningMark(codePoint) && !TagCharacters.isDefaultIgnorable(codePoint)

    private fun isSegmentContinuation(codePoint: Int) =
        isExtension(codePoint) || TagCharacters.isXidContinue(codePoint) && !TagCharacters.isDefaultIgnorable(codePoint)

    private fun isExtension(codePoint: Int) = codePoint == '-'.code || codePoint == '+'.code || codePoint == '&'.code

    private fun isApostrophe(codePoint: Int) = codePoint == '\''.code || codePoint == 0x2019

    private fun startsNonCombiningXid(text: String, at: Int): Boolean {
        if (at >= text.length || TagCharacters.emojiLength(text, at) > 0) return false
        val codePoint = text.codePointAt(at)
        return !isLoneSurrogate(codePoint) && isSegmentStarter(codePoint) && !isExtension(codePoint)
    }

    /** The length of an HTML character reference (`&amp;`, `&#35;`, `&#x23;`) at [at], or 0. */
    private fun referenceLength(text: String, at: Int): Int {
        if (text.length - at < 3 || text[at] != '&') return 0
        if (text[at + 1] == '#') {
            var pos = at + 2
            val hex = pos < text.length && (text[pos] == 'x' || text[pos] == 'X')
            if (hex) pos++
            val maxDigits = if (hex) 6 else 7
            val start = pos
            while (pos < text.length && pos - start < maxDigits && isDigit(text[pos], hex)) pos++
            return if (pos > start && pos < text.length && text[pos] == ';') pos + 1 - at else 0
        }
        var pos = at + 1
        while (pos < text.length && pos - at <= MAX_REFERENCE_NAME && isAsciiAlphanumeric(text[pos])) pos++
        if (pos == at + 1 || pos >= text.length || text[pos] != ';') return 0
        return if (TagCharacters.isEntity(text.substring(at + 1, pos))) pos + 1 - at else 0
    }

    private fun isDigit(c: Char, hex: Boolean) = c in '0'..'9' || hex && (c in 'a'..'f' || c in 'A'..'F')

    private fun isAsciiAlphanumeric(c: Char) = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9'

    fun isAsciiPunctuation(c: Char) = c in '!'..'/' || c in ':'..'@' || c in '['..'`' || c in '{'..'~'

    private fun isLoneSurrogate(codePoint: Int) = codePoint in Char.MIN_SURROGATE.code..Char.MAX_SURROGATE.code
}
