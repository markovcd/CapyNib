package io.github.markovcd.capynib.core.memos

/** What Capynib reads from and writes to a memo. [position] is its create time in epoch seconds, which memos orders by. */
data class MemoState(val content: String, val pinned: Boolean, val archived: Boolean, val position: Long) {
    /** Everything but the [position]: what a reorder leaves alone. */
    fun sameContentAs(other: MemoState): Boolean = copy(position = other.position) == other
}
