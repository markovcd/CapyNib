package io.github.markovcd.capynib.audio

import io.github.markovcd.capynib.core.note.Recording
import kotlinx.coroutines.flow.StateFlow

/** Plays one recording at a time through the phone's speaker. The device boundary: tests put a fake behind it. */
interface Playback {
    /** The recording playing, or null. */
    val playing: StateFlow<PlayingClip?>

    /** Plays [recording], or stops it when it is the one playing; whatever else was playing stops. */
    fun toggle(recording: Recording)

    fun stop()
}
