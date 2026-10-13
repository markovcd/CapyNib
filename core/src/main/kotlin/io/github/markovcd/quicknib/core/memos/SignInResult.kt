package io.github.markovcd.quicknib.core.memos

/** How a [BrowserSignIn] ended. */
sealed interface SignInResult {
    /** Signed in as [username]; [accessToken] is a new access token for the app. */
    data class SignedIn(val username: String, val accessToken: String) : SignInResult

    /** The sign-in provider or the memos server said no, such as when sign-in was cancelled in the browser. */
    data class Refused(val reason: String) : SignInResult

    data class Failed(val failure: SyncFailure) : SignInResult
}
