package app.gamenative.wowaction

import app.gamenative.ui.components.AppText
import app.gamenative.R
import com.winlator.inputcontrols.Binding
import com.winlator.inputcontrols.BindingCombo
import com.winlator.inputcontrols.RadialMenu

/** Group of the action list in the editor. */
enum class WowActionGroup(val titleDe: String, val titleEn: String) {
    MENUS("Menüs", "Menus"),
    MENUS_MORE("Weitere Menüs", "More menus"),
    ACTION_BAR("Aktionsleiste", "Action bar"),
    TARGET_COMBAT("Ziel / Kampf", "Target / Combat"),
    MOVEMENT("Bewegung", "Movement"),
    OTHER("Sonstiges", "Other"),
    EMOTES("Emotes", "Emotes"),
    INTERFACE("Oberfläche", "Interface"),
    ;

    /** Title in the current app language. */
    val title: String get() = if (AppText.isGerman()) titleDe else titleEn
}

/**
 * One action of WoW that has a key binding in WoW's key bindings window.
 *
 * @property command    binding command exactly as WoW writes it in bindings-cache.wtf (e.g. TOGGLEWORLDMAP); doubles as the
 *                      id stored in the profile ("wowAction")
 * @property nameDe     German name
 * @property nameEn     English name (as in WoW's key bindings window)
 * @property defaultKey WoW key notation of the standard key (e.g. "M", "SHIFT-TAB"), null = unbound by default
 * @property certain    false = standard key / command name written from memory and not checked against a WoW installation
 * @property symbol     name of the own vector symbol (see [WowSymbols])
 * @property shortName  short wheel/button label if different from [name] (e.g. "1" for action bar slot 1)
 * @property chatCommand chat text typed into the WoW chat instead of a key (emotes, e.g. "/dance"); null = key action
 */
data class WowAction(
    val command: String,
    val nameDe: String,
    val nameEn: String,
    val group: WowActionGroup,
    val defaultKey: String?,
    val certain: Boolean,
    val symbol: String,
    val shortName: String? = null,
    val chatCommand: String? = null,
) {
    val id: String get() = command
    /** Name in the current app language: German in a German app, else WoW's English name. */
    val name: String get() = if (AppText.isGerman()) nameDe else nameEn
    val wheelLabel: String get() = shortName ?: name

    /** Standard emoji of the action (system font, no own graphics), null for actions without a fitting one (action bar slots). */
    val emoji: String? get() = WowActionCatalog.emojiOf(command)
}

