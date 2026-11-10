package app.gamenative.ui.screen.xserver.editor.sheet

import org.junit.Assert.assertEquals
import org.junit.Test

class RangeItemOrderTest {
    private val rows = listOf(100, 100, 100, 100)

    @Test
    fun `no drag keeps the place`() {
        for (i in rows.indices) assertEquals(i, RangeItemOrder.targetIndex(i, 0f, rows))
    }

    @Test
    fun `dragging down by about a row moves one place`() {
        assertEquals(1, RangeItemOrder.targetIndex(0, 100f, rows))
        assertEquals(2, RangeItemOrder.targetIndex(0, 200f, rows))
        // less than half a row stays
        assertEquals(0, RangeItemOrder.targetIndex(0, 49f, rows))
        assertEquals(1, RangeItemOrder.targetIndex(0, 51f, rows))
    }

    @Test
    fun `dragging up works the same way`() {
        assertEquals(2, RangeItemOrder.targetIndex(3, -100f, rows))
        assertEquals(0, RangeItemOrder.targetIndex(3, -300f, rows))
        assertEquals(3, RangeItemOrder.targetIndex(3, -49f, rows))
    }

    @Test
    fun `never leaves the list`() {
        assertEquals(3, RangeItemOrder.targetIndex(1, 5000f, rows))
        assertEquals(0, RangeItemOrder.targetIndex(2, -5000f, rows))
    }

    @Test
    fun `rows of different heights use the middle of the dragged row`() {
        val uneven = listOf(100, 200, 100)
        // row 0 middle at 50; dragged by 170 -> 220, inside row 1 (100..300)
        assertEquals(1, RangeItemOrder.targetIndex(0, 170f, uneven))
        // row 2 middle at 350; dragged by -130 -> 220, inside row 1
        assertEquals(1, RangeItemOrder.targetIndex(2, -130f, uneven))
        // row 2 dragged by -260 -> 90, inside row 0
        assertEquals(0, RangeItemOrder.targetIndex(2, -260f, uneven))
    }

    @Test
    fun `without measured heights nothing moves`() {
        assertEquals(1, RangeItemOrder.targetIndex(1, 300f, listOf(100, 0, 100)))
        assertEquals(0, RangeItemOrder.targetIndex(0, 300f, emptyList()))
        assertEquals(7, RangeItemOrder.targetIndex(7, 300f, rows))
    }

    @Test
    fun `step stays inside the list`() {
        assertEquals(0, RangeItemOrder.step(0, -1, 5))
        assertEquals(1, RangeItemOrder.step(0, 1, 5))
        assertEquals(4, RangeItemOrder.step(4, 1, 5))
        assertEquals(0, RangeItemOrder.step(0, 1, 0))
    }
}
