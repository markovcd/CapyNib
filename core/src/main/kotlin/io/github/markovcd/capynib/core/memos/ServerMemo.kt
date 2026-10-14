package io.github.markovcd.capynib.core.memos

/** A memo as listed by the server. [id] is the part of its name after `memos/`; [attachments] are the files linked to it. */
data class ServerMemo(
    val id: String,
    val state: MemoState,
    val createdAt: Long,
    val updatedAt: Long,
    val attachments: List<ServerAttachment> = emptyList(),
)
