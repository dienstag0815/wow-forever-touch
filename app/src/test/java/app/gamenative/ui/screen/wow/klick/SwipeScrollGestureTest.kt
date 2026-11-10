package app.gamenative.ui.screen.wow.klick

import org.junit.Rule
import app.gamenative.TestTexts
import app.gamenative.ui.screen.wow.klick.GestureEvent.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wischen in Listen (Mausrad statt Kamera / Halten) in der Gestenerkennung: Tippen, senkrechtes Wischen in und außerhalb von
 * Scrollbereichen, Diagonale, waagrecht, Halten vor dem Wischen, zweiter Finger, Nachlauf.
 *
 * Die Scrollbereiche sind hier fest: ein Rechteck x 100..500, y 100..900; Schritt 40 px, Schwelle 30 px.
 */
private class SwipeSim(areaOn: Boolean = true, holdLeft: Boolean = true, wheel: Boolean = true, window: Long = 0L) {
    val g = OneFingerGesture(
        holdMs = 350L,
        holdLeftEnabled = holdLeft,
        wheelEnabled = wheel,
        tapWindowMs = window,
        geometry = ClickWheel.Geometry(innerRadius = 30f, outerRadius = 100f),
        scrollAreaAt = { x, y -> areaOn && x in 100f..500f && y in 100f..900f },
        scrollStepPx = { 40f },
        scrollThresholdPx = 30f,
    )
    var now = 1_000L
    private var timerAt = -1L
    val log = ArrayList<GestureEvent>()

    private fun apply(s: GestureStep): List<GestureEvent> {
        if (s.disarm) timerAt = -1L
        if (s.armMs >= 0) timerAt = now + s.armMs
        log.addAll(s.events)
        return s.events
    }

    fun wait(ms: Long): List<GestureEvent> {
        val target = now + ms
        val out = ArrayList<GestureEvent>()
        if (timerAt in 0..target) {
            now = timerAt
            timerAt = -1L
            out += apply(g.timerFired())
        }
        now = target
        return out
    }

    fun down(x: Float, y: Float) = apply(g.down(x, y, now))
    fun move(x: Float, y: Float) = apply(g.move(x, y, now))
    fun up(x: Float, y: Float) = apply(g.up(x, y, now))
    fun cancel() = apply(g.cancel())
    val timerArmed get() = timerAt >= 0
    fun kinds() = log.map { it.kind }
    fun ticks() = log.filter { it.kind == Kind.SCROLL }.sumOf { it.ticks }
}

class SwipeScrollGestureTest {
    @get:Rule
    val texts = TestTexts.rule("de")

    @Test
    fun aTapInAListIsStillAClick() {
        val s = SwipeSim()
        s.down(300f, 500f)
        s.wait(60)
        val e = s.up(301f, 502f)
        assertEquals(listOf(Kind.CLICK), e.map { it.kind })
        assertEquals(0, s.ticks())
    }

    @Test
    fun movingALittleStaysATapAndNoScroll() {
        val s = SwipeSim()
        s.down(300f, 500f)
        s.move(302f, 515f) // 15 px: under the threshold of 30
        s.move(298f, 520f)
        assertEquals(OneFingerGesture.Phase.FIRST_DOWN, s.g.phase)
        assertEquals(listOf(Kind.CLICK), s.up(298f, 520f).map { it.kind })
    }

