package io.github.markovcd.capynib.audio

import java.io.File

/** A microphone that "records" [clip] into the file when stopped, or refuses to start when [broken]. */
class FakeMicrophone(var clip: String = "hum", var broken: Boolean = false) : Microphone {
    private var file: File? = null

    override fun start(file: File): Boolean {
        if (broken) return false
        this.file = file
        return true
    }

    override fun stop(): Boolean {
        val recording = file ?: return false
        file = null
        recording.writeText(clip)
        return true
    }
}
