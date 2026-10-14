package io.github.markovcd.capynib.capture

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import io.github.markovcd.capynib.core.note.Capture
import io.github.markovcd.capynib.ui.MainActivity

/** The intents that start a quick capture, and the [Capture] each one asks for. */
object CaptureIntents {
    // Also written in res/xml/shortcuts.xml.
    const val NEW_NOTE = "io.github.markovcd.capynib.action.NEW_NOTE"
    const val NEW_CHECKLIST = "io.github.markovcd.capynib.action.NEW_CHECKLIST"
    const val NEW_AUDIO_NOTE = "io.github.markovcd.capynib.action.NEW_AUDIO_NOTE"
    const val NEW_PHOTO = "io.github.markovcd.capynib.action.NEW_PHOTO"

    /** Opens the app on a new note for [action], one of the actions above, over whatever it was showing. */
    fun newNote(context: Context, action: String): Intent =
        Intent(action)
            .setClass(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    /**
     * What [intent] asks to capture; null for anything else, and for an intent replayed from recents.
     * Shared images come with [sharedPhotos].
     */
    fun capture(intent: Intent): Capture? {
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return null
        return when (intent.action) {
            NEW_NOTE -> Capture.EmptyNote
            NEW_CHECKLIST -> Capture.EmptyChecklist
            NEW_AUDIO_NOTE -> Capture.EmptyRecording
            NEW_PHOTO -> Capture.EmptyPhoto
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> {
                val text = Capture.shared(
                    intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
                    intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString(),
                )
                when {
                    intent.sharesImages -> text ?: Capture.EmptyNote
                    intent.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true -> text
                    else -> null
                }
            }
            else -> null
        }
    }

    /**
     * The images [intent] shares, for the note it captures to copy in. Only another app's content is
     * taken: a file path, or an address of Capynib's own ([packageName]), would copy the app's private
     * files into a note that syncs.
     */
    fun sharedPhotos(intent: Intent, packageName: String): List<Uri> {
        if (!intent.sharesImages) return emptyList()
        val streams = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            else -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        }
        return streams.filter { uri ->
            val authority = uri.authority.orEmpty()
            uri.scheme == ContentResolver.SCHEME_CONTENT && authority != packageName && !authority.startsWith("$packageName.")
        }
    }

    private val Intent.sharesImages: Boolean
        get() = (action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE) && type?.startsWith("image/") == true
}
