package app.gamenative.ui.screen.wow.context

import app.gamenative.ui.touchstyle.RadialArc
import app.gamenative.ui.touchstyle.RadialOpen
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The context menu as ring segments around the pressed point: rings, edge handling, free areas, hits. */
class ContextMenuLayoutTest {
    // density 1: bands 58 thick, first ring from a middle radius of 80, margin 8, dead zone 34
    private val geo = ContextMenuLayout.Geometry.forDensity(1f)
    private val screenW = 1280f
    private val screenH = 800f

    private fun compute(
        count: Int,
        x: Float = 640f,
        y: Float = 400f,
        keep: List<MenuRect> = emptyList(),
        rings: List<Int>? = null,
    ) = ContextMenuLayout.compute(count, x, y, screenW, screenH, geo, keep, ContextMenuLayout.ARC_DEG, rings)

    private fun insideScreen(r: ContextMenuLayout.Result): Boolean {
        val b = r.shape.bounds()
        return r.centerX + b.left >= geo.margin - 0.01f && r.centerY + b.top >= geo.margin - 0.01f &&
            r.centerX + b.right <= screenW - geo.margin + 0.01f && r.centerY + b.bottom <= screenH - geo.margin + 0.01f
    }

    private fun fingerFree(r: ContextMenuLayout.Result, x: Float, y: Float) =
        r.segments.none { RadialArc.distanceTo(it, x - r.centerX, y - r.centerY) < geo.dead }

    private fun degrees(dx: Float, dy: Float) = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat()

    @Test
    fun theConstantsAreTheAgreedOnesAndTheSameAsAllOtherRadialMenus() {
        assertEquals(270f, ContextMenuLayout.ARC_DEG, 0f)
        assertEquals(RadialArc.ARC_DEG, ContextMenuLayout.ARC_DEG, 0f)
        assertEquals(8, ContextMenuLayout.FIRST_RING_CAP)
        assertEquals(RadialArc.MAX_STEP_DEG, ContextMenuLayout.MAX_STEP_DEG, 0f)
        assertEquals(RadialArc.ARC_DEG, compute(5).arcDeg, 0f)
    }

    @Test
    fun noEntriesNoSegments() {
        val r = compute(0)
        assertTrue(r.segments.isEmpty())
        assertEquals(-1, ContextMenuLayout.indexAt(r, 640f, 400f, geo))
    }

    // ---- the arc ---------------------------------------------------------------------------------------------

    @Test
    fun theSegmentsLieOnTheUpperArcAndTheWedgeBelowTheFingerStaysFree() {
        for (n in listOf(1, 3, 5, 8, 9, 12)) {
            val r = compute(n)
            assertEquals(n, r.segments.size)
            for (seg in r.segments) {
                assertTrue("n=$n start ${seg.startDeg}", seg.startDeg >= -225f - 0.01f)
                assertTrue("n=$n end", seg.startDeg + seg.sweepDeg <= 45f + 0.01f)
            }
            // nothing in the wedge below the finger: a point right below hits nothing at any radius
            for (radius in listOf(40f, 100f, 160f, 210f)) {
                val x = r.centerX
                val y = r.centerY + radius
                assertEquals("n=$n radius=$radius", -1, ContextMenuLayout.indexAt(r, x, y, geo))
            }
        }
    }

    @Test
    fun theMostImportantEntriesLieOnTopInTheMiddle() {
        val r = compute(5)
        assertEquals(-90f, r.segments[0].midDeg, 0.01f)
        assertEquals(-45f, r.segments[1].midDeg, 0.01f)
        assertEquals(-135f, r.segments[2].midDeg, 0.01f)
    }

    @Test
    fun theRingsHoldEightEntriesEach() {
        assertEquals(listOf(8), ContextMenuLayout.ringSizes(8))
        assertEquals(listOf(8, 1), ContextMenuLayout.ringSizes(9))
        assertEquals(listOf(8, 8, 2), ContextMenuLayout.ringSizes(18))
        assertTrue(ContextMenuLayout.ringSizes(0).isEmpty())
        assertEquals(1, compute(8).shape.rings.size)
        assertEquals(2, compute(9).shape.rings.size)
    }

