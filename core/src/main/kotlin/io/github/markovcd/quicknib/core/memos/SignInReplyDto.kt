package io.github.markovcd.quicknib.core.memos

import kotlinx.serialization.Serializable

@Serializable
internal data class SignInReplyDto(val user: UserDto? = null, val accessToken: String = "")
