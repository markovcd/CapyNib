package io.github.markovcd.quicknib.cli

import kotlinx.serialization.Serializable

/** A finished sign-in as `login --json` prints it. */
@Serializable
data class LoginJson(val username: String, val accessToken: String)
