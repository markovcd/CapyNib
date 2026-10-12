package io.github.markovcd.quicknib.core.memos

/** What QuickNib reads from and writes to a memo. */
data class MemoState(val content: String, val pinned: Boolean, val archived: Boolean)
