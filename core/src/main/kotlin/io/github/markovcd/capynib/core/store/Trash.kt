package io.github.markovcd.capynib.core.store

/** How many days the trash keeps a deleted note before it is deleted for good; the user picks within [DAYS]. */
object Trash {
    const val DEFAULT_DAYS = 7

    val DAYS = 1..365

    /** [text] as a number of days, or null when it is not a whole number in [DAYS]. */
    fun parseDays(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in DAYS }
}
