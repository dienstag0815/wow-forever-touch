package app.gamenative.ui.screen.wow.klick

import org.junit.Rule
import app.gamenative.TestTexts
import app.gamenative.ui.screen.wow.klick.GestureEvent.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A virtual clock plus the single timer the view would run, so the tests read like a finger on glass. */
private class Sim(
    window: Long = 250L,
    hold: Long = 350L,
    holdLeft: Boolean = true,
    wheel: Boolean = true,
) {
    val g = OneFingerGesture(
        holdMs = hold,
        holdLeftEnabled = holdLeft,
        wheelEnabled = wheel,
        tapWindowMs = window,
        geometry = ClickWheel.Geometry(innerRadius = 30f, outerRadius = 100f),
    )
    var now = 0L
    private var timerAt = -1L
    val log = ArrayList<GestureEvent>()

    private fun apply(s: GestureStep): List<GestureEvent> {
        if (s.disarm) timerAt = -1L
        if (s.armMs >= 0) timerAt = now + s.armMs
        log.addAll(s.events)
        return s.events
    }

    /** Lets time pass; the timer fires when its moment is reached. */
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
    fun move(x: Float, y: Float) = apply(g.move(x, y))
    fun up(x: Float, y: Float) = apply(g.up(x, y, now))
    fun cancel() = apply(g.cancel())
    val timerArmed get() = timerAt >= 0
    fun kinds() = log.map { it.kind }
}

class OneFingerGestureTest {
    @get:Rule
    val texts = TestTexts.rule("de")

    @Test
    fun defaults() {
        assertEquals(350L, OneFingerGesture.DEFAULT_HOLD_MS)
        assertEquals(250L, OneFingerGesture.DEFAULT_TAP_WINDOW_MS)
        assertEquals(250L, OneFingerGesture.DEFAULT_WHEEL_HOLD_MS)
        assertEquals(60f, OneFingerGesture.SECOND_TAP_DISTANCE_PX, 0f)
        assertEquals(50f, OneFingerGesture.MOVE_TOLERANCE_PX, 0f)
    }

    // ── tap ──────────────────────────────────────────────────────────

    @Test
    fun tap_clickIsHeldBackUntilTheWindowEnds() {
        val s = Sim()
        s.down(100f, 200f)
        s.wait(80)
        assertTrue(s.up(101f, 201f).isEmpty()) // nothing yet
        assertEquals(OneFingerGesture.Phase.WAIT_SECOND, s.g.phase)
        assertTrue(s.wait(249).isEmpty()) // still inside the window
        val e = s.wait(1)
        assertEquals(1, e.size)
        assertEquals(Kind.CLICK, e[0].kind)
        assertEquals(101f, e[0].x, 0f)
        assertEquals(201f, e[0].y, 0f)
        assertEquals(OneFingerGesture.Phase.IDLE, s.g.phase)
    }

    @Test
    fun tap_windowRunsFromTheLiftNotFromTheTouchDown() {
        val s = Sim()
        s.down(0f, 0f)
        s.wait(300) // hold timer is 350: still a tap
        s.up(0f, 0f)
        assertTrue(s.wait(249).isEmpty())
        assertEquals(listOf(Kind.CLICK), s.wait(1).map { it.kind })
    }

    @Test
    fun doubleTap_sendsBothClicks_noWheel() {
        val s = Sim()
        s.down(100f, 100f)
        s.up(100f, 100f)
        s.wait(120)
        s.down(110f, 105f) // within 60 px, inside the window
        s.wait(80) // less than the wheel hold time
        val e = s.up(110f, 105f)
        assertEquals(listOf(Kind.DOUBLE_CLICK), e.map { it.kind })
        assertEquals(110f, e[0].x, 0f)
        assertFalse(s.timerArmed)
        assertEquals(listOf(Kind.DOUBLE_CLICK), s.kinds()) // exactly one event, never a single click on top
        s.wait(1000)
        assertEquals(listOf(Kind.DOUBLE_CLICK), s.kinds())
    }

    @Test
    fun secondTapTooFarAway_flushesTheFirstClickAndStartsFresh() {
        val s = Sim()
        s.down(100f, 100f)
        s.up(100f, 100f)
        s.wait(100)
        val e = s.down(300f, 100f) // 200 px away
        assertEquals(listOf(Kind.CLICK), e.map { it.kind })
        assertEquals(100f, e[0].x, 0f)
        assertEquals(OneFingerGesture.Phase.FIRST_DOWN, s.g.phase)
        // the new touch is a normal tap again, held back as well
        assertTrue(s.up(300f, 100f).isEmpty())
        assertEquals(listOf(Kind.CLICK), s.wait(250).map { it.kind })
    }

