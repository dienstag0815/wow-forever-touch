package app.gamenative.wowaction

import org.junit.Rule
import app.gamenative.TestTexts
import app.gamenative.wowicons.ActionSlotMap
import app.gamenative.wowicons.BridgeData
import app.gamenative.wowicons.BridgeEntry
import com.winlator.inputcontrols.Binding
import com.winlator.inputcontrols.BindingCombo
import com.winlator.inputcontrols.RadialMenu
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionBarTemplatesTest {
    @get:Rule
    val texts = TestTexts.rule("de")

    private fun data(vararg slots: Int): BridgeData = BridgeData(
        updated = 1L, playerClass = "MAGE", specName = "Fire", character = "Anna", realm = "R",
        slots = slots.associateWith { BridgeEntry(type = "spell", spellId = 100L + it, name = "Zauber $it", iconFileId = 1000L + it) },
        spells = emptyList(), mounts = emptyList(), toys = emptyList(), items = emptyList(),
    )

    private val keys: Map<String, BindingCombo> = mapOf(
        "ACTIONBUTTON1" to BindingCombo.of(Binding.KEY_1),
        "ACTIONBUTTON3" to BindingCombo.of(Binding.KEY_3),
        "ACTIONBUTTON5" to BindingCombo.of(Binding.KEY_5),
        "ACTIONBUTTON7" to BindingCombo.of(Binding.KEY_7),
    )

    @Test
    fun `bar numbers map to the binding commands of the key bindings window`() {
        assertEquals("ACTIONBUTTON4", ActionBarTemplates.commandFor(1, 4))
        assertEquals("MULTIACTIONBAR1BUTTON12", ActionBarTemplates.commandFor(2, 12))
        assertEquals("MULTIACTIONBAR2BUTTON1", ActionBarTemplates.commandFor(3, 1))
        assertEquals("MULTIACTIONBAR3BUTTON2", ActionBarTemplates.commandFor(4, 2))
        assertEquals("MULTIACTIONBAR4BUTTON2", ActionBarTemplates.commandFor(5, 2))
        assertEquals("MULTIACTIONBAR5BUTTON9", ActionBarTemplates.commandFor(6, 9))
        assertEquals("MULTIACTIONBAR6BUTTON9", ActionBarTemplates.commandFor(7, 9))
        assertEquals("MULTIACTIONBAR7BUTTON9", ActionBarTemplates.commandFor(8, 9))
        for (bar in 1..ActionBarTemplates.BAR_COUNT) {
            val commands = ActionBarTemplates.commands(bar)
            assertEquals(12, commands.size)
            assertEquals(12, commands.toSet().size)
            for (command in commands) {
                assertNotNull("$bar $command in the catalog", WowActionCatalog.find(command))
                assertNotNull("$bar $command has a slot", ActionSlotMap.slotFor(command))
            }
        }
    }

    @Test
    fun `bars map to disjoint slot ranges of twelve`() {
        val first = (1..8).map { ActionSlotMap.slotFor(ActionBarTemplates.commandFor(it, 1))!! }
        assertEquals(listOf(1, 61, 49, 25, 37, 145, 157, 169), first)
        val all = (1..8).flatMap { bar -> (1..12).map { ActionSlotMap.slotFor(ActionBarTemplates.commandFor(bar, it))!! } }
        assertEquals(96, all.toSet().size)
    }

    @Test
    fun `only occupied places are taken when addon data exists and the order is kept`() {
        // Bar 1: places 1, 3, 5, 7 (slots 1, 3, 5, 7); bar 2 slot 61 is place 1 of bar 2.
        val d = data(7, 1, 5, 3, 61)
        assertEquals(listOf(1, 3, 5, 7), ActionBarTemplates.occupiedPlaces(1, d))
        assertEquals(listOf(1), ActionBarTemplates.occupiedPlaces(2, d))
        assertEquals(emptyList<Int>(), ActionBarTemplates.occupiedPlaces(3, d))
        assertEquals((1..12).toList(), ActionBarTemplates.occupiedPlaces(1, null))
    }

    @Test
    fun `menu with data has the occupied places with action and key`() {
        val applied = ActionBarTemplates.buildMenu("button-3", 1, keys, data(1, 3, 5, 7))
        val menu = applied.menu
        assertEquals("button-3", menu.id)
        assertEquals("Leiste 1", menu.name)
        assertTrue(applied.onlyOccupied)
        assertEquals(listOf(1, 3, 5, 7), applied.places)
        assertEquals(
            listOf("ACTIONBUTTON1", "ACTIONBUTTON3", "ACTIONBUTTON5", "ACTIONBUTTON7"),
            menu.slots.map { it.wowAction },
        )
        assertEquals(BindingCombo.of(Binding.KEY_5), menu.slots[2].bindingCombo)
        assertTrue(menu.slots.all { it.label.isEmpty() })
        assertEquals(0, applied.withoutKey)
        assertEquals("Leiste 1: 4 belegte Plätze übernommen", applied.message)
    }

    @Test
    fun `menu without data has all twelve places`() {
        val applied = ActionBarTemplates.buildMenu("m", 2, emptyMap(), null)
        assertFalse(applied.onlyOccupied)
        assertEquals(12, applied.menu.slots.size)
        assertEquals("MULTIACTIONBAR1BUTTON1", applied.menu.slots[0].wowAction)
        assertEquals("MULTIACTIONBAR1BUTTON12", applied.menu.slots[11].wowAction)
        assertEquals("Leiste 2", applied.menu.name)
        // No keys at all: all twelve fields are reported as without a key.
        assertEquals(12, applied.withoutKey)
        assertTrue(applied.message.startsWith("Leiste 2: keine Addon-Daten, alle 12 Plätze übernommen"))
    }

    @Test
    fun `an empty bar with data gives all twelve places`() {
        val applied = ActionBarTemplates.buildMenu("m", 4, emptyMap(), data(1, 2, 3))
        assertFalse(applied.onlyOccupied)
        assertEquals(12, applied.menu.slots.size)
        assertEquals("MULTIACTIONBAR3BUTTON1", applied.menu.slots[0].wowAction)
        assertTrue(applied.message.contains("ist leer"))
    }

    @Test
    fun `a bar with one occupied place is filled up to the minimum`() {
        val applied = ActionBarTemplates.buildMenu("m", 1, keys, data(3))
        assertEquals(RadialMenu.MIN_SLOTS, applied.menu.slots.size)
        assertEquals(listOf(3, 1), applied.places)
        assertEquals(listOf("ACTIONBUTTON3", "ACTIONBUTTON1"), applied.menu.slots.map { it.wowAction })
    }

    @Test
    fun `bars four to eight can be built`() {
        for (bar in 4..8) {
            // Slot of place 2 of this bar is occupied.
            val slot = ActionSlotMap.slotFor(ActionBarTemplates.commandFor(bar, 2))!!
            val applied = ActionBarTemplates.buildMenu("m", bar, emptyMap(), data(slot))
            assertEquals(2, applied.menu.slots.size)
            assertEquals(ActionBarTemplates.commandFor(bar, 2), applied.menu.slots[0].wowAction)
        }
    }

    @Test
    fun `menu survives the profile json with its actions`() {
        val menu = ActionBarTemplates.buildMenu("m", 1, keys, data(1, 3, 5)).menu
        val restored = RadialMenu.fromJSONObject(menu.toJSONObject())
        assertEquals(menu.name, restored.name)
        assertEquals(menu.slots.map { it.wowAction }, restored.slots.map { it.wowAction })
    }

    @Test
    fun `ring takes the first occupied places for 4 6 and 8 segments`() {
        val d = data(2, 4, 6, 8, 10, 12)
        for (count in listOf(4, 6, 8)) {
            val slots = ActionBarTemplates.ringSlots(1, count, keys, d)
            assertEquals(count, slots.size)
        }
        assertEquals(
            listOf("ACTIONBUTTON2", "ACTIONBUTTON4", "ACTIONBUTTON6", "ACTIONBUTTON8"),
            ActionBarTemplates.ringSlots(1, 4, keys, d).map { it.wowAction },
        )
        // Six occupied places, ring of eight: the next two empty places follow (1 and 3).
        assertEquals(
            listOf(2, 4, 6, 8, 10, 12, 1, 3).map { "ACTIONBUTTON$it" },
            ActionBarTemplates.ringSlots(1, 8, keys, d).map { it.wowAction },
        )
    }

    @Test
    fun `ring without data takes places one to n`() {
        assertEquals(
            (1..6).map { "MULTIACTIONBAR2BUTTON$it" },
            ActionBarTemplates.ringSlots(3, 6, keys, null).map { it.wowAction },
        )
    }

    @Test
    fun `a field changed by hand stays after the template and the others keep their action`() {
        val menu = ActionBarTemplates.buildMenu("m", 1, keys, null).menu
        // Place 7 and 8 set by hand to other things (a game menu and a chat command).
        menu.slots[6].setWowAction("TOGGLEWORLDMAP")
        menu.slots[6].setBindingCombo(BindingCombo.of(Binding.KEY_M))
        menu.slots[7].setWowAction(null)
        menu.slots[7].setChatCommand("/dance")
        menu.slots[7].setLabel("Tanz")

        assertEquals("TOGGLEWORLDMAP", menu.slots[6].wowAction)
        assertEquals(BindingCombo.of(Binding.KEY_M), menu.slots[6].bindingCombo)
        assertEquals("/dance", menu.slots[7].chatCommand)
        assertNull(menu.slots[7].wowAction)
        for (index in 0..5) assertEquals("ACTIONBUTTON${index + 1}", menu.slots[index].wowAction)
        for (index in 8..11) assertEquals("ACTIONBUTTON${index + 1}", menu.slots[index].wowAction)

        // The change also survives the profile json.
        val restored = RadialMenu.fromJSONObject(menu.toJSONObject())
        assertEquals("TOGGLEWORLDMAP", restored.slots[6].wowAction)
        assertEquals("/dance", restored.slots[7].chatCommand)
        assertEquals("ACTIONBUTTON1", restored.slots[0].wowAction)
    }

    @Test
    fun `ring fields are separate objects that can be changed one by one`() {
        val slots = ActionBarTemplates.ringSlots(1, 8, keys, null)
        slots[6].setWowAction("TOGGLEGAMEMENU")
        assertEquals("TOGGLEGAMEMENU", slots[6].wowAction)
        assertEquals("ACTIONBUTTON8", slots[7].wowAction)
        assertEquals("ACTIONBUTTON6", slots[5].wowAction)
    }

    @Test
    fun `customized fields are detected so the template asks first`() {
        val fresh = listOf(RadialMenu.Slot("", Binding.KEY_1), RadialMenu.Slot("", Binding.KEY_2))
        assertFalse(ActionBarTemplates.isCustomized(fresh))
        assertTrue(ActionBarTemplates.isCustomized(ActionBarTemplates.buildMenu("m", 1, keys, null).menu.slots))
        val labelled = listOf(RadialMenu.Slot("Heilen", Binding.KEY_1))
        assertTrue(ActionBarTemplates.isCustomized(labelled))
        val chat = listOf(RadialMenu.Slot("", BindingCombo.none(), "EMOTE_DANCE", "/dance"))
        assertTrue(ActionBarTemplates.isCustomized(chat))
    }

    // ---- ring templates besides the action bars ----------------------------------------------------------------

    private fun template(name: String) = ActionBarTemplates.RING_TEMPLATES.first { it.name == name }

    @Test
    fun `ring page offers all menu templates of the radial menu`() {
        assertEquals(
            listOf("Spielmenüs", "Spielmenüs 2", "WoW-Menü komplett", "WoW-Menü komplett 2", "WoW-Menü Rest", "Angriffe", "Oberfläche", "Chat", "Emotes"),
            ActionBarTemplates.RING_TEMPLATES.map { it.name },
        )
        // every command of every template is in the catalog
        for (t in ActionBarTemplates.RING_TEMPLATES) {
            for (c in t.commands) assertNotNull("${t.name}: $c", WowActionCatalog.find(c))
        }
    }

    @Test
    fun `game menus 2 holds the 6 menu actions the first template lacks`() {
        assertEquals(
            listOf("TOGGLEGUILDTAB", "TOGGLESOCIAL", "TOGGLEACHIEVEMENT", "TOGGLECOLLECTIONS", "TOGGLEENCOUNTERJOURNAL", "TOGGLEGAMEMENU"),
            ActionBarTemplates.GAME_MENU_2_COMMANDS,
        )
        val menuActions = WowActionCatalog.all.filter { it.group == WowActionGroup.MENUS }.map { it.command }.toSet()
        assertEquals(14, menuActions.size)
        assertEquals(menuActions, (WowActionCatalog.GAME_MENU_COMMANDS + ActionBarTemplates.GAME_MENU_2_COMMANDS).toSet())
    }

    @Test
    fun `wow menu complete holds the buttons of the menu bar and fits the radial menu`() {
        val commands = WowActionCatalog.WOW_MENU_COMMANDS
        assertEquals(12, commands.size)
        assertTrue(commands.size in RadialMenu.MIN_SLOTS..RadialMenu.MAX_SLOTS)
        assertEquals(commands.size, commands.toSet().size)
        // every field is a menu action of the catalog (real WoW icons), and they are exactly the 14 menu actions without map and social
        for (c in commands) assertEquals(c, WowActionGroup.MENUS, WowActionCatalog.find(c)!!.group)
        val menus = WowActionCatalog.all.filter { it.group == WowActionGroup.MENUS }.map { it.command }.toSet()
        assertEquals(menus - setOf("TOGGLEWORLDMAP", "TOGGLESOCIAL"), commands.toSet())
        assertEquals(
            listOf(
                "Charakter", "Zauberbuch", "Talente", "Berufe", "Quests", "Erfolge", "Gilde", "Gruppensuche", "Sammlungen",
                "Abenteuerführer", "Taschen", "Spielmenü",
            ),
            WowActionCatalog.buildMenu("m", "WoW-Menü komplett", commands, keys).slots.map { it.label },
        )
        val second = WowActionCatalog.WOW_MENU_2_COMMANDS
        assertTrue(second.size in RadialMenu.MIN_SLOTS..RadialMenu.MAX_SLOTS)
        assertEquals(listOf("TOGGLESOCIAL", "WFB_CHANNELS", "WFB_CHATMENU", "TOGGLEWORLDMAP", "WFB_HOUSING"), second)
        for (c in second) assertNotNull(c, WowActionCatalog.find(c))
        // in a radial menu every field has its key: the WoW keys or the standard keys of the addon
        val resolved = WowActionResolver.resolveAll(null).mapValues { it.value.combo }
        for (list in listOf(commands, second)) {
            val menu = WowActionCatalog.buildMenu("m", "WoW-Menü komplett", list, resolved)
            assertEquals(list.size, menu.slots.size)
            assertTrue(menu.slots.all { it.wowAction != null && !it.bindingCombo.isEmpty })
        }
    }

    @Test
    fun `wow menu complete fills 4 6 and 8 ring segments and 2 comes after it`() {
        val t = template("WoW-Menü komplett")
        for (count in listOf(4, 6, 8)) {
            val slots = ActionBarTemplates.ringSlotsFor(t, count, keys)
            assertEquals(count, slots.size)
            assertEquals(WowActionCatalog.WOW_MENU_COMMANDS.take(count), slots.map { it.wowAction })
            assertEquals(0, ActionBarTemplates.ringEmpty(t, count))
        }
        val second = template("WoW-Menü komplett 2")
        assertEquals(3, ActionBarTemplates.ringEmpty(second, 8))
        assertEquals(WowActionCatalog.WOW_MENU_2_COMMANDS, ActionBarTemplates.ringSlotsFor(second, 6, keys).take(5).map { it.wowAction })
    }

    @Test
    fun `wow menu rest holds only what the micro menu of 12 does not have`() {
        val micro = WowActionCatalog.MICRO_MENU_12_COMMANDS
        val rest = WowActionCatalog.WOW_MENU_REST_COMMANDS
        // the micro menu of 12.x: twelve buttons (character, professions, spellbook + talents, achievements, quest log, housing, guild, group finder,
        // collections, adventure guide, game menu; the shop has no action), all in the catalog
        assertEquals(12, micro.size)
        assertEquals(micro.size, micro.toSet().size)
        for (c in micro) assertNotNull(c, WowActionCatalog.find(c))
        // no overlap, and the rest fits the radial menu and is not empty
        assertEquals(emptySet<String>(), micro.toSet() intersect rest.toSet())
        assertEquals(10, rest.size)
        assertEquals(rest.size, rest.toSet().size)
        assertTrue(rest.size in RadialMenu.MIN_SLOTS..RadialMenu.MAX_SLOTS)
        for (c in rest) assertNotNull(c, WowActionCatalog.find(c))
        // the windows the menu bar lacks, in this order; the first 8 are the template of an 8 key ring
        assertEquals(
            listOf("Karte", "Taschen", "Soziales", "Ruf", "Währungen", "PvP", "Raid", "Chat-Kanäle", "Chat-Menü", "Menüleiste und Taschenleiste aus/ein"),
            WowActionCatalog.buildMenu("m", "WoW-Menü Rest", rest, keys).slots.map { WowActionCatalog.find(it.wowAction)!!.nameDe },
        )
        assertTrue(rest.containsAll(listOf("TOGGLEWORLDMAP", "OPENALLBAGS", "TOGGLESOCIAL")))
    }

    @Test
    fun `wow menu rest has a key for every field and the standard windows are the real binding commands`() {
        val rest = WowActionCatalog.WOW_MENU_REST_COMMANDS
        val resolved = WowActionResolver.resolveAll(null).mapValues { it.value.combo }
        val menu = WowActionCatalog.buildMenu("m", "WoW-Menü Rest", rest, resolved)
        assertEquals(rest.size, menu.slots.size)
        assertTrue(menu.slots.all { it.wowAction != null && !it.bindingCombo.isEmpty && it.isEnabled })
        // reputation, currencies, PvP and raid: commands of Bindings_Standard.xml; the addon puts ALT-CTRL-F1 to F3 and ALT-CTRL-HOME on them
        val extra = mapOf(
            "TOGGLECHARACTER2" to "ALT-CTRL-F1", "TOGGLECURRENCY" to "ALT-CTRL-F2", "TOGGLECHARACTER4" to "ALT-CTRL-F3", "TOGGLERAIDTAB" to "ALT-CTRL-HOME",
        )
        for ((command, key) in extra) {
            val action = WowActionCatalog.find(command)!!
            assertEquals(command, WowActionGroup.MENUS_MORE, action.group)
            assertEquals(command, key, action.defaultKey)
            assertEquals(command, listOf(Binding.KEY_ALT_L, Binding.KEY_CTRL_L), WowKeys.toCombo(key)!!.bindings.take(2))
            assertNotNull(command, action.emoji)
        }
        // those keys are free: no other catalog action has the same standard key
        val others = WowActionCatalog.all.filter { it.command !in extra }.mapNotNull { it.defaultKey }.toSet()
        assertTrue(extra.values.none { it in others })
        // a key from the bindings file of the game wins (reputation on U)
        val bindings = WowBindings.parse("bind U TOGGLECHARACTER2\n")
        assertEquals("U", WowActionResolver.resolve(WowActionCatalog.find("TOGGLECHARACTER2")!!, bindings)!!.wowKey)
        // the template survives the profile json and the ring takes the first entries
        val restored = RadialMenu.fromJSONObject(menu.toJSONObject())
        assertEquals(menu.slots.map { it.wowAction }, restored.slots.map { it.wowAction })
        val t = template("WoW-Menü Rest")
        for (count in listOf(4, 6, 8)) {
            assertEquals(rest.take(count), ActionBarTemplates.ringSlotsFor(t, count, keys).map { it.wowAction })
            assertEquals(0, ActionBarTemplates.ringEmpty(t, count))
        }
        assertEquals(2, ActionBarTemplates.ringEmpty(t, 12))
    }

    @Test
    fun `addon sets the extra keys of the rest template`() {
        val lua = java.io.File("src/main/assets/wow/addon/TouchForever/TouchForeverUI.lua").readText(Charsets.UTF_8)
        val block = lua.substringAfter("UI.EXTRA_KEYS = {").substringBefore("}")
        val luaKeys = Regex("(\\w+)\\s*=\\s*\"([A-Z0-9-]+)\"").findAll(block).associate { it.groupValues[1] to it.groupValues[2] }
        assertEquals(4, luaKeys.size)
        for ((command, key) in luaKeys) assertEquals(command, key, WowActionCatalog.find(command)!!.defaultKey)
        val commands = lua.substringAfter("UI.EXTRA_COMMANDS = {").substringBefore("}")
        for (command in luaKeys.keys) assertTrue(command, commands.contains("\"$command\""))
    }

    @Test
    fun `game menus fill 4 6 and 8 segments with the first entries and the keys from wow`() {
        val withKeys = mapOf("TOGGLEWORLDMAP" to BindingCombo.of(Binding.KEY_M), "TOGGLETALENTS" to BindingCombo.of(Binding.KEY_N))
        val t = template("Spielmenüs")
        for (count in listOf(4, 6, 8)) {
            val slots = ActionBarTemplates.ringSlotsFor(t, count, withKeys)
            assertEquals(count, slots.size)
            assertEquals(WowActionCatalog.GAME_MENU_COMMANDS.take(count), slots.map { it.wowAction })
            assertEquals(0, ActionBarTemplates.ringEmpty(t, count))
        }
        val four = ActionBarTemplates.ringSlotsFor(t, 4, withKeys)
        assertEquals(BindingCombo.of(Binding.KEY_M), four[0].bindingCombo)
        assertEquals(BindingCombo.of(Binding.KEY_N), four[1].bindingCombo)
        // no key known yet: the field stays inactive until WoW has one
        assertTrue(four[2].bindingCombo.isEmpty)
        // the field names itself
        assertTrue(four.all { it.label.isEmpty() })
    }

    @Test
    fun `a template shorter than the ring leaves the rest empty`() {
        val t = template("Spielmenüs 2")
        val slots = ActionBarTemplates.ringSlotsFor(t, 8, keys)
        assertEquals(8, slots.size)
        assertEquals(ActionBarTemplates.GAME_MENU_2_COMMANDS, slots.take(6).map { it.wowAction })
        assertNull(slots[6].wowAction)
        assertNull(slots[7].wowAction)
        assertFalse(slots[7].isEnabled)
        assertEquals(2, ActionBarTemplates.ringEmpty(t, 8))
        assertEquals(0, ActionBarTemplates.ringEmpty(t, 6))
        assertEquals(0, ActionBarTemplates.ringEmpty(t, 4))
        // 7 entries on 8 segments
        assertEquals(1, ActionBarTemplates.ringEmpty(template("Oberfläche"), 8))
    }

    @Test
    fun `chat template ends with the keyboard and emotes are chat commands`() {
        val chat = ActionBarTemplates.ringSlotsFor(template("Chat"), 8, keys)
        assertEquals(8, chat.size)
        assertEquals(BindingCombo.of(Binding.SHOW_KEYBOARD), chat[7].bindingCombo)
        assertEquals("Tastatur", chat[7].label)
        assertNotNull(chat[0].chatCommand)
        assertEquals(0, ActionBarTemplates.ringEmpty(template("Chat"), 8))
        // the first four of the chat template have no keyboard field
        assertTrue(ActionBarTemplates.ringSlotsFor(template("Chat"), 4, keys).none { it.bindingCombo == BindingCombo.of(Binding.SHOW_KEYBOARD) })

        val emotes = ActionBarTemplates.ringSlotsFor(template("Emotes"), 6, keys)
        assertEquals(WowActionCatalog.EMOTE_COMMANDS.take(6), emotes.map { it.wowAction })
        assertTrue(emotes.all { it.chatCommand != null && it.bindingCombo.isEmpty })
        assertTrue(emotes.all { it.isEnabled })
    }

    @Test
    fun `attacks template is action bar one places one to n`() {
        val slots = ActionBarTemplates.ringSlotsFor(template("Angriffe"), 6, keys)
        assertEquals((1..6).map { "ACTIONBUTTON$it" }, slots.map { it.wowAction })
        assertEquals(BindingCombo.of(Binding.KEY_1), slots[0].bindingCombo)
    }

    @Test
    fun `ring template fields are fresh objects each time`() {
        val a = ActionBarTemplates.ringSlotsFor(template("Spielmenüs"), 4, keys)
        val b = ActionBarTemplates.ringSlotsFor(template("Spielmenüs"), 4, keys)
        a[0].setWowAction("TOGGLEGAMEMENU")
        assertEquals("TOGGLEWORLDMAP", b[0].wowAction)
        // a ring made from a template counts as customized, so the next template asks first
        assertTrue(ActionBarTemplates.isCustomized(a))
    }
}
