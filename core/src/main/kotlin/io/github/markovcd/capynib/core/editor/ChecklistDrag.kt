package io.github.markovcd.capynib.core.editor

import io.github.markovcd.capynib.core.note.Checklist

/**
 * Where a checklist line dragged by its handle goes. It swaps with a neighbor of its own section (see
 * [Checklist.sections]) once it is past half of it. A swap leaves the other section's lines at their indexes,
 * so no line changes section under the finger.
 */
object ChecklistDrag {
    /** The [swaps] to make in order, the line's [index] after them, and how far [offset] the finger is from it there. */
    data class Moved(val swaps: List<Pair<Int, Int>>, val index: Int, val offset: Float)

    /** Moves line [from] of [body], dragged [offset] pixels from its place, past every neighbor it is over by half. */
    fun move(body: String, from: Int, offset: Float, heightOf: (Int) -> Int): Moved {
        val section = Checklist.sections(body).toList().firstOrNull { from in it } ?: return Moved(emptyList(), from, offset)
        val swaps = mutableListOf<Pair<Int, Int>>()
        var index = from
        var left = offset
        while (true) {
            val position = section.indexOf(index)
            val below = section.getOrNull(position + 1)
            val above = section.getOrNull(position - 1)
            val target = when {
                below != null && left > heightOf(below) / 2f -> below
                above != null && left < -heightOf(above) / 2f -> above
                else -> break
            }
            swaps += index to target
            left += if (target > index) -heightOf(target) else heightOf(target)
            index = target
        }
        return Moved(swaps, index, left)
    }
}
