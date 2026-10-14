package io.github.markovcd.capynib.core.note

/**
 * A voice clip in a note, synced as an attachment of the note's memo. [durationMillis] is null for
 * a clip recorded elsewhere, which says nothing of its length; [downloaded] is false until sync has
 * fetched its bytes.
 */
data class Recording(
    val id: String,
    val noteId: String,
    val fileName: String,
    val type: String,
    val durationMillis: Long?,
    val createdAt: Long,
    val downloaded: Boolean,
) {
    companion object {
        /** What a clip recorded on the phone is: AAC in an MPEG-4 file. */
        const val TYPE = "audio/mp4"

        /** Whether an attachment of [type] is a recording; other attachments are left to the memos web UI. */
        fun isRecording(type: String): Boolean = type.startsWith("audio/")
    }
}
