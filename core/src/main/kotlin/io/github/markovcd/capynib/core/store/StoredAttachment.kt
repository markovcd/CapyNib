package io.github.markovcd.capynib.core.store

import io.github.markovcd.capynib.core.note.Photo
import io.github.markovcd.capynib.core.note.Recording

/** A recording or photo as sync sees it: the attachment of note [noteId]'s memo it is sent as. */
data class StoredAttachment(val id: String, val noteId: String, val fileName: String, val type: String) {
    companion object {
        /** Whether the device keeps an attachment of [type]: recordings and photos; the rest are left to the memos web app. */
        fun isKept(type: String): Boolean = Recording.isRecording(type) || Photo.isPhoto(type)
    }
}
