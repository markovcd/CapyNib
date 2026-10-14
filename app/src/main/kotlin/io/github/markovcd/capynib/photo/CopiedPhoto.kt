package io.github.markovcd.capynib.photo

import java.io.File

/** An image copied into [file] of Capynib's own, of [type], waiting for a note to take it. */
data class CopiedPhoto(val file: File, val type: String)
