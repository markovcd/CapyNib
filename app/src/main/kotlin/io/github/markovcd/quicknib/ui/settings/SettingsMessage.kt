package io.github.markovcd.quicknib.ui.settings

import io.github.markovcd.quicknib.core.memos.SyncFailure

/** What the settings screen says under the connect button. */
sealed interface SettingsMessage {
    data object NotConnected : SettingsMessage

    data object BadAddress : SettingsMessage

    data object TokenMissing : SettingsMessage

    data object Checking : SettingsMessage

    data class Connected(val username: String) : SettingsMessage

    data class Failed(val failure: SyncFailure) : SettingsMessage
}
