package io.github.markovcd.capynib.core.note

/**
 * Reads and edits checklist items inside a plain-text note body. Items are stored the way
 * memos stores task lists, `- [ ] milk` and `- [x] eggs`, one per line; every other line is text.
 * Indexes are line indexes of [parse].
 */
object Checklist {
    private val itemPattern = Regex("""^- \[([ xX])] ?(.*)$""")

    fun parse(body: String): List<ChecklistLine> =
        if (body.isEmpty()) {
            emptyList()
        } else {
            body.split('\n').map { line ->
                val match = itemPattern.matchEntire(line)
                if (match == null) {
                    ChecklistLine.Text(line)
                } else {
                    ChecklistLine.Item(match.groupValues[2], match.groupValues[1] != " ")
                }
            }
        }

    fun render(lines: List<ChecklistLine>): String =
        lines.joinToString("\n") { line ->
            when (line) {
                is ChecklistLine.Text -> line.text
                is ChecklistLine.Item -> (if (line.checked) "- [x] " else "- [ ] ") + line.text
            }
        }

    fun isChecklist(body: String): Boolean = parse(body).any { it is ChecklistLine.Item }

    /** Every non-blank line becomes an unchecked item; blank lines are dropped. */
    fun toChecklist(body: String): String {
        val items = parse(body)
            .filter { it is ChecklistLine.Item || it.text.isNotBlank() }
            .map { it as? ChecklistLine.Item ?: ChecklistLine.Item(it.text, checked = false) }
        return render(items.ifEmpty { listOf(ChecklistLine.Item("", checked = false)) })
    }

    /** Every item becomes a plain text line, checked or not. */
    fun toText(body: String): String =
        parse(body).joinToString("\n") { it.text }.let { if (it.isBlank()) "" else it }

    fun toggle(body: String, index: Int): String = edit(body, index) { line ->
        when (line) {
            is ChecklistLine.Item -> line.copy(checked = !line.checked)
            is ChecklistLine.Text -> line
        }
    }

    fun setText(body: String, index: Int, text: String): String = edit(body, index) { line ->
        when (line) {
            is ChecklistLine.Item -> line.copy(text = text)
            is ChecklistLine.Text -> line.copy(text = text)
        }
    }

    /** Inserts an unchecked item after [index] (or at the start for -1); returns the new body. */
    fun insertItemAfter(body: String, index: Int, text: String = ""): String {
        val lines = parse(body).toMutableList()
        require(index in -1..lines.lastIndex) { "line $index is outside 0..${lines.lastIndex}" }
        lines.add(index + 1, ChecklistLine.Item(text, checked = false))
        return render(lines)
    }

    fun remove(body: String, index: Int): String {
        val lines = parse(body).toMutableList()
        require(index in lines.indices) { "line $index is outside 0..${lines.lastIndex}" }
        lines.removeAt(index)
        return render(lines)
    }

    private fun edit(body: String, index: Int, change: (ChecklistLine) -> ChecklistLine): String {
        val lines = parse(body).toMutableList()
        require(index in lines.indices) { "line $index is outside 0..${lines.lastIndex}" }
        lines[index] = change(lines[index])
        return render(lines)
    }
}
