package io.github.markovcd.capynib.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.markovcd.capynib.core.store.Trash
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class TrashSettingsStoreTest {
    private val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("trash-test", Context.MODE_PRIVATE)

    @Test
    fun Saved_days_read_back() {
        TrashSettingsStore(prefs).save(30)

        assertEquals(30, TrashSettingsStore(prefs).days.value)
    }

    @Test
    fun Nothing_saved_means_the_default_week() {
        assertEquals(Trash.DEFAULT_DAYS, TrashSettingsStore(prefs).days.value)
    }

    @Test
    fun Days_that_are_unreadable_or_out_of_range_load_as_the_default() {
        for (edit in listOf<(android.content.SharedPreferences.Editor) -> Unit>({ it.putString("days", "thirty") }, { it.putInt("days", 0) })) {
            prefs.edit().also(edit).commit()

            assertEquals(Trash.DEFAULT_DAYS, TrashSettingsStore(prefs).days.value)
        }
    }
}
