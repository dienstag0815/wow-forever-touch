package app.gamenative.ui.screen.xserver.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class ResizeMathTest {
    @Test
    fun `doubling the distance doubles the scale`() {
        assertEquals(2.0f, ResizeMath.scaleFromDistance(1.0f, 100f, 200f), 0.001f)
    }

    @Test
    fun `scale is clamped to the allowed range`() {
        assertEquals(ResizeMath.MAX_SCALE, ResizeMath.scaleFromDistance(2.0f, 10f, 1000f), 0.001f)
        assertEquals(ResizeMath.MIN_SCALE, ResizeMath.scaleFromDistance(1.0f, 100f, 1f), 0.001f)
    }

    @Test
    fun `a tiny start distance keeps the start scale`() {
        assertEquals(1.5f, ResizeMath.scaleFromDistance(1.5f, 0f, 300f), 0.001f)
    }

    @Test
    fun `values are rounded to five percent steps`() {
        assertEquals(1.25f, ResizeMath.tidy(1.27f), 0.001f)
        assertEquals(1.3f, ResizeMath.tidy(1.29f), 0.001f)
    }

    @Test
    fun `values close to one snap to exactly one`() {
        assertEquals(1.0f, ResizeMath.tidy(1.04f), 0.0f)
        assertEquals(1.0f, ResizeMath.tidy(0.96f), 0.0f)
    }

    @Test
    fun `nan falls back to one`() {
        assertEquals(1.0f, ResizeMath.clampScale(Float.NaN), 0.0f)
    }
}
