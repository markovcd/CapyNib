package io.github.markovcd.capynib.ui

/** A recording's length or place, as minutes and seconds: `0:07`, `12:30`. */
fun clipTime(millis: Long): String {
    val seconds = millis.coerceAtLeast(0) / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
