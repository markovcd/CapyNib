package io.github.markovcd.capynib.audio

import android.media.MediaPlayer
import io.github.markovcd.capynib.core.note.Recording
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

/** [Playback] over [MediaPlayer], reading each recording's bytes from [fileOf]; [scope] runs on the main thread. */
class AndroidPlayback(private val scope: CoroutineScope, private val fileOf: (String) -> File) : Playback {
    private val state = MutableStateFlow<PlayingClip?>(null)
    private var player: MediaPlayer? = null
    private var ticker: Job? = null

    override val playing: StateFlow<PlayingClip?> = state.asStateFlow()

    override fun toggle(recording: Recording) {
        val wasPlaying = state.value?.id
        stop()
        if (wasPlaying == recording.id || !recording.downloaded) return
        val media = MediaPlayer()
        try {
            media.setDataSource(fileOf(recording.id).path)
            media.prepare()
        } catch (e: IOException) {
            media.release()
            return
        }
        media.setOnCompletionListener { stop() }
        media.start()
        player = media
        state.value = PlayingClip(recording.id, 0, media.duration.toLong())
        ticker = scope.launch {
            while (isActive) {
                delay(TICK_MILLIS)
                player?.let { state.value = PlayingClip(recording.id, it.currentPosition.toLong(), it.duration.toLong()) }
            }
        }
    }

    override fun stop() {
        ticker?.cancel()
        ticker = null
        player?.release()
        player = null
        state.value = null
    }

    private companion object {
        const val TICK_MILLIS = 100L
    }
}
