package io.github.markovcd.capynib.core.store

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/** How the trash keeps deleted notes. */
object Trash {
    /** How long a note stays in the trash before it is deleted for good. */
    val KEPT_FOR: Duration = 7.days
}
