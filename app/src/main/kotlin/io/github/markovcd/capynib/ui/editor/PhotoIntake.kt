package io.github.markovcd.capynib.ui.editor

import android.content.ContentResolver
import android.net.Uri
import io.github.markovcd.capynib.core.note.Photo
import io.github.markovcd.capynib.photo.PhotoCopier
import java.io.File

/**
 * Brings photos into the open note: one the camera app wrote into a file from [newFile], reached
 * through the address [uriOf] gives it, or a copy of an image picked from the phone. Either is handed
 * to [onPhoto] with its type.
 */
class PhotoIntake(
    private val newFile: () -> File,
    val uriOf: (File) -> Uri,
    resolver: ContentResolver,
    private val onPhoto: (File, String) -> Unit,
) {
    private val copier = PhotoCopier(newFile, resolver)

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
        val photo = copier.copy(uri) ?: return false
        onPhoto(photo.file, photo.type)
        return true
    }
}
