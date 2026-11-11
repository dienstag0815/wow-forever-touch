package app.gamenative.wowaction

import org.junit.Rule
import app.gamenative.TestTexts
import com.winlator.inputcontrols.Binding
import com.winlator.inputcontrols.BindingCombo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WowActionCatalogTest {
    @get:Rule
    val texts = TestTexts.rule("de")

    @Test
    fun `commands are unique and not blank`() {
        val commands = WowActionCatalog.all.map { it.command }
        assertEquals(commands.size, commands.toSet().size)
        assertTrue(commands.all { it.isNotBlank() && it == it.uppercase() && !it.contains(' ') })
    }

    @Test
    fun `catalog size and groups`() {
        assertEquals(174, WowActionCatalog.all.size)
        val groups = WowActionCatalog.byGroup()
        assertEquals(14, groups.getValue(WowActionGroup.MENUS).size)
        assertEquals(4, groups.getValue(WowActionGroup.MENUS_MORE).size)
        assertEquals(96, groups.getValue(WowActionGroup.ACTION_BAR).size)
        assertEquals(10, groups.getValue(WowActionGroup.TARGET_COMBAT).size)
        assertEquals(10, groups.getValue(WowActionGroup.MOVEMENT).size)
        assertEquals(4, groups.getValue(WowActionGroup.OTHER).size)
        assertEquals(20, groups.getValue(WowActionGroup.EMOTES).size)
        assertEquals(16, groups.getValue(WowActionGroup.INTERFACE).size)
    }

    @Test
    fun `every symbol is drawn by the painter`() {
        for (action in WowActionCatalog.all) {
            assertTrue("${action.command} -> ${action.symbol}", action.symbol in WowSymbols.KNOWN)
        }
    }

    @Test
    fun `every default key can be sent`() {
        for (action in WowActionCatalog.all) {
            val key = action.defaultKey ?: continue
            assertNotNull("${action.command} default $key", WowKeys.toCombo(key))
        }
    }

    @Test
    fun `required actions of the order exist with their standard keys`() {
        val expected = mapOf(
            "TOGGLEWORLDMAP" to "M",
            "TOGGLETALENTS" to "N",
            "TOGGLEPROFESSIONBOOK" to "K",
            "TOGGLESPELLBOOK" to "P",
            "OPENALLBAGS" to "B",
            "TOGGLEQUESTLOG" to "L",
            "TOGGLECHARACTER0" to "C",
            "TOGGLEGAMEMENU" to "ESCAPE",
            "ACTIONBUTTON1" to "1",
            "ACTIONBUTTON10" to "0",
            "ACTIONBUTTON11" to "-",
            "ACTIONBUTTON12" to "=",
            "TARGETNEARESTENEMY" to "TAB",
            "TOGGLEAUTORUN" to "NUMLOCK",
            "SITORSTAND" to "X",
        )
        for ((command, key) in expected) {
            assertEquals(command, key, WowActionCatalog.find(command)?.defaultKey)
        }
        for (command in listOf("TOGGLEGROUPFINDER", "TOGGLECOLLECTIONS", "TOGGLEACHIEVEMENT", "TOGGLEGUILDTAB", "INTERACTTARGET")) {
            assertNotNull(command, WowActionCatalog.find(command))
        }
        assertNull(WowActionCatalog.find("DOES_NOT_EXIST"))
        assertNull(WowActionCatalog.find(null))
    }

    @Test
    fun `search finds german english and command`() {
        assertEquals("TOGGLEWORLDMAP", WowActionCatalog.search("karte").first().command)
        assertEquals("TOGGLEWORLDMAP", WowActionCatalog.search("world map").first().command)
        assertTrue(WowActionCatalog.search("talents").any { it.command == "TOGGLETALENTS" })
        assertTrue(WowActionCatalog.search("MULTIACTIONBAR1BUTTON3").any { it.command == "MULTIACTIONBAR1BUTTON3" })
        assertEquals(WowActionCatalog.all.size, WowActionCatalog.search("  ").size)
        assertTrue(WowActionCatalog.search("gibtsnicht").isEmpty())
    }

    @Test
    fun `display label falls back to the action name and then to the key`() {
        val combo = BindingCombo.of(Binding.KEY_M)
        assertEquals("Meine Karte", WowActionCatalog.displayLabel("Meine Karte", "TOGGLEWORLDMAP", combo))
        assertEquals("Karte", WowActionCatalog.displayLabel("", "TOGGLEWORLDMAP", combo))
        assertEquals("3", WowActionCatalog.displayLabel(null, "ACTIONBUTTON3", combo))
        assertEquals("M", WowActionCatalog.displayLabel("", null, combo))
        assertEquals("", WowActionCatalog.displayLabel("", null, BindingCombo.none()))
    }

    @Test
    fun `menu templates have the fields of the order`() {
        val keys = WowActionResolver.resolveAll(null).mapValues { it.value.combo }
        val game = WowActionCatalog.buildMenu("button-1", "Spielmenüs", WowActionCatalog.GAME_MENU_COMMANDS, keys)
        assertEquals(8, game.slots.size)
        assertEquals(
            listOf("Karte", "Talente", "Berufe", "Zauberbuch", "Taschen", "Quests", "Charakter", "Gruppensuche"),
            game.slots.map { it.label },
        )
        assertEquals("TOGGLEWORLDMAP", game.slots[0].wowAction)
        assertEquals(BindingCombo.of(Binding.KEY_M), game.slots[0].bindingCombo)
        assertEquals("button-1", game.id)

        val attacks = WowActionCatalog.buildMenu("button-2", "Angriffe", WowActionCatalog.ATTACK_COMMANDS, keys)
        assertEquals(8, attacks.slots.size)
        assertEquals(BindingCombo.of(Binding.KEY_8), attacks.slots[7].bindingCombo)
        assertEquals("ACTIONBUTTON8", attacks.slots[7].wowAction)
    }

    @Test
    fun `template field without a key stays disabled but keeps its action`() {
        val menu = WowActionCatalog.buildMenu("x", "y", listOf("MULTIACTIONBAR5BUTTON1"), emptyMap())
        assertEquals(1, menu.slots.size)
        assertTrue(menu.slots[0].bindingCombo.isEmpty)
        assertEquals("MULTIACTIONBAR5BUTTON1", menu.slots[0].wowAction)
    }

    @Test
    fun `emote group has twenty chat commands with german names`() {
        val emotes = WowActionCatalog.byGroup().getValue(WowActionGroup.EMOTES)
        assertEquals(20, emotes.size)
        assertTrue(emotes.all { it.command.startsWith("EMOTE_") && it.defaultKey == null })
        assertTrue(emotes.all { it.chatCommand != null && it.chatCommand!!.startsWith("/") })
        assertEquals(emotes.size, emotes.map { it.chatCommand }.toSet().size)
        val byName = emotes.associate { it.nameDe to it.chatCommand }
        assertEquals("/wave", byName["Winken"])
        assertEquals("/hello", byName["Hallo"])
        assertEquals("/bye", byName["Tschüss"])
        assertEquals("/dance", byName["Tanzen"])
        assertEquals("/cheer", byName["Jubeln"])
        assertEquals("/laugh", byName["Lachen"])
        assertEquals("/bow", byName["Verbeugen"])
        assertEquals("/thank", byName["Danke"])
        assertEquals("/applaud", byName["Applaus"])
        assertEquals("/cry", byName["Weinen"])
        assertEquals("/roar", byName["Brüllen"])
        assertEquals("/flirt", byName["Flirten"])
        assertEquals("/kiss", byName["Küssen"])
        assertEquals("/point", byName["Zeigen"])
        assertEquals("/salute", byName["Salutieren"])
        assertEquals("/sleep", byName["Schlafen"])
        assertEquals("/sit", byName["Sitzen"])
        assertEquals("/nod", byName["Ja"])
        assertEquals("/no", byName["Nein"])
        assertEquals("/roll", byName["Würfeln"])
    }

    @Test
    fun `only emotes and chat openers carry a chat command and they have no key`() {
        for (action in WowActionCatalog.all) {
            val isChatAction = action.group == WowActionGroup.EMOTES || action.command.startsWith("WFBCHAT_")
            assertEquals(action.command, isChatAction, action.chatCommand != null)
            if (isChatAction) assertNull(action.command, action.defaultKey)
        }
        // Emotes are not part of the key resolution.
        val keys = WowActionResolver.resolveAll(null)
        assertTrue(keys.keys.none { it.startsWith("EMOTE_") })
    }

    @Test
    fun `emotes are found by german name and by command`() {
        assertTrue(WowActionCatalog.search("tanz").any { it.command == "EMOTE_DANCE" })
        assertTrue(WowActionCatalog.search("/dance").any { it.command == "EMOTE_DANCE" })
        assertEquals("EMOTE_DANCE", WowActionCatalog.find("EMOTE_DANCE")?.id)
        assertEquals("Tanzen", WowActionCatalog.displayLabel("", "EMOTE_DANCE", BindingCombo.none()))
    }

    @Test
    fun `emote template has eight chat command fields`() {
        val menu = WowActionCatalog.buildMenu("button-9", "WoW Emotes", WowActionCatalog.EMOTE_COMMANDS, emptyMap())
        assertEquals(
            listOf("Winken", "Tanzen", "Jubeln", "Lachen", "Verbeugen", "Danke", "Hallo", "Sitzen"),
            menu.slots.map { it.label },
        )
        assertEquals(
            listOf("/wave", "/dance", "/cheer", "/laugh", "/bow", "/thank", "/hello", "/sit"),
            menu.slots.map { it.chatCommand },
        )
        assertTrue(menu.slots.all { it.bindingCombo.isEmpty && it.isEnabled })
        assertEquals("EMOTE_DANCE", menu.slots[1].wowAction)
        assertEquals(8, menu.enabledSlots.size)
    }

    @Test
    fun `emote template survives the profile json`() {
        val menu = WowActionCatalog.buildMenu("button-9", "WoW Emotes", WowActionCatalog.EMOTE_COMMANDS, emptyMap())
        val restored = com.winlator.inputcontrols.RadialMenu.fromJSONObject(menu.toJSONObject())
        assertEquals(menu.slots.map { it.chatCommand }, restored.slots.map { it.chatCommand })
        assertEquals(menu.slots.map { it.wowAction }, restored.slots.map { it.wowAction })
    }
}
