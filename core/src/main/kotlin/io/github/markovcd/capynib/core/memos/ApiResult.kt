package io.github.markovcd.capynib.core.memos

/** The outcome of one call to the memos server. */
sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>

    /** The memo named in the call does not exist. */
    data object NotFound : ApiResult<Nothing>

    /** A memo with the id given to create already exists. */
    data object AlreadyExists : ApiResult<Nothing>

    data class Failed(val failure: SyncFailure) : ApiResult<Nothing>
}
