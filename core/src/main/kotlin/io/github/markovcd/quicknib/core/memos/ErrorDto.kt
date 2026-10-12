package io.github.markovcd.quicknib.core.memos

import kotlinx.serialization.Serializable

@Serializable
internal data class ErrorDto(val code: Int = 0, val message: String = "")
