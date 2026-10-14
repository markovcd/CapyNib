package io.github.markovcd.capynib.ui.editor

import android.content.ContentResolver
import android.net.Uri
import io.github.markovcd.capynib.core.note.Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Brings photos into the open note: one the camera app wrote into a file from [newFile], reached
 * through the address [uriOf] gives it, or a copy of an image picked from the phone. Either is handed
 * to [onPhoto] with its type.
 */
class PhotoIntake(
    private val newFile: () -> File,
    val uriOf: (File) -> Uri,
    private val resolver: ContentResolver,
    private val onPhoto: (File, String) -> Unit,
) {
    /** A file for the camera app to write a photo into. */
    fun newShot(): File = newFile()

    /** Keeps [shot] when the camera app says it [took] a photo into it, and throws it away otherwise; returns whether it kept it. */
    fun taken(shot: File, took: Boolean): Boolean {
        if (!took || shot.length() == 0L) {
            shot.delete()
            return false
        }
        onPhoto(shot, Photo.JPEG)
        return true
    }

    /** Copies the image at [uri] into the note; false when it could not be read. */
    suspend fun picked(uri: Uri): Boolean {
        val copy = newFile()
        val copied = withContext(Dispatchers.IO) {
            try {
                resolver.openInputStream(uri)?.use { input -> copy.outputStream().use { input.copyTo(it) } } != null
            } catch (e: IOException) {
                false
            }
        }
        if (!copied || copy.length() == 0L) {
            copy.delete()
            return false
        }
        onPhoto(copy, resolver.getType(uri)?.takeIf(Photo::isPhoto) ?: Photo.JPEG)
        return true
    }
}