    @Test
    fun aNinthEntryStartsASecondRingOutsideAndBehindTheFirst() {
        val r = compute(11)
        assertEquals(listOf(8, 3), r.shape.rings.map { it.count })
        assertEquals(r.shape.rings[0].outer + geo.arc.ringGap, r.shape.rings[1].inner, 0.01f)
        assertTrue(r.segments.drop(8).all { it.ring == 1 && it.inner > r.shape.rings[0].outer })
    }

    @Test
    fun theCallerCanSplitTheRings() {
        val r = compute(11, rings = listOf(6, 5))
        assertEquals(listOf(6, 5), r.shape.rings.map { it.count })
        assertEquals(listOf(0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1), r.segments.map { it.ring })
    }

    // ---- edges and free areas -----------------------------------------------------------------------------------

    @Test
    fun inTheMiddleNothingIsShiftedOrRotated() {
        for (n in listOf(1, 3, 5, 6, 8, 9, 12)) {
            val r = compute(n)
            assertFalse("n=$n", r.shifted)
            assertEquals(0f, r.rotationDeg, 0f)
            assertTrue(insideScreen(r))
        }
    }

    @Test
    fun atEveryEdgeAndCornerTheMenuStaysOnTheScreenAndTheFingerStaysFree() {
        val points = listOf(
            20f to 20f, 30f to 400f, 1260f to 780f, 640f to 790f, 640f to 10f, 20f to 780f, 1260f to 20f, 1270f to 400f,
        )
        for ((x, y) in points) {
            for (n in listOf(5, 8, 12)) {
                val r = compute(n, x, y)
                assertTrue("($x, $y) n=$n inside", insideScreen(r))
                assertTrue("($x, $y) n=$n finger free", fingerFree(r, x, y))
            }
        }
    }

    @Test
    fun atTheBottomEdgeTheWedgeIsWhereTheScreenEndsSoNothingIsTurned() {
        // the free wedge points down: near the bottom edge the menu just moves up a little, the arc stays as it is
        val r = compute(5, 640f, 790f)
        assertEquals(0f, r.rotationDeg, 0f)
        assertTrue(r.shifted)
    }

    @Test
    fun atTheTopEdgeTheArcOpensDownwardsAndTheMiddleStaysOnTheFinger() {
        // 100 below the top edge: upwards (109) would run off the screen, downwards fits
        val r = compute(5, 640f, 100f)
        assertEquals(180f, r.rotationDeg, 0f)
        assertFalse(r.shifted)
        assertTrue(insideScreen(r))
        // the entries now lie below the finger, the wedge points up
        assertTrue(r.segments.all { it.midY() > -1f })
        assertEquals(-1, ContextMenuLayout.indexAt(r, r.anchorX, r.anchorY - 90f, geo))
        assertEquals(0, ContextMenuLayout.indexAt(r, r.centerX, r.centerY + r.segments[0].midRadius, geo))
    }

    @Test
    fun atTheSideEdgesTheArcOpensTowardsTheMiddleOfTheScreen() {
        val left = compute(5, 100f, 400f)
        assertEquals(90f, left.rotationDeg, 0f)
        assertFalse(left.shifted)
        val right = compute(5, 1180f, 400f)
        assertEquals(-90f, right.rotationDeg, 0f)
        assertFalse(right.shifted)
    }

    @Test
    fun aFixedDirectionOfTheButtonIsKeptIfItFits() {
        val down = ContextMenuLayout.compute(5, 640f, 400f, screenW, screenH, geo, emptyList(), ContextMenuLayout.ARC_DEG, null, RadialOpen.DOWN)
        assertEquals(180f, down.rotationDeg, 0f)
        assertFalse(down.shifted)
        val auto = ContextMenuLayout.compute(5, 640f, 400f, screenW, screenH, geo)
        assertEquals(0f, auto.rotationDeg, 0f)
    }

    @Test
    fun theArcTurnsWhenTheMoveToTheScreenWouldPutTheFingerOnASegment() {
        // at the left edge the arc opens to the right (the least move); the finger stays free
        val r = compute(5, 30f, 400f)
        assertNotEquals(0f, r.rotationDeg, 0f)
        assertTrue(fingerFree(r, 30f, 400f))
        assertTrue(insideScreen(r))
    }

