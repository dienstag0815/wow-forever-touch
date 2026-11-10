package app.gamenative.ui.screen.xserver.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElementPlacementTest {
    private val step = 10f
    private val areaW = 1000f
    private val areaH = 600f

    @Test
    fun `free preferred point is kept`() {
        val result = ElementPlacement.findFreeCenter(500f, 300f, step, areaW, areaH, emptyList())

        assertEquals(500f to 300f, result)
    }

    @Test
    fun `occupied preferred point moves to a free grid point`() {
        val result = ElementPlacement.findFreeCenter(500f, 300f, step, areaW, areaH, listOf(500f to 300f))

        assertTrue(result != (500f to 300f))
        assertEquals(510f to 310f, result)
    }

    @Test
    fun `a duplicate sits five grid steps right and down`() {
        val result = ElementPlacement.duplicateCenter(300f, 200f, step, areaW, areaH, listOf(300f to 200f))

        assertEquals(350f to 250f, result)
    }

    @Test
    fun `a duplicate near the right edge goes to the other side`() {
        val result = ElementPlacement.duplicateCenter(990f, 200f, step, areaW, areaH, listOf(990f to 200f))

        assertTrue(result.first <= areaW - 2 * step)
        assertTrue(result.first != 990f || result.second != 200f)
    }

    @Test
    fun `stacked duplicates do not land on each other`() {
        val occupied = mutableListOf(300f to 200f)
        repeat(5) {
            val copy = ElementPlacement.duplicateCenter(
                occupied.last().first, occupied.last().second, step, areaW, areaH, occupied,
            )
            assertTrue(occupied.none { it == copy })
            occupied.add(copy)
        }
    }

    @Test
    fun `new elements never leave the margin`() {
        val result = ElementPlacement.findFreeCenter(-50f, 5000f, step, areaW, areaH, emptyList())

        assertTrue(result.first >= 2 * step && result.first <= areaW - 2 * step)
        assertTrue(result.second >= 2 * step && result.second <= areaH - 2 * step)
    }
}
