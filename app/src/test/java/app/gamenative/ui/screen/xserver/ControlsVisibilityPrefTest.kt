package app.gamenative.ui.screen.xserver

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlsVisibilityPrefTest {

    @Test
    fun decide_nullStored_usesDefaultTrue() {
        assertTrue(ControlsVisibilityPref.decide(null, true))
    }

    @Test
    fun decide_nullStored_usesDefaultFalse() {
        assertFalse(ControlsVisibilityPref.decide(null, false))
    }

    @Test
    fun decide_storedFalse_overridesDefaultTrue() {
        assertFalse(ControlsVisibilityPref.decide(false, true))
    }

    @Test
    fun decide_storedTrue_overridesDefaultFalse() {
        assertTrue(ControlsVisibilityPref.decide(true, false))
    }
}
