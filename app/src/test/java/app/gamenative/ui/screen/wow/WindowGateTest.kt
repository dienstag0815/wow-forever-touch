package app.gamenative.ui.screen.wow

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.gamenative.cooldown.CooldownStrip
import com.winlator.inputcontrols.Binding
import com.winlator.inputcontrols.ControlElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Zustand des Ausblendens über Spielfenstern im Prozess, und das Zusammenspiel mit der Login-Regel. */
@RunWith(RobolectricTestRunner::class)
class WindowGateTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun strip(known: Boolean, vararg rects: List<Int>): CooldownStrip.Decoded {
        val d = CooldownStrip.Decoded()
        d.windowsKnown = known
        d.windowCount = rects.size
        for ((i, r) in rects.withIndex()) for (e in 0 until 4) d.windows[4 * i + e] = r[e]
        return d
    }

    /** Spielbild = Bildschirm 2000 x 1000. */
    private fun feed(decoded: CooldownStrip.Decoded, nowMs: Long): Boolean = WindowGate.onStrip(decoded, 2000, 1000, 2000, 1000, nowMs)

    private fun fresh(mode: WindowHideMode = WindowHideMode.COVERED) {
        WindowGate.setMode(context, mode)
        WindowGate.clear()
    }

    // ---- Einstellung ------------------------------------------------------------------------------------------------

    @Test
    fun theStandardIsCoveredAndTheChoiceIsStored() {
        assertEquals(WindowHideMode.COVERED, WindowGate.savedMode(context))
        WindowGate.setMode(context, WindowHideMode.ALL)
        assertEquals(WindowHideMode.ALL, WindowGate.savedMode(context))
        assertEquals(WindowHideMode.ALL, WindowGate.mode())
        WindowGate.setMode(context, WindowHideMode.OFF)
        assertEquals(WindowHideMode.OFF, WindowGate.savedMode(context))
        WindowGate.setMode(context, WindowHideMode.COVERED)
        assertEquals(WindowHideMode.COVERED, WindowGate.savedMode(context))
    }

    @Test
    fun setModeReportsAChangeOnlyWhenThereIsOne() {
        fresh(WindowHideMode.COVERED)
        assertFalse(WindowGate.setMode(context, WindowHideMode.COVERED))
        assertTrue(WindowGate.setMode(context, WindowHideMode.OFF))
        assertFalse(WindowGate.setMode(context, WindowHideMode.OFF))
        fresh()
    }

    @Test
    fun offNeedsNoSignalAndIgnoresTheStrip() {
        fresh(WindowHideMode.OFF)
        assertFalse(WindowGate.needsSignal())
        assertFalse(feed(strip(true, listOf(10, 10, 100, 100)), 0))
        assertFalse(WindowGate.hasWindows())
        assertNull(WindowGate.viewRects(1000, 500, false))
        fresh()
        assertTrue(WindowGate.needsSignal())
    }

    @Test
    fun switchingOffForgetsTheWindows() {
        fresh()
        feed(strip(true, listOf(10, 10, 100, 100)), 0)
        assertTrue(WindowGate.hasWindows())
        WindowGate.setMode(context, WindowHideMode.OFF)
        assertFalse(WindowGate.hasWindows())
        fresh()
    }

    // ---- Stand ------------------------------------------------------------------------------------------------------

    @Test
    fun windowsAppearChangeAndGo() {
        fresh()
        assertFalse(WindowGate.hasWindows())
        assertTrue("Taschen auf", feed(strip(true, listOf(170, 130, 240, 245)), 100))
        assertTrue(WindowGate.hasWindows())
        assertFalse("gleich", feed(strip(true, listOf(170, 130, 240, 245)), 133))
        assertTrue("Karte dazu", feed(strip(true, listOf(10, 20, 230, 230), listOf(170, 130, 240, 245)), 166))
        assertTrue("alles zu", feed(strip(true), 200))
        assertFalse(WindowGate.hasWindows())
        assertNull(WindowGate.viewRects(1000, 500, false))
    }

    @Test
    fun unreadableSectionKeepsTheLastStandBrieflyThenGivesUp() {
        fresh()
        feed(strip(true, listOf(10, 10, 100, 100)), 0)
        assertFalse(feed(strip(false), 1_000))
        assertTrue("noch gültig", WindowGate.hasWindows())
        assertFalse(feed(strip(false), 1_399))
        assertTrue(WindowGate.hasWindows())
        assertTrue("nach 400 ms vergessen", feed(strip(false), 1_400))
        assertFalse(WindowGate.hasWindows())
    }

    @Test
    fun anOldAddonNeverHidesAnything() {
        fresh()
        for (t in 0L..2_000L step 33) assertFalse(feed(strip(false), t))
        assertFalse(WindowGate.hasWindows())
    }

    @Test
    fun clearWhenTheStripIsLost() {
        fresh()
        feed(strip(true, listOf(10, 10, 100, 100)), 0)
        assertTrue(WindowGate.clear())
        assertFalse(WindowGate.hasWindows())
        assertFalse("nichts mehr zu löschen", WindowGate.clear())
    }

    @Test
    fun viewRectsFollowTheViewSizeAndAreCachedPerSnapshot() {
        fresh()
        feed(strip(true, listOf(0, 0, 255, 255)), 0)
        val a = WindowGate.viewRects(1000, 500, false)
        assertNotNull(a)
        assertEquals(1000f, a!!.rects[2], 0.01f)
        assertTrue("gleiche Größe: dieselbe Abbildung", a === WindowGate.viewRects(1000, 500, false))
        val b = WindowGate.viewRects(2000, 1000, false)!!
        assertEquals(2000f, b.rects[2], 0.01f)
    }

    // ---- Zusammenspiel von Fenster- und Login-Regel -------------------------------------------------------------

    /** Die Regel der Ansicht: im Login ausgeblendet (außer ESC/Tastatur) ODER von einem Fenster überdeckt (außer ESC/Tastatur). */
    private fun hiddenOverall(loginHidden: Boolean, type: ControlElement.Type, primary: Binding?, windowHidden: Boolean): Boolean =
        (loginHidden && !keepsInLogin(type, primary)) || (windowHidden && !WindowHideRule.alwaysKept(type, primary))

    @Test
    fun escapeAndKeyboardStayUnderBothRules() {
        for (primary in listOf(Binding.KEY_ESC, Binding.SHOW_KEYBOARD)) {
            assertTrue(WindowHideRule.alwaysKept(ControlElement.Type.BUTTON, primary))
            for (login in listOf(false, true)) for (window in listOf(false, true)) {
                assertFalse("$primary login=$login window=$window", hiddenOverall(login, ControlElement.Type.BUTTON, primary, window))
            }
        }
    }

    @Test
    fun otherElementsAreHiddenByEitherRule() {
        val type = ControlElement.Type.BUTTON
        assertFalse(hiddenOverall(false, type, Binding.KEY_1, false))
        assertTrue("Login", hiddenOverall(true, type, Binding.KEY_1, false))
        assertTrue("Fenster", hiddenOverall(false, type, Binding.KEY_1, true))
        assertTrue("beide", hiddenOverall(true, type, Binding.KEY_1, true))
        assertTrue("Stick im Fenster", hiddenOverall(false, ControlElement.Type.STICK, null, true))
    }

    @Test
    fun theWindowRuleKeepsExactlyWhatTheLoginRuleKeeps() {
        for (type in ControlElement.Type.entries) for (primary in listOf(null, Binding.KEY_ESC, Binding.SHOW_KEYBOARD, Binding.KEY_1, Binding.OPEN_NAVIGATION_MENU)) {
            assertEquals("$type $primary", keepsInLogin(type, primary), WindowHideRule.alwaysKept(type, primary))
        }
    }

    // ---- Ein Durchgang wie in der Ansicht ----------------------------------------------------------------------------

    @Test
    fun bagsOpenHideTheButtonsUnderThemAndTheyReturnAfterTheDelay() {
        fresh()
        val tracker = WindowHideTracker()
        // Ansicht 2000 x 1000 = Bild. Taschen unten rechts: x 70 % bis 95 %, y 50 % bis 96 %.
        feed(strip(true, listOf(178, 128, 242, 245)), 0)
        val windows = WindowGate.viewRects(2000, 1000, false)
        assertNotNull(windows)
        // Taschen ab x = 1396 px. Knopf 1 liegt ganz drin, Knopf 2 weit daneben, Knopf 3 (1200 bis 1440) ragt zu gut 18 % hinein,
        // Knopf 4 (1200 bis 1405) streift das Fenster nur (unter 5 %).
        val inside = WindowGeometry.coveredShare(1500f, 600f, 1700f, 700f, windows!!)
        val beside = WindowGeometry.coveredShare(100f, 600f, 300f, 700f, windows)
        val edge = WindowGeometry.coveredShare(1200f, 600f, 1440f, 700f, windows)
        val graze = WindowGeometry.coveredShare(1200f, 600f, 1405f, 700f, windows)
        assertEquals(1f, inside, 0.0001f)
        assertEquals(0f, beside, 0f)
        assertTrue("ab 10 %: weg", edge in 0.11f..0.3f)
        assertTrue("unter 10 %: bleibt", graze in 0.01f..0.1f)
        val mode = WindowHideMode.COVERED
        val key1 = Any()
        val key2 = Any()
        val key3 = Any()
        val key4 = Any()
        fun frame(now: Long, w: WindowViewRects?): List<Boolean> {
            fun covered(l: Float, t: Float, r: Float, b: Float) =
                w != null && WindowHideRule.covers(mode, WindowGeometry.coveredShare(l, t, r, b, w), w.count)
            return listOf(
                tracker.hidden(key1, covered(1500f, 600f, 1700f, 700f), held = false, nowMs = now),
                tracker.hidden(key2, covered(100f, 600f, 300f, 700f), held = false, nowMs = now),
                tracker.hidden(key3, covered(1200f, 600f, 1440f, 700f), held = false, nowMs = now),
                tracker.hidden(key4, covered(1200f, 600f, 1405f, 700f), held = false, nowMs = now),
            )
        }
        assertEquals(listOf(true, false, true, false), frame(0, windows))
        // Taschen zu
        feed(strip(true), 500)
        assertNull(WindowGate.viewRects(2000, 1000, false))
        assertEquals("noch gesperrt", listOf(true, false, true, false), frame(500, null))
        assertEquals(listOf(true, false, true, false), frame(649, null))
        assertEquals("nach 150 ms wieder da", listOf(false, false, false, false), frame(650, null))
    }

    @Test
    fun allModeHidesEverythingUntilTheLastWindowCloses() {
        fresh(WindowHideMode.ALL)
        feed(strip(true, listOf(10, 10, 20, 20)), 0)
        val w = WindowGate.viewRects(2000, 1000, false)!!
        assertTrue("auch weit weg vom Fenster", WindowHideRule.covers(WindowHideMode.ALL, 0f, w.count))
        feed(strip(true), 100)
        assertNull(WindowGate.viewRects(2000, 1000, false))
        fresh()
    }

    @Test
    fun aStickGrazingTheBottomOfAQuestDialogIsHidden() {
        fresh()
        // Questdialog links (Gruppe 5): x 1,5 % bis 34,5 %, y 14,5 % bis 78,8 % des Bildes. Ansicht = Bild 2000 x 1000.
        feed(strip(true, listOf(4, 37, 88, 201)), 0)
        val w = WindowGate.viewRects(2000, 1000, false)!!
        // Stick 400 x 400 mit der Oberkante 90 px im Fenster (unten am Fenster, über "Cancel"): gut 22 % Überdeckung.
        val stick = WindowGeometry.coveredShare(100f, 700f, 500f, 1100f, w)
        assertTrue("Anteil $stick", stick > WindowHideRule.COVER_SHARE)
        assertTrue(WindowHideRule.covers(WindowHideMode.COVERED, stick, w.count))
        // Ein Knopf rechts im Bild bleibt.
        assertFalse(WindowHideRule.covers(WindowHideMode.COVERED, WindowGeometry.coveredShare(1500f, 700f, 1700f, 900f, w), w.count))
        fresh()
    }
}
