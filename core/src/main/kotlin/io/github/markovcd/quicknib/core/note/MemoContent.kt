package io.github.markovcd.quicknib.core.note

/** The parts of a note that live in a memo's Markdown content. */
data class MemoContent(val title: String, val body: String, val color: NoteColor)
