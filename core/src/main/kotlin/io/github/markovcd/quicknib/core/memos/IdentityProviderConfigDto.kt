package io.github.markovcd.quicknib.core.memos

import kotlinx.serialization.Serializable

@Serializable
internal data class IdentityProviderConfigDto(val oauth2Config: OAuth2ConfigDto? = null)
