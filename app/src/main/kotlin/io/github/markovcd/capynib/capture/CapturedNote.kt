package io.github.markovcd.capynib.capture

/** A note a quick capture made, for the screen to open; with [record] it opens recording a clip. */
data class CapturedNote(val id: String, val record: Boolean = false)
