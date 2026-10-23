package io.github.markovcd.capynib.core.board

/**
 * Where a card dragged across the board goes. Over another card of its section it takes that card's place, and the
 * cards between shift up or down one; the drop is then a [BoardContent.move] to its index.
 */
object CardDrag {
    /** [order], the section's ids as shown, after card [id] is dragged over card [over]; as it was when either is not in it. */
    fun over(order: List<String>, id: String, over: String): List<String> {
        val target = order.indexOf(over)
        if (id == over || target < 0 || id !in order) return order
        return order.toMutableList().apply {
            remove(id)
            add(target, id)
        }
    }
}
