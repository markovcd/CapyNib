package io.github.markovcd.capynib.settings

import android.content.SharedPreferences
import io.github.markovcd.capynib.core.memos.ServerAddress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Keeps [SyncSettings] in shared preferences, the token encrypted by [cipher]. Anything missing or
 * unreadable loads as not configured rather than failing.
 */
class SyncSettingsStore(private val prefs: SharedPreferences, private val cipher: TokenCipher) {
    private val current = MutableStateFlow(load())

    val settings: StateFlow<SyncSettings> = current.asStateFlow()

    fun save(settings: SyncSettings) {
        prefs.edit()
            .putString(KEY_SERVER, settings.server?.toString())
            .putString(KEY_TOKEN, if (settings.token.isEmpty()) null else cipher.encrypt(settings.token))
            .apply()
        current.value = settings
    }

    private fun load(): SyncSettings {
        val server = prefs.getString(KEY_SERVER, null)?.let(ServerAddress::parse) ?: return SyncSettings.None
        val token = prefs.getString(KEY_TOKEN, null)?.let(cipher::decrypt) ?: return SyncSettings(server, "")
        return SyncSettings(server, token)
    }

    private companion object {
        // Persisted keys; never renamed.
        const val KEY_SERVER = "server"
        const val KEY_TOKEN = "token"
    }
}
