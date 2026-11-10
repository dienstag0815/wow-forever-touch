package app.gamenative.ui.touchstyle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Direction of the radial menus by screen area and per button: the middle stays on the pressed point, the arc turns towards the middle
 * of the screen, a fixed direction holds unless it would run off the screen, and only if nothing fits the menu is moved.
 */
class RadialPlacementTest {
    // density 1: bands 58 thick, first ring from a middle radius of 80 (outer 109), margin 8, dead zone 34
    private val geo = RadialArc.Geometry.forDensity(1f)
    private val screenW = 1280f
    private val screenH = 800f
    private val margin = 8f
    private val dead = 34f

    private fun build(n: Int): (Float) -> RadialArc.Shape =
        { rotation -> RadialArc.build(n, geo, RadialArc.Order.SEQUENTIAL, RadialArc.WIDE_STEP_DEG, RadialArc.ARC_DEG, rotation) }

    private fun choose(
        n: Int,
        x: Float,
        y: Float,
        direction: RadialOpen = RadialOpen.AUTO,
        w: Float = screenW,
        h: Float = screenH,
    ) = RadialPlacement.choose(build(n), x, y, w, h, margin, dead, direction)

    private fun inside(c: RadialPlacement.Choice, w: Float = screenW, h: Float = screenH): Boolean {
        val b = c.shape.bounds()
        return c.centerX + b.left >= margin - 0.01f && c.centerY + b.top >= margin - 0.01f &&
            c.centerX + b.right <= w - margin + 0.01f && c.centerY + b.bottom <= h - margin + 0.01f
    }

    private fun fingerFree(c: RadialPlacement.Choice, x: Float, y: Float) =
        c.shape.segments.none { RadialArc.distanceTo(it, x - c.centerX, y - c.centerY) < dead }

    // ---- the saved id ------------------------------------------------------------------------------------------

    @Test
    fun theIdsAreStableAndAnUnknownOrMissingIdMeansAutomatic() {
        assertNull(RadialOpen.AUTO.id)
        assertEquals(listOf("up", "down", "left", "right"), RadialOpen.values().mapNotNull { it.id })
        for (o in RadialOpen.values()) assertEquals(o, RadialOpen.fromId(o.id))
        assertEquals(RadialOpen.AUTO, RadialOpen.fromId(null))
        assertEquals(RadialOpen.AUTO, RadialOpen.fromId(""))
        assertEquals(RadialOpen.AUTO, RadialOpen.fromId("auto"))
        assertEquals(RadialOpen.AUTO, RadialOpen.fromId("diagonal"))
    }

    @Test
    fun theRotationOfAFixedDirectionPointsTheMiddleOfTheArcThere() {
        // arc middle = -90 + rotation (0 = right, 90 = down)
        assertEquals(-90f, RadialArc.CENTER_DEG + RadialOpen.UP.rotationDeg!!, 0f)
        assertEquals(90f, RadialArc.CENTER_DEG + RadialOpen.DOWN.rotationDeg!!, 0f)
        assertEquals(180f, RadialPlacement.signed(RadialArc.CENTER_DEG + RadialOpen.LEFT.rotationDeg!!), 0f)
        assertEquals(0f, RadialArc.CENTER_DEG + RadialOpen.RIGHT.rotationDeg!!, 0f)
        assertNull(RadialOpen.AUTO.rotationDeg)
    }

    // ---- automatic direction by screen area -----------------------------------------------------------------------

    @Test
    fun autoTurnsAwayFromTheEdgesAndStaysUpInTheMiddle() {
        val reach = 117f
        assertEquals(0f, RadialPlacement.autoRotation(640f, 400f, screenW, screenH, reach), 0f)
        // top edge: down; bottom edge: up (as before); left edge: right; right edge: left
        assertEquals(180f, RadialPlacement.autoRotation(640f, 30f, screenW, screenH, reach), 0f)
        assertEquals(0f, RadialPlacement.autoRotation(640f, 770f, screenW, screenH, reach), 0f)
        assertEquals(90f, RadialPlacement.autoRotation(30f, 400f, screenW, screenH, reach), 0f)
        assertEquals(-90f, RadialPlacement.autoRotation(1250f, 400f, screenW, screenH, reach), 0f)
    }

