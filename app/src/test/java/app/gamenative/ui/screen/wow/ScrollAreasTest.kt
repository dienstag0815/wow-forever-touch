package app.gamenative.ui.screen.wow

import org.junit.Rule
import app.gamenative.TestTexts
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.gamenative.cooldown.CooldownStrip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Wischen in Listen: Rechnung, Trefferprüfung der Scrollbereiche, Zustand im Prozess und gespeicherte Einstellung. */
@RunWith(RobolectricTestRunner::class)
class ScrollAreasTest {
    @get:Rule
    val texts = TestTexts.rule("de")

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun strip(known: Boolean, vararg areas: List<Int>): CooldownStrip.Decoded {
        val d = CooldownStrip.Decoded()
        d.scrollKnown = known
        d.scrollCount = areas.size
        for ((i, a) in areas.withIndex()) for (e in 0 until 4) d.scrollAreas[4 * i + e] = a[e]
        return d
    }

    /** Spielbild = Bildschirm 2000 x 1000. */
    private fun feed(decoded: CooldownStrip.Decoded, nowMs: Long) = ScrollGate.onStrip(decoded, 2000, 1000, 2000, 1000, nowMs)

    private fun fresh(enabled: Boolean = true) {
        ScrollGate.setEnabled(enabled)
        ScrollGate.clear()
    }

    // ---- Rechnung -------------------------------------------------------------------------------------------------------

    @Test
    fun theThresholdAndTheStepFollowTheDensity() {
        assertEquals(10f, SwipeScrollMath.thresholdPx(1f), 0f)
        assertEquals(30f, SwipeScrollMath.thresholdPx(3f), 0f)
        assertEquals(28f * 3f, SwipeScrollMath.stepPx(3f, 1f), 0.001f)
        // twice the speed = half the finger distance per wheel step
        assertEquals(28f * 3f / 2f, SwipeScrollMath.stepPx(3f, 2f), 0.001f)
        // out of range or garbage speeds are limited, never zero
        assertEquals(SwipeScrollMath.stepPx(3f, SwipeScrollMath.MAX_SPEED), SwipeScrollMath.stepPx(3f, 100f), 0.001f)
        assertEquals(SwipeScrollMath.stepPx(3f, SwipeScrollMath.MIN_SPEED), SwipeScrollMath.stepPx(3f, 0f), 0.001f)
        assertEquals(SwipeScrollMath.stepPx(3f, 1f), SwipeScrollMath.stepPx(3f, Float.NaN), 0.001f)
        assertTrue(SwipeScrollMath.stepPx(0.01f, 6f) >= 4f)
    }

    @Test
    fun aVerticalSwipeNeedsTravelAndMostlyVerticalMovement() {
        val t = 30f
        assertTrue(SwipeScrollMath.isVerticalSwipe(0f, 30f, t))
        assertTrue(SwipeScrollMath.isVerticalSwipe(5f, -40f, t))
        assertFalse("zu kurz", SwipeScrollMath.isVerticalSwipe(0f, 29f, t))
        assertFalse("diagonal", SwipeScrollMath.isVerticalSwipe(40f, 45f, t))
        assertFalse("waagrecht", SwipeScrollMath.isVerticalSwipe(60f, 5f, t))
        assertTrue("gerade noch senkrecht genug", SwipeScrollMath.isVerticalSwipe(20f, 30f, t))
        assertFalse("knapp zu schräg", SwipeScrollMath.isVerticalSwipe(21f, 30f, t))
    }

    @Test
    fun theSpeedStepperGoesRoundAndLabelsAreReadable() {
        var s = 0.5f
        val seen = ArrayList<Float>()
        repeat(SwipeScrollMath.SPEEDS.size) {
            s = SwipeScrollMath.nextSpeed(s)
            seen += s
        }
        assertEquals(listOf(0.75f, 1f, 1.5f, 2f, 3f, 4f, 0.5f), seen)
        assertEquals(0.5f, SwipeScrollMath.nextSpeed(4f), 0f)
        assertEquals("1x", SwipeScrollMath.speedLabel(1f))
        assertEquals("1,5x", SwipeScrollMath.speedLabel(1.5f))
        assertEquals("0,75x", SwipeScrollMath.speedLabel(0.75f))
        for (speed in SwipeScrollMath.SPEEDS) {
            assertTrue(speed >= SwipeScrollMath.MIN_SPEED && speed <= SwipeScrollMath.MAX_SPEED)
        }
    }

    // ---- Trefferprüfung -----------------------------------------------------------------------------------------------

    @Test
    fun aPointInsideAnAreaHitsOutsideDoesNot() {
        // Bereich: 20 % bis 50 % der Breite, 10 % bis 90 % der Höhe des Bildes 2000 x 1000 = x 400..1000, y 100..900
        val s = ScrollSnapshot.from(strip(true, listOf(51, 26, 128, 230)), 2000, 1000, 2000, 1000)!!
        assertTrue(ScrollGeometry.contains(s, 700f, 500f))
        assertTrue(ScrollGeometry.contains(s, 410f, 110f))
        assertFalse(ScrollGeometry.contains(s, 300f, 500f))
        assertFalse(ScrollGeometry.contains(s, 700f, 50f))
        assertFalse(ScrollGeometry.contains(s, 1100f, 500f))
        assertEquals(-1, ScrollGeometry.indexAt(s, 1500f, 500f))
    }

