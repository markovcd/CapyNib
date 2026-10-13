package io.github.markovcd.capynib.core.memos

/** What the sign-in provider sent the browser back to [LoopbackReceiver] with. */
sealed interface BrowserCallback {
    data class Code(val code: String) : BrowserCallback

    /** The provider's `error` parameter, such as `access_denied` when sign-in was cancelled. */
    data class Error(val error: String) : BrowserCallback
}
