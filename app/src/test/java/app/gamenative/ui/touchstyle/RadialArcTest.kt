package app.gamenative.ui.touchstyle

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The one layout of all radial menus: ring segments on an arc of 270 degrees, open at the bottom (angles, rings, hits). */
class RadialArcTest {
    // density 1: bands 58 thick, segments at least 56 wide, first ring from a middle radius of 80, 6 between rings
    private val geo = RadialArc.Geometry.forDensity(1f)
    private val dead = 34f

    private fun shape(
        n: Int,
        order: RadialArc.Order = RadialArc.Order.IMPORTANCE,
        maxStep: Float = RadialArc.MAX_STEP_DEG,
        rotation: Float = 0f,
        rings: List<Int>? = null,
    ) = RadialArc.build(n, geo, order, maxStep, RadialArc.ARC_DEG, rotation, rings)

    private fun at(radius: Float, deg: Float): Pair<Float, Float> {
        val rad = Math.toRadians(deg.toDouble())
        return Pair((radius * cos(rad)).toFloat(), (radius * sin(rad)).toFloat())
    }

    private fun hit(s: RadialArc.Shape, radius: Float, deg: Float): Int {
        val p = at(radius, deg)
        return RadialArc.indexAt(s, p.first, p.second, dead)
    }

    private val orders = listOf(
        Triple(RadialArc.Order.IMPORTANCE, RadialArc.MAX_STEP_DEG, "context"),
        Triple(RadialArc.Order.SEQUENTIAL, RadialArc.WIDE_STEP_DEG, "fixed menus"),
    )

    @Test
    fun theArcIsOneConstantAndTheWedgeBelowIsFree() {
        assertEquals(270f, RadialArc.ARC_DEG, 0f)
        assertEquals(8, RadialArc.FIRST_RING_CAP)
        assertEquals(360f - RadialArc.ARC_DEG, 90f, 0f)
        assertEquals(RadialArc.ARC_DEG, shape(5).arcDeg, 0f)
        assertEquals(RadialArc.ARC_DEG / 4f, RadialArc.WIDE_STEP_DEG, 0f)
    }

    @Test
    fun noEntriesNoSegments() {
        val s = shape(0)
        assertTrue(s.segments.isEmpty())
        assertEquals(-1, RadialArc.indexAt(s, 0f, -100f, dead))
        assertEquals(0f, s.outerRadius, 0f)
    }

    @Test
    fun noSegmentLiesInTheFreeWedgeAtTheBottom() {
        for ((order, step, name) in orders) {
            for (n in 1..12) {
                val s = shape(n, order, step)
                assertEquals("$name n=$n", n, s.segments.size)
                for (seg in s.segments) {
                    // the wedge is 45..135 degrees (below the centre): every segment stays on the arc from -225 to 45
                    assertTrue("$name n=$n start ${seg.startDeg}", seg.startDeg >= -225f - 0.01f)
                    assertTrue("$name n=$n end ${seg.startDeg + seg.sweepDeg}", seg.startDeg + seg.sweepDeg <= 45f + 0.01f)
                }
            }
        }
    }

    @Test
    fun aHitInTheWedgeFindsNothingAtAnyRing() {
        for ((order, step, name) in orders) {
            for (n in 1..12) {
                val s = shape(n, order, step)
                val radii = s.rings.map { (it.inner + it.outer) / 2f } + listOf(dead + 1f, s.outerRadius + 5f)
                for (r in radii) {
                    for (deg in listOf(70f, 80f, 90f, 100f, 110f)) {
                        assertEquals("$name n=$n r=$r deg=$deg", -1, hit(s, r, deg))
                    }
                }
            }
        }
    }

