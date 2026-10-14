package io.github.markovcd.capynib.core.store

import io.github.markovcd.capynib.core.memos.ServerAttachment
import java.io.File

/**
 * The bytes of each recording and photo on this device: one file per attachment in [dir], named by
 * its id. Files found in [legacy], the folder that held recordings alone, move into [dir] on opening.
 */
class AttachmentFiles(private val dir: File, legacy: File? = null) {
    init {
        legacy?.listFiles()?.let { old ->
            dir.mkdirs()
            old.filter { ServerAttachment.ID.matches(it.name) }.forEach { adopt(it, it.name) }
            legacy.deleteRecursively()
        }
    }

    fun file(id: String): File {
        // An id comes from the server too; only one memos would accept stays inside [dir].
        require(ServerAttachment.ID.matches(id)) { "'$id' is not an attachment id" }
        return File(dir, id)
    }

    /** Moves the file at [source] in as attachment [id]'s bytes. */
    fun adopt(source: File, id: String) {
        dir.mkdirs()
        val target = file(id)
        if (!source.renameTo(target)) {
            source.copyTo(target, overwrite = true)
            source.delete()
        }
    }

    /** Attachment [id]'s bytes, or null when this device has none. */
    fun read(id: String): ByteArray? = file(id).takeIf { it.isFile }?.readBytes()

    /** Writes attachment [id]'s bytes whole: a reader sees the old file or the new one, never half of it. */
    fun write(id: String, bytes: ByteArray) {
        dir.mkdirs()
        val partial = File(dir, "$id.partial")
        partial.writeBytes(bytes)
        if (!partial.renameTo(file(id))) {
            partial.delete()
            error("could not write attachment $id in $dir")
        }
    }

    fun delete(id: String) {
        file(id).delete()
    }
}