    @Test
    fun aVerticalSwipeDownInAListScrollsWheelUpLikeAPhone() {
        val s = SwipeSim()
        s.down(300f, 500f)
        s.wait(40)
        // 30 px: the swipe starts; 30 / 40 px per step = no whole step yet
        val start = s.move(300f, 530f)
        assertEquals(listOf(Kind.SCROLL_STARTED), start.map { it.kind })
        assertEquals(300f, start[0].x, 0f)
        assertEquals(500f, start[0].y, 0f) // the pointer goes back to the touch-down point, inside the list
        assertEquals(OneFingerGesture.Phase.SCROLL, s.g.phase)
        assertFalse("the hold timer is gone", s.timerArmed)
        // 20 more px = 50 in total: one step (finger down = content follows down = wheel up = positive)
        val e = s.move(300f, 550f)
        assertEquals(listOf(Kind.SCROLL), e.map { it.kind })
        assertEquals(1, e[0].ticks)
        assertEquals(300f, e[0].x, 0f)
        assertEquals(500f, e[0].y, 0f)
        // nothing more on a tiny move, the remainder (10 px) is kept
        assertTrue(s.move(300f, 555f).isEmpty())
        assertEquals(2, s.move(300f, 625f).sumOf { it.ticks }) // 75 px more + 15 left = 90 -> 2 steps (80), 10 left
        s.up(300f, 625f)
        assertEquals(OneFingerGesture.Phase.IDLE, s.g.phase)
        assertEquals(3, s.ticks())
        assertFalse(Kind.CLICK in s.kinds())
        assertFalse(Kind.CAMERA_STARTED in s.kinds())
    }

    @Test
    fun swipingUpScrollsTheWheelDown() {
        val s = SwipeSim()
        s.down(300f, 800f)
        s.move(300f, 770f) // start: 30 up
        s.move(300f, 690f) // 110 px up in total = 2 whole steps (80), 30 left
        assertEquals(-2, s.ticks())
        s.up(300f, 690f)
        assertTrue(s.log.filter { it.kind == Kind.SCROLL }.all { it.ticks < 0 })
    }

    @Test
    fun theDistanceCountsFromTheTouchDownPoint() {
        // A fast first move: all of it counts, not only what is beyond the threshold.
        val s = SwipeSim()
        s.down(300f, 300f)
        s.move(300f, 420f) // 120 px at once = 3 steps
        assertEquals(listOf(Kind.SCROLL_STARTED, Kind.SCROLL), s.kinds())
        assertEquals(3, s.ticks())
    }

    @Test
    fun reversingTheDirectionTurnsTheWheelAround() {
        val s = SwipeSim()
        s.down(300f, 500f)
        s.move(300f, 580f) // +2
        s.move(300f, 500f) // 80 px back = -2
        assertEquals(listOf(2, -2), s.log.filter { it.kind == Kind.SCROLL }.map { it.ticks })
    }

    @Test
    fun outsideAListASwipeIsTheCameraAsBefore() {
        val s = SwipeSim()
        s.down(800f, 500f) // outside x 100..500
        val e = s.move(800f, 590f) // more than the 50 px move tolerance
        assertEquals(listOf(Kind.CAMERA_STARTED), e.map { it.kind })
        assertEquals(OneFingerGesture.Phase.CAMERA, s.g.phase)
        assertEquals(0, s.ticks())
        s.up(800f, 590f)
        assertEquals(listOf(Kind.CAMERA_STARTED, Kind.CAMERA_ENDED), s.kinds())
    }

    @Test
    fun withoutAnyAreaNothingChanges() {
        val s = SwipeSim(areaOn = false)
        s.down(300f, 500f)
        assertEquals(listOf(Kind.CAMERA_STARTED), s.move(300f, 590f).map { it.kind })
        assertEquals(0, s.ticks())
    }

    @Test
    fun aDiagonalMoveInAListIsTheCameraAsBefore() {
        val s = SwipeSim()
        s.down(300f, 500f)
        assertTrue(s.move(330f, 535f).isEmpty()) // 35 px down but 30 sideways: not vertical enough, not yet past the tolerance (46 < 50)
        assertEquals(OneFingerGesture.Phase.FIRST_DOWN, s.g.phase)
        assertEquals(listOf(Kind.CAMERA_STARTED), s.move(400f, 560f).map { it.kind }) // 100 sideways, 60 down: the camera
        assertEquals(0, s.ticks())
    }

    @Test
    fun aHorizontalMoveInAListIsTheCameraAsBefore() {
        val s = SwipeSim()
        s.down(300f, 500f)
        assertEquals(listOf(Kind.CAMERA_STARTED), s.move(400f, 505f).map { it.kind })
        assertEquals(0, s.ticks())
    }

