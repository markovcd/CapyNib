package io.github.markovcd.capynib.core.memos

import kotlinx.serialization.Serializable

@Serializable
internal data class IdentityProvidersDto(val identityProviders: List<IdentityProviderDto> = emptyList())
