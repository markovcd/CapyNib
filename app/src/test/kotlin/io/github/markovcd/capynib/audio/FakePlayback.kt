package io.github.markovcd.capynib.audio

import io.github.markovcd.capynib.core.note.Recording
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A speaker that plays nothing but says what it would play: each clip [durationMillis] long, held at [positionMillis]. */
class FakePlayback(private val durationMillis: Long = 4_000, private val positionMillis: Long = 1_000) : Playback {
    private val state = MutableStateFlow<PlayingClip?>(null)

    override val playing: StateFlow<PlayingClip?> = state.asStateFlow()

    override fun toggle(recording: Recording) {
        state.value = if (state.value?.id == recording.id) null else PlayingClip(recording.id, positionMillis, durationMillis)
    }

    override fun stop() {
        state.value = null
    }
}