    @Test
    fun aSwipeThatTurnsDiagonalAfterTheStartKeepsScrolling() {
        // Once it is a swipe it stays one until the finger lifts (a list swipe drifts sideways).
        val s = SwipeSim()
        s.down(300f, 500f)
        s.move(300f, 535f)
        assertEquals(OneFingerGesture.Phase.SCROLL, s.g.phase)
        s.move(420f, 640f) // far to the side
        assertEquals(OneFingerGesture.Phase.SCROLL, s.g.phase)
        assertEquals(3, s.ticks()) // 140 px down = 3 steps
        assertFalse(Kind.CAMERA_STARTED in s.kinds())
    }

    @Test
    fun restingFirstIsAHoldAndTheSwipeAfterItDragsInsteadOfScrolling() {
        val s = SwipeSim()
        s.down(300f, 500f)
        s.wait(350)
        assertEquals(OneFingerGesture.Phase.HOLD_LEFT, s.g.phase)
        assertEquals(listOf(Kind.LEFT_MOVE), s.move(300f, 600f).map { it.kind })
        assertEquals(0, s.ticks())
        assertEquals(listOf(Kind.LEFT_DOWN, Kind.LEFT_MOVE, Kind.LEFT_UP), s.also { it.up(300f, 600f) }.kinds())
    }

    @Test
    fun withHoldSwitchedOffTheSwipeStillScrolls() {
        val s = SwipeSim(holdLeft = false, wheel = false)
        s.down(300f, 500f)
        assertFalse(s.timerArmed)
        s.move(300f, 600f)
        assertEquals(2, s.ticks())
    }

    @Test
    fun theSecondTouchOfTapAndHoldNeverScrolls() {
        // Tap, touch again near the first tap and swipe: that is the wheel gesture, not a list swipe.
        val s = SwipeSim(window = 250L)
        s.down(300f, 500f)
        s.up(300f, 500f)
        assertEquals(OneFingerGesture.Phase.WAIT_SECOND, s.g.phase)
        s.wait(50)
        s.down(305f, 505f)
        assertEquals(OneFingerGesture.Phase.SECOND_DOWN, s.g.phase)
        val e = s.move(305f, 620f)
        assertFalse(Kind.SCROLL in e.map { it.kind })
        assertEquals(0, s.ticks())
    }

    @Test
    fun aSecondFingerEndsTheSwipeWithoutAClick() {
        val s = SwipeSim()
        s.down(300f, 500f)
        s.move(300f, 600f)
        assertEquals(OneFingerGesture.Phase.SCROLL, s.g.phase)
        val e = s.cancel()
        assertTrue(e.isEmpty())
        assertEquals(OneFingerGesture.Phase.IDLE, s.g.phase)
        assertFalse(Kind.CLICK in s.kinds())
        assertTrue(s.g.abort().events.isEmpty())
    }

    @Test
    fun aNewTouchAfterTheSwipeStartsFresh() {
        val s = SwipeSim()
        s.down(300f, 500f)
        s.move(300f, 600f)
        s.up(300f, 600f)
        s.wait(500)
        s.down(300f, 500f)
        assertEquals(OneFingerGesture.Phase.FIRST_DOWN, s.g.phase)
        assertEquals(listOf(Kind.CLICK), s.up(300f, 500f).map { it.kind })
    }

    @Test
    fun aLostLiftBeforeANewTouchIsClosedFirst() {
        val s = SwipeSim()
        s.down(300f, 500f)
        s.move(300f, 600f)
        val e = s.down(300f, 300f) // the lift of the swipe never arrived
        assertTrue(e.isEmpty())
        assertEquals(OneFingerGesture.Phase.FIRST_DOWN, s.g.phase)
    }

    // ── coasting ─────────────────────────────────────────────────────

    @Test
    fun aFastSwipeThatLiftsWhileMovingCoastsAFewSteps() {
        val s = SwipeSim()
        s.down(300f, 300f)
        for (i in 1..8) {
            s.wait(10)
            s.move(300f, 300f + 20f * i) // 2 px per ms
        }
        val up = s.up(300f, 460f)
        val fling = up.filter { it.kind == Kind.SCROLL_FLING }
        assertEquals(1, fling.size)
        assertTrue(fling[0].ticks in 1..6)
        assertEquals(300f, fling[0].x, 0f)
        assertEquals(300f, fling[0].y, 0f)
    }

