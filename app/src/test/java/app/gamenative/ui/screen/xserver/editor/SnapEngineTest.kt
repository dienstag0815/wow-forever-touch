package app.gamenative.ui.screen.xserver.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapEngineTest {
    private val areaW = 1000f
    private val areaH = 600f
    private val engine = SnapEngine(gridSize = 10f, threshold = 6f)

    private fun rect(cx: Float, cy: Float, halfW: Float = 20f, halfH: Float = 20f) =
        SnapRect.centered(cx, cy, halfW, halfH)

    @Test
    fun `without neighbours the center snaps to the grid`() {
        val result = engine.snap(rect(103f, 47f), emptyList(), areaW, areaH)

        assertEquals(100f, result.centerX, 0.001f)
        assertEquals(50f, result.centerY, 0.001f)
        assertFalse(result.objectSnapX)
        assertFalse(result.objectSnapY)
        assertTrue(result.guides.isEmpty())
    }

    @Test
    fun `grid rounds to the nearest line and not down`() {
        val up = engine.snap(rect(106f, 196f), emptyList(), areaW, areaH)
        val down = engine.snap(rect(104f, 194f), emptyList(), areaW, areaH)

        assertEquals(110f, up.centerX, 0.001f)
        assertEquals(200f, up.centerY, 0.001f)
        assertEquals(100f, down.centerX, 0.001f)
        assertEquals(190f, down.centerY, 0.001f)
    }

    @Test
    fun `grid can be switched off`() {
        val free = SnapEngine(gridSize = 10f, threshold = 6f, gridEnabled = false)
        val result = free.snap(rect(103f, 47f), emptyList(), areaW, areaH)

        assertEquals(103f, result.centerX, 0.001f)
        assertEquals(47f, result.centerY, 0.001f)
    }

    @Test
    fun `edge of the moved element catches on the edge of another one`() {
        val other = SnapRect(200f, 100f, 260f, 140f)
        // left edge at 263 is 3 px right of the other element's right edge (260)
        val result = engine.snap(rect(283f, 120f), listOf(other), areaW, areaH)

        assertEquals(280f, result.centerX, 0.001f)
        assertEquals(260f, result.rect.left, 0.001f)
        assertTrue(result.objectSnapX)
        assertTrue(result.guides.any { it.vertical && it.position == 260f })
    }

    @Test
    fun `center catches on the center of another element and shows a horizontal guide`() {
        val other = SnapRect(200f, 100f, 260f, 140f) // center 230 / 120
        val result = engine.snap(rect(233f, 124f, halfW = 10f, halfH = 10f), listOf(other), areaW, areaH)

        assertEquals(230f, result.centerX, 0.001f)
        assertEquals(120f, result.centerY, 0.001f)
        assertTrue(result.objectSnapX)
        assertTrue(result.objectSnapY)
        val horizontal = result.guides.first { !it.vertical && it.position == 120f }
        // the guide spans both elements
        assertEquals(200f, horizontal.start, 0.001f)
        assertEquals(260f, horizontal.end, 0.001f)
    }

    @Test
    fun `beyond the threshold nothing catches and the grid applies`() {
        val other = SnapRect(200f, 100f, 260f, 140f)
        // left edge at 267 is 7 px from 260: outside the 6 px threshold; center 287 -> grid 290
        val result = engine.snap(rect(287f, 301f), listOf(other), areaW, areaH)

        assertFalse(result.objectSnapX)
        assertEquals(290f, result.centerX, 0.001f)
    }

    @Test
    fun `exactly at the threshold still catches`() {
        val other = SnapRect(200f, 100f, 260f, 140f)
        val result = engine.snap(rect(286f, 301f), listOf(other), areaW, areaH)

        assertTrue(result.objectSnapX)
        assertEquals(260f, result.rect.left, 0.001f)
    }

    @Test
    fun `the nearest candidate wins`() {
        val a = SnapRect(100f, 400f, 150f, 440f)
        val b = SnapRect(300f, 400f, 350f, 440f)
        // moved left edge at 297: 3 px from b.left (300), 147 px from a.right (150)
        val result = engine.snap(rect(317f, 300f), listOf(a, b), areaW, areaH)

        assertEquals(300f, result.rect.left, 0.001f)
    }

    @Test
    fun `screen center and screen edges catch`() {
        val center = engine.snap(rect(497f, 303f), emptyList(), areaW, areaH)
        assertEquals(500f, center.centerX, 0.001f)
        assertEquals(300f, center.centerY, 0.001f)
        val vertical = center.guides.first { it.vertical && it.position == 500f }
        assertEquals(0f, vertical.start, 0.001f)
        assertEquals(areaH, vertical.end, 0.001f)

        val edge = engine.snap(rect(23f, 300f), emptyList(), areaW, areaH) // left edge at 3
        assertEquals(0f, edge.rect.left, 0.001f)
        assertTrue(edge.objectSnapX)
    }

    @Test
    fun `x and y snap independently`() {
        val other = SnapRect(200f, 100f, 260f, 140f)
        // x catches (left 263 -> 260), y is far from everything and goes to the grid
        val result = engine.snap(rect(283f, 403f), listOf(other), areaW, areaH)

        assertTrue(result.objectSnapX)
        assertFalse(result.objectSnapY)
        assertEquals(400f, result.centerY, 0.001f)
    }

    @Test
    fun `anchors identify the locked lines`() {
        val other = SnapRect(200f, 100f, 260f, 140f)
        val result = engine.snap(rect(283f, 120f), listOf(other), areaW, areaH)

        assertTrue(result.anchors.contains(SnapAnchor(vertical = true, position = 260)))
        assertTrue(result.anchors.contains(SnapAnchor(vertical = false, position = 120)))
    }

    @Test
    fun `zero grid size does not divide by zero`() {
        val noGrid = SnapEngine(gridSize = 0f, threshold = 6f)
        val result = noGrid.snap(rect(103f, 47f), emptyList(), areaW, areaH)

        assertEquals(103f, result.centerX, 0.001f)
    }
}
