package app.gamenative.ui.screen.wow

import androidx.test.core.app.ApplicationProvider
import com.winlator.inputcontrols.Binding
import com.winlator.inputcontrols.ControlElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LoginTouchTest {
    // --- Sichtbarkeit: Zustand x Signal ---

    @Test
    fun visibleTruthTable() {
        // Ausblenden aus: immer sichtbar
        assertTrue(touchVisible(hideInLogin = false, forceShown = false, inWorld = false))
        assertTrue(touchVisible(hideInLogin = false, forceShown = false, inWorld = true))
        // Ausblenden an: nur in der Welt oder mit Notausgang
        assertFalse(touchVisible(hideInLogin = true, forceShown = false, inWorld = false))
        assertTrue(touchVisible(hideInLogin = true, forceShown = false, inWorld = true))
        assertTrue(touchVisible(hideInLogin = true, forceShown = true, inWorld = false))
        assertTrue(touchVisible(hideInLogin = true, forceShown = true, inWorld = true))
    }

    // --- Welt-Erkennung: Zeitgrenzen ---

    @Test
    fun inWorldBoundaries() {
        val grace = WorldPresence.LOSE_GRACE_MS
        assertFalse("nie gelesen", WorldPresence.inWorld(Long.MIN_VALUE, 10_000, grace))
        assertTrue(WorldPresence.inWorld(1_000, 1_000, grace))
        assertTrue("genau an der Grenze", WorldPresence.inWorld(1_000, 1_000 + grace, grace))
        assertFalse("eine Millisekunde darüber", WorldPresence.inWorld(1_000, 1_000 + grace + 1, grace))
    }

    @Test
    fun presenceStartsOutsideTheWorld() {
        val p = WorldPresence()
        assertFalse(p.update(0, false))
        assertFalse(p.update(250, false))
    }

    @Test
    fun presenceEntersAtOnceWhenTheStripIsRead() {
        val p = WorldPresence()
        assertFalse(p.update(0, false))
        assertTrue(p.update(250, true))
        assertTrue(p.update(280, true))
    }

    @Test
    fun presenceSurvivesAShortGapAndLeavesAfterTheGrace() {
        val p = WorldPresence(graceMs = 6_000, pauseGapMs = 1_500)
        assertTrue(p.update(0, true))
        // Streifen fehlt, aber Bilder laufen (Durchgänge alle 250 ms): Karenzzeit
        var t = 250L
        while (t <= 6_000) {
            assertTrue("bei $t ms noch in der Welt", p.update(t, false))
            t += 250
        }
        assertFalse("nach der Karenzzeit draußen", p.update(6_250, false))
        // Wieder gelesen: sofort drin
        assertTrue(p.update(6_500, true))
    }

    @Test
    fun aPausedGameDoesNotCountAsMissingStrip() {
        val p = WorldPresence(graceMs = 6_000, pauseGapMs = 1_500)
        assertTrue(p.update(0, true))
        // Seitenmenü 60 s offen, keine Bilder; das erste Bild danach liest den Streifen (noch) nicht
        assertTrue(p.update(60_000, false))
        // ... und die Karenzzeit läuft erst ab dem Weiterlaufen
        assertTrue(p.update(60_250, false))
    }

    @Test
    fun resetForgetsTheWorld() {
        val p = WorldPresence()
        assertTrue(p.update(0, true))
        p.reset()
        assertFalse(p.update(100, false))
    }

    // --- Der Zustand im Prozess ---

    @Test
    fun gateHidesUntilTheWorldAndAgainAfterLeaving() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        LoginTouchGate.setHideEnabled(context, true)
        LoginTouchGate.reset(context)
        assertTrue("im Login ausgeblendet", LoginTouchGate.isHidden())
        assertFalse(LoginTouchGate.inWorld())

        assertTrue("Streifen gelesen: sichtbar, Änderung gemeldet", LoginTouchGate.onPass(1_000, true))
        assertFalse(LoginTouchGate.isHidden())
        assertTrue(LoginTouchGate.inWorld())
        assertFalse("keine Änderung", LoginTouchGate.onPass(1_033, true))

        // Ausgeloggt: Streifen weg, nach der Karenzzeit (Durchgänge alle 250 ms) wieder ausgeblendet
        val leftAt = missUntilHidden(1_033)
        assertTrue("Ausblenden nach der Karenzzeit", leftAt > 1_033 + WorldPresence.LOSE_GRACE_MS)
        assertTrue(leftAt <= 1_033 + WorldPresence.LOSE_GRACE_MS + 250)
        assertTrue(LoginTouchGate.isHidden())
        assertFalse(LoginTouchGate.inWorld())
    }

    /** Durchgänge ohne Streifen im Abstand von 250 ms ab [from]; gibt die Zeit zurück, zu der das Ausblenden einsetzt. */
    private fun missUntilHidden(from: Long): Long {
        var t = from
        while (t < from + 20_000) {
            t += 250
            if (LoginTouchGate.onPass(t, false)) return t
        }
        throw AssertionError("wurde nie ausgeblendet")
    }

    @Test
    fun gateEmergencyExitShowsAndTheWorldClearsIt() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        LoginTouchGate.setHideEnabled(context, true)
        LoginTouchGate.reset(context)
        assertTrue(LoginTouchGate.isHidden())
        assertTrue("Notausgang ändert die Sichtbarkeit", LoginTouchGate.showNow())
        assertFalse(LoginTouchGate.isHidden())
        // in der Welt gelesen und wieder draußen: der Notausgang gilt nicht mehr
        LoginTouchGate.onPass(100, true)
        missUntilHidden(100)
        assertTrue(LoginTouchGate.isHidden())
    }

    @Test
    fun gateSwitchOffShowsEverythingAndNeedsNoSignal() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        LoginTouchGate.setHideEnabled(context, true)
        LoginTouchGate.reset(context)
        assertTrue(LoginTouchGate.needsSignal())
        assertTrue(LoginTouchGate.setHideEnabled(context, false))
        assertFalse(LoginTouchGate.isHidden())
        assertFalse(LoginTouchGate.needsSignal())
        assertFalse(LoginTouchGate.hideEnabled(context))
        // eine neue Sitzung folgt dem gespeicherten Schalter
        LoginTouchGate.reset(context)
        assertFalse(LoginTouchGate.isHidden())
        LoginTouchGate.setHideEnabled(context, true)
        LoginTouchGate.reset(context)
        assertTrue(LoginTouchGate.isHidden())
    }

    // --- Welche Elemente im Login bleiben ---

    @Test
    fun escapeAndKeyboardButtonsStayInLogin() {
        assertTrue(keepsInLogin(ControlElement.Type.BUTTON, Binding.KEY_ESC))
        assertTrue(keepsInLogin(ControlElement.Type.BUTTON, Binding.SHOW_KEYBOARD))
    }

    @Test
    fun everythingElseIsHiddenInLogin() {
        assertFalse(keepsInLogin(ControlElement.Type.BUTTON, Binding.KEY_1))
        assertFalse(keepsInLogin(ControlElement.Type.BUTTON, Binding.KEY_ENTER))
        assertFalse(keepsInLogin(ControlElement.Type.BUTTON, Binding.MOUSE_LEFT_BUTTON))
        assertFalse(keepsInLogin(ControlElement.Type.BUTTON, Binding.OPEN_NAVIGATION_MENU))
        assertFalse(keepsInLogin(ControlElement.Type.BUTTON, Binding.VOICE_CHAT))
        assertFalse(keepsInLogin(ControlElement.Type.BUTTON, Binding.NONE))
        assertFalse(keepsInLogin(ControlElement.Type.BUTTON, null))
    }

    @Test
    fun sticksAndWheelsNeverStayEvenWithAKeptBinding() {
        for (type in ControlElement.Type.entries) {
            if (type == ControlElement.Type.BUTTON) continue
            assertFalse(type.name, keepsInLogin(type, Binding.KEY_ESC))
            assertFalse(type.name, keepsInLogin(type, Binding.SHOW_KEYBOARD))
        }
    }

    @Test
    fun theKeptBindingsAreExactlyEscapeAndKeyboard() {
        assertEquals(setOf(Binding.KEY_ESC, Binding.SHOW_KEYBOARD), LOGIN_KEPT_BINDINGS)
    }
}