    @Test
    fun theCoastGoesTheDirectionOfTheSwipeAndIsLimited() {
        val s = SwipeSim()
        s.down(300f, 800f)
        for (i in 1..10) {
            s.wait(5)
            s.move(300f, 800f - 60f * i) // 12 px per ms upwards, very fast
        }
        val fling = s.up(300f, 200f).single { it.kind == Kind.SCROLL_FLING }
        assertEquals(-6, fling.ticks) // limit of 6 steps, wheel down for a swipe up
    }

    @Test
    fun aSlowSwipeOrOneThatRestedBeforeLiftingDoesNotCoast() {
        val slow = SwipeSim()
        slow.down(300f, 300f)
        for (i in 1..6) {
            slow.wait(100)
            slow.move(300f, 300f + 10f * i) // 0.1 px per ms
        }
        assertTrue(slow.up(300f, 360f).none { it.kind == Kind.SCROLL_FLING })

        val rested = SwipeSim()
        rested.down(300f, 300f)
        for (i in 1..8) {
            rested.wait(10)
            rested.move(300f, 300f + 20f * i)
        }
        rested.wait(200) // the finger stopped before it lifted
        assertTrue(rested.up(300f, 460f).none { it.kind == Kind.SCROLL_FLING })
    }

    @Test
    fun withoutTimestampsThereIsNoCoast() {
        // The unit tests of the other gestures call move(x, y) without a time: that must not coast.
        val g = OneFingerGesture(
            geometry = ClickWheel.Geometry(30f, 100f),
            scrollAreaAt = { _, _ -> true },
            scrollStepPx = { 40f },
            scrollThresholdPx = 30f,
        )
        g.down(300f, 300f, 0)
        g.move(300f, 400f)
        val e = g.up(300f, 400f, 10).events
        assertTrue(e.none { it.kind == Kind.SCROLL_FLING })
    }

    // ── robustness ───────────────────────────────────────────────────

    @Test
    fun aBrokenAreaCheckMeansNoScroll() {
        val g = OneFingerGesture(
            geometry = ClickWheel.Geometry(30f, 100f),
            scrollAreaAt = { _, _ -> throw IllegalStateException("boom") },
            scrollStepPx = { 40f },
            scrollThresholdPx = 30f,
        )
        g.down(300f, 300f, 0)
        assertEquals(listOf(Kind.CAMERA_STARTED), g.move(300f, 400f, 5).events.map { it.kind })
    }

    @Test
    fun aTinyStepSettingIsLimited() {
        val g = OneFingerGesture(
            geometry = ClickWheel.Geometry(30f, 100f),
            scrollAreaAt = { _, _ -> true },
            scrollStepPx = { 0.5f },
            scrollThresholdPx = 30f,
        )
        g.down(300f, 300f, 0)
        val ticks = g.move(300f, 360f, 5).events.filter { it.kind == Kind.SCROLL }.sumOf { it.ticks }
        // 60 px at the lower limit of 6 px per step = 10 steps (not 120)
        assertEquals(10, ticks)
    }

    @Test
    fun aVeryFastMoveSendsAtMostTwelveStepsPerEvent() {
        val s = SwipeSim()
        s.down(300f, 100f)
        s.move(300f, 900f) // 800 px = 20 steps at once
        assertEquals(12, s.ticks())
    }

    @Test
    fun theSwipeUsesTheStepOfThatTouch() {
        var step = 40f
        val g = OneFingerGesture(
            geometry = ClickWheel.Geometry(30f, 100f),
            scrollAreaAt = { _, _ -> true },
            scrollStepPx = { step },
            scrollThresholdPx = 30f,
        )
        g.down(300f, 300f, 0)
        assertEquals(2, g.move(300f, 380f, 5).events.sumOf { it.ticks })
        g.up(300f, 380f, 10)
        step = 20f // the setting changed between two touches
        g.down(300f, 300f, 1_000)
        assertEquals(4, g.move(300f, 380f, 1_005).events.sumOf { it.ticks })
    }
}