    @Test
    fun theMostImportantEntryIsOnTopInTheMiddleThenRightAndLeftAlternating() {
        // odd count: the first segment is centred on the top
        val five = shape(5)
        assertEquals(-90f, five.segments[0].midDeg, 0.01f)
        assertEquals(-90f + 45f, five.segments[1].midDeg, 0.01f)      // second: right of the middle
        assertEquals(-90f - 45f, five.segments[2].midDeg, 0.01f)      // third: left of the middle
        assertEquals(-90f + 90f, five.segments[3].midDeg, 0.01f)
        assertEquals(-90f - 90f, five.segments[4].midDeg, 0.01f)
        // even count: the first is just right of the middle
        val four = shape(4)
        assertEquals(-90f + 22.5f, four.segments[0].midDeg, 0.01f)
        assertEquals(-90f - 22.5f, four.segments[1].midDeg, 0.01f)
    }

    @Test
    fun fewEntriesStayTogetherOnTopManyFillTheArc() {
        // three entries: a step of at most 45 degrees (135 degrees in all), eight: 270 / 8
        val three = shape(3)
        assertEquals(RadialArc.MAX_STEP_DEG, three.rings[0].pitchDeg, 0.001f)
        val eight = shape(8)
        assertEquals(270f / 8f, eight.rings[0].pitchDeg, 0.001f)
        assertEquals(1, shape(1).segments.size)
        assertEquals(-90f, shape(1).segments[0].midDeg, 0.01f)
    }

    @Test
    fun fixedMenusKeepTheirOrderClockwiseFromTheLeftEndOfTheArc() {
        for (n in 2..8) {
            val s = shape(n, RadialArc.Order.SEQUENTIAL, RadialArc.WIDE_STEP_DEG)
            val mids = s.segments.map { it.midDeg }
            assertEquals("n=$n clockwise", mids.sorted(), mids)
            for (i in 1 until n) assertTrue("n=$n step", mids[i] - mids[i - 1] > 0f)
            // symmetric around the top
            assertEquals("n=$n symmetric", -180f, mids.first() + mids.last(), 0.01f)
            // every entry keeps its index
            for ((i, seg) in s.segments.withIndex()) assertEquals(i, seg.index)
        }
        val six = shape(6, RadialArc.Order.SEQUENTIAL, RadialArc.WIDE_STEP_DEG)
        assertEquals(45f, six.rings[0].pitchDeg, 0.001f)                  // six fields: 45 degrees each, the whole arc
        assertEquals(-90f - 112.5f, six.segments[0].midDeg, 0.01f)
        assertEquals(-90f + 112.5f, six.segments[5].midDeg, 0.01f)
    }

    @Test
    fun moreThanEightEntriesGoOnASecondRingOutside() {
        assertEquals(listOf(8), RadialArc.ringSizes(8))
        assertEquals(listOf(8, 1), RadialArc.ringSizes(9))
        assertEquals(listOf(8, 4), RadialArc.ringSizes(12))
        assertEquals(emptyList<Int>(), RadialArc.ringSizes(0))
        val s = shape(12)
        assertEquals(2, s.rings.size)
        assertEquals(listOf(0, 8), s.rings.map { it.first })
        assertEquals(listOf(8, 4), s.rings.map { it.count })
        // the outer band sits one gap behind the inner one, like the outer band of the stick ring
        assertEquals(s.rings[0].outer + geo.ringGap, s.rings[1].inner, 0.01f)
        assertEquals(geo.thickness, s.rings[0].outer - s.rings[0].inner, 0.01f)
        assertEquals(geo.thickness, s.rings[1].outer - s.rings[1].inner, 0.01f)
        assertTrue(s.segments.take(8).all { it.ring == 0 } && s.segments.drop(8).all { it.ring == 1 })
        assertEquals(s.rings[1].outer, s.outerRadius, 0.01f)
    }

    @Test
    fun ringsCanBeSplitByTheCaller() {
        val s = shape(11, rings = listOf(6, 5))
        assertEquals(listOf(6, 5), s.rings.map { it.count })
        assertEquals(listOf(0, 6), s.rings.map { it.first })
        // a split that does not add up falls back to the standard one
        assertEquals(listOf(8, 3), shape(11, rings = listOf(6, 4)).rings.map { it.count })
        assertEquals(listOf(8, 3), shape(11, rings = listOf(11, 0)).rings.map { it.count })
        assertEquals(listOf(11), RadialArc.validRingCounts(11, listOf(11)))
    }

