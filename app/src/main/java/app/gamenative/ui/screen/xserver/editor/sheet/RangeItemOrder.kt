package app.gamenative.ui.screen.xserver.editor.sheet

/** Maths of dragging an entry of a key strip to another place in the list (pure Kotlin, tested without Android). */
internal object RangeItemOrder {
    /**
     * Where the entry [start] lands when its handle is dragged by [dragPx] (positive = down) over rows of the given [heights]
     * (in pixels, one per entry, spacing included): the row under the middle of the dragged one. Stays at [start] without
     * measured heights, never leaves the list.
     */
    fun targetIndex(start: Int, dragPx: Float, heights: List<Int>): Int {
        if (heights.isEmpty() || start !in heights.indices) return start
        if (heights.any { it <= 0 }) return start
        var top = 0
        for (i in 0 until start) top += heights[i]
        val middle = top + heights[start] / 2f + dragPx
        if (middle <= 0f) return 0
        var rowTop = 0
        for (i in heights.indices) {
            val rowBottom = rowTop + heights[i]
            if (middle < rowBottom) return i
            rowTop = rowBottom
        }
        return heights.lastIndex
    }

    /** The entry after a step of [delta] (-1 = up, +1 = down); stays inside the list. */
    fun step(index: Int, delta: Int, count: Int): Int = (index + delta).coerceIn(0, (count - 1).coerceAtLeast(0))
}
