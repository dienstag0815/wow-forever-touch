package com.winlator.widget

import com.winlator.math.XForm
import org.junit.Assert.assertEquals
import org.junit.Test

/** Touch -> game picture mapping when the view size changes (fold/unfold). The picture keeps its start size. */
class TouchMappingTest {

    // Picture chosen at start on the unfolded inner display
    private val pictureW = 1600
    private val pictureH = 1440

    private fun map(xform: FloatArray, x: Float, y: Float): Pair<Float, Float> {
        val p = XForm.transformPoint(xform, x, y)
        return p[0] to p[1]
    }

    private fun assertMaps(xform: FloatArray, viewX: Float, viewY: Float, picX: Float, picY: Float, delta: Float = 1.5f) {
        val (x, y) = map(xform, viewX, viewY)
        assertEquals("x for ($viewX,$viewY)", picX, x, delta)
        assertEquals("y for ($viewX,$viewY)", picY, y, delta)
    }

    @Test
    fun unfoldedView_centerIsPictureCenter() {
        val xform = XForm.getInstance()
        TouchMapping.update(xform, 2184, 1968, pictureW, pictureH, false)
        assertMaps(xform, 1092f, 984f, 800f, 720f)
    }

    @Test
    fun foldedView_letterboxedPictureStillMapsCornersAndCenter() {
        val xform = XForm.getInstance()
        // Outer screen 2520x1080: the 1600x1440 picture shrinks to 1200x1080 and sits centered (bars of 660 px left and right).
        TouchMapping.update(xform, 2520, 1080, pictureW, pictureH, false)
        assertMaps(xform, 660f, 0f, 0f, 0f)
        assertMaps(xform, 1260f, 540f, 800f, 720f)
        assertMaps(xform, 1860f, 1080f, 1600f, 1440f)
    }

    @Test
    fun sizeChangeOnTheSameMapping_isRecomputedNotKept() {
        val xform = XForm.getInstance()
        TouchMapping.update(xform, 2184, 1968, pictureW, pictureH, false)
        val before = map(xform, 1260f, 540f)
        TouchMapping.update(xform, 2520, 1080, pictureW, pictureH, false)
        val after = map(xform, 1260f, 540f)
        // The same finger position hits a different picture position once the view is another size.
        assertEquals(800f, after.first, 1.5f)
        assertEquals(720f, after.second, 1.5f)
        assertEquals(true, kotlin.math.abs(before.first - after.first) > 100f)
    }

    @Test
    fun foldAndUnfoldAgain_returnsToTheSameMapping() {
        val xform = XForm.getInstance()
        TouchMapping.update(xform, 2184, 1968, pictureW, pictureH, false)
        val start = map(xform, 500f, 700f)
        TouchMapping.update(xform, 2520, 1080, pictureW, pictureH, false)
        TouchMapping.update(xform, 2184, 1968, pictureW, pictureH, false)
        val back = map(xform, 500f, 700f)
        assertEquals(start.first, back.first, 0.01f)
        assertEquals(start.second, back.second, 0.01f)
    }

    @Test
    fun widePictureOnSquareView_hasBarsTopAndBottom() {
        val xform = XForm.getInstance()
        // Started folded: picture 1833x786 (wide). Unfolded view 2184x1968 scales it up by 1.19 and centers it vertically.
        TouchMapping.update(xform, 2184, 1968, 1833, 786, false)
        assertMaps(xform, 1092f, 984f, 916.5f, 393f, 2f)
        // Top edge of the picture: (1968 - 786 * 1.1915) / 2 = 515.7
        assertMaps(xform, 0f, 516f, 0f, 0f, 2f)
    }

    @Test
    fun fullscreen_scalesWithoutOffset() {
        val xform = XForm.getInstance()
        TouchMapping.update(xform, 2000, 1000, 1000, 500, true)
        assertMaps(xform, 1000f, 500f, 500f, 250f, 0.01f)
    }

    @Test
    fun viewWithoutSize_keepsThePreviousMapping() {
        val xform = XForm.getInstance()
        TouchMapping.update(xform, 2184, 1968, pictureW, pictureH, false)
        val before = map(xform, 700f, 800f)
        TouchMapping.update(xform, 0, 0, pictureW, pictureH, false)
        val after = map(xform, 700f, 800f)
        assertEquals(before.first, after.first, 0f)
        assertEquals(before.second, after.second, 0f)
    }
}
