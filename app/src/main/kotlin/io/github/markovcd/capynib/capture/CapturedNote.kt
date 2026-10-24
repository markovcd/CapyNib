package io.github.markovcd.capynib.capture

import io.github.markovcd.capynib.core.note.Opening

/** A note a quick capture made, for the screen to open the way [opening] says. */
data class CapturedNote(val id: String, val opening: Opening = Opening.Type)
