package io.github.markovcd.capynib.ui.settings

import io.github.markovcd.capynib.core.memos.ServerReport
import io.github.markovcd.capynib.core.memos.SyncFailure

/** What the settings screen says under the connect button. */
sealed interface SettingsMessage {
    data object NotConnected : SettingsMessage

    data object BadAddress : SettingsMessage

    data object TokenMissing : SettingsMessage

    data object Checking : SettingsMessage

    data class Connected(val host: String, val username: String) : SettingsMessage

    /** Check server tried every call sync makes; [report] says which work. */
    data class Checked(val report: ServerReport) : SettingsMessage

    /** The sign-in page of [provider] is open in the browser. */
    data class WaitingForBrowser(val provider: String) : SettingsMessage

    data object NoSignInProvider : SettingsMessage

    data class SignInRefused(val reason: String) : SettingsMessage

    data object NoBrowser : SettingsMessage

    data class Failed(val failure: SyncFailure) : SettingsMessage
}
