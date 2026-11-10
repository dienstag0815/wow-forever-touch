package app.gamenative.cooldown

import app.gamenative.TestTexts
import app.gamenative.ui.components.TextRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CooldownCheckTest {
    private fun de(ref: TextRef) = TestTexts.de(ref)

    private val bridge = CooldownCheck.BridgeInfo("Anna", "Blackrock", slots = 72, spells = 140, entries = 400)

    private fun report(
        iconsEnabled: Boolean = true,
        bridge: CooldownCheck.BridgeInfo? = this.bridge,
        cooldownsEnabled: Boolean = true,
        stripReadable: Boolean = true,
        stripReason: String = "Streifen gelesen",
        cooling: Int = 3,
        locked: Boolean = false,
    ) = CooldownCheck.build(iconsEnabled, bridge, cooldownsEnabled, stripReadable, stripReason, cooling, locked)

    @Test
    fun `everything works`() {
        val r = report()
        assertEquals("Symbole und Abklingzeiten funktionieren", de(r.summary))
        assertEquals("Addon TouchForever: gefunden, Daten von Anna-Blackrock", de(r.lines[0]))
        assertEquals("Symboldaten: 72 Leistenplätze, 140 Zauber, 400 Einträge", de(r.lines[1]))
        assertEquals("Abklingzeit-Streifen: lesbar, 3 mit Abklingzeit", de(r.lines[2]))
        assertEquals("", de(r.hint))
    }

    @Test
    fun `an unreadable strip names its reason`() {
        val r = report(stripReadable = false, stripReason = "Streifen nicht gefunden (Addon aus, Spiel lädt oder Streifen verdeckt)")
        assertEquals("Symbole funktionieren, Abklingzeiten nicht", de(r.summary))
        assertTrue(de(r.lines[2]).startsWith("Abklingzeit-Streifen: nicht lesbar: Streifen nicht gefunden"))
        assertTrue(de(r.hint).contains("/tf strip"))
    }

    @Test
    fun `without addon data the hint asks for a reload`() {
        val r = report(bridge = null)
        assertEquals("Abklingzeiten funktionieren, Symbole nicht", de(r.summary))
        assertEquals("Addon TouchForever: keine Daten gefunden", de(r.lines[0]))
        assertEquals("Symboldaten: keine", de(r.lines[1]))
        assertTrue(de(r.hint).contains("/reload"))
    }

    @Test
    fun `settings that are off are named and give no hint`() {
        val r = report(iconsEnabled = false, bridge = null, cooldownsEnabled = false, stripReadable = false)
        assertEquals("Weder Symbole noch Abklingzeiten verfügbar", de(r.summary))
        assertTrue(de(r.lines[0]).contains("Echte Symbole aus WoW"))
        assertTrue(de(r.lines[2]).contains("Abklingzeiten anzeigen"))
        assertEquals("", de(r.hint))
    }

    @Test
    fun `locked values in combat are mentioned`() {
        assertTrue(de(report(locked = true).lines[2]).endsWith("Werte im Kampf gesperrt"))
    }

    @Test
    fun `a character without a name is not printed as empty`() {
        val r = report(bridge = CooldownCheck.BridgeInfo(null, "X", 1, 1, 1))
        assertTrue(de(r.lines[0]).contains("unbekanntem Charakter"))
        assertFalse(de(r.lines[0]).contains("null"))
    }

    @Test
    fun `no text of the check mentions the old mirror names`() {
        val r = report(stripReadable = false, bridge = null)
        val all = (listOf(r.summary, r.hint) + r.lines).joinToString("\n") { de(it) }
        assertFalse(all.contains("Spiegel", ignoreCase = true))
        assertFalse(all.contains("mirror", ignoreCase = true))
    }
}
