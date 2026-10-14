package io.github.markovcd.capynib.capture

/**
 * A note a quick capture made, for the screen to open; with [record] it opens recording a clip, and
 * with [photo] it opens the camera.
 */
data class CapturedNote(val id: String, val record: Boolean = false, val photo: Boolean = false)
