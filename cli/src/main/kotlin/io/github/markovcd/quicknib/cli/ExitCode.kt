package io.github.markovcd.quicknib.cli

/** What the command line's exit codes mean, one thing each. */
object ExitCode {
    const val OK = 0
    const val USAGE = 1
    const val NOT_FOUND = 2
    const val SYNC_FAILED = 3
    const val LOGIN_FAILED = 4
}
