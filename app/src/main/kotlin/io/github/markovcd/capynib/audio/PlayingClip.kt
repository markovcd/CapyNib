package io.github.markovcd.capynib.audio

/** The recording [id] playing now, [positionMillis] into its [durationMillis]. */
data class PlayingClip(val id: String, val positionMillis: Long, val durationMillis: Long)
