package app.gamenative.wowicons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WowheadJsonTest {
    private fun resource(name: String): String =
        javaClass.getResourceAsStream("/wowicons/$name")!!.readBytes().toString(Charsets.UTF_8)

    @Test
    fun realSpellAnswer() {
        // Real answer of https://nether.wowhead.com/tooltip/spell/133 (Fireball), fetched 08.11.2026.
        val t = WowheadJson.parse(resource("wowhead_spell_133.json"))!!
        assertEquals("Fireball", t.name)
        assertEquals("spell_fire_flamebolt", t.icon)
        assertEquals("spell_fire_flamebolt", WowheadJson.iconName(resource("wowhead_spell_133.json")))
    }

    @Test
    fun realItemAnswerWithEscapedHtml() {
        // Real answer of https://nether.wowhead.com/tooltip/item/6948 (Hearthstone).
        val t = WowheadJson.parse(resource("wowhead_item_6948.json"))!!
        assertEquals("Hearthstone", t.name)
        assertEquals("inv_misc_rune_01", t.icon)
    }

    @Test
    fun errorAnswerHasNoIcon() {
        assertNull(WowheadJson.iconName(resource("wowhead_error.json")))
    }

    @Test
    fun garbageAndOddAnswers() {
        assertNull(WowheadJson.iconName(""))
        assertNull(WowheadJson.iconName("<html>not json</html>"))
        assertNull(WowheadJson.iconName("[1,2,3]"))
        assertNull(WowheadJson.iconName("{\"name\":\"x\"}"))
        assertNull(WowheadJson.iconName("{\"icon\":\"../../etc/passwd\"}"))
        assertNull(WowheadJson.iconName("{\"icon\":\"\"}"))
        assertNull(WowheadJson.iconName("{\"icon\":\"abc\""))
        assertEquals("a_b-1", WowheadJson.iconName("{ \"icon\" : \"A_B-1\" , \"x\": [1, {\"y\": null}], \"z\": true }"))
    }

    @Test
    fun unicodeEscapesInNames() {
        assertEquals("Flämmchen", WowheadJson.parse("{\"name\":\"Fl\\u00e4mmchen\",\"icon\":\"x\"}")!!.name)
    }

    @Test
    fun urls() {
        assertEquals("https://render.worldofwarcraft.com/us/icons/56/135812.jpg", IconUrls.render(135812))
        assertEquals("https://render.worldofwarcraft.com/us/icons/56/spell_fire_flamebolt.jpg", IconUrls.render("spell_fire_flamebolt"))
        assertEquals("https://wow.zamimg.com/images/wow/icons/large/spell_fire_flamebolt.jpg", IconUrls.zamimg("spell_fire_flamebolt"))
        assertEquals("https://nether.wowhead.com/tooltip/spell/133", IconUrls.wowheadSpell(133))
        assertEquals("https://nether.wowhead.com/tooltip/item/6948", IconUrls.wowheadItem(6948))
    }
}
