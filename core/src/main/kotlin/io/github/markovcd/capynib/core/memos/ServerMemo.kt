package io.github.markovcd.capynib.core.memos

/** A memo as listed by the server. [id] is the part of its name after `memos/`. */
data class ServerMemo(
    val id: String,
    val state: MemoState,
    val createdAt: Long,
    val updatedAt: Long,
)
