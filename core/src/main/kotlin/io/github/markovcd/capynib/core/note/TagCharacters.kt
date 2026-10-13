package io.github.markovcd.capynib.core.note

/**
 * The character classes memos' tag lexer reads, loaded from `memos-tag-data.txt` (written by
 * `scripts/memos-tag-data.py` from the memos and goldmark sources). Java's own Unicode tables are
 * a different Unicode version on every JDK and Android release, so these are carried instead.
 */
internal object TagCharacters {
    private val sections: Map<String, List<String>> by lazy { load() }
    private val xidContinue by lazy { ranges("xid-continue") }
    private val defaultIgnorable by lazy { ranges("default-ignorable") }
    private val combiningMarks by lazy { ranges("combining-marks") }
    private val emoji: Set<String> by lazy {
        section("emoji").mapTo(HashSet()) { line ->
            line.split(' ').joinToString("") { String(Character.toChars(it.toInt(16))) }
        }
    }
    private val longestEmoji by lazy { emoji.maxOf { it.toByteArray(Charsets.UTF_8).size } }
    private val entities: Set<String> by lazy { section("entities").toHashSet() }

    fun isXidContinue(codePoint: Int) = xidContinue.contains(codePoint)

    fun isDefaultIgnorable(codePoint: Int) = defaultIgnorable.contains(codePoint)

    fun isCombiningMark(codePoint: Int) = combiningMarks.contains(codePoint)

    fun isEntity(name: String) = name in entities

    /** The length in chars of the longest fully qualified emoji starting at [from], or 0. */
    fun emojiLength(text: String, from: Int): Int {
        var match = 0
        var bytes = 0
        var end = from
        while (end < text.length) {
            val codePoint = text.codePointAt(end)
            bytes += utf8Length(codePoint)
            if (bytes > longestEmoji) break
            end += Character.charCount(codePoint)
            if (text.substring(from, end) in emoji) match = end - from
        }
        return match
    }

    private fun utf8Length(codePoint: Int) = when {
        codePoint < 0x80 -> 1
        codePoint < 0x800 -> 2
        codePoint < 0x10000 -> 3
        else -> 4
    }

    private fun section(name: String) = checkNotNull(sections[name]) { "memos-tag-data.txt has no [$name] section" }

    private fun ranges(name: String): CodePointRanges {
        val rows = section(name)
        val lows = IntArray(rows.size)
        val highs = IntArray(rows.size)
        rows.forEachIndexed { i, row ->
            lows[i] = row.substringBefore('-').toInt(16)
            highs[i] = row.substringAfter('-').toInt(16)
        }
        return CodePointRanges(lows, highs)
    }

    private fun load(): Map<String, List<String>> {
        val stream = checkNotNull(TagCharacters::class.java.getResourceAsStream("memos-tag-data.txt")) { "memos-tag-data.txt is missing from the core jar" }
        val sections = mutableMapOf<String, MutableList<String>>()
        var current: MutableList<String>? = null
        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.filter { it.isNotEmpty() && !it.startsWith('#') }.forEach { line ->
                if (line.startsWith('[')) {
                    current = sections.getOrPut(line.removeSurrounding("[", "]")) { mutableListOf() }
                } else {
                    checkNotNull(current) { "memos-tag-data.txt: '$line' comes before any section" }.add(line)
                }
            }
        }
        return sections
    }
}
