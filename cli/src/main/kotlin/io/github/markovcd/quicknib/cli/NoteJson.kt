package io.github.markovcd.quicknib.cli

import io.github.markovcd.quicknib.core.note.Checklist
import io.github.markovcd.quicknib.core.note.ChecklistLine
import io.github.markovcd.quicknib.core.note.Note
import kotlinx.serialization.Serializable

/** A note as `--json` prints it. `lines` is the body split into text lines and checklist items. */
@Serializable
data class NoteJson(
    val id: String,
    val title: String,
    val body: String,
    val color: String,
    val pinned: Boolean,
    val archived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val lines: List<LineJson>,
) {
    @Serializable
    data class LineJson(val text: String, val checked: Boolean? = null)

    companion object {
        fun of(note: Note) = NoteJson(
            id = note.id,
            title = note.title,
            body = note.body,
            color = note.color.key,
            pinned = note.pinned,
            archived = note.archived,
            createdAt = note.createdAt,
            updatedAt = note.updatedAt,
            lines = Checklist.parse(note.body).map { line ->
                when (line) {
                    is ChecklistLine.Text -> LineJson(line.text)
                    is ChecklistLine.Item -> LineJson(line.text, line.checked)
                }
            },
        )
    }
}
