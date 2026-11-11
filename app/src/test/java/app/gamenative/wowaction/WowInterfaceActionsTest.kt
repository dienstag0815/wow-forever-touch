package app.gamenative.wowaction

import org.junit.Rule
import app.gamenative.TestTexts
import app.gamenative.wowicons.BridgeInstaller
import com.winlator.inputcontrols.Binding
import com.winlator.inputcontrols.BindingCombo
import com.winlator.inputcontrols.RadialMenu
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The catalog entries and menu templates of the addon's interface commands against the addon files in the assets. */
class WowInterfaceActionsTest {
    @get:Rule
    val texts = TestTexts.rule("de")

    private val addonDir = File("src/main/assets/wow/addon/TouchForever")
    private val bindingsXml by lazy { File(addonDir, "Bindings.xml").readText(Charsets.UTF_8) }
    private val uiLua by lazy { File(addonDir, "TouchForeverUI.lua").readText(Charsets.UTF_8) }
    private val optionsLua by lazy { File(addonDir, "TouchForeverOptions.lua").readText(Charsets.UTF_8) }

    private val keyCommands: List<WowAction> get() = WowActionCatalog.all.filter { it.command.startsWith("WFB_") }

    private fun xmlCommands(): List<String> = Regex("<Binding\\s+name=\"(\\w+)\"").findAll(bindingsXml).map { it.groupValues[1] }.toList()

    @Test
    fun `bindings xml and catalog list the same commands`() {
        assertEquals(11, xmlCommands().size)
        assertEquals(xmlCommands().toSet(), keyCommands.map { it.command }.toSet())
        // the "Oberfläche" template has the first seven; the four new ones (menu bar, channels, chat menu, housing) are in WoW-Menü komplett 2
        assertTrue(xmlCommands().containsAll(WowActionCatalog.INTERFACE_COMMANDS))
        assertEquals(
            setOf("WFB_TOGGLEMENUBAR", "WFB_CHANNELS", "WFB_CHATMENU", "WFB_HOUSING"),
            xmlCommands().toSet() - WowActionCatalog.INTERFACE_COMMANDS.toSet(),
        )
        assertTrue(keyCommands.all { it.group == WowActionGroup.INTERFACE })
    }

    @Test
    fun `every binding is in the category WoW Forever and calls a function of the addon`() {
        val blocks = Regex("<Binding\\s+name=\"(\\w+)\"([^>]*)>(.*?)</Binding>", RegexOption.DOT_MATCHES_ALL).findAll(bindingsXml).toList()
        assertEquals(11, blocks.size)
        for (block in blocks) {
            val (name, attributes, body) = block.destructured
            assertTrue("$name category", attributes.contains("category=\"WOWFOREVER\""))
            val call = Regex("TouchForeverUI\\.(\\w+)\\(\\)").find(body)
            assertNotNull("$name body", call)
            val function = call!!.groupValues[1]
            val defined = Regex("(function UI\\.$function\\(|UI\\.$function\\s*=|, UI\\.$function\\b)").containsMatchIn(uiLua + optionsLua)
            assertTrue("$name -> $function", defined)
            assertTrue("$name display name", uiLua.contains("BINDING_NAME_$name ="))
        }
        assertTrue(uiLua.contains("BINDING_CATEGORY_WOWFOREVER ="))
        assertTrue(bindingsXml.contains("header=\"WOWFOREVER\""))
    }

    @Test
    fun `standard keys of the catalog equal the standard keys of the addon`() {
        val block = uiLua.substringAfter("UI.DEFAULT_KEYS = {").substringBefore("}")
        val luaKeys = Regex("(WFB_\\w+)\\s*=\\s*\"([A-Z0-9-]+)\"").findAll(block).associate { it.groupValues[1] to it.groupValues[2] }
        assertEquals(11, luaKeys.size)
        for (action in keyCommands) {
            assertEquals(action.command, luaKeys[action.command], action.defaultKey)
        }
        val commands = Regex("UI.COMMANDS = \\{([^}]*)\\}").find(uiLua)!!.groupValues[1]
        for (action in keyCommands) assertTrue(action.command, commands.contains("\"${action.command}\""))
    }

