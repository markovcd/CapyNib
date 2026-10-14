package io.github.markovcd.capynib.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.io.IOException

/** [Microphone] over [MediaRecorder]: AAC in an MPEG-4 file, as Keep records. */
class AndroidMicrophone(private val context: Context) : Microphone {
    private var recorder: MediaRecorder? = null

    override fun start(file: File): Boolean {
        stop()
        // The constructor without a context is the only one before Android 12.
        val created = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        return try {
            created.setAudioSource(MediaRecorder.AudioSource.MIC)
            created.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            created.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            created.setAudioEncodingBitRate(BIT_RATE)
            created.setAudioSamplingRate(SAMPLE_RATE)
            created.setOutputFile(file.path)
            created.prepare()
            created.start()
            recorder = created
            true
        } catch (e: IOException) {
            created.release()
            false
        } catch (e: RuntimeException) {
            // MediaRecorder reports a microphone that is busy or refused as a RuntimeException.
            created.release()
            false
        }
    }

    override fun stop(): Boolean {
        val running = recorder ?: return false
        recorder = null
        return try {
            running.stop()
            true
        } catch (e: RuntimeException) {
            // Stopping before any sound was written fails; there is no clip to keep.
            false
        } finally {
            running.release()
        }
    }

    private companion object {
        const val BIT_RATE = 64_000
        const val SAMPLE_RATE = 44_100
    }
}
