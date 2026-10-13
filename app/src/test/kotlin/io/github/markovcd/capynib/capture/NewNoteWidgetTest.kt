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
class NewNoteWidgetTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    /** What tapping the widget asks the app to capture. */
    private fun tapped(): Capture? {
        val widget = NewNoteWidget.views(context).apply(context, FrameLayout(context))
        widget.findViewById<android.view.View>(R.id.widget_new_note).performClick()
        return shadowOf(context).nextStartedActivity?.let(CaptureIntents::capture)
    }

    @Test
    fun Tapping_the_widget_opens_a_new_note() {
        assertEquals(Capture.EmptyNote, tapped())
    }
}