    @Test
    fun segmentsDoNotTouchAndKeepTheirWidth() {
        for ((order, step, name) in orders) {
            for (n in 1..16) {
                val s = shape(n, order, step)
                for (ring in s.rings) {
                    val ringSegs = s.segments.subList(ring.first, ring.first + ring.count).sortedBy { it.midDeg }
                    for (i in 1 until ringSegs.size) {
                        val gap = ringSegs[i].startDeg - (ringSegs[i - 1].startDeg + ringSegs[i - 1].sweepDeg)
                        assertTrue("$name n=$n gap $gap", gap >= RadialArc.SEPARATION_DEG - 0.01f)
                    }
                    for (seg in ringSegs) {
                        val chord = 2f * seg.midRadius * sin(Math.toRadians((seg.sweepDeg / 2f).toDouble())).toFloat()
                        assertTrue("$name n=$n width $chord", chord >= geo.minSegmentWidth - 0.5f)
                    }
                }
                // ring bands never overlap
                for (k in 1 until s.rings.size) assertTrue(s.rings[k].inner >= s.rings[k - 1].outer + geo.ringGap - 0.01f)
            }
        }
    }

    @Test
    fun theDeadZoneAndTheMiddleOfEachSegmentHit() {
        for ((order, step, name) in orders) {
            for (n in 1..12) {
                val s = shape(n, order, step)
                for ((i, seg) in s.segments.withIndex()) {
                    assertEquals("$name n=$n i=$i", i, RadialArc.indexAt(s, seg.midX(), seg.midY(), dead))
                    // inside the dead zone nothing, in the direction of the segment
                    val near = at(dead - 1f, seg.midDeg)
                    assertEquals(-1, RadialArc.indexAt(s, near.first, near.second, dead))
                }
            }
        }
        assertEquals(-1, RadialArc.indexAt(shape(5), 0f, 0f, dead))
    }

    @Test
    fun betweenTwoSegmentsTheNearerOneWinsAndTheGapBelongsToANeighbour() {
        val s = shape(6)
        val a = s.segments.first { it.index == 0 }
        val b = s.segments.first { it.index == 1 }
        val r = a.midRadius
        // a hair on the side of a: a; a hair on the side of b: b (the segments of 0 and 1 are neighbours)
        val mid = (a.midDeg + b.midDeg) / 2f
        assertEquals(0, hit(s, r, mid + (if (a.midDeg < b.midDeg) -1f else 1f)))
        assertEquals(1, hit(s, r, mid + (if (a.midDeg < b.midDeg) 1f else -1f)))
        // the separation between two segments is not a hole
        assertNotEquals(-1, hit(s, r, mid))
    }

    @Test
    fun outsideTheBandAndInTheHoleTheDirectionStillCounts() {
        val s = shape(5)
        val top = s.segments[0]
        // in the hole between the dead zone and the inner edge of the band: the entry in that direction
        assertEquals(0, hit(s, (dead + top.inner) / 2f, -90f))
        // a little outside the band (less than its thickness): still the entry in that direction
        assertEquals(0, hit(s, top.outer + 10f, -90f))
        // far outside (more than one band beyond): nothing
        assertEquals(-1, hit(s, top.outer + geo.thickness + 5f, -90f))
    }

    @Test
    fun onTwoRingsTheBandUnderTheFingerPicksTheEntry() {
        val s = shape(12)
        val inner = s.segments[0]
        val outer = s.segments[8]
        assertEquals(0, RadialArc.indexAt(s, inner.midX(), inner.midY(), dead))
        assertEquals(8, RadialArc.indexAt(s, outer.midX(), outer.midY(), dead))
        // at the angle of an outer entry, but in the inner band: the inner entry
        val p = at(inner.midRadius, outer.midDeg)
        val inInner = RadialArc.indexAt(s, p.first, p.second, dead)
        assertTrue(inInner in 0..7)
        // at the angle of the outer entry, in the gap between the bands (the nearer band wins, never nothing)
        val g = at((s.rings[0].outer + s.rings[1].inner) / 2f, outer.midDeg)
        assertNotEquals(-1, RadialArc.indexAt(s, g.first, g.second, dead))
    }