    @Test
    fun `standard keys are free, distinct, in WoW notation and can be sent`() {
        val keys = keyCommands.map { it.defaultKey!! }
        assertEquals(keys.size, keys.toSet().size)
        for (key in keys) {
            assertTrue(key, key.startsWith("CTRL-SHIFT-F"))
            val combo = WowKeys.toCombo(key)
            assertNotNull(key, combo)
            assertEquals(listOf(Binding.KEY_CTRL_L, Binding.KEY_SHIFT_L), combo!!.bindings.take(2))
        }
        // no standard key of another catalog action
        val others = WowActionCatalog.all.filter { !it.command.startsWith("WFB_") }.mapNotNull { it.defaultKey }.toSet()
        assertTrue(keys.none { it in others })
    }

    @Test
    fun `a key from the bindings cache of the game replaces the standard key`() {
        val bindings = WowBindings.parse("bind CTRL-SHIFT-F5 WFB_TOGGLEBARS\nbind ALT-F2 WFB_TOGGLECHAT\n")
        val bars = WowActionResolver.resolve(WowActionCatalog.find("WFB_TOGGLEBARS")!!, bindings)!!
        assertEquals(WowKeySource.WOW_FILE, bars.source)
        assertEquals(listOf(Binding.KEY_CTRL_L, Binding.KEY_SHIFT_L, Binding.KEY_F5), bars.combo.bindings)
        val chat = WowActionResolver.resolve(WowActionCatalog.find("WFB_TOGGLECHAT")!!, bindings)!!
        assertEquals("ALT-F2", chat.wowKey)
        // without a file the standard key counts
        assertEquals(WowKeySource.DEFAULT, WowActionResolver.resolve(WowActionCatalog.find("WFB_PEEKALL")!!, null)!!.source)
    }

    @Test
    fun `names are german, emoji exist and the group has its own title`() {
        assertEquals("Oberfläche", WowActionGroup.INTERFACE.title)
        val expected = mapOf(
            "WFB_TOGGLEBARS" to "Aktionsleisten aus/ein",
            "WFB_PEEKBARS" to "Aktionsleisten kurz zeigen",
            "WFB_TOGGLECHAT" to "Chat aus/ein",
            "WFB_TOGGLEMINIMAP" to "Minikarte aus/ein",
            "WFB_TOGGLEQUESTS" to "Questverfolgung aus/ein",
            "WFB_PEEKALL" to "Alles kurz zeigen",
            "WFB_OPTIONS" to "Addon-Einstellungen",
            "WFB_TOGGLEMENUBAR" to "Menüleiste und Taschenleiste aus/ein",
            "WFB_CHANNELS" to "Chat-Kanäle",
            "WFB_CHATMENU" to "Chat-Menü",
            "WFB_HOUSING" to "Wohnen",
            "WFBCHAT_SAY" to "Sagen",
            "WFBCHAT_PARTY" to "Gruppe",
            "WFBCHAT_GUILD" to "Gilde",
            "WFBCHAT_WHISPER" to "Flüstern",
        )
        for ((command, name) in expected) {
            val action = WowActionCatalog.find(command)
            assertNotNull(command, action)
            assertEquals(name, action!!.nameDe)
            assertNotNull(command, action.emoji)
        }
    }

    @Test
    fun `chat openers are slash commands that the addon knows`() {
        val openers = WowActionCatalog.all.filter { it.command.startsWith("WFBCHAT_") }
        assertEquals(4, openers.size)
        for (opener in openers) {
            val command = opener.chatCommand!!
            assertTrue(command, command.startsWith("/tf "))
            val word = command.removePrefix("/tf ")
            assertTrue(command, uiLua.contains("word == \"$word\""))
        }
        // the main file hands unknown subcommands to the interface module
        val main = File(addonDir, "TouchForever.lua").readText(Charsets.UTF_8)
        assertTrue(main.contains("ns.slash(message)"))
        assertTrue(uiLua.contains("ns.slash = UI.Slash"))
    }

    @Test
    fun `toc loads the interface module and the save variable stays`() {
        val toc = File(addonDir, "TouchForever.toc").readText(Charsets.UTF_8)
        val files = toc.lines().filter { it.isNotBlank() && !it.startsWith("##") }.map { it.trim() }
        assertEquals(listOf("TouchForeverLang.lua", "TouchForeverLocale.lua", "TouchForever.lua", "TouchForeverUI.lua", "TouchForeverLayout.lua", "TouchForeverOptions.lua", "TouchForeverCooldown.lua", "TouchForeverContext.lua", "TouchForeverBindings.lua"), files)
        assertTrue(toc.contains("## SavedVariablesPerCharacter: TouchForeverDB"))
        for (name in files) assertTrue(name, File(addonDir, name).isFile)
        // Bindings.xml is picked up by the game by its name, it is not part of the toc
        assertTrue(File(addonDir, "Bindings.xml").isFile)
    }

