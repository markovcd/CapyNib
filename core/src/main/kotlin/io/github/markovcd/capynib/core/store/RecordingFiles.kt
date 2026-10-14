package io.github.markovcd.capynib.core.store

import io.github.markovcd.capynib.core.memos.ServerAttachment
import java.io.File

/** The bytes of each recording on this device: one file per recording in [dir], named by its id. */
class RecordingFiles(private val dir: File) {
    fun file(id: String): File {
        // An id comes from the server too; only one memos would accept stays inside [dir].
        require(ServerAttachment.ID.matches(id)) { "'$id' is not a recording id" }
        return File(dir, id)
    }

    /** Moves the clip at [source] in as recording [id]'s bytes. */
    fun adopt(source: File, id: String) {
        dir.mkdirs()
        val target = file(id)
        if (!source.renameTo(target)) {
            source.copyTo(target, overwrite = true)
            source.delete()
        }
    }

    /** Recording [id]'s bytes, or null when this device has none. */
    fun read(id: String): ByteArray? = file(id).takeIf { it.isFile }?.readBytes()

    /** Writes recording [id]'s bytes whole: a reader sees the old file or the new one, never half of it. */
    fun write(id: String, bytes: ByteArray) {
        dir.mkdirs()
        val partial = File(dir, "$id.partial")
        partial.writeBytes(bytes)
        if (!partial.renameTo(file(id))) {
            partial.delete()
            error("could not write recording $id in $dir")
        }
    }

    fun delete(id: String) {
        file(id).delete()
    }
}
