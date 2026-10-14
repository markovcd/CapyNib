package io.github.markovcd.capynib.core.memos

import kotlinx.serialization.Serializable

/** The fields of the memos v1 `Attachment` message that Capynib uses. */
@Serializable
internal data class AttachmentDto(
    val name: String = "",
    val createTime: String = "",
    val filename: String = "",
    val type: String = "",
)
