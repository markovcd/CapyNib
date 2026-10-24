package io.github.markovcd.capynib.core.note

/**
 * What a quick capture (a widget, a launcher shortcut, the tile, or text or photos shared from another app) starts a note with,
 * and how the note opens ([opening]).
 */
data class Capture(val title: String = "", val body: String = "", val opening: Opening = Opening.Type) {
    companion object {
        val EmptyNote = Capture()

        val EmptyChecklist = Capture(body = Checklist.toChecklist(""))

        val EmptyRecording = Capture(opening = Opening.Record)

        val EmptyPhoto = Capture(opening = Opening.Photo)

        /**
         * A note from text another app shared. A browser shares a link as the text and the page title
         * as the subject, so the subject becomes the title unless the text already holds it. Null when
         * nothing was shared.
         */
        fun shared(text: String?, subject: String?): Capture? {
            val body = text?.trim().orEmpty()
            val title = subject?.trim()?.replace(Regex("\\s*\\n\\s*"), " ").orEmpty()
            return when {
                body.isEmpty() && title.isEmpty() -> null
                title in body -> Capture(body = body)
                else -> Capture(title, body)
            }
        }
    }
}
