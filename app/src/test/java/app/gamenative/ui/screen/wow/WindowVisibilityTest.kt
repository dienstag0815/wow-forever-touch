package app.gamenative.ui.screen.wow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Option "Bei offenen Fenstern" je Element: Automatisch / Immer sichtbar / Ausblenden, zusammen mit dem Modus der Einstellung. */
class WindowVisibilityTest {
    private fun hides(
        rule: String?,
        mode: WindowHideMode,
        share: Float = 0f,
        windows: Int = 1,
        essential: Boolean = false,
        keepers: Int = 1,
    ) = WindowHideRule.hides(rule, mode, share, windows, essential, keepers)

    @Test
    fun automaticIsTheOldRule() {
        // covered mode: only above 10 %
        assertFalse(hides(null, WindowHideMode.COVERED, share = 0.1f))
        assertTrue(hides(null, WindowHideMode.COVERED, share = 0.11f))
        // all mode: everything but Escape and keyboard
        assertTrue(hides(null, WindowHideMode.ALL))
        assertFalse(hides(null, WindowHideMode.ALL, essential = true))
        assertFalse(hides(null, WindowHideMode.COVERED, share = 1f, essential = true))
        // unknown values are automatic, too
        assertTrue(hides("nonsense", WindowHideMode.ALL))
    }

    @Test
    fun keepSurvivesModeAllAndACoveringWindow() {
        assertFalse(hides("keep", WindowHideMode.ALL))
        assertFalse(hides("keep", WindowHideMode.COVERED, share = 1f))
    }

    @Test
    fun hideGoesEvenWhereAutomaticWouldStay() {
        // a window is open but does not touch the element: automatic stays, hide goes
        assertFalse(hides(null, WindowHideMode.COVERED, share = 0f))
        assertTrue(hides("hide", WindowHideMode.COVERED, share = 0f))
        assertTrue(hides("hide", WindowHideMode.ALL))
    }

    @Test
    fun nothingHidesWithoutAWindowOrWithTheSettingOff() {
        assertFalse(hides("hide", WindowHideMode.ALL, windows = 0))
        assertFalse(hides("hide", WindowHideMode.OFF))
        assertFalse(hides("keep", WindowHideMode.OFF))
        assertFalse(hides(null, WindowHideMode.OFF, share = 1f))
    }

    @Test
    fun theLastEscapeOrKeyboardButtonCannotBeHidden() {
        // another one stays visible: this one may go
        assertTrue(hides("hide", WindowHideMode.ALL, essential = true, keepers = 1))
        // none would stay: it stays and the editor says so
        assertFalse(hides("hide", WindowHideMode.ALL, essential = true, keepers = 0))
        assertFalse(hides("hide", WindowHideMode.COVERED, share = 1f, essential = true, keepers = 0))
        assertTrue(WindowHideRule.lockedOut("hide", essential = true, safeKeepers = 0))
        assertFalse(WindowHideRule.lockedOut("hide", essential = true, safeKeepers = 1))
        assertFalse(WindowHideRule.lockedOut("keep", essential = true, safeKeepers = 0))
        assertFalse(WindowHideRule.lockedOut("hide", essential = false, safeKeepers = 0))
        assertFalse(WindowHideRule.lockedOut(null, essential = true, safeKeepers = 0))
    }

    @Test
    fun onlyKnownValuesAreStored() {
        assertNull(WindowVisibility.normalize(null))
        assertNull(WindowVisibility.normalize("auto"))
        assertNull(WindowVisibility.normalize("x"))
        assertEquals("keep", WindowVisibility.normalize("keep"))
        assertEquals("hide", WindowVisibility.normalize("hide"))
        assertEquals(WindowVisibility.AUTO, WindowVisibility.fromKey(null))
        assertEquals(WindowVisibility.KEEP, WindowVisibility.fromKey("keep"))
    }

    @Test
    fun theLoginRuleIsUntouched() {
        // keepsInLogin stays the one rule of Escape and keyboard; the option does not enter it
        assertTrue(WindowHideRule.alwaysKept(com.winlator.inputcontrols.ControlElement.Type.BUTTON, com.winlator.inputcontrols.Binding.KEY_ESC))
        assertTrue(keepsInLogin(com.winlator.inputcontrols.ControlElement.Type.BUTTON, com.winlator.inputcontrols.Binding.SHOW_KEYBOARD))
        assertFalse(keepsInLogin(com.winlator.inputcontrols.ControlElement.Type.BUTTON, com.winlator.inputcontrols.Binding.NONE))
    }
}
