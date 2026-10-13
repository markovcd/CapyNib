package io.github.markovcd.capynib.core.note

/** Sorted, disjoint inclusive ranges of code points, searched by bisection. */
internal class CodePointRanges(private val lows: IntArray, private val highs: IntArray) {
    fun contains(codePoint: Int): Boolean {
        var low = 0
        var high = lows.size
        while (low < high) {
            val middle = (low + high) ushr 1
            when {
                codePoint < lows[middle] -> high = middle
                codePoint > highs[middle] -> low = middle + 1
                else -> return true
            }
        }
        return false
    }
}