    @Test
    fun secondTouchAfterTheWindow_isAFreshTap() {
        val s = Sim()
        s.down(0f, 0f)
        s.up(0f, 0f)
        s.wait(250) // click is out
        assertEquals(listOf(Kind.CLICK), s.kinds())
        s.down(5f, 5f)
        assertEquals(OneFingerGesture.Phase.FIRST_DOWN, s.g.phase)
    }

    // ── tap + hold = wheel ───────────────────────────────────────────

    @Test
    fun tapThenHold_opensTheWheelWithoutAnyClick() {
        val s = Sim()
        s.down(500f, 500f)
        s.up(500f, 500f)
        s.wait(100)
        s.down(505f, 498f)
        assertEquals(OneFingerGesture.Phase.SECOND_DOWN, s.g.phase)
        assertTrue(s.wait(249).isEmpty())
        val e = s.wait(1)
        assertEquals(listOf(Kind.WHEEL_OPENED), e.map { it.kind })
        assertEquals(505f, e[0].x, 0f)
        assertEquals(OneFingerGesture.Phase.WHEEL, s.g.phase)
        assertEquals("left", s.g.selected?.id)
        // nothing was clicked, neither now nor later
        s.wait(2000)
        assertEquals(listOf(Kind.WHEEL_OPENED), s.kinds())
    }

    @Test
    fun wheel_selectsByDirectionAndCommitsOnLift() {
        val s = Sim()
        s.down(0f, 0f)
        s.up(0f, 0f)
        s.down(0f, 0f)
        s.wait(250)
        assertEquals(listOf(Kind.WHEEL_CHANGED), s.move(0f, -70f).map { it.kind })
        assertEquals("shift", s.g.selected?.id)
        assertTrue(s.move(4f, -72f).isEmpty()) // same field
        assertEquals(listOf(Kind.WHEEL_CHANGED), s.move(70f, 0f).map { it.kind })
        assertEquals("ctrl", s.g.selected?.id)
        s.move(-70f, 0f)
        assertEquals("alt", s.g.selected?.id)
        s.move(0f, 70f)
        assertEquals("right", s.g.selected?.id)
        s.move(3f, -3f)
        assertEquals("left", s.g.selected?.id)
        s.move(0f, -70f)
        val e = s.up(0f, -70f)
        assertEquals(listOf(Kind.WHEEL_COMMIT), e.map { it.kind })
        assertEquals("shift", e[0].field?.id)
        assertEquals(OneFingerGesture.Phase.IDLE, s.g.phase)
    }

    @Test
    fun wheel_draggingOutsideTheCircleCancels() {
        val s = Sim()
        s.down(0f, 0f)
        s.up(0f, 0f)
        s.down(0f, 0f)
        s.wait(250)
        s.move(0f, -70f)
        s.move(0f, -150f)
        assertNull(s.g.selected)
        val e = s.up(0f, -150f)
        assertEquals(Kind.WHEEL_COMMIT, e[0].kind)
        assertNull(e[0].field)
    }

    @Test
    fun wheel_secondFingerClosesItWithoutClick() {
        val s = Sim()
        s.down(0f, 0f)
        s.up(0f, 0f)
        s.down(0f, 0f)
        s.wait(250)
        assertTrue(s.cancel().isEmpty())
        assertEquals(OneFingerGesture.Phase.IDLE, s.g.phase)
        assertNull(s.g.selected)
        assertEquals(listOf(Kind.WHEEL_OPENED), s.kinds())
    }

    @Test
    fun secondTouchMovingEarly_isCamera_andTheFirstClickStillGoesOut() {
        val s = Sim()
        s.down(100f, 100f)
        s.up(100f, 100f)
        s.down(100f, 100f)
        val e = s.move(100f, 170f) // 70 px > 50 px
        assertEquals(listOf(Kind.CLICK, Kind.CAMERA_STARTED), e.map { it.kind })
        assertEquals(100f, e[0].x, 0f)
        assertEquals(OneFingerGesture.Phase.CAMERA, s.g.phase)
        assertFalse(s.timerArmed)
        s.wait(1000)
        assertEquals(listOf(Kind.CLICK, Kind.CAMERA_STARTED), s.kinds())
    }

    @Test
    fun secondFingerWhileTheClickIsHeldBack_sendsTheClick() {
        val s = Sim()
        s.down(10f, 10f)
        s.up(10f, 10f)
        s.down(10f, 10f)
        assertEquals(listOf(Kind.CLICK), s.cancel().map { it.kind })
        assertEquals(OneFingerGesture.Phase.IDLE, s.g.phase)
        s.wait(1000)
        assertEquals(1, s.log.size)
    }

