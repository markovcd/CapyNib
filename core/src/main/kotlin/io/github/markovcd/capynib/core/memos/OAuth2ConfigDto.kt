package io.github.markovcd.capynib.core.memos

import kotlinx.serialization.Serializable

@Serializable
internal data class OAuth2ConfigDto(
    val clientId: String = "",
    val authUrl: String = "",
    val scopes: List<String> = emptyList(),
)
