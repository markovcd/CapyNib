package io.github.markovcd.capynib.ui.board

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/**
 * A note being dragged to a new place on the board. While the finger moves, the board shows its
 * section in [order], with the dragged card drawn [offsetOf] its slot so it stays under the finger.
 * Points and bounds are in root coordinates.
 */
@Stable
class BoardDrag {
    /** Each card's bounds as laid out, before the drag offset is drawn. */
    private val bounds = mutableStateMapOf<String, Rect>()
    private var start = Offset.Zero
    private var startBounds = Rect.Zero
    private var startIndex = 0
    private var finger by mutableStateOf(Offset.Zero)

    var dragged by mutableStateOf<String?>(null)
        private set

    /** The dragged section's ids as shown; kept after the drop until the board has the move. */
    var order by mutableStateOf<List<String>?>(null)
        private set

    fun place(id: String, rect: Rect) {
        bounds[id] = rect
    }

    fun forget(id: String) {
        bounds.remove(id)
    }

    fun noteAt(point: Offset): String? = bounds.entries.firstOrNull { it.value.contains(point) }?.key

    fun start(id: String, at: Offset, section: List<String>) {
        dragged = id
        start = at
        finger = at
        startBounds = bounds[id] ?: Rect.Zero
        startIndex = section.indexOf(id)
        order = section
    }

    /** Follows the finger to [to]; over another card of the section, the dragged one takes its place. */
    fun drag(to: Offset) {
        finger = to
        val id = dragged ?: return
        val shown = order ?: return
        val over = shown.firstOrNull { it != id && bounds[it]?.contains(to) == true } ?: return
        order = shown.toMutableList().apply {
            remove(id)
            add(shown.indexOf(over), id)
        }
    }

    fun offsetOf(id: String): Offset {
        if (id != dragged) return Offset.Zero
        val slot = bounds[id] ?: return Offset.Zero
        return finger - start - (slot.topLeft - startBounds.topLeft)
    }

    /** Ends the drag; returns the note and the index it was dropped at, or null when it did not move. */
    fun drop(): Pair<String, Int>? {
        val id = dragged ?: return null
        dragged = null
        val index = order?.indexOf(id) ?: return null
        if (index == startIndex) order = null
        return if (index == startIndex) null else id to index
    }

    /** The board caught up with the drop: show its own order again. */
    fun settle() {
        if (dragged == null) order = null
    }
}