    @Test
    fun theRingStepsAsideFromTheTargetWindowIfThatIsPossible() {
        val zone = ContextMenuLayout.defaultKeepClear(screenW, screenH)
        val x = 300f
        val y = 150f
        fun covered(r: ContextMenuLayout.Result) =
            r.segments.indices.sumOf { i -> zone.sumOf { z -> z.overlapArea(r.rectOf(i)!!).toDouble() } }
        val plain = compute(5, x, y)
        val kept = compute(5, x, y, zone)
        assertTrue("the plain ring covers the window", covered(plain) > 0.0)
        assertTrue("with the area kept free less is covered", covered(kept) < covered(plain))
        assertTrue(fingerFree(kept, x, y))
        assertTrue(insideScreen(kept))
    }

    @Test
    fun theDefaultAreasAreTheUnitFramesAndTheTooltip() {
        val zones = ContextMenuLayout.defaultKeepClear(1000f, 600f)
        assertEquals(2, zones.size)
        // the unit frames top left, the tooltip to their right and lower
        assertTrue(zones[0].left < 100f && zones[0].top == 0f && zones[0].right > 400f)
        assertTrue(zones[1].left > 500f && zones[1].top > 50f)
    }

    // ---- hits ---------------------------------------------------------------------------------------------------

    @Test
    fun liftingAtTheFingerDoesNothingAndTheMiddleOfASegmentHits() {
        val r = compute(5)
        assertEquals(-1, ContextMenuLayout.indexAt(r, r.anchorX, r.anchorY, geo))
        assertEquals(-1, ContextMenuLayout.indexAt(r, r.anchorX + geo.dead - 1f, r.anchorY, geo))
        for ((i, seg) in r.segments.withIndex()) {
            assertEquals("segment $i", i, ContextMenuLayout.indexAt(r, r.centerX + seg.midX(), r.centerY + seg.midY(), geo))
        }
    }

    @Test
    fun aPointOutsideASegmentButInItsDirectionHitsItsEntry() {
        val r = compute(5)
        val top = r.segments[0]
        // a little outside the band, straight above the first segment
        assertEquals(0, ContextMenuLayout.indexAt(r, r.centerX, r.centerY - top.outer - 10f, geo))
        // in the hole between the finger and the band
        assertEquals(0, ContextMenuLayout.indexAt(r, r.centerX, r.centerY - (geo.dead + top.inner) / 2f, geo))
    }

    @Test
    fun aPointFarOutsideHitsNothing() {
        val r = compute(5)
        val top = r.segments[0]
        assertEquals(-1, ContextMenuLayout.indexAt(r, r.centerX, r.centerY - top.outer - geo.arc.thickness - 10f, geo))
    }

    @Test
    fun onTheSecondRingTheOuterEntryIsFoundNotTheInnerOne() {
        val r = compute(11)
        val outer = r.segments[8]
        assertEquals(8, ContextMenuLayout.indexAt(r, r.centerX + outer.midX(), r.centerY + outer.midY(), geo))
        val inner = r.segments[0]
        assertEquals(0, ContextMenuLayout.indexAt(r, r.centerX + inner.midX(), r.centerY + inner.midY(), geo))
    }

    @Test
    fun theDirectionOfASegmentFromTheMiddleOfTheRingIsItsAngle() {
        val r = compute(5)
        for ((i, seg) in r.segments.withIndex()) {
            val x = r.centerX + seg.midX()
            val y = r.centerY + seg.midY()
            val d = degrees(x - r.centerX, y - r.centerY)
            assertEquals(i.toString(), 0f, RadialArc.angleDiff(seg.midDeg, d), 0.5f)
            assertEquals(seg.midRadius, hypot(x - r.centerX, y - r.centerY), 0.5f)
            assertEquals(cos(Math.toRadians(seg.midDeg.toDouble())).toFloat() * seg.midRadius, seg.midX(), 0.01f)
            assertEquals(sin(Math.toRadians(seg.midDeg.toDouble())).toFloat() * seg.midRadius, seg.midY(), 0.01f)
        }
    }

    @Test
    fun theDensityScalesEverything() {
        val g2 = ContextMenuLayout.Geometry.forDensity(2f)
        assertEquals(2f * geo.arc.thickness, g2.arc.thickness, 0.001f)
        assertEquals(2f * geo.arc.minMid, g2.arc.minMid, 0.001f)
        assertEquals(2f * geo.dead, g2.dead, 0.001f)
        assertEquals(2f * geo.margin, g2.margin, 0.001f)
        val r = ContextMenuLayout.compute(5, 1200f, 800f, 2560f, 1600f, g2)
        assertEquals(2f * 109.07f, r.shape.outerRadius, 1f)
    }
}
