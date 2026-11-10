package app.gamenative.ui.screen.wow

import app.gamenative.wowaction.WowActionCatalog
import app.gamenative.wowaction.WowKeys
import com.winlator.inputcontrols.Binding
import com.winlator.inputcontrols.BindingCombo
import com.winlator.inputcontrols.WowActionBindings
import com.winlator.xserver.XKeycode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** "Addon-Einstellungen öffnen": die Taste der Addon-Bindung WFB_OPTIONS, ihre Reihenfolge und das Senden. */
class AddonOptionsKeyTest {
    @Before
    fun clean() {
        WowActionBindings.setResolved(null)
    }

    @After
    fun cleanAfter() {
        WowActionBindings.setResolved(null)
    }

    @Test
    fun `the catalog has the action and its standard key can be sent`() {
        val action = WowActionCatalog.find(AddonOptionsKey.ACTION_ID)
        assertNotNull("Aktion fehlt im Katalog", action)
        val key = action!!.defaultKey
        assertNotNull(key)
        assertNotNull(WowKeys.toCombo(key!!))
        // Strg und Umschalt mit einer Funktionstaste: nie eine Taste, die im Spiel beim Tippen vorkommt.
        assertTrue(key.startsWith("CTRL-SHIFT-F"))
    }

    @Test
    fun `without resolved keys the standard key of the catalog is used`() {
        val standard = WowKeys.toCombo(WowActionCatalog.find(AddonOptionsKey.ACTION_ID)!!.defaultKey!!)
        assertEquals(standard, AddonOptionsKey.combo())
    }

    @Test
    fun `a key the player resolved wins over the standard key`() {
        val own = WowKeys.toCombo("CTRL-SHIFT-F12")!!
        WowActionBindings.setResolved(mapOf(AddonOptionsKey.ACTION_ID to own))
        assertEquals(own, AddonOptionsKey.combo())
    }

    @Test
    fun `the keys go down modifiers first and the main key last`() {
        val combo = WowKeys.toCombo("CTRL-SHIFT-F12")!!
        assertEquals(
            listOf(XKeycode.KEY_CTRL_L, XKeycode.KEY_SHIFT_L, XKeycode.KEY_F12),
            AddonOptionsKey.keyCodes(combo),
        )
    }

    @Test
    fun `no key, an empty key, a sequence and a non keyboard key are not sendable`() {
        assertTrue(AddonOptionsKey.keyCodes(null).isEmpty())
        assertTrue(AddonOptionsKey.keyCodes(BindingCombo.none()).isEmpty())
        // Strg plus eine Gamepad-Taste: halb gesendet waere es die falsche Taste
        val mixed = BindingCombo.fromBindings(listOf(Binding.KEY_CTRL_L, Binding.GAMEPAD_BUTTON_A))
        assertTrue(AddonOptionsKey.keyCodes(mixed).isEmpty())
        val sequence = BindingCombo.fromBindings(listOf(Binding.KEY_CTRL_L, Binding.KEY_F11), BindingCombo.Mode.SEQUENCE)
        assertTrue(AddonOptionsKey.keyCodes(sequence).isEmpty())
    }

    @Test
    fun `sending presses in order, waits and releases in reverse order`() {
        val log = mutableListOf<String>()
        val codes = listOf(XKeycode.KEY_CTRL_L, XKeycode.KEY_SHIFT_L, XKeycode.KEY_F12)
        AddonOptionsKey.send(
            codes = codes,
            press = { log += "down ${it.name}" },
            release = { log += "up ${it.name}" },
            pause = { log += "wait $it" },
        )
        assertEquals(
            listOf(
                "down KEY_CTRL_L", "down KEY_SHIFT_L", "down KEY_F12",
                "wait ${AddonOptionsKey.HOLD_MS}",
                "up KEY_F12", "up KEY_SHIFT_L", "up KEY_CTRL_L",
            ),
            log,
        )
    }

    @Test
    fun `sending nothing does nothing`() {
        val log = mutableListOf<String>()
        AddonOptionsKey.send(emptyList(), { log += "down" }, { log += "up" }, { log += "wait" })
        assertTrue(log.isEmpty())
    }
}
