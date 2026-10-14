package io.github.markovcd.capynib.ui.editor

import io.github.markovcd.capynib.audio.Microphone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Records a clip into the open note: [start] begins, [stop] hands the clip to [onRecorded] with
 * its length. [since] is when the clip in progress began, or null when nothing is recording.
 */
class Recorder(
    private val microphone: Microphone,
    private val newFile: () -> File,
    private val clock: () -> Long,
    private val onRecorded: (File, Long) -> Unit,
) {
    private val started = MutableStateFlow<Long?>(null)
    private var clip: File? = null

    val since: StateFlow<Long?> = started.asStateFlow()

    /** Starts a clip; false when the microphone would not start. */
    fun start(): Boolean {
        if (started.value != null) return true
        val file = newFile()
        if (!microphone.start(file)) {
            file.delete()
            return false
        }
        clip = file
        started.value = clock()
        return true
    }

    /** Ends the clip and keeps it, unless nothing playable was recorded. */
    fun stop() {
        val file = clip ?: return
        val began = started.value ?: return
        clip = null
        started.value = null
        if (microphone.stop() && file.length() > 0) onRecorded(file, clock() - began) else file.delete()
    }
}