    @Test
    fun theBoundsEnclosePointsOnAllEdges() {
        val s = shape(5)
        val b = s.bounds()
        val outer = s.outerRadius
        assertEquals(-outer, b.top, 0.01f)                                // a segment is centred on the top
        assertEquals(-b.left, b.right, 0.01f)                              // symmetric
        assertTrue(b.bottom > 0f && b.bottom < outer)                      // the wedge below is free: the arc ends above the bottom
        assertTrue(b.right <= outer + 0.01f)
        for (seg in s.segments) {
            for (deg in listOf(seg.startDeg, seg.startDeg + seg.sweepDeg)) {
                for (radius in listOf(seg.inner, seg.outer)) {
                    val p = at(radius, deg)
                    assertTrue(p.first >= b.left - 0.01f && p.first <= b.right + 0.01f)
                    assertTrue(p.second >= b.top - 0.01f && p.second <= b.bottom + 0.01f)
                }
            }
        }
        assertEquals(RadialArc.Bounds(0f, 0f, 0f, 0f), RadialArc.boundsOf(emptyList()))
    }

    @Test
    fun theDistanceToASegmentIsZeroInsideAndGrowsOutside() {
        val seg = shape(5).segments[0]                                      // centred on the top
        assertEquals(0f, RadialArc.distanceTo(seg, seg.midX(), seg.midY()), 0.01f)
        val above = at(seg.outer + 10f, -90f)
        assertEquals(10f, RadialArc.distanceTo(seg, above.first, above.second), 0.01f)
        val hole = at(seg.inner - 10f, -90f)
        assertEquals(10f, RadialArc.distanceTo(seg, hole.first, hole.second), 0.01f)
        // the opposite side of the arc is far away
        val down = at(seg.midRadius, 90f)
        assertTrue(RadialArc.distanceTo(seg, down.first, down.second) > seg.midRadius)
    }

    @Test
    fun rotatingTurnsTheWholeArc() {
        val plain = shape(5)
        val turned = shape(5, rotation = 30f)
        assertEquals(30f, turned.rotationDeg, 0f)
        for (i in 0 until 5) assertEquals(plain.segments[i].midDeg + 30f, turned.segments[i].midDeg, 0.01f)
        // a turned arc hits where it was turned to
        assertEquals(0, hit(turned, plain.segments[0].midRadius, -60f))
        assertEquals(-1, hit(plain, plain.segments[0].midRadius, 90f))
    }

    @Test
    fun symbolAndNameAreSmallerThanTheSegment() {
        for (n in listOf(3, 6, 8)) {
            val s = shape(n)
            for (seg in s.segments) {
                val c = RadialArc.contentFor(seg, geo)
                assertTrue("symbol smaller than the band", c.iconSize <= geo.thickness * RadialArc.ICON_FRACTION + 0.01f)
                assertTrue(c.iconSize < (seg.outer - seg.inner) * 0.5f)
                assertEquals(seg.midX(), c.iconX, 0.01f)
                assertTrue("the name stands below the symbol", c.labelBaseline > c.iconY + c.iconSize / 2f - 0.01f)
                assertTrue("the name stays inside the band", c.labelBaseline <= seg.midY() + (seg.outer - seg.inner) / 2f)
                assertTrue(c.labelWidth > 0f && c.labelWidth <= 2f * seg.outer)
            }
        }
    }

    @Test
    fun angleHelpers() {
        assertEquals(350f, RadialArc.normalise(-10f), 0.001f)
        assertEquals(10f, RadialArc.normalise(370f), 0.001f)
        assertEquals(20f, RadialArc.angleDiff(350f, 10f), 0.001f)
        assertEquals(180f, RadialArc.angleDiff(0f, 180f), 0.001f)
        assertTrue(abs(RadialArc.angleDiff(-90f, 270f)) < 0.001f)
    }
}
