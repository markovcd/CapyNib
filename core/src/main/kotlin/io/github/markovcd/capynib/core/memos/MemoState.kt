package io.github.markovcd.capynib.core.memos

/** What Capynib reads from and writes to a memo. */
data class MemoState(val content: String, val pinned: Boolean, val archived: Boolean)
