package io.github.markovcd.capynib.settings

import android.content.SharedPreferences
import io.github.markovcd.capynib.core.store.Trash
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Keeps how many days the trash keeps a deleted note, in shared preferences. Anything missing,
 * unreadable or out of [Trash.DAYS] loads as [Trash.DEFAULT_DAYS].
 */
class TrashSettingsStore(private val prefs: SharedPreferences) {
    private val current = MutableStateFlow(load())

    val days: StateFlow<Int> = current.asStateFlow()

    fun save(days: Int) {
        require(days in Trash.DAYS) { "$days days is outside ${Trash.DAYS}" }
        prefs.edit().putInt(KEY_DAYS, days).apply()
        current.value = days
    }

    private fun load(): Int = (prefs.all[KEY_DAYS] as? Int)?.takeIf { it in Trash.DAYS } ?: Trash.DEFAULT_DAYS

    private companion object {
        // Persisted key; never renamed.
        const val KEY_DAYS = "days"
    }
}