    @Test
    fun autoUsesDiagonalsInTheCorners() {
        val reach = 117f
        assertEquals(135f, RadialPlacement.autoRotation(50f, 50f, screenW, screenH, reach), 0f)
        assertEquals(-135f, RadialPlacement.autoRotation(1230f, 50f, screenW, screenH, reach), 0f)
        assertEquals(45f, RadialPlacement.autoRotation(50f, 750f, screenW, screenH, reach), 0f)
        assertEquals(-45f, RadialPlacement.autoRotation(1230f, 750f, screenW, screenH, reach), 0f)
    }

    @Test
    fun inTheMiddleTheMenuOpensUpOnThePressedPointWithoutMoving() {
        for (n in listOf(1, 4, 6, 8, 9, 12)) {
            val c = choose(n, 640f, 400f)
            assertEquals("n=$n", 0f, c.shape.rotationDeg, 0f)
            assertEquals(640f, c.centerX, 0.01f)
            assertEquals(400f, c.centerY, 0.01f)
            assertEquals(0f, c.shift, 0.01f)
        }
    }

    @Test
    fun atTheTopEdgeTheMenuOpensDownwardsAndStaysOnThePressedPoint() {
        // y = 100: opening upwards would run off the screen (109 up), opening downwards fits (75 up)
        val c = choose(6, 640f, 100f)
        assertEquals(180f, c.shape.rotationDeg, 0f)
        assertEquals(640f, c.centerX, 0.01f)
        assertEquals(100f, c.centerY, 0.01f)
        assertEquals(0f, c.shift, 0.01f)
        assertTrue(inside(c))
    }

    @Test
    fun atTheSideEdgesTheMenuOpensTowardsTheMiddle() {
        val left = choose(6, 100f, 400f)
        assertEquals(90f, left.shape.rotationDeg, 0f)
        assertEquals(100f, left.centerX, 0.01f)
        assertEquals(0f, left.shift, 0.01f)
        val right = choose(6, 1180f, 400f)
        assertEquals(-90f, right.shape.rotationDeg, 0f)
        assertEquals(1180f, right.centerX, 0.01f)
        assertEquals(0f, right.shift, 0.01f)
    }

    @Test
    fun atTheBottomEdgeTheArcStaysUpAndIsMovedOnlyAsMuchAsNeeded() {
        // the free wedge is where the screen ends; nothing fits without moving, so the least move wins
        val c = choose(6, 640f, 790f)
        assertEquals(0f, c.shape.rotationDeg, 0f)
        assertTrue(c.shift > 0f)
        assertTrue(inside(c))
        assertTrue(fingerFree(c, 640f, 790f))
    }

    @Test
    fun atEveryEdgeAndCornerTheMenuStaysOnTheScreenAndTheFingerStaysFree() {
        val points = listOf(
            20f to 20f, 30f to 400f, 1260f to 780f, 640f to 790f, 640f to 10f, 20f to 780f, 1260f to 20f, 1270f to 400f,
        )
        for ((x, y) in points) {
            for (n in listOf(3, 6, 8, 12)) {
                for (direction in RadialOpen.values()) {
                    val c = choose(n, x, y, direction)
                    assertTrue("($x, $y) n=$n $direction inside", inside(c))
                    assertTrue("($x, $y) n=$n $direction finger free", fingerFree(c, x, y))
                }
            }
        }
    }

    @Test
    fun theMenuIsMovedOnlyIfNoDirectionFitsAndThenOpensTheCheapestWay() {
        // 12 fields (two rings, 173 out) near the left edge of a small screen: nothing fits, opening to the right moves least
        val c = choose(12, 30f, 250f, RadialOpen.AUTO, 500f, 500f)
        assertEquals(90f, c.shape.rotationDeg, 0f)
        assertTrue(c.shift > 0f)
        assertTrue(inside(c, 500f, 500f))
    }

    // ---- fixed direction ------------------------------------------------------------------------------------------

    @Test
    fun aFixedDirectionHoldsWhenItFits() {
        for ((direction, rotation) in listOf(
            RadialOpen.UP to 0f, RadialOpen.DOWN to 180f, RadialOpen.LEFT to -90f, RadialOpen.RIGHT to 90f,
        )) {
            val c = choose(6, 640f, 400f, direction)
            assertEquals("$direction", rotation, c.shape.rotationDeg, 0f)
            assertEquals(0f, c.shift, 0.01f)
        }
    }

