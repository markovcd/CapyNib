package io.github.markovcd.quicknib.core.memos

import kotlinx.serialization.Serializable

@Serializable
internal data class PersonalAccessTokenReplyDto(val token: String = "")
