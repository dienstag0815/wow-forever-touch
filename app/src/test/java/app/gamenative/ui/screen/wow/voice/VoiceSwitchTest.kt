package app.gamenative.ui.screen.wow.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kippschalter "Umlaute als echte Zeichen" auf der Einstellungsseite: gespeichert wird weiter der [UmlautMode]. */
class VoiceSwitchTest {
    @Test
    fun theUmlautSwitchIsOnExactlyForRealCharacters() {
        assertTrue(umlautSwitchOn(UmlautMode.DIRECT))
        assertFalse(umlautSwitchOn(UmlautMode.ASCII))
    }

    @Test
    fun switchingMapsBackToTheStoredModes() {
        assertEquals(UmlautMode.DIRECT, umlautModeFor(true))
        assertEquals(UmlautMode.ASCII, umlautModeFor(false))
        for (mode in UmlautMode.entries) assertEquals(mode, umlautModeFor(umlautSwitchOn(mode)))
    }
}