/** The catalog of WoW actions the editor offers. Pure Kotlin, no Android classes besides the profile model. */
object WowActionCatalog {
    private val NUMBER_ROW = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "-", "=")

    val all: List<WowAction> = buildList<WowAction> {
        fun menu(command: String, de: String, en: String, key: String?, certain: Boolean, symbol: String) =
            add(WowAction(command, de, en, WowActionGroup.MENUS, key, certain, symbol))

        menu("TOGGLEWORLDMAP", "Karte", "World Map", "M", true, "map")
        menu("TOGGLETALENTS", "Talente", "Talents", "N", true, "talents")
        menu("TOGGLEPROFESSIONBOOK", "Berufe", "Professions", "K", false, "hammer")
        menu("TOGGLESPELLBOOK", "Zauberbuch", "Spellbook", "P", true, "book")
        menu("OPENALLBAGS", "Taschen", "Open All Bags", "B", true, "bag")
        menu("TOGGLEQUESTLOG", "Quests", "Quest Log", "L", true, "quest")
        menu("TOGGLECHARACTER0", "Charakter", "Character Info", "C", true, "person")
        menu("TOGGLEGROUPFINDER", "Gruppensuche", "Group Finder", "I", false, "group")
        menu("TOGGLECOLLECTIONS", "Sammlungen", "Collections", "SHIFT-P", false, "gem")
        menu("TOGGLEACHIEVEMENT", "Erfolge", "Achievements", "Y", false, "star")
        menu("TOGGLEGUILDTAB", "Gilde", "Guild", "J", false, "banner")
        menu("TOGGLESOCIAL", "Soziales", "Social", "O", false, "group")
        menu("TOGGLEENCOUNTERJOURNAL", "Abenteuerführer", "Adventure Guide", "SHIFT-J", false, "journal")
        menu("TOGGLEGAMEMENU", "Spielmenü", "Game Menu", "ESCAPE", true, "gear")

        // Windows that WoW's menu bar (12.x) does not have. Standard WoW commands (Bindings_Standard.xml); WoW gives them no key by
        // default (or none that is certain), so the addon TouchForever sets the keys ALT-CTRL-F1 to F3 and ALT-CTRL-HOME once at login (UI.EXTRA_KEYS; never an F4 key: Alt+F4 closes the window).
        fun menuMore(command: String, de: String, en: String, key: String, symbol: String) =
            add(WowAction(command, de, en, WowActionGroup.MENUS_MORE, key, true, symbol))

        menuMore("TOGGLECHARACTER2", "Ruf", "Reputation", "ALT-CTRL-F1", "banner")
        menuMore("TOGGLECURRENCY", "Währungen", "Currency", "ALT-CTRL-F2", "gem")
        menuMore("TOGGLECHARACTER4", "PvP", "PvP", "ALT-CTRL-F3", "sword")
        menuMore("TOGGLERAIDTAB", "Raid", "Raid", "ALT-CTRL-HOME", "group")

        for (index in 0 until 12) {
            val number = index + 1
            add(
                WowAction(
                    "ACTIONBUTTON$number", "Aktionsleiste 1, Platz $number", "Action Button $number",
                    WowActionGroup.ACTION_BAR, NUMBER_ROW[index], true, "slot", shortName = number.toString(),
                ),
            )
        }
        for (index in 0 until 12) {
            val number = index + 1
            add(
                WowAction(
                    "MULTIACTIONBAR1BUTTON$number", "Aktionsleiste 2 (unten links), Platz $number",
                    "Bottom Left Bar $number", WowActionGroup.ACTION_BAR, ReplacementKeys.keyFor("MULTIACTIONBAR1BUTTON$number"), false, "slot",
                    shortName = "2.$number",
                ),
            )
        }
        for (index in 0 until 12) {
            val number = index + 1
            add(
                WowAction(
                    "MULTIACTIONBAR2BUTTON$number", "Aktionsleiste 3 (unten rechts), Platz $number",
                    "Bottom Right Bar $number", WowActionGroup.ACTION_BAR, ReplacementKeys.keyFor("MULTIACTIONBAR2BUTTON$number"), false, "slot",
                    shortName = "3.$number",
                ),
            )
        }
        // Bars 4 to 8 (retail numbering of the key bindings window): MULTIACTIONBAR3 = right, MULTIACTIONBAR4 = right 2,
        // MULTIACTIONBAR5 to 7 = bars 6 to 8. No standard keys in WoW; bars 2 to 5 get the replacement keys of [ReplacementKeys] (the addon
        // sets them while the player has no key of their own), bars 6 to 8 get their keys from WoW once the player binds them there.
        val extraBars = listOf(4 to "4 (rechts)", 5 to "5 (rechts 2)", 6 to "6", 7 to "7", 8 to "8")
        for ((bar, nameDe) in extraBars) {
            for (index in 0 until 12) {
                val number = index + 1
                add(
                    WowAction(
                        "MULTIACTIONBAR${bar - 1}BUTTON$number", "Aktionsleiste $nameDe, Platz $number",
                        "Action Bar $bar Button $number", WowActionGroup.ACTION_BAR,
                        ReplacementKeys.keyFor("MULTIACTIONBAR${bar - 1}BUTTON$number"), false, "slot",
                        shortName = "$bar.$number",
                    ),
                )
            }
        }

        fun target(command: String, de: String, en: String, key: String?, certain: Boolean, symbol: String) =
            add(WowAction(command, de, en, WowActionGroup.TARGET_COMBAT, key, certain, symbol))

        target("INTERACTTARGET", "Mit Ziel interagieren", "Interact with Target", null, false, "cursor")
        target("TARGETNEARESTENEMY", "Nächster Gegner", "Target Nearest Enemy", "TAB", true, "target")
        target("TARGETPREVIOUSENEMY", "Vorheriger Gegner", "Target Previous Enemy", "SHIFT-TAB", true, "target")
        target("TARGETSELF", "Selbst anvisieren", "Target Self", "F1", true, "person")
        for (index in 1..4) {
            target("TARGETPARTYMEMBER$index", "Gruppenmitglied $index", "Target Party Member $index", "F${index + 1}", true, "group")
        }
        target("ASSISTTARGET", "Ziel assistieren", "Assist Target", "F", false, "target")
        target("TARGETLASTTARGET", "Letztes Ziel", "Target Last Target", "G", false, "target")

        fun move(command: String, de: String, en: String, key: String?, certain: Boolean, symbol: String) =
            add(WowAction(command, de, en, WowActionGroup.MOVEMENT, key, certain, symbol))

        move("MOVEFORWARD", "Vorwärts", "Move Forward", "W", true, "arrow_up")
        move("MOVEBACKWARD", "Rückwärts", "Move Backward", "S", true, "arrow_down")
        move("STRAFELEFT", "Links seitwärts", "Strafe Left", "Q", true, "arrow_left")
        move("STRAFERIGHT", "Rechts seitwärts", "Strafe Right", "E", true, "arrow_right")
        move("TURNLEFT", "Links drehen", "Turn Left", "A", true, "arrow_left")
        move("TURNRIGHT", "Rechts drehen", "Turn Right", "D", true, "arrow_right")
        move("JUMP", "Springen", "Jump", "SPACE", true, "arrow_up")
        move("TOGGLEAUTORUN", "Automatisch laufen", "Toggle Autorun", "NUMLOCK", true, "run")
        move("SITORSTAND", "Sitzen / Stehen", "Sit/Stand", "X", true, "sit")
        move("TOGGLESHEATH", "Waffe ziehen / wegstecken", "Sheath/Unsheath Weapons", "Z", true, "sword")

        fun other(command: String, de: String, en: String, key: String?, certain: Boolean, symbol: String) =
            add(WowAction(command, de, en, WowActionGroup.OTHER, key, certain, symbol))

        other("OPENCHAT", "Chat öffnen", "Open Chat", "ENTER", true, "chat")
        other("REPLY", "Antworten", "Reply", "R", true, "chat")
        other("SCREENSHOT", "Bildschirmfoto", "Screenshot", "PRINTSCREEN", true, "camera")
        other("TOGGLEUI", "Oberfläche ausblenden", "Toggle User Interface", "ALT-Z", true, "eye")

        // Emotes are chat commands, not keys. The English slash command works in every client language.
        fun emote(id: String, de: String, command: String, symbol: String) =
            add(
                WowAction(
                    "EMOTE_$id", de, id.lowercase().replaceFirstChar { it.uppercase() }, WowActionGroup.EMOTES,
                    null, true, symbol, chatCommand = command,
                ),
            )

        emote("WAVE", "Winken", "/wave", "emote_wave")
        emote("HELLO", "Hallo", "/hello", "chat")
        emote("BYE", "Tschüss", "/bye", "emote_bye")
        emote("DANCE", "Tanzen", "/dance", "emote_dance")
        emote("CHEER", "Jubeln", "/cheer", "emote_cheer")
        emote("LAUGH", "Lachen", "/laugh", "emote_laugh")
        emote("BOW", "Verbeugen", "/bow", "emote_bow")
        emote("THANK", "Danke", "/thank", "emote_heart")
        emote("APPLAUD", "Applaus", "/applaud", "emote_clap")
        emote("CRY", "Weinen", "/cry", "emote_cry")
        emote("ROAR", "Brüllen", "/roar", "emote_roar")
        emote("FLIRT", "Flirten", "/flirt", "emote_wink")
        emote("KISS", "Küssen", "/kiss", "emote_kiss")
        emote("POINT", "Zeigen", "/point", "emote_point")
        emote("SALUTE", "Salutieren", "/salute", "emote_salute")
        emote("SLEEP", "Schlafen", "/sleep", "emote_sleep")
        emote("SIT", "Sitzen", "/sit", "sit")
        emote("NOD", "Ja", "/nod", "emote_yes")
        emote("NO", "Nein", "/no", "emote_no")
        emote("ROLL", "Würfeln", "/roll", "emote_dice")

        // Own commands of the addon TouchForever (Bindings.xml, category "TouchForever"); the addon sets the standard keys.
        fun ui(command: String, de: String, en: String, key: String, symbol: String) =
            add(WowAction(command, de, en, WowActionGroup.INTERFACE, key, true, symbol))
        ui("WFB_TOGGLEBARS", "Aktionsleisten aus/ein", "Action bars on/off", "CTRL-SHIFT-F5", "slot")
        ui("WFB_PEEKBARS", "Aktionsleisten kurz zeigen", "Peek at the action bars", "CTRL-SHIFT-F6", "eye")
        ui("WFB_TOGGLECHAT", "Chat aus/ein", "Chat on/off", "CTRL-SHIFT-F7", "chat")
        ui("WFB_TOGGLEMINIMAP", "Minikarte aus/ein", "Minimap on/off", "CTRL-SHIFT-F8", "map")
        ui("WFB_TOGGLEQUESTS", "Questverfolgung aus/ein", "Quest tracker on/off", "CTRL-SHIFT-F9", "quest")
        ui("WFB_PEEKALL", "Alles kurz zeigen", "Peek at everything", "CTRL-SHIFT-F10", "eye")
        ui("WFB_OPTIONS", "Addon-Einstellungen", "Addon settings", "CTRL-SHIFT-F11", "gear")
        // Addon 1.6.0: the buttons beside the chat and the menu bar can be taken away; their functions stay reachable by key.
        ui("WFB_TOGGLEMENUBAR", "Menüleiste und Taschenleiste aus/ein", "Menu bar and bag bar on/off", "CTRL-SHIFT-F12", "eye")
        ui("WFB_CHANNELS", "Chat-Kanäle", "Chat channels", "CTRL-SHIFT-F1", "chat")
        ui("WFB_CHATMENU", "Chat-Menü", "Chat menu", "CTRL-SHIFT-F2", "gear")
        ui("WFB_HOUSING", "Wohnen", "Housing dashboard", "CTRL-SHIFT-F3", "banner")
        // Reload button: no binding name of Bindings.xml (hence no WFB_ prefix) but an override binding of the addon that clicks
        // its secure /reload button (TouchForever.lua, ns.RELOAD_KEY); the key is a real key press, so the macro may run.
        ui(ReloadGuard.ACTION_ID, "Oberfläche neu laden", "Reload interface", ReloadGuard.RELOAD_KEY, "reload")

        // Chat openers: the addon command "/tf say" opens the chat box with the channel already typed ("/s ").
        fun chatOpener(id: String, de: String, command: String) =
            add(
                WowAction(
                    "WFBCHAT_$id", de, id.lowercase().replaceFirstChar { it.uppercase() }, WowActionGroup.INTERFACE,
                    null, true, "chat", chatCommand = command,
                ),
            )
        chatOpener("SAY", "Sagen", "/tf say")
        chatOpener("PARTY", "Gruppe", "/tf party")
        chatOpener("GUILD", "Gilde", "/tf guild")
        chatOpener("WHISPER", "Flüstern", "/tf whisper")
    }

    private val byCommand: Map<String, WowAction> = all.associateBy { it.command }

    fun find(id: String?): WowAction? = if (id == null) null else byCommand[id]

    /**
     * Standard emoji per action id. The action bar slots have none (they show their number); target party members share
     * one emoji. The vector symbol stays available (setting "Emoji statt Symbol").
     */
    private val EMOJI: Map<String, String> = buildMap {
        put("TOGGLEWORLDMAP", "🗺️")
        put("TOGGLETALENTS", "🌟")
        put("TOGGLEPROFESSIONBOOK", "⚒️")
        put("TOGGLESPELLBOOK", "📖")
        put("OPENALLBAGS", "🎒")
        put("TOGGLEQUESTLOG", "📜")
        put("TOGGLECHARACTER0", "🧍")
        put("TOGGLEGROUPFINDER", "🔍")
        put("TOGGLECOLLECTIONS", "🐾")
        put("TOGGLEACHIEVEMENT", "🏆")
        put("TOGGLEGUILDTAB", "🛡️")
        put("TOGGLESOCIAL", "👥")
        put("TOGGLEENCOUNTERJOURNAL", "📕")
        put("TOGGLEGAMEMENU", "⚙️")
        put("TOGGLECHARACTER2", "🎖️")
        put("TOGGLECURRENCY", "🪙")
        put("TOGGLECHARACTER4", "🗡️")
        put("TOGGLERAIDTAB", "🐉")
        put("INTERACTTARGET", "✋")
        put("TARGETNEARESTENEMY", "🎯")
        put("TARGETPREVIOUSENEMY", "🔙")
        put("TARGETSELF", "🙋")
        for (index in 1..4) put("TARGETPARTYMEMBER$index", "👥")
        put("ASSISTTARGET", "🤝")
        put("TARGETLASTTARGET", "🔁")
        put("MOVEFORWARD", "⬆️")
        put("MOVEBACKWARD", "⬇️")
        put("STRAFELEFT", "⬅️")
        put("STRAFERIGHT", "➡️")
        put("TURNLEFT", "↩️")
        put("TURNRIGHT", "↪️")
        put("JUMP", "🦘")
        put("TOGGLEAUTORUN", "🏃")
        put("SITORSTAND", "🪑")
        put("TOGGLESHEATH", "⚔️")
        put("OPENCHAT", "💬")
        put("REPLY", "✉️")
        put("SCREENSHOT", "📷")
        put("TOGGLEUI", "👁️")
        put("EMOTE_WAVE", "👋")
        put("EMOTE_HELLO", "🙂")
        put("EMOTE_BYE", "🚶")
        put("EMOTE_DANCE", "💃")
        put("EMOTE_CHEER", "🎉")
        put("EMOTE_LAUGH", "😂")
        put("EMOTE_BOW", "🙇")
        put("EMOTE_THANK", "🙏")
        put("EMOTE_APPLAUD", "👏")
        put("EMOTE_CRY", "😢")
        put("EMOTE_ROAR", "🦁")
        put("EMOTE_FLIRT", "😉")
        put("EMOTE_KISS", "😘")
        put("EMOTE_POINT", "👉")
        put("EMOTE_SALUTE", "🫡")
        put("EMOTE_SLEEP", "😴")
        put("EMOTE_SIT", "🪑")
        put("EMOTE_NOD", "👍")
        put("EMOTE_NO", "🙅")
        put("EMOTE_ROLL", "🎲")
        put("WFB_TOGGLEBARS", "🎛️")
        put("WFB_PEEKBARS", "👀")
        put("WFB_TOGGLECHAT", "🗨️")
        put("WFB_TOGGLEMINIMAP", "🧭")
        put("WFB_TOGGLEQUESTS", "❗")
        put("WFB_PEEKALL", "🔆")
        put("WFB_OPTIONS", "⚙️")
        put("WFB_TOGGLEMENUBAR", "🧰")
        put("WFB_CHANNELS", "📡")
        put("WFB_CHATMENU", "💬")
        put("WFB_HOUSING", "🏠")
        put(ReloadGuard.ACTION_ID, "🔄")
        put("WFBCHAT_SAY", "🗣️")
        put("WFBCHAT_PARTY", "👪")
        put("WFBCHAT_GUILD", "🏰")
        put("WFBCHAT_WHISPER", "🤫")
    }

    /** Standard emoji for the action id, or null. */
    @JvmStatic
    fun emojiOf(id: String?): String? = if (id == null) null else EMOJI[id]

    /** Own symbol for the action, or null if [id] is not in the catalog (also used from Java). */
    fun symbolOf(id: String?): String? = find(id)?.symbol

    fun byGroup(): Map<WowActionGroup, List<WowAction>> = all.groupBy { it.group }

    /** Case-insensitive search in German and English name and in the command. A blank query matches everything. */
    fun search(query: String): List<WowAction> {
        val q = query.trim()
        if (q.isEmpty()) return all
        return all.filter {
            it.nameDe.contains(q, ignoreCase = true) ||
                it.nameEn.contains(q, ignoreCase = true) ||
                it.command.contains(q, ignoreCase = true) ||
                (it.chatCommand?.contains(q, ignoreCase = true) ?: false)
        }
    }

    /** Label of a wheel field / button: own text if set, else the action name, else the key. */
    fun displayLabel(ownLabel: String?, actionId: String?, combo: BindingCombo?): String {
        if (!ownLabel.isNullOrBlank()) return ownLabel
        find(actionId)?.let { return it.wheelLabel }
        return if (combo == null || combo.isEmpty) "" else combo.toString()
    }

    /** Menu "Spielmenüs": map, talents, professions, spellbook, bags, quests, character, group finder. */
    val GAME_MENU_COMMANDS: List<String> = listOf(
        "TOGGLEWORLDMAP", "TOGGLETALENTS", "TOGGLEPROFESSIONBOOK", "TOGGLESPELLBOOK",
        "OPENALLBAGS", "TOGGLEQUESTLOG", "TOGGLECHARACTER0", "TOGGLEGROUPFINDER",
    )

    /**
     * Menu "WoW-Menü komplett": the buttons of WoW's menu bar in one radial menu (12 fields = [RadialMenu.MAX_SLOTS]): character,
     * spellbook, talents, professions, quests, achievements, guild, group finder, collections, adventure guide, bags, game menu.
     * Every field is a "MENUS" action of the catalog (real WoW icons); the first 8 are the template for rings with 8 keys.
     */
    val WOW_MENU_COMMANDS: List<String> = listOf(
        "TOGGLECHARACTER0", "TOGGLESPELLBOOK", "TOGGLETALENTS", "TOGGLEPROFESSIONBOOK", "TOGGLEQUESTLOG", "TOGGLEACHIEVEMENT",
        "TOGGLEGUILDTAB", "TOGGLEGROUPFINDER", "TOGGLECOLLECTIONS", "TOGGLEENCOUNTERJOURNAL", "OPENALLBAGS", "TOGGLEGAMEMENU",
    )

    /**
     * Menu "WoW-Menü komplett 2" (second level, what has no room in the first one): social, channels, chat menu (the buttons
     * beside the chat that the compact chat of the addon takes away), map, housing.
     */
    val WOW_MENU_2_COMMANDS: List<String> = listOf(
        "TOGGLESOCIAL", "WFB_CHANNELS", "WFB_CHATMENU", "TOGGLEWORLDMAP", "WFB_HOUSING",
    )

    /**
     * The buttons of the micro menu (menu bar) of retail 12.x as actions of the catalog, in the order of the bar. Source: the game's
     * own interface code, Blizzard_MicroMenu (MainMenuBarMicroButtons.lua / .xml and MicroMenuContainerOverrides.lua, read 08.11.2026):
     * CharacterMicroButton (TOGGLECHARACTER0), ProfessionMicroButton (TOGGLEPROFESSIONBOOK), PlayerSpellsMicroButton (spellbook and
     * talents in one window: TOGGLESPELLBOOK, TOGGLETALENTS), AchievementMicroButton (TOGGLEACHIEVEMENT), QuestLogMicroButton
     * (TOGGLEQUESTLOG), HousingMicroButton (TOGGLEHOUSINGDASHBOARD, here the addon command WFB_HOUSING), GuildMicroButton
     * (TOGGLEGUILDTAB), LFDMicroButton (TOGGLEGROUPFINDER), CollectionsMicroButton (TOGGLECOLLECTIONS), EJMicroButton
     * (TOGGLEENCOUNTERJOURNAL), StoreMicroButton (the shop: no key binding, no action here) and MainMenuMicroButton (TOGGLEGAMEMENU).
     * HelpMicroButton exists but is hidden. The bag bar next to it (BagsBar) is not part of the micro menu.
     */
    val MICRO_MENU_12_COMMANDS: List<String> = listOf(
        "TOGGLECHARACTER0", "TOGGLEPROFESSIONBOOK", "TOGGLESPELLBOOK", "TOGGLETALENTS", "TOGGLEACHIEVEMENT", "TOGGLEQUESTLOG",
        "WFB_HOUSING", "TOGGLEGUILDTAB", "TOGGLEGROUPFINDER", "TOGGLECOLLECTIONS", "TOGGLEENCOUNTERJOURNAL", "TOGGLEGAMEMENU",
    )

    /**
     * Menu "WoW-Menü Rest": what the micro menu of 12.x does NOT have ([MICRO_MENU_12_COMMANDS]), 10 fields (the first 8 are the
     * template for rings with 8 keys): map, bags, social, reputation, currencies, PvP, raid, chat channels, chat menu, menu bar on/off.
     */
    val WOW_MENU_REST_COMMANDS: List<String> = listOf(
        "TOGGLEWORLDMAP", "OPENALLBAGS", "TOGGLESOCIAL", "TOGGLECHARACTER2", "TOGGLECURRENCY", "TOGGLECHARACTER4", "TOGGLERAIDTAB",
        "WFB_CHANNELS", "WFB_CHATMENU", "WFB_TOGGLEMENUBAR",
    )

    /** Menu "WoW Emotes": wave, dance, cheer, laugh, bow, thank, hello, sit (chat commands). */
    val EMOTE_COMMANDS: List<String> = listOf(
        "EMOTE_WAVE", "EMOTE_DANCE", "EMOTE_CHEER", "EMOTE_LAUGH", "EMOTE_BOW", "EMOTE_THANK", "EMOTE_HELLO", "EMOTE_SIT",
    )

    /** Symbol of a field/button that types a free chat command (no catalog entry). */
    const val CUSTOM_CHAT_SYMBOL = "chat"

    /** Menu "Angriffe": action bar 1, slots 1 to 8. */
    val ATTACK_COMMANDS: List<String> = (1..8).map { "ACTIONBUTTON$it" }

    /**
     * Radial menu with one field per command: the field stores the action id and the key currently resolved for it
     * ([keys] maps the action id to its combination, a missing entry leaves the field without a key until WoW provides one).
     */
    fun buildMenu(id: String, name: String, commands: List<String>, keys: Map<String, BindingCombo>): RadialMenu {
        val menu = RadialMenu()
        menu.id = id
        menu.name = name
        for (command in commands) {
            val action = find(command) ?: continue
            val chat = action.chatCommand
            if (chat != null) {
                // Chat command field: no key, the text is typed into the chat.
                menu.addSlot(RadialMenu.Slot(action.wheelLabel, BindingCombo.none(), command, chat))
            } else {
                menu.addSlot(RadialMenu.Slot(action.wheelLabel, keys[command] ?: BindingCombo.none(), command))
            }
        }
        return menu
    }

    /** Menu "Oberfläche": action bars on/off and peek, chat, minimap, quests on/off, peek at everything, addon settings (keys of the addon). */
    val INTERFACE_COMMANDS: List<String> = listOf(
        "WFB_TOGGLEBARS", "WFB_PEEKBARS", "WFB_TOGGLECHAT", "WFB_TOGGLEMINIMAP", "WFB_TOGGLEQUESTS", "WFB_PEEKALL",
        "WFB_OPTIONS",
    )

    /** Fields of the menu "Chat" before the on-screen keyboard field: channel openers, reply, open the chat box, chat on/off. */
    val CHAT_COMMANDS: List<String> = listOf(
        "WFBCHAT_SAY", "WFBCHAT_PARTY", "WFBCHAT_GUILD", "WFBCHAT_WHISPER", "REPLY", "OPENCHAT", "WFB_TOGGLECHAT",
    )

    /** Menu "Chat": [CHAT_COMMANDS] plus a last field that shows the on-screen keyboard (to type after the box has opened). */
    fun buildChatMenu(id: String, name: String, keys: Map<String, BindingCombo>): RadialMenu {
        val menu = buildMenu(id, name, CHAT_COMMANDS, keys)
        menu.addSlot(RadialMenu.Slot(AppText.get(R.string.game_keyboard), BindingCombo.of(Binding.SHOW_KEYBOARD)))
        return menu
    }
}
