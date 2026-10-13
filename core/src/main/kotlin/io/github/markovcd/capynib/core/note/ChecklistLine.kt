package io.github.markovcd.capynib.core.note

/** One line of a note body: free text, or a checklist item. */
sealed interface ChecklistLine {
    val text: String

    data class Text(override val text: String) : ChecklistLine

    data class Item(override val text: String, val checked: Boolean) : ChecklistLine
}
