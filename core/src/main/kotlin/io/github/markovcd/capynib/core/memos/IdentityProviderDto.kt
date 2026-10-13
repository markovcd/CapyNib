package io.github.markovcd.capynib.core.memos

import kotlinx.serialization.Serializable

@Serializable
internal data class IdentityProviderDto(
    val name: String = "",
    val type: String = "",
    val title: String = "",
    val config: IdentityProviderConfigDto = IdentityProviderConfigDto(),
)
