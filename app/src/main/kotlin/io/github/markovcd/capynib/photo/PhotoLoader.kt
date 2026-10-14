package io.github.markovcd.capynib.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.LruCache
import io.github.markovcd.capynib.core.note.Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

private const val CACHE_BYTES = 32 * 1024 * 1024

/**
 * Decodes photos for the screen from the files [fileOf] names: scaled down to about the size they are
 * drawn at, turned upright by their EXIF orientation, and kept while they fit in memory.
 */
class PhotoLoader(private val fileOf: (String) -> File) {
    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    /** [photo] no larger than [maxSide] pixels a side, or null when it is not on the device or is not an image. */
    suspend fun load(photo: Photo, maxSide: Int): Bitmap? {
        if (!photo.downloaded) return null
        val key = "${photo.id}@$maxSide"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) { decode(fileOf(photo.id), maxSide) }?.also { cache.put(key, it) }
    }

    private fun decode(file: File, maxSide: Int): Bitmap? {
        if (!file.isFile) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val degrees = rotation(file)
        if (degrees == 0) return bitmap
        val turn = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, turn, true)
    }

    /** How far the camera says to turn the image to stand it upright; images without EXIF need none. */
    private fun rotation(file: File): Int =
        try {
            when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (e: IOException) {
            0
        }
}
