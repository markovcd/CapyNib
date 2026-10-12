package io.github.markovcd.quicknib.core.memos

/** Why talking to the memos server did not work. */
sealed interface SyncFailure {
    val message: String

    /** The server could not be reached: no network, wrong address, timeout. */
    data class Unreachable(override val message: String) : SyncFailure

    /** The server refused the access token. */
    data class Unauthorized(override val message: String) : SyncFailure

    /** The server answered with an error, or with something that is not memos. */
    data class Server(val httpStatus: Int, override val message: String) : SyncFailure
}
