package io.github.markovcd.capynib.core.note

/**
 * An image in a note, taken with the camera or chosen from the phone, synced as an attachment of
 * the note's memo. [downloaded] is false until sync has fetched its bytes.
 */
data class Photo(
    val id: String,
    val noteId: String,
    val fileName: String,
    val type: String,
    val createdAt: Long,
    val downloaded: Boolean,
) {
    companion object {
        /** What the camera takes. */
        const val JPEG = "image/jpeg"

        private val EXTENSIONS = mapOf(
            JPEG to "jpg",
            "image/png" to "png",
            "image/webp" to "webp",
            "image/gif" to "gif",
            "image/heic" to "heic",
            "image/heif" to "heif",
            "image/avif" to "avif",
        )

        /** Whether an attachment of [type] is a photo. */
        fun isPhoto(type: String): Boolean = type.startsWith("image/")

        /** The name photo [id] of [type] is sent under; the memos web app shows it. */
        fun fileName(id: String, type: String): String = "photo-$id.${EXTENSIONS[type] ?: "img"}"
    }
}
