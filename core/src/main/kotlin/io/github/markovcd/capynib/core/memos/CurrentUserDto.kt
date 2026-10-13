package io.github.markovcd.capynib.core.memos

import kotlinx.serialization.Serializable

@Serializable
internal data class CurrentUserDto(val user: UserDto? = null)
