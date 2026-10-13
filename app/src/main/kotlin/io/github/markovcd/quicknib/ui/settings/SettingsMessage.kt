package io.github.markovcd.quicknib.ui.settings

import io.github.markovcd.quicknib.core.memos.SyncFailure

/** What the settings screen says under the connect button. */
sealed interface SettingsMessage {
    data object NotConnected : SettingsMessage

    data object BadAddress : SettingsMessage

    data object TokenMissing : SettingsMessage

    data object Checking : SettingsMessage

    data class Connected(val username: String) : SettingsMessage

    /** The sign-in page of [provider] is open in the browser. */
    data class WaitingForBrowser(val provider: String) : SettingsMessage

    data object NoSignInProvider : SettingsMessage

    data class SignInRefused(val reason: String) : SettingsMessage

    data object NoBrowser : SettingsMessage

    data class Failed(val failure: SyncFailure) : SettingsMessage
}
