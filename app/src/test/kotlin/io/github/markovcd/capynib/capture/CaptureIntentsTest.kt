package io.github.markovcd.capynib.capture

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import io.github.markovcd.capynib.R
import io.github.markovcd.capynib.core.note.Capture
import io.github.markovcd.capynib.ui.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class CaptureIntentsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun shared(type: String, text: String?, subject: String? = null) = Intent(Intent.ACTION_SEND).setType(type).apply {
        text?.let { putExtra(Intent.EXTRA_TEXT, it) }
        subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
    }

    @Test
    fun A_link_shared_from_a_browser_becomes_a_note_titled_with_the_page() {
        assertEquals(
            Capture(title = "Capybaras", body = "https://example.com/capybaras"),
            CaptureIntents.capture(shared("text/plain", "https://example.com/capybaras", "Capybaras")),
        )
    }

    @Test
    fun A_share_that_is_not_text_captures_nothing() {
        assertNull(CaptureIntents.capture(shared("image/png", "photo")))
    }

    @Test
    fun The_new_note_checklist_and_audio_note_intents_capture_an_empty_note_checklist_and_recording() {
        assertEquals(Capture.EmptyNote, CaptureIntents.capture(CaptureIntents.newNote(context, CaptureIntents.NEW_NOTE)))
        assertEquals(Capture.EmptyChecklist, CaptureIntents.capture(CaptureIntents.newNote(context, CaptureIntents.NEW_CHECKLIST)))
        assertEquals(Capture.EmptyRecording, CaptureIntents.capture(CaptureIntents.newNote(context, CaptureIntents.NEW_AUDIO_NOTE)))
    }

    @Test
    fun The_new_photo_intent_captures_an_empty_note_that_takes_a_photo() {
        assertEquals(Capture.EmptyPhoto, CaptureIntents.capture(CaptureIntents.newNote(context, CaptureIntents.NEW_PHOTO)))
    }

    @Test
    fun Opening_the_app_from_the_launcher_captures_nothing() {
        assertNull(CaptureIntents.capture(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)))
    }

    @Test
    fun A_capture_reopened_from_recents_is_not_captured_again() {
        val replayed = CaptureIntents.newNote(context, CaptureIntents.NEW_NOTE).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        assertNull(CaptureIntents.capture(replayed))
    }

    @Test
    fun Every_launcher_shortcut_opens_the_app_on_a_quick_capture() {
        val shortcuts = shortcuts()
        val captures = mapOf("new_note" to Capture.EmptyNote, "new_checklist" to Capture.EmptyChecklist, "new_audio_note" to Capture.EmptyRecording)
        assertEquals(captures.keys.toList(), shortcuts.map { it.first })
        for ((id, intent) in shortcuts) {
            assertEquals(id, MainActivity::class.java.name, intent.component?.className)
            assertEquals(id, context.packageName, intent.component?.packageName)
            assertEquals(id, captures[id], CaptureIntents.capture(intent))
        }
    }

    /** Each shortcut's id and intent, as res/xml/shortcuts.xml declares them. */
    private fun shortcuts(): List<Pair<String, Intent>> {
        val android = "http://schemas.android.com/apk/res/android"
        val parser = context.resources.getXml(R.xml.shortcuts)
        val found = mutableListOf<Pair<String, Intent>>()
        var id = ""
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "shortcut" -> id = parser.getAttributeValue(android, "shortcutId")
                "intent" -> found += id to Intent(parser.getAttributeValue(android, "action")).setClassName(
                    parser.getAttributeValue(android, "targetPackage"),
                    parser.getAttributeValue(android, "targetClass"),
                )
            }
        }
        return found
    }
}
