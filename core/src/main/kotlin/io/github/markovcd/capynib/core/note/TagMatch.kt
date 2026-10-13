package io.github.markovcd.capynib.core.note

/** One `#tag` in a stretch of text: [start] is the `#`, [end] is exclusive, [value] the tag without the `#`. */
internal data class TagMatch(val start: Int, val end: Int, val value: String)