    @Test
    fun theSmallestAreaComesFirstWhenListsAreNested() {
        val inner = listOf(100, 100, 150, 150)
        val outer = listOf(20, 20, 230, 230)
        val s = ScrollSnapshot.from(strip(true, inner, outer), 2000, 1000, 2000, 1000)!!
        val x = 125f / 255f * 2000f
        val y = 125f / 255f * 1000f
        assertEquals("im inneren", 0, ScrollGeometry.indexAt(s, x, y))
        assertEquals("nur im äußeren", 1, ScrollGeometry.indexAt(s, 40f / 255f * 2000f, 40f / 255f * 1000f))
    }

    @Test
    fun theAreasFollowThePictureNotTheScreen() {
        // Das Spielbild (1600 x 900) liegt in einem größeren X-Bildschirm (2000 x 1000) an der oberen linken Ecke.
        val s = ScrollSnapshot.from(strip(true, listOf(0, 0, 255, 255)), 1600, 900, 2000, 1000)!!
        assertTrue(ScrollGeometry.contains(s, 1590f, 890f))
        assertFalse(ScrollGeometry.contains(s, 1700f, 500f))
        assertFalse(ScrollGeometry.contains(s, 500f, 950f))
    }

    @Test
    fun noSnapshotWithoutKnownAreas() {
        assertNull(ScrollSnapshot.from(strip(false, listOf(1, 2, 3, 4)), 2000, 1000, 2000, 1000))
        assertNull(ScrollSnapshot.from(strip(true), 2000, 1000, 2000, 1000))
    }

    // ---- Zustand --------------------------------------------------------------------------------------------------------

    @Test
    fun theGateFollowsTheStripAndForgetsWhenItDisappears() {
        fresh()
        assertFalse(ScrollGate.contains(700f, 500f))
        feed(strip(true, listOf(51, 26, 128, 230)), 0)
        assertTrue(ScrollGate.hasAreas())
        assertTrue(ScrollGate.contains(700f, 500f))
        assertFalse(ScrollGate.contains(100f, 500f))
        // the window closes: the addon reports an empty list
        feed(strip(true), 100)
        assertFalse(ScrollGate.contains(700f, 500f))
        feed(strip(true, listOf(51, 26, 128, 230)), 200)
        // the section is unreadable for a moment: the last state stays ...
        feed(strip(false), 300)
        assertTrue(ScrollGate.contains(700f, 500f))
        // ... but not longer than a few reads
        feed(strip(false), 300 + WindowFeed.FORGET_AFTER_MS + 10)
        assertFalse(ScrollGate.contains(700f, 500f))
        fresh()
    }

    @Test
    fun clearAndSwitchingOffRemoveTheAreas() {
        fresh()
        feed(strip(true, listOf(51, 26, 128, 230)), 0)
        assertTrue(ScrollGate.contains(700f, 500f))
        ScrollGate.clear()
        assertFalse(ScrollGate.contains(700f, 500f))
        feed(strip(true, listOf(51, 26, 128, 230)), 100)
        assertTrue(ScrollGate.contains(700f, 500f))
        ScrollGate.setEnabled(false)
        assertFalse(ScrollGate.contains(700f, 500f))
        assertFalse(ScrollGate.needsSignal())
        feed(strip(true, listOf(51, 26, 128, 230)), 200) // switched off: nothing is taken in
        ScrollGate.setEnabled(true)
        assertFalse(ScrollGate.contains(700f, 500f))
        assertTrue(ScrollGate.needsSignal())
        fresh()
    }

    // ---- Einstellung ----------------------------------------------------------------------------------------------------

    @Test
    fun theSettingIsOnByDefaultAndStored() {
        assertTrue(SwipeScrollSetting.isEnabled(context))
        assertEquals(SwipeScrollMath.DEFAULT_SPEED, SwipeScrollSetting.speed(context), 0f)
        SwipeScrollSetting.setEnabled(context, false)
        assertFalse(SwipeScrollSetting.isEnabled(context))
        assertFalse(ScrollGate.enabled())
        SwipeScrollSetting.setEnabled(context, true)
        assertTrue(SwipeScrollSetting.isEnabled(context))
        assertTrue(ScrollGate.enabled())
        SwipeScrollSetting.setSpeed(context, 2f)
        assertEquals(2f, SwipeScrollSetting.speed(context), 0f)
        // the step in pixels follows the speed (Robolectric's density is 1.0)
        val density = context.resources.displayMetrics.density
        assertEquals(SwipeScrollMath.stepPx(density, 2f), SwipeScrollSetting.stepPx(context), 0.001f)
        SwipeScrollSetting.setSpeed(context, 100f) // limited
        assertEquals(SwipeScrollMath.MAX_SPEED, SwipeScrollSetting.speed(context), 0f)
        SwipeScrollSetting.setSpeed(context, SwipeScrollMath.DEFAULT_SPEED)
    }
}
