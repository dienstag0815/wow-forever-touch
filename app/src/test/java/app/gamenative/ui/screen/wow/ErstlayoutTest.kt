package app.gamenative.ui.screen.wow

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Erststart: welche Steuerung ein Spieler ohne gespeicherte Wahl bekommt, und dass die Grundknöpfe im Standardprofil stehen. */
class ErstlayoutTest {
    @Test
    fun freshInstall_getsTheStandardLayout() {
        assertEquals(InputMode.TOUCH_STANDARD, InputMode.resolve(null, freshInstall = true, launchedBefore = false))
    }

    @Test
    fun updateThatNeverStartedAGame_isStillAFirstTimePlayer() {
        assertEquals(InputMode.TOUCH_STANDARD, InputMode.resolve(null, freshInstall = false, launchedBefore = false))
    }

    @Test
    fun unreadableInstallTimes_withoutEarlierLaunch_getTheStandardLayout() {
        assertEquals(InputMode.TOUCH_STANDARD, InputMode.resolve(null, freshInstall = null, launchedBefore = false))
    }

    @Test
    fun updateWithEarlierLaunch_keepsStandard() {
        assertEquals(InputMode.CLASSIC, InputMode.resolve(null, freshInstall = false, launchedBefore = true))
        assertEquals(InputMode.CLASSIC, InputMode.resolve(null, freshInstall = null, launchedBefore = true))
    }

    @Test
    fun freshInstall_getsTheStandardLayoutEvenWithAnEarlierLaunchFlag() {
        assertEquals(InputMode.TOUCH_STANDARD, InputMode.resolve(null, freshInstall = true, launchedBefore = true))
    }

    @Test
    fun savedChoice_alwaysWins_alsoOneFromABackupImport() {
        for (mode in InputMode.entries) {
            for (fresh in listOf(true, false, null)) {
                for (launched in listOf(true, false)) {
                    assertEquals(mode, InputMode.resolve(mode.name, fresh, launched))
                }
            }
        }
        assertEquals(InputMode.CLASSIC, InputMode.resolve("GIBT_ES_NICHT", freshInstall = true, launchedBefore = false))
    }

    @Test
    fun oldOneArgumentDefault_isUnchanged() {
        assertEquals(InputMode.TOUCH_STANDARD, InputMode.defaultFor(freshInstall = true))
        assertEquals(InputMode.CLASSIC, InputMode.defaultFor(freshInstall = false))
    }

    @Test
    fun defaultTemplate_hasEscKeyboardAndMenuButtonsAtTheTop() {
        val json = JSONObject(File("src/main/assets/" + WowTouchProfiles.FULL_ASSET_PATH).readText())
        val elements = json.getJSONArray("elements")
        fun binding(e: JSONObject): String? = e.getJSONArray("bindings").let { b -> (0 until b.length()).map { b.optString(it) }.firstOrNull { it != "NONE" } }
        val wanted = listOf("KEY_ESC", "SHOW_KEYBOARD", "OPEN_NAVIGATION_MENU")
        for (name in wanted) {
            val e = (0 until elements.length()).map { elements.getJSONObject(it) }.firstOrNull { binding(it) == name }
            assertTrue("$name fehlt im Standardprofil", e != null)
            assertTrue("$name nicht oben", e!!.getDouble("y") < 0.15)
            assertTrue("$name nicht in der Mitte-links-Reihe", e.getDouble("x") in 0.25..0.45)
        }
    }
}
