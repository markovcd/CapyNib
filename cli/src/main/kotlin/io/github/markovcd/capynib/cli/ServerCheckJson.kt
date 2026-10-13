package io.github.markovcd.capynib.cli

import kotlinx.serialization.Serializable

/** One call in `server --json`. */
@Serializable
data class ServerCheckJson(val call: String, val works: Boolean, val problem: String? = null)
