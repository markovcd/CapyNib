package io.github.markovcd.capynib.core.memos

/**
 * The outcome of one call to the memos server. A call that names no memo or attachment answers an [Answer];
 * one on a memo or attachment that may be gone answers a [Lookup].
 */
sealed interface ApiResult<out T> {
    /** [Ok] or [Failed]. */
    sealed interface Answer<out T> : ApiResult<T>

    /** [Ok], [NotFound] or [Failed]. */
    sealed interface Lookup<out T> : ApiResult<T>

    data class Ok<T>(val value: T) : Answer<T>, Lookup<T>

    /** The memo or attachment named in the call does not exist. */
    data object NotFound : Lookup<Nothing>

    /** A memo with the id given to create already exists. */
    data object AlreadyExists : ApiResult<Nothing>

    data class Failed(val failure: SyncFailure) : Answer<Nothing>, Lookup<Nothing>
}