    // ── hold = left mouse button ─────────────────────────────────────

    @Test
    fun hold_pressesTheLeftButtonThenFollowsAbsolutelyAndReleasesAtTheEnd() {
        val s = Sim()
        s.down(200f, 300f)
        s.move(210f, 305f) // shaking inside the tolerance
        assertTrue(s.wait(339).isEmpty())
        val down = s.wait(11)
        assertEquals(listOf(Kind.LEFT_DOWN), down.map { it.kind })
        assertEquals(210f, down[0].x, 0f) // where the finger is now
        assertEquals(305f, down[0].y, 0f)
        assertEquals(OneFingerGesture.Phase.HOLD_LEFT, s.g.phase)
        // moving far away is NOT a camera drag now: the pointer follows, absolute
        val m1 = s.move(600f, 700f)
        assertEquals(listOf(Kind.LEFT_MOVE), m1.map { it.kind })
        assertEquals(600f, m1[0].x, 0f)
        assertEquals(700f, m1[0].y, 0f)
        s.move(900f, 100f)
        val up = s.up(900f, 120f)
        assertEquals(listOf(Kind.LEFT_UP), up.map { it.kind })
        assertEquals(900f, up[0].x, 0f)
        assertEquals(120f, up[0].y, 0f)
        assertEquals(listOf(Kind.LEFT_DOWN, Kind.LEFT_MOVE, Kind.LEFT_MOVE, Kind.LEFT_UP), s.kinds())
        assertEquals(OneFingerGesture.Phase.IDLE, s.g.phase)
    }

    @Test
    fun hold_afterwardsNoClickAndNoWheelChance() {
        val s = Sim()
        s.down(0f, 0f)
        s.wait(350)
        s.up(0f, 0f)
        s.wait(1000)
        assertEquals(listOf(Kind.LEFT_DOWN, Kind.LEFT_UP), s.kinds())
        // touching again right after a hold is a normal first touch, not tap + hold
        s.down(0f, 0f)
        assertEquals(OneFingerGesture.Phase.FIRST_DOWN, s.g.phase)
    }

    @Test
    fun hold_secondFingerReleasesTheLeftButton() {
        val s = Sim()
        s.down(50f, 60f)
        s.wait(350)
        s.move(80f, 90f)
        val e = s.cancel()
        assertEquals(listOf(Kind.LEFT_UP), e.map { it.kind })
        assertEquals(80f, e[0].x, 0f)
        assertEquals(90f, e[0].y, 0f)
    }

    @Test
    fun hold_lostLift_isClosedByTheNextTouchDown() {
        val s = Sim()
        s.down(0f, 0f)
        s.wait(350)
        val e = s.down(500f, 500f) // the up event never came
        assertEquals(listOf(Kind.LEFT_UP), e.map { it.kind })
        assertEquals(OneFingerGesture.Phase.FIRST_DOWN, s.g.phase)
    }

    @Test
    fun holdDisabled_restingDoesNothing() {
        val s = Sim(holdLeft = false)
        s.down(0f, 0f)
        assertFalse(s.timerArmed)
        s.wait(2000)
        assertTrue(s.log.isEmpty())
        s.up(0f, 0f)
        assertEquals(listOf(Kind.CLICK), s.wait(250).map { it.kind })
    }

    // ── drag early = camera ──────────────────────────────────────────

    @Test
    fun movingBeforeTheHoldTime_isCamera_andNeitherHoldNorWheelEverFire() {
        val s = Sim()
        s.down(100f, 100f)
        assertTrue(s.move(130f, 120f).isEmpty()) // inside 50 px
        val e = s.move(100f, 160f) // 60 px
        assertEquals(listOf(Kind.CAMERA_STARTED), e.map { it.kind })
        assertEquals(OneFingerGesture.Phase.CAMERA, s.g.phase)
        assertFalse(s.timerArmed)
        s.wait(2000)
        assertEquals(listOf(Kind.CAMERA_STARTED), s.kinds())
        assertEquals(listOf(Kind.CAMERA_ENDED), s.up(300f, 300f).map { it.kind })
        // a camera drag is no tap: nothing is held back, nothing to wait for
        s.wait(2000)
        assertEquals(listOf(Kind.CAMERA_STARTED, Kind.CAMERA_ENDED), s.kinds())
    }

    @Test
    fun exactlyOnTheTolerance_isStillQuiet() {
        val s = Sim()
        s.down(0f, 0f)
        assertTrue(s.move(50f, 0f).isEmpty())
        assertEquals(OneFingerGesture.Phase.FIRST_DOWN, s.g.phase)
    }

