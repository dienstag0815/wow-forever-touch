package app.gamenative.wowaction

import app.gamenative.TestTexts
import com.winlator.inputcontrols.Binding
import com.winlator.inputcontrols.BindingCombo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The display of the German standard texts stored in layouts: the stored value is untouched, only the display follows the app language. */
class StandardNamesEnglishTest {
    @get:Rule
    val texts = TestTexts.rule("en")

    @Test
    fun paletteTextsAreShownInEnglish() {
        assertEquals("Panels", StandardNames.displayLabel("WoW-Menü"))
        assertEquals("Menus", StandardNames.displayLabel("Menüs"))
        assertEquals("Circle", StandardNames.displayLabel("Kreis"))
        assertEquals("Emote", StandardNames.displayLabel("Emote"))
        assertEquals("Menu", StandardNames.displayLabel("Menü"))
        assertEquals("Menu", StandardNames.displayLabel("MENÜ"))
        assertEquals("Keys", StandardNames.displayLabel("Tastatur"))
        assertEquals("Edit", StandardNames.displayLabel("Bearb."))
        assertEquals("Voice", StandardNames.displayLabel("Sprache"))
        assertEquals("Bar 3", StandardNames.displayLabel("Leiste 3"))
    }

    @Test
    fun menuNamesAreShownInEnglish() {
        assertEquals("Game menus", StandardNames.displayLabel("Spielmenüs"))
        assertEquals("Full WoW menu", StandardNames.displayLabel("WoW-Menü komplett"))
        assertEquals("Full WoW menu 2", StandardNames.displayLabel("WoW-Menü komplett 2"))
        assertEquals("Rest of the WoW menu", StandardNames.displayLabel("WoW-Menü Rest"))
        assertEquals("Attacks", StandardNames.displayLabel("Angriffe"))
        assertEquals("Interface", StandardNames.displayLabel("Oberfläche"))
        assertEquals("WoW emotes", StandardNames.displayLabel("WoW Emotes"))
    }

    @Test
    fun typedTextStaysAsTyped() {
        assertEquals("Mein Knopf", StandardNames.displayLabel("Mein Knopf"))
        assertEquals("Leiste 3 links", StandardNames.displayLabel("Leiste 3 links"))
        assertEquals("", StandardNames.displayLabel(""))
        assertEquals("", StandardNames.displayLabel(null))
        assertNull(StandardNames.resOf("Mein Knopf"))
        assertNull(StandardNames.barOf("Leiste"))
    }

    @Test
    fun fieldLabelsFollowTheActionName() {
        assertEquals("Open All Bags", StandardNames.slotLabel("Taschen", "OPENALLBAGS"))
        assertEquals("Open All Bags", StandardNames.slotLabel("Open All Bags", "OPENALLBAGS"))
        // Old layout without action id: the German catalog name is known.
        assertEquals("World Map", StandardNames.slotLabel("Karte", null))
        assertEquals("Talents", StandardNames.slotLabel("Talente", null))
        // The player's own text is never replaced.
        assertEquals("Mein Beutel", StandardNames.slotLabel("Mein Beutel", "OPENALLBAGS"))
        assertEquals("", StandardNames.slotLabel("", "OPENALLBAGS"))
        // A standard text that is no action name goes through the standard list.
        assertEquals("Menu", StandardNames.slotLabel("Menü", null))
    }

    @Test
    fun actionBarSlotKeepsItsShortName() {
        val slot = WowActionCatalog.find("MULTIACTIONBAR1BUTTON2")!!
        assertEquals("2.2", StandardNames.slotLabel("2.2", slot.command))
        assertTrue(StandardNames.isActionName("2.2", slot))
    }

    @Test
    fun ringCaptionShowsTheEnglishName() {
        val caption = BindingCaption.forSlot("Taschen", "OPENALLBAGS", null, BindingCombo.of(Binding.KEY_B), 0, emoji = false)
        assertEquals("Open All Bags", caption.text)
    }

    @Test
    fun everyStandardTextHasAnEnglishAndAGermanResource() {
        for (stored in StandardNames.storedTexts) {
            val res = StandardNames.resOf(stored)!!
            assertTrue(stored, TestTexts.text(res, emptyList(), "en").isNotBlank())
            assertTrue(stored, TestTexts.text(res, emptyList(), "de").isNotBlank())
        }
    }

    @Test
    fun englishButtonTextsFitTheStandardButton() {
        // "Keyboard" was cut to "Keyboa" in the standard button; the short texts stay within six characters.
        for (stored in listOf("Tastatur", "Bearb.", "Menü", "WoW-Menü", "Menüs", "Kreis", "Emote", "Sprache")) {
            val shown = StandardNames.displayLabel(stored)
            assertTrue("$stored -> $shown", shown.length <= 6)
        }
    }

    @Test
    fun keyboardCaptionIsShort() {
        assertEquals("Keys", BindingCaption.forBinding(Binding.SHOW_KEYBOARD))
    }
}

class StandardNamesGermanTest {
    @get:Rule
    val texts = TestTexts.rule("de")

    @Test
    fun germanStaysGerman() {
        assertEquals("WoW-Menü", StandardNames.displayLabel("WoW-Menü"))
        assertEquals("Tastatur", StandardNames.displayLabel("Tastatur"))
        assertEquals("Bearb.", StandardNames.displayLabel("Bearb."))
        assertEquals("Leiste 3", StandardNames.displayLabel("Leiste 3"))
        assertEquals("WoW-Menü komplett", StandardNames.displayLabel("WoW-Menü komplett"))
    }

    @Test
    fun englishValuesOfNewerVersionsAreShownInGerman() {
        assertEquals("Bearb.", StandardNames.displayLabel("Edit"))
        assertEquals("Sprache", StandardNames.displayLabel("Voice"))
    }

    @Test
    fun fieldLabelsFollowTheActionName() {
        assertEquals("Taschen", StandardNames.slotLabel("Taschen", "OPENALLBAGS"))
        assertEquals("Taschen", StandardNames.slotLabel("Open All Bags", "OPENALLBAGS"))
        assertEquals("Mein Beutel", StandardNames.slotLabel("Mein Beutel", "OPENALLBAGS"))
        assertEquals("Karte", StandardNames.slotLabel("Karte", null))
    }

    @Test
    fun keyboardCaptionIsGerman() {
        assertEquals("Tastatur", BindingCaption.forBinding(Binding.SHOW_KEYBOARD))
    }
}
