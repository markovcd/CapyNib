package io.github.markovcd.capynib.audio

import java.io.File

/** The phone's microphone, recording one clip at a time. The device boundary: tests put a fake behind it. */
interface Microphone {
    /** Starts recording into [file]; false when the microphone would not start. */
    fun start(file: File): Boolean

    /** Stops recording; false when nothing playable was recorded, such as a clip too short to keep. */
    fun stop(): Boolean
}
