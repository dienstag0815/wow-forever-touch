package app.gamenative.ui.screen.wow.klick

import org.junit.Rule
import app.gamenative.TestTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TwoFingerTapDetectorTest {
    @get:Rule
    val texts = TestTexts.rule("de")

    private fun detector() = TwoFingerTapDetector()

    @Test
    fun limits_areTwoFiftyMillisecondsAndThirtyPixels() {
        assertEquals(250L, TwoFingerTapDetector.MAX_DURATION_MS)
        assertEquals(30f, TwoFingerTapDetector.MAX_TRAVEL_PX, 0f)
    }

    @Test
    fun quickTapWithoutMovement_isATap_atTheFirstFinger() {
        val d = detector()
        d.begin(1000, 200f, 300f, 200f, 300f, 600f, 320f)
        d.move(202f, 301f, 601f, 322f)
        assertTrue(d.isTap(1150))
        assertEquals(202f, d.firstX, 0f)
        assertEquals(301f, d.firstY, 0f)
    }

    @Test
    fun tooSlow_isNotATap() {
        val d = detector()
        d.begin(1000, 0f, 0f, 0f, 0f, 100f, 0f)
        assertTrue(d.isTap(1250))
        assertFalse(d.isTap(1251))
    }

    @Test
    fun pinch_isNotATap() {
        val d = detector()
        d.begin(1000, 100f, 100f, 100f, 100f, 200f, 100f)
        d.move(90f, 100f, 215f, 100f) // second finger moved 15 px: still fine
        assertTrue(d.stillTap)
        d.move(60f, 100f, 260f, 100f) // spread: first finger 40 px, second 60 px
        assertFalse(d.stillTap)
        assertFalse(d.isTap(1100))
    }

    @Test
    fun twoFingerDrag_isNotATap() {
        val d = detector()
        d.begin(1000, 100f, 100f, 100f, 100f, 200f, 100f)
        d.move(100f, 160f, 200f, 160f)
        assertFalse(d.isTap(1100))
    }

    @Test
    fun onceMovedTooFar_comingBackDoesNotMakeItATapAgain() {
        val d = detector()
        d.begin(1000, 0f, 0f, 0f, 0f, 100f, 0f)
        d.move(0f, 80f, 100f, 0f)
        d.move(0f, 0f, 100f, 0f)
        assertFalse(d.isTap(1100))
    }

    @Test
    fun firstFingerAlreadyMovedBeforeTheSecondArrived_isNotATap() {
        val d = detector()
        d.begin(1000, 0f, 0f, 90f, 0f, 200f, 0f)
        assertFalse(d.stillTap)
        assertFalse(d.isTap(1100))
    }

    @Test
    fun beforeBeginAndAfterReset_neverATap() {
        val d = detector()
        assertFalse(d.isTap(0))
        d.begin(1000, 0f, 0f, 0f, 0f, 100f, 0f)
        d.reset()
        assertFalse(d.isTap(1050))
    }
}