    @Test
    fun `all addon files are installed with the game folder`() {
        val root = Files.createTempDirectory("wfb-install").toFile()
        try {
            File(root, "_forever_").mkdirs()
            val files = addonDir.listFiles { f -> f.isFile }!!.associate { it.name to it.readBytes() }
            assertTrue(files.keys.containsAll(listOf("Bindings.xml", "TouchForeverUI.lua", "TouchForever.lua", "TouchForever.toc")))
            assertEquals(files.size, BridgeInstaller.install(root, files))
            val target = File(root, "_forever_/Interface/AddOns/TouchForever")
            assertTrue(File(target, "Bindings.xml").isFile)
            assertEquals(0, BridgeInstaller.install(root, files))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `line endings of the addon files are LF`() {
        for (file in addonDir.listFiles { f -> f.isFile }!!) {
            assertTrue(file.name, !file.readText(Charsets.UTF_8).contains('\r'))
        }
    }

    @Test
    fun `interface template has the seven commands in a ring`() {
        val keys = WowActionResolver.resolveAll(null).mapValues { it.value.combo }
        val menu = WowActionCatalog.buildMenu("b", "Oberfläche", WowActionCatalog.INTERFACE_COMMANDS, keys)
        assertEquals(7, menu.slots.size)
        assertEquals(
            listOf(
                "Aktionsleisten aus/ein", "Aktionsleisten kurz zeigen", "Chat aus/ein", "Minikarte aus/ein",
                "Questverfolgung aus/ein", "Alles kurz zeigen", "Addon-Einstellungen",
            ),
            menu.slots.map { it.label },
        )
        assertTrue(menu.slots.all { it.chatCommand == null && !it.bindingCombo.isEmpty && it.isEnabled })
        assertEquals(WowKeys.toCombo("CTRL-SHIFT-F5")!!.bindings, menu.slots[0].bindingCombo.bindings)
        assertEquals("WFB_TOGGLEBARS", menu.slots[0].wowAction)
        assertTrue(menu.slots.size in RadialMenu.MIN_SLOTS..RadialMenu.MAX_SLOTS)
    }

    @Test
    fun `chat template opens the channels and ends with the keyboard field`() {
        val keys = WowActionResolver.resolveAll(null).mapValues { it.value.combo }
        val menu = WowActionCatalog.buildChatMenu("b", "Chat", keys)
        assertEquals(8, menu.slots.size)
        assertEquals(
            listOf("Sagen", "Gruppe", "Gilde", "Flüstern", "Antworten", "Chat öffnen", "Chat aus/ein", "Tastatur"),
            menu.slots.map { it.label },
        )
        assertEquals(listOf("/tf say", "/tf party", "/tf guild", "/tf whisper"), menu.slots.take(4).map { it.chatCommand })
        assertTrue(menu.slots.take(4).all { it.bindingCombo.isEmpty })
        assertEquals(BindingCombo.of(Binding.KEY_R), menu.slots[4].bindingCombo)
        assertEquals(BindingCombo.of(Binding.KEY_ENTER), menu.slots[5].bindingCombo)
        assertEquals("WFB_TOGGLECHAT", menu.slots[6].wowAction)
        assertEquals(BindingCombo.of(Binding.SHOW_KEYBOARD), menu.slots[7].bindingCombo)
        assertNull(menu.slots[7].wowAction)
        assertEquals(8, menu.enabledSlots.size)
    }

    @Test
    fun `both templates survive the profile json`() {
        val keys = WowActionResolver.resolveAll(null).mapValues { it.value.combo }
        for (menu in listOf(
            WowActionCatalog.buildMenu("a", "Oberfläche", WowActionCatalog.INTERFACE_COMMANDS, keys),
            WowActionCatalog.buildChatMenu("b", "Chat", keys),
        )) {
            val restored = RadialMenu.fromJSONObject(menu.toJSONObject())
            assertEquals(menu.slots.map { it.label }, restored.slots.map { it.label })
            assertEquals(menu.slots.map { it.chatCommand }, restored.slots.map { it.chatCommand })
            assertEquals(menu.slots.map { it.wowAction }, restored.slots.map { it.wowAction })
            assertEquals(menu.slots.map { it.bindingCombo.bindings }, restored.slots.map { it.bindingCombo.bindings })
        }
    }

    @Test
    fun `chat openers can be found by name and command`() {
        assertTrue(WowActionCatalog.search("flüstern").any { it.command == "WFBCHAT_WHISPER" })
        assertTrue(WowActionCatalog.search("/tf say").any { it.command == "WFBCHAT_SAY" })
        assertTrue(WowActionCatalog.search("aktionsleisten").any { it.command == "WFB_TOGGLEBARS" })
        assertEquals("Sagen", WowActionCatalog.displayLabel("", "WFBCHAT_SAY", BindingCombo.none()))
    }

    @Test
    fun `addon version is 1_6_0 and the phone preset is ten point eight lines`() {
        val toc = File(addonDir, "TouchForever.toc").readText(Charsets.UTF_8)
        assertTrue(toc.contains("## Version: 1.6.0"))
        assertTrue(uiLua.contains("local CHAT_PRESET = { size = 10, lines = 8,"))
        // the preset of 1.1.0 is migrated once; own values stay
        assertTrue(uiLua.contains("local OLD_CHAT_PRESET = { size = 12, lines = 5 }"))
        assertTrue(uiLua.contains("o.size == OLD_CHAT_PRESET.size and o.lines == OLD_CHAT_PRESET.lines"))
        assertTrue(uiLua.contains("db.uiVersion = 2"))
        assertTrue(uiLua.contains("local CHAT_SIZE_MIN, CHAT_SIZE_MAX = 6, 24"))
    }

    @Test
    fun `quest tracker size has its commands and is applied after edit mode and world entry`() {
        assertTrue(uiLua.contains("local QUEST_SCALE_DEFAULT, QUEST_SCALE_MIN = 70, 50"))
        for (word in listOf("size", "reset", "height")) assertTrue(word, uiLua.contains("word == \"$word\""))
        assertTrue(uiLua.contains("\"EditMode.Exit\""))
        assertTrue(uiLua.contains("hooksecurefunc, frame, \"SetScale\""))
        assertTrue(uiLua.contains("QUEST_FRAME_NAMES = { \"ObjectiveTrackerFrame\", \"QuestWatchFrame\", \"WatchFrame\" }"))
        // not in combat: caught up after it
        assertTrue(uiLua.contains("if inCombat() then pendingCombat = true; return end"))
        assertTrue(uiLua.contains("/tf quests [show|hide|peek|size 50-100|height N|reset]"))
    }

    @Test
    fun `options page and window cover everything the commands do`() {
        // every value option of the window / settings page and the matching setter of the interface module
        val ids = Regex("\\{ id = \"(\\w+)\", page = (\\d), kind = \"(\\w+)\"").findAll(optionsLua).map { Triple(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }.toList()
        assertEquals(43, ids.size) // fold8.34: + Touch-Forever-Layout an/neu (fold8.17: + Minikarte am Rand (fold8.16: Chat links, Andocken, Taschenleiste, Weltkarte)
        // a set, the order of the rows does not matter; "note" (fold8.24) is a hint line without a control: only the touch window
        // draws it, the Settings API page has no branch for it (nothing to set there) and OPT.change ignores it
        assertEquals(setOf("check", "slider", "choice", "action", "buttons", "note"), ids.map { it.third }.toSet())
        assertEquals(listOf("touchnote"), ids.filter { it.third == "note" }.map { it.first })
        for (id in listOf("bars", "minimap", "quests", "questsize", "questheight", "chatshown", "chatphone", "chatmode", "chatsize", "chatlines", "chatbg", "chatfilter", "chatshort", "chatstamps", "gear", "reloadicons", "defaultkeys", "resetall")) {
            assertTrue(id, ids.any { it.first == id })
        }
        // fold8.8: pages 1 surface, 2 minimap, 3 chat, 4 chat more, 5 icons and cooldowns, 6 touch and windows (7 = help, no rows);
        // 10 rows at most fit a page of the window
        val pageOf = mapOf("xpmode" to "1", "xpwidth" to "1", "menubar" to "1", "minimapshape" to "2", "zoombig" to "2", "chatcompact" to "4",
            "chatextra" to "4", "chattabs" to "4", "strip" to "5", "reloadicons" to "5", "windows" to "6")
        for ((id, page) in pageOf) assertEquals(id, page, ids.single { it.first == id }.second)
        for (page in 1..6) assertTrue("page $page", ids.count { it.second == page.toString() } in 1..10)
        for (setter in listOf("SetBarsHidden", "SetChatHidden", "SetMinimapHidden", "SetQuestsHidden", "SetQuestScale", "SetQuestHeight", "ChatPhone", "ChatSet", "SetDefaultKeys", "ResetAll", "SetXpMode", "SetXpWidth", "SetMenuHidden", "SetMinimapShape", "SetZoomBig")) {
            assertTrue(setter, uiLua.contains("function UI.$setter("))
            assertTrue(setter, optionsLua.contains("UI.$setter("))
        }
        // ranges equal the commands
        assertTrue(optionsLua.contains("min = 50, max = 100, step = 5"))
        assertTrue(optionsLua.contains("min = 6, max = 24, step = 1"))
        assertTrue(optionsLua.contains("min = 3, max = 20, step = 1"))
        assertTrue(optionsLua.contains("min = 30, max = 100, step = 5"))
        assertTrue(uiLua.contains("local CHAT_LINES_MIN, CHAT_LINES_MAX = 3, 20"))
        assertTrue(uiLua.contains("local XP_WIDTH_DEFAULT, XP_WIDTH_MIN, XP_WIDTH_MAX = 40, 30, 100"))
    }

    @Test
    fun `window is made for touch and opens in every way`() {
        assertTrue(optionsLua.contains("local ROW_H = 46"))
        assertTrue(optionsLua.contains("tinsert(UISpecialFrames, \"TouchForeverWindow\")"))
        assertTrue(optionsLua.contains("win:SetMovable(true)"))
        assertTrue(optionsLua.contains("newButton(win, ROW_H, ROW_H, \"X\""))
        assertTrue(optionsLua.contains("UI.Options = OPT.toggle"))
        // /tf without argument opens the window, the old reload-button switch moved to "/tf button"
        val main = File(addonDir, "TouchForever.lua").readText(Charsets.UTF_8)
        assertTrue(main.contains("ns.UI.Open()"))
        assertTrue(main.contains("message ~= \"button\""))
        assertTrue(main.contains("ns.reloadIcons = function()"))
        assertTrue(main.contains("ns.button = {"))
        // the gear is off by default (the option uses the locale keys of TouchForeverLocale.lua, not literal texts)
        assertTrue(Regex("""id = "gear", page = 6, kind = "check", label = L\.gear_button_on_the_screen_edge,\s*default = false,""").containsMatchIn(optionsLua))
    }

    @Test
    fun `settings page uses the settings api of the client and falls back`() {
        for (call in listOf(
            "Settings.RegisterVerticalLayoutCategory", "Settings.RegisterProxySetting", "Settings.RegisterAddOnSetting",
            "Settings.RegisterAddOnCategory", "Settings.CreateSlider", "Settings.CreateSliderOptions",
            "Settings.CreateControlTextContainer", "Settings.OpenToCategory", "InterfaceOptions_AddCategory",
        )) {
            assertTrue(call, optionsLua.contains(call))
        }
        assertTrue(optionsLua.contains("Settings.CreateCheckbox or Settings.CreateCheckBox"))
    }

    @Test
    fun `reload buttons are secure macro buttons and ReloadUI is not called by addon code`() {
        val main = File(addonDir, "TouchForever.lua").readText(Charsets.UTF_8)
        for (text in listOf(main, uiLua, optionsLua)) {
            val calls = text.lines().filter { !it.trimStart().startsWith("--") && it.contains("ReloadUI(") }
            assertTrue("ReloadUI( in addon code: $calls", calls.isEmpty())
        }
        assertTrue(main.contains("\"UIPanelButtonTemplate,SecureActionButtonTemplate\""))
        assertTrue(main.contains("btn:SetAttribute(\"type\", \"macro\")"))
        assertTrue(main.contains("btn:SetAttribute(\"macrotext\", \"/reload\")"))
        assertTrue(main.contains("RegisterForClicks(\"AnyUp\")"))
        // secure frames get their attributes outside of combat only
        assertTrue(main.contains("unarmed[btn] = true"))
        assertTrue(main.contains("for btn in pairs(unarmed) do armReload(btn) end"))
        // the touch window row is a secure button too
        assertTrue(optionsLua.contains("secure = \"reload\""))
        assertTrue(optionsLua.contains("\"SecureActionButtonTemplate\""))
        assertTrue(optionsLua.contains("ns.armReloadButton(button)"))
    }

    @Test
    fun `status bars, compact chat and menu bar use the frames of retail 12`() {
        for (name in listOf("MainStatusTrackingBarContainer", "SecondaryStatusTrackingBarContainer", "StatusTrackingBarManager")) {
            assertTrue(name, uiLua.contains("\"$name\""))
        }
        for (mode in listOf("standard", "compact", "minimap", "off")) assertTrue(mode, uiLua.contains("$mode = true"))
        assertTrue(uiLua.contains("\"MicroMenuContainer\", \"MicroButtonAndBagsBar\"")) // fold8.16: bag bar is its own option
        assertTrue(uiLua.contains("local bags = { frames = { \"BagsBar\" }")) // fold8.17: bag bar modes full / pack / off
        for (name in listOf("ChatFrameMenuButton", "ChatFrameChannelButton", "QuickJoinToastButton")) {
            assertTrue(name, uiLua.contains("\"$name\""))
        }
        assertTrue(uiLua.contains("FCF_FadeInChatFrame"))
        // not in combat, caught up afterwards
        assertTrue(uiLua.contains("if inCombat() then pendingCombat = true; return end"))
        // old saved data gets the defaults of 1.6.0
        for (line in listOf(
            "if o.compact == nil then o.compact = true end",
            "if db.xpMode == nil then db.xpMode = \"compact\" end",
            "if db.xpWidth == nil then db.xpWidth = XP_WIDTH_DEFAULT end",
            "if db.menuHidden == nil then db.menuHidden = false end",
        )) assertTrue(line, uiLua.contains(line))
    }

    @Test
    fun `square minimap uses the frames of retail 12 and only changes outside of combat`() {
        // Blizzard_Minimap 12.x: cluster with container, minimap, header strip and the small items; mask, ring and the global for rim buttons
        for (name in listOf("MinimapCluster", "Minimap", "MinimapCompassTexture", "GameTimeFrame", "TimeManagerClockButton")) {
            assertTrue(name, uiLua.contains("\"$name\""))
        }
        for (part in listOf("BorderTop", "ZoneTextButton", "Tracking", "IndicatorFrame", "InstanceDifficulty", "MinimapContainer")) {
            assertTrue(part, uiLua.contains("cluster.$part") || uiLua.contains("$part = true"))
        }
        assertTrue(uiLua.contains("local MINIMAP_SQUARE_MASK = \"Interface\\\\BUTTONS\\\\WHITE8X8\""))
        assertTrue(uiLua.contains("function GetMinimapShape()"))
        assertTrue(uiLua.contains("return \"SQUARE\""))
        assertTrue(uiLua.contains("\"SetHeaderUnderneath\""))
        assertTrue(uiLua.contains("\"MiniMapIndicatorFrame_UpdatePosition\""))
        // round is the preset, a saved value is not overwritten, the combat lock catches up on PLAYER_REGEN_ENABLED
        assertTrue(uiLua.contains("if db.minimapShape == nil then db.minimapShape = \"round\" end"))
        assertTrue(uiLua.contains("if inCombat() then pendingCombat = true; return end"))
        assertTrue(uiLua.contains("applyMinimapShape(); quests.apply()"))
        assertTrue(optionsLua.contains("id = \"minimapshape\", page = 2, kind = \"choice\""))
        assertTrue(uiLua.contains("/tf minimap [show|hide|peek|square|round|corner on|corner off|zoom big|zoom small|info]"))
    }

    @Test
    fun `new bindings open the functions the compact chat takes away`() {
        assertTrue(uiLua.contains("ToggleChannelFrame"))
        assertTrue(uiLua.contains("ChatFrameMenuButton"))
        assertTrue(uiLua.contains("HousingFramesUtil.ToggleHousingDashboard"))
        val channels = WowActionCatalog.find("WFB_CHANNELS")!!
        assertEquals("CTRL-SHIFT-F1", channels.defaultKey)
        assertEquals("Chat-Kanäle", channels.nameDe)
    }
}
