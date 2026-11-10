package app.gamenative.ui.screen.wow.klick

import org.junit.Rule
import app.gamenative.TestTexts
import app.gamenative.ui.screen.wow.klick.GestureEvent.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hold on a unit or an item: menu, left button held (as ever) or camera. The decision "is there something to open a menu for" is
 * the [contextAt] function (the view asks the strip); here it is a switch, and the time it is asked with is recorded.
 */
private class ContextSim(
    var unitUnderFinger: Boolean = true,
    holdLeft: Boolean = true,
    wheel: Boolean = true,
    window: Long = 0L,
    withContext: Boolean = true,
) {
    val asked = ArrayList<Long>()
    val g = OneFingerGesture(
        holdMs = 350L,
        holdLeftEnabled = holdLeft,
        wheelEnabled = wheel,
        tapWindowMs = window,
        geometry = ClickWheel.Geometry(innerRadius = 30f, outerRadius = 100f),
        contextAt = if (withContext) ({ downMs: Long -> asked.add(downMs); unitUnderFinger }) else null,
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
    fun abort() = apply(g.abort())
    val timerArmed get() = timerAt >= 0
    fun kinds() = log.map { it.kind }
}

class ContextGestureTest {
    @get:Rule
    val texts = TestTexts.rule("de")

    @Test
    fun holdingOnAUnitOpensTheMenuInsteadOfPressingTheLeftButton() {
        val sim = ContextSim()
        sim.down(300f, 400f)
        assertTrue(sim.timerArmed)
        assertTrue(sim.wait(349).isEmpty())
        val opened = sim.wait(1)
        assertEquals(listOf(Kind.CONTEXT_OPENED), opened.map { it.kind })
        assertEquals(300f, opened[0].x, 0f)
        assertEquals(400f, opened[0].y, 0f)
        assertEquals(OneFingerGesture.Phase.CONTEXT, sim.g.phase)
        assertFalse(Kind.LEFT_DOWN in sim.kinds())
    }

    @Test
    fun theContextIsAskedOnceWithTheTimeOfTheTouchDown() {
        val sim = ContextSim()
        sim.now = 5_000L
        sim.down(300f, 400f)
        sim.wait(400)
        assertEquals(listOf(5_000L), sim.asked)
    }

    @Test
    fun withNothingUnderTheFingerHoldStaysTheLeftButton() {
        val sim = ContextSim(unitUnderFinger = false)
        sim.down(300f, 400f)
        val events = sim.wait(400)
        assertEquals(listOf(Kind.LEFT_DOWN), events.map { it.kind })
        assertEquals(OneFingerGesture.Phase.HOLD_LEFT, sim.g.phase)
        val up = sim.up(300f, 400f)
        assertEquals(listOf(Kind.LEFT_UP), up.map { it.kind })
    }

    @Test
    fun movingBeforeTheTimeIsUpIsTheCameraWhateverIsUnderTheFinger() {
        val sim = ContextSim()
        sim.down(300f, 400f)
        sim.wait(100)
        val moved = sim.move(300f + OneFingerGesture.MOVE_TOLERANCE_PX + 5f, 400f)
        assertEquals(listOf(Kind.CAMERA_STARTED), moved.map { it.kind })
        assertTrue(sim.wait(400).isEmpty())
        assertFalse(Kind.CONTEXT_OPENED in sim.kinds())
        assertEquals(listOf(Kind.CAMERA_ENDED), sim.up(400f, 400f).map { it.kind })
    }

    @Test
    fun aSmallJitterBeforeTheTimeStillOpensTheMenuAtTheFingerNow() {
        val sim = ContextSim()
        sim.down(300f, 400f)
        sim.move(310f, 405f)
        val opened = sim.wait(400)
        assertEquals(listOf(Kind.CONTEXT_OPENED), opened.map { it.kind })
        assertEquals(310f, opened[0].x, 0f)
        // the touch-down point (where the pointer stays) is unchanged
        assertEquals(300f, sim.g.centerX, 0f)
        assertEquals(400f, sim.g.centerY, 0f)
    }

    @Test
    fun theFingerOnlySelectsWhileTheMenuIsOpenAndNeverStartsTheCamera() {
        val sim = ContextSim()
        sim.down(300f, 400f)
        sim.wait(400)
        val far = sim.move(800f, 100f)
        assertEquals(listOf(Kind.CONTEXT_MOVED), far.map { it.kind })
        assertEquals(800f, far[0].x, 0f)
        assertEquals(100f, far[0].y, 0f)
        assertEquals(OneFingerGesture.Phase.CONTEXT, sim.g.phase)
        assertFalse(Kind.CAMERA_STARTED in sim.kinds())
    }

    @Test
    fun liftingRunsTheEntryUnderTheFingerAtTheLiftPoint() {
        val sim = ContextSim()
        sim.down(300f, 400f)
        sim.wait(400)
        sim.move(800f, 100f)
        val up = sim.up(805f, 98f)
        assertEquals(listOf(Kind.CONTEXT_COMMIT), up.map { it.kind })
        assertEquals(805f, up[0].x, 0f)
        assertEquals(98f, up[0].y, 0f)
        assertEquals(OneFingerGesture.Phase.IDLE, sim.g.phase)
        assertFalse(sim.timerArmed)
        // no left button was ever pressed
        assertFalse(Kind.LEFT_DOWN in sim.kinds() || Kind.LEFT_UP in sim.kinds() || Kind.CLICK in sim.kinds())
    }

    @Test
    fun aSecondFingerClosesTheMenuWithoutRunningAnything() {
        val sim = ContextSim()
        sim.down(300f, 400f)
        sim.wait(400)
        val cancelled = sim.cancel()
        assertEquals(listOf(Kind.CONTEXT_CANCELLED), cancelled.map { it.kind })
        assertEquals(OneFingerGesture.Phase.IDLE, sim.g.phase)
        assertFalse(Kind.CONTEXT_COMMIT in sim.kinds())
        // the view going away does the same
        val sim2 = ContextSim()
        sim2.down(300f, 400f)
        sim2.wait(400)
        assertEquals(listOf(Kind.CONTEXT_CANCELLED), sim2.abort().map { it.kind })
    }

    @Test
    fun theMenuWorksWithoutTheHoldingOfTheLeftButton() {
        val sim = ContextSim(holdLeft = false)
        sim.down(300f, 400f)
        assertTrue("the timer is needed for the menu", sim.timerArmed)
        assertEquals(listOf(Kind.CONTEXT_OPENED), sim.wait(400).map { it.kind })
        // and without a unit nothing happens at all, as before
        val none = ContextSim(unitUnderFinger = false, holdLeft = false)
        none.down(300f, 400f)
        assertTrue(none.wait(400).isEmpty())
        assertEquals(OneFingerGesture.Phase.FIRST_DOWN, none.g.phase)
    }

    @Test
    fun withoutAContextFunctionTheEngineBehavesAsBefore() {
        val sim = ContextSim(withContext = false)
        sim.down(300f, 400f)
        assertEquals(listOf(Kind.LEFT_DOWN), sim.wait(400).map { it.kind })
        val noHold = ContextSim(withContext = false, holdLeft = false)
        noHold.down(300f, 400f)
        assertFalse("no timer without hold and without context", noHold.timerArmed)
        assertTrue(noHold.asked.isEmpty())
    }

    @Test
    fun aTapIsStillAClickAndRemembersWhenItCameDown() {
        val sim = ContextSim()
        sim.now = 7_000L
        sim.down(300f, 400f)
        sim.wait(80)
        val up = sim.up(300f, 400f)
        assertEquals(listOf(Kind.CLICK), up.map { it.kind })
        assertEquals(7_000L, sim.g.tapDownMs)
        assertTrue(sim.asked.isEmpty())
    }

    @Test
    fun aHeldBackTapKeepsItsOwnDownTimeWhenANewTouchFollows() {
        val sim = ContextSim(window = 250L)
        sim.now = 2_000L
        sim.down(300f, 400f)
        sim.wait(60)
        sim.up(300f, 400f)
        assertEquals(2_000L, sim.g.tapDownMs)
        // a touch somewhere else sends the held-back click; it belongs to the first touch
        sim.wait(100)
        val events = sim.down(900f, 100f)
        assertEquals(listOf(Kind.CLICK), events.map { it.kind })
        assertEquals(2_000L, sim.g.tapDownMs)
    }

    @Test
    fun aLostLiftClosesTheOldMenuBeforeTheNextTouch() {
        val sim = ContextSim()
        sim.down(300f, 400f)
        sim.wait(400)
        val events = sim.down(100f, 100f)
        assertTrue(Kind.CONTEXT_CANCELLED in events.map { it.kind })
    }
}
