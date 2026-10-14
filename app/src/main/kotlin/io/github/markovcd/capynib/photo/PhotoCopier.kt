package io.github.markovcd.capynib.photo

import android.content.ContentResolver
import android.net.Uri
import io.github.markovcd.capynib.core.note.Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Copies images another app holds into files from [newFile], for a note to take. */
class PhotoCopier(private val newFile: () -> File, private val resolver: ContentResolver) {
    /** A copy of the image at [uri]; null when it could not be read or its app says it is not an image. */
    suspend fun copy(uri: Uri): CopiedPhoto? {
        val type = resolver.getType(uri)
        if (type != null && !Photo.isPhoto(type)) return null
        val copy = newFile()
        val copied = withContext(Dispatchers.IO) {
            try {
                resolver.openInputStream(uri)?.use { input -> copy.outputStream().use { input.copyTo(it) } } != null
            } catch (e: IOException) {
                false
            } catch (e: SecurityException) {
                false
            }
        }
        if (!copied || copy.length() == 0L) {
            copy.delete()
            return null
        }
        return CopiedPhoto(copy, type ?: Photo.JPEG)
    }
}
