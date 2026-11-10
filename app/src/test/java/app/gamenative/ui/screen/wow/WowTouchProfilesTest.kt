package app.gamenative.ui.screen.wow

import app.gamenative.TestTexts
import app.gamenative.ui.components.TextRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WowTouchProfilesTest {
    /** Bestätigungstext des Zurücksetzens (Ressource plus Profilname), wie ihn der Dialog zeigt. */
    private fun resetConfirmText(mode: InputMode) = TextRef(WowTouchProfiles.RESET_CONFIRM_TEXT, WowTouchProfiles.resetTargetName(mode))

    private val profiles = listOf(0 to "Physical Controller Default", 6 to "Touch (WoW)", 9 to "Touch (Mein Layout)")

    @Test
    fun findProfileId_matchesByExactName() {
        assertEquals(6, WowTouchProfiles.findProfileId(profiles, "Touch (WoW)"))
        assertEquals(9, WowTouchProfiles.findProfileId(profiles, "Touch (Mein Layout)"))
    }

    @Test
    fun findProfileId_missingIsNull() {
        assertNull(WowTouchProfiles.findProfileId(profiles.take(2), "Touch (Mein Layout)"))
        assertNull(WowTouchProfiles.findProfileId(profiles, "touch (wow)"))
        assertNull(WowTouchProfiles.findProfileId(emptyList(), "Touch (WoW)"))
    }

    @Test
    fun resetTarget_isTheChosenTouchProfile() {
        assertEquals(InputMode.TOUCH, WowTouchProfiles.resetTarget(InputMode.TOUCH))
        assertEquals(InputMode.TOUCH_CUSTOM, WowTouchProfiles.resetTarget(InputMode.TOUCH_CUSTOM))
    }

    @Test
    fun resetTarget_inClassicIsTheStandardTouchProfile() {
        assertEquals(InputMode.TOUCH, WowTouchProfiles.resetTarget(InputMode.CLASSIC))
    }

    @Test
    fun resetConfirmText_namesTheProfile() {
        assertTrue(TestTexts.de(resetConfirmText(InputMode.TOUCH_CUSTOM)).contains("Touch (Mein Layout)"))
        assertTrue(TestTexts.de(resetConfirmText(InputMode.CLASSIC)).contains("Touch (WoW)"))
    }

    @Test
    fun assetPath_pointsAtTheBundledTouchProfile() {
        val file = java.io.File("src/main/assets/" + WowTouchProfiles.ASSET_PATH)
        val json = org.json.JSONObject(file.readText())
        assertEquals(WowTouch.PROFILE_NAME, json.getString("name"))
    }
}
