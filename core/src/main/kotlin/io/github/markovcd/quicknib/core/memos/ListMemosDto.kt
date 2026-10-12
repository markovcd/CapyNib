package io.github.markovcd.quicknib.core.memos

import kotlinx.serialization.Serializable

@Serializable
internal data class ListMemosDto(
    val memos: List<MemoDto> = emptyList(),
    val nextPageToken: String = "",
)