    @Test
    fun aFixedDirectionThatRunsOffTheScreenGivesWayToTheNearestOneThatFits() {
        // up at y = 100 would run off the top; the first direction in line that fits is down
        val c = choose(6, 640f, 100f, RadialOpen.UP)
        assertEquals(180f, c.shape.rotationDeg, 0f)
        assertEquals(0f, c.shift, 0.01f)
        assertEquals(100f, c.centerY, 0.01f)
        assertTrue(inside(c))
        // to the left at the left edge: the arc cannot open that way, so it goes to the right
        val left = choose(6, 100f, 400f, RadialOpen.LEFT)
        assertEquals(90f, left.shape.rotationDeg, 0f)
        assertEquals(0f, left.shift, 0.01f)
    }

    @Test
    fun aFixedDirectionOverridesTheAutomaticOneWhereBothFit() {
        // the left-edge case from above, but the button says "up" and up fits at y = 400 with the edge 140 away
        val c = choose(6, 140f, 400f, RadialOpen.UP)
        assertEquals(0f, c.shape.rotationDeg, 0f)
        assertEquals(0f, c.shift, 0.01f)
    }

    // ---- the candidate order ----------------------------------------------------------------------------------------

    @Test
    fun theCandidatesStartWithThePreferredDirectionAndListEveryStepOnce() {
        for (preferred in listOf(0f, 90f, 180f, -90f, 45f, -135f)) {
            val list = RadialPlacement.candidates(preferred)
            assertEquals(24, list.size)
            assertEquals(24, list.map { RadialArc.normalise(it) }.toSet().size)
            assertEquals(preferred, list[0], 0f)
            // the eight main directions come first, nearest to the wish first
            val main = list.take(8)
            assertTrue(main.all { RadialArc.normalise(it) % 45f == 0f })
            val distances = main.map { RadialArc.angleDiff(it, preferred) }
            assertEquals(distances.sorted(), distances)
            assertEquals(180f, distances.last(), 0f)
        }
    }

    @Test
    fun signedAnglesLieInMinus180ToPlus180() {
        assertEquals(-90f, RadialPlacement.signed(270f), 0f)
        assertEquals(180f, RadialPlacement.signed(180f), 0f)
        assertEquals(0f, RadialPlacement.signed(360f), 0f)
        assertEquals(-45f, RadialPlacement.signed(-45f), 0f)
    }

    // ---- hits do not change with the turn ----------------------------------------------------------------------------

    @Test
    fun theFieldsKeepTheirOrderAndEveryFieldIsHitUnderItsOwnMiddleAtEveryTurn() {
        for (n in listOf(4, 6, 8, 9, 12)) {
            val base = build(n)(0f)
            for (rotation in RadialPlacement.candidates(0f)) {
                val turned = build(n)(rotation)
                assertEquals(rotation, turned.rotationDeg, 0f)
                for (i in 0 until n) {
                    // same place on the arc, only turned
                    val shift = RadialArc.normalise(turned.segments[i].midDeg - base.segments[i].midDeg - rotation)
                    assertTrue("n=$n i=$i rot=$rotation", shift < 0.01f || shift > 359.99f)
                    assertEquals(base.segments[i].ring, turned.segments[i].ring)
                    val s = turned.segments[i]
                    assertEquals("n=$n i=$i rot=$rotation", i, RadialArc.indexAt(turned, s.midX(), s.midY(), 0f))
                }
            }
        }
    }

    @Test
    fun theWedgeIsFreeOnTheOppositeSideOfTheOpeningDirection() {
        // opening down: the wedge points up and nothing there is hit
        val down = build(8)(RadialOpen.DOWN.rotationDeg!!)
        for (radius in listOf(60f, 100f, 140f)) {
            assertEquals(-1, RadialArc.indexAt(down, 0f, -radius, 0f))
        }
        assertNotEquals(-1, RadialArc.indexAt(down, 0f, 80f, 0f))
        // opening right: the wedge points left
        val right = build(8)(RadialOpen.RIGHT.rotationDeg!!)
        for (radius in listOf(60f, 100f, 140f)) {
            assertEquals(-1, RadialArc.indexAt(right, -radius, 0f, 0f))
        }
        assertNotEquals(-1, RadialArc.indexAt(right, 80f, 0f, 0f))
        assertFalse(right.segments.isEmpty())
    }
}
