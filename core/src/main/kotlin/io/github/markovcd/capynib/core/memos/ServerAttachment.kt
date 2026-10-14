package io.github.markovcd.capynib.core.memos

/** A file linked to a memo on the server. [id] is the part of its name after `attachments/`. */
data class ServerAttachment(val id: String, val fileName: String, val type: String, val createdAt: Long) {
    companion object {
        /** The ids memos accepts for a new attachment. */
        val ID = Regex("[A-Za-z0-9]([A-Za-z0-9-]{0,34}[A-Za-z0-9])?")
    }
}
