package io.github.markovcd.capynib.capture

import android.app.Application
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import io.github.markovcd.capynib.R
import io.github.markovcd.capynib.core.note.Capture
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CaptureWidgetTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    /** What tapping [widget] asks the app to capture. */
    private fun tapped(widget: CaptureWidget): Capture? {
        val view = widget.views(context).apply(context, FrameLayout(context))
        view.findViewById<android.view.View>(R.id.widget_button).performClick()
        return shadowOf(context).nextStartedActivity?.let(CaptureIntents::capture)
    }

    @Test
    fun Tapping_the_pen_widget_opens_a_new_note() {
        assertEquals(Capture.EmptyNote, tapped(NewNoteWidget()))
    }

    @Test
    fun Tapping_the_checkbox_widget_opens_a_new_checklist() {
        assertEquals(Capture.EmptyChecklist, tapped(NewChecklistWidget()))
    }

    @Test
    fun Tapping_the_microphone_widget_opens_a_new_note_recording() {
        assertEquals(Capture.EmptyRecording, tapped(NewAudioNoteWidget()))
    }

    @Test
    fun Tapping_the_camera_widget_opens_a_new_note_taking_a_photo() {
        assertEquals(Capture.EmptyPhoto, tapped(NewPhotoWidget()))
    }
}
