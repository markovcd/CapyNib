package io.github.markovcd.capynib.capture

import android.content.Context
import android.content.Intent
import io.github.markovcd.capynib.core.note.Capture
import io.github.markovcd.capynib.ui.MainActivity

/** The intents that start a quick capture, and the [Capture] each one asks for. */
object CaptureIntents {
    // Also written in res/xml/shortcuts.xml.
    const val NEW_NOTE = "io.github.markovcd.capynib.action.NEW_NOTE"
    const val NEW_CHECKLIST = "io.github.markovcd.capynib.action.NEW_CHECKLIST"

    /** Opens the app on a new note, over whatever it was showing. */
    fun newNote(context: Context, checklist: Boolean): Intent =
        Intent(if (checklist) NEW_CHECKLIST else NEW_NOTE)
            .setClass(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    /** What [intent] asks to capture; null for anything else, and for an intent replayed from recents. */
    fun capture(intent: Intent): Capture? {
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return null
        return when (intent.action) {
            NEW_NOTE -> Capture.EmptyNote
            NEW_CHECKLIST -> Capture.EmptyChecklist
            Intent.ACTION_SEND ->
                if (intent.type?.startsWith("text/") == true) {
                    Capture.shared(
                        intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
                        intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString(),
                    )
                } else {
                    null
                }
            else -> null
        }
    }
}
