package io.github.markovcd.capynib.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.markovcd.capynib.TestGraphs
import io.github.markovcd.capynib.core.memos.ServerAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class SyncSettingsStoreTest {
    private val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("test", Context.MODE_PRIVATE)

    @Test
    fun Saved_settings_read_back_and_the_token_is_never_stored_in_the_clear() {
        val settings = SyncSettings(ServerAddress.parse("https://memos.example.com"), "memos_pat_secret")

        SyncSettingsStore(prefs, TestGraphs.ReversingCipher).save(settings)

        assertEquals(settings, SyncSettingsStore(prefs, TestGraphs.ReversingCipher).settings.value)
        assertTrue(prefs.all.values.none { it.toString().contains("memos_pat_secret") })
    }

    @Test
    fun A_token_that_cannot_be_decrypted_loads_as_not_configured() {
        prefs.edit().putString("server", "https://memos.example.com").putString("token", "garbage").commit()

        val loaded = SyncSettingsStore(prefs, TestGraphs.ReversingCipher).settings.value

        assertEquals(false, loaded.isConfigured)
        assertEquals("https://memos.example.com", loaded.server.toString())
    }

    @Test
    fun Nothing_saved_means_notes_stay_local() {
        assertEquals(SyncSettings.None, SyncSettingsStore(prefs, TestGraphs.ReversingCipher).settings.value)
    }
}
