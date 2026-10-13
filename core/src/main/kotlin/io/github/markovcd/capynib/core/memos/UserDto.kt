package io.github.markovcd.capynib.core.memos

import kotlinx.serialization.Serializable

@Serializable
internal data class UserDto(
    val name: String = "",
    val username: String = "",
)