    // ── window 0 ─────────────────────────────────────────────────────

    @Test
    fun windowZero_clickGoesOutAtTheLift() {
        val s = Sim(window = 0)
        s.down(40f, 50f)
        val e = s.up(41f, 51f)
        assertEquals(listOf(Kind.CLICK), e.map { it.kind })
        assertEquals(41f, e[0].x, 0f)
        assertEquals(OneFingerGesture.Phase.IDLE, s.g.phase)
        assertFalse(s.timerArmed)
    }

    @Test
    fun windowZero_wheelStillReachableAfterTheClick() {
        val s = Sim(window = 0)
        s.down(40f, 50f)
        s.up(40f, 50f)
        s.wait(100)
        s.down(45f, 52f)
        assertEquals(OneFingerGesture.Phase.SECOND_DOWN, s.g.phase)
        assertEquals(listOf(Kind.WHEEL_OPENED), s.wait(250).map { it.kind })
        // the click had gone out already: exactly one CLICK, then the wheel
        assertEquals(listOf(Kind.CLICK, Kind.WHEEL_OPENED), s.kinds())
    }

    @Test
    fun windowZero_quickSecondTapIsAnotherPlainClick() {
        val s = Sim(window = 0)
        s.down(0f, 0f)
        s.up(0f, 0f)
        s.wait(60)
        s.down(0f, 0f)
        assertEquals(listOf(Kind.CLICK), s.up(0f, 0f).map { it.kind })
        assertEquals(listOf(Kind.CLICK, Kind.CLICK), s.kinds())
    }

    @Test
    fun windowZero_laterTouchIsNotTapPlusHold() {
        val s = Sim(window = 0)
        s.down(0f, 0f)
        s.up(0f, 0f)
        s.wait(400)
        s.down(0f, 0f)
        assertEquals(OneFingerGesture.Phase.FIRST_DOWN, s.g.phase)
    }

    @Test
    fun wheelOff_noHoldBack_noWheel_evenWithAWindow() {
        val s = Sim(window = 250, wheel = false)
        assertEquals(0L, s.g.tapWindowMs)
        s.down(0f, 0f)
        assertEquals(listOf(Kind.CLICK), s.up(0f, 0f).map { it.kind })
        s.wait(50)
        s.down(0f, 0f)
        assertEquals(OneFingerGesture.Phase.FIRST_DOWN, s.g.phase)
    }

    @Test
    fun windowIsClampedToFourHundred() {
        assertEquals(400L, Sim(window = 900).g.tapWindowMs)
        assertEquals(0L, Sim(window = -5).g.tapWindowMs)
    }

    @Test
    fun abort_releasesTheLeftButton_butDropsAHeldBackClick() {
        val held = Sim()
        held.down(0f, 0f)
        held.up(0f, 0f)
        assertTrue(held.g.abort().events.isEmpty())
        assertEquals(OneFingerGesture.Phase.IDLE, held.g.phase)
        val hold = Sim()
        hold.down(1f, 2f)
        hold.wait(350)
        val e = hold.g.abort().events
        assertEquals(listOf(Kind.LEFT_UP), e.map { it.kind })
        assertEquals(1f, e[0].x, 0f)
    }

    // ── robustness ───────────────────────────────────────────────────

    @Test
    fun staleTimer_doesNothing() {
        val g = OneFingerGesture(geometry = ClickWheel.Geometry(30f, 100f))
        assertTrue(g.timerFired().events.isEmpty())
        g.down(0f, 0f, 0)
        g.up(0f, 0f, 10) // now waiting for a second touch
        g.down(500f, 500f, 20) // far away: first click flushed, fresh touch
        // a timer of the old state firing now is the hold timer of the new touch: fine, left button goes down
        val e = g.timerFired().events
        assertEquals(listOf(Kind.LEFT_DOWN), e.map { it.kind })
        assertTrue(g.timerFired().events.isEmpty()) // and not twice
    }

    @Test
    fun tripleTap_firstTwoAreADoubleClick_thirdIsFreshAndHeldBack() {
        val s = Sim()
        s.down(0f, 0f); s.up(0f, 0f)
        s.wait(50)
        s.down(0f, 0f); s.up(0f, 0f) // double click
        s.wait(50)
        s.down(0f, 0f)
        assertEquals(OneFingerGesture.Phase.FIRST_DOWN, s.g.phase) // not a wheel chance
        assertTrue(s.up(0f, 0f).isEmpty())
        assertEquals(listOf(Kind.DOUBLE_CLICK, Kind.CLICK), s.also { it.wait(250) }.kinds())
    }
}
