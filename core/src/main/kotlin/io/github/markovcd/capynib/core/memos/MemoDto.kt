package io.github.markovcd.capynib.core.memos

import kotlinx.serialization.Serializable

/** The fields of the memos v1 `Memo` message that Capynib uses. */
@Serializable
internal data class MemoDto(
    val name: String = "",
    val state: String = "",
    val creator: String = "",
    val createTime: String = "",
    val updateTime: String = "",
    val content: String = "",
    val visibility: String = "",
    val pinned: Boolean = false,
    val attachments: List<AttachmentDto> = emptyList(),
)
