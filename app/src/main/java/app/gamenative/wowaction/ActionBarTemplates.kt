package app.gamenative.wowaction

import app.gamenative.ui.components.AppText
import app.gamenative.R
import androidx.annotation.StringRes
import app.gamenative.wowicons.ActionSlotMap
import app.gamenative.wowicons.BridgeData
import com.winlator.inputcontrols.BindingCombo
import com.winlator.inputcontrols.RadialMenu

/**
 * Templates "Aktionsleiste 1 bis 8" for the radial menu and for the key ring of a stick: the fields get the key binding
 * commands of one WoW action bar (`wowAction` + the key WoW has for it, so symbols and re-binding work as before).
 *
 * Numbering as in WoW's key bindings window (retail): bar 1 = `ACTIONBUTTONn`, bar 2 = `MULTIACTIONBAR1BUTTONn` (bottom left),
 * bar 3 = `MULTIACTIONBAR2BUTTONn` (bottom right), bar 4 = `MULTIACTIONBAR3BUTTONn` (right), bar 5 = `MULTIACTIONBAR4BUTTONn`
 * (right 2), bars 6 to 8 = `MULTIACTIONBAR5..7BUTTONn`. Which slot of the addon data a command is: see [ActionSlotMap].
 *
 * With addon data only occupied places are taken (empty places skipped, order kept); without data all places.
 */
object ActionBarTemplates {
    const val BAR_COUNT = 8
    const val PER_BAR = 12

    /** Command of place [place] (1..12) on bar [bar] (1..8). */
    fun commandFor(bar: Int, place: Int): String =
        if (bar == 1) "ACTIONBUTTON$place" else "MULTIACTIONBAR${bar - 1}BUTTON$place"

    /** The 12 commands of [bar], in place order. */
    fun commands(bar: Int): List<String> = (1..PER_BAR).map { commandFor(bar.coerceIn(1, BAR_COUNT), it) }

    /** Name of the menu a template creates. */
    fun menuName(bar: Int): String = AppText.get(R.string.game_tpl_bar, bar.coerceIn(1, BAR_COUNT))

    /** Short explanation of a bar for the chips' hint (names as in WoW's edit mode). */
    fun barTitle(bar: Int): String = when (bar) {
        1 -> AppText.get(R.string.game_tpl_bar_1)
        2 -> AppText.get(R.string.game_tpl_bar_2)
        3 -> AppText.get(R.string.game_tpl_bar_3)
        4 -> AppText.get(R.string.game_tpl_bar_4)
        5 -> AppText.get(R.string.game_tpl_bar_5)
        else -> AppText.get(R.string.game_tpl_bar, bar)
    }

    /** Places (1..12) of [bar] that hold something according to [data]; all places without data. */
    fun occupiedPlaces(bar: Int, data: BridgeData?): List<Int> {
        if (data == null) return (1..PER_BAR).toList()
        return (1..PER_BAR).filter { place ->
            val slot = ActionSlotMap.slotFor(commandFor(bar, place))
            slot != null && data.slots.containsKey(slot)
        }
    }

    /** How a menu template came out; [message] is shown to the player. */
    class Applied(
        val menu: RadialMenu,
        val places: List<Int>,
        /** True if only occupied places were taken. */
        val onlyOccupied: Boolean,
        /** Fields whose action has no key in WoW yet (they stay inactive until the player binds a key there). */
        val withoutKey: Int,
        val bar: Int,
        val hadData: Boolean,
    ) {
        val message: String
            get() {
                val name = menuName(bar)
                val head = when {
                    !hadData -> AppText.get(R.string.game_tpl_applied_nodata, name, places.size)
                    !onlyOccupied -> AppText.get(R.string.game_tpl_applied_empty, name, places.size)
                    else -> AppText.get(R.string.game_tpl_applied_some, name, places.size)
                }
                return if (withoutKey > 0) AppText.get(R.string.game_tpl_applied_nokey, head, withoutKey) else head
            }
    }

    /** One field for [command] (action id and the key WoW has for it); null for ids that are not in the catalog. */
    fun slotFor(command: String, keys: Map<String, BindingCombo>): RadialMenu.Slot? {
        if (WowActionCatalog.find(command) == null) return null
        // Empty label: the field names itself (spell name from the addon, else the place number).
        return RadialMenu.Slot("", keys[command] ?: BindingCombo.none(), command)
    }

    /**
     * Menu for [bar]: occupied places in order ([data] != null), but at least [RadialMenu.MIN_SLOTS] fields (filled up with the
     * first empty places); a bar without any occupied place, or no data at all, gives all 12 places.
     */
    fun buildMenu(id: String, bar: Int, keys: Map<String, BindingCombo>, data: BridgeData?): Applied {
        val number = bar.coerceIn(1, BAR_COUNT)
        val occupied = occupiedPlaces(number, data)
        val onlyOccupied = data != null && occupied.isNotEmpty()
        var places = if (onlyOccupied) occupied else (1..PER_BAR).toList()
        if (onlyOccupied && places.size < RadialMenu.MIN_SLOTS) {
            places = places + (1..PER_BAR).filter { it !in places }.take(RadialMenu.MIN_SLOTS - places.size)
        }
        val menu = RadialMenu()
        menu.id = id
        menu.name = menuName(number)
        var withoutKey = 0
        for (place in places) {
            val command = commandFor(number, place)
            val slot = slotFor(command, keys) ?: continue
            menu.addSlot(slot)
            if (keys[command]?.isEmpty != false) withoutKey++
        }
        return Applied(menu, places, onlyOccupied, withoutKey, number, data != null)
    }

    /**
     * The fields of a stick ring with [count] segments (4, 6 or 8) for [bar]: the first occupied places; if fewer are
     * occupied than the ring has segments, the next empty places follow so no segment stays unassigned.
     */
    fun ringSlots(bar: Int, count: Int, keys: Map<String, BindingCombo>, data: BridgeData?): List<RadialMenu.Slot> {
        val number = bar.coerceIn(1, BAR_COUNT)
        val occupied = if (data == null) emptyList() else occupiedPlaces(number, data)
        val order = if (data == null) (1..PER_BAR).toList() else occupied + (1..PER_BAR).filter { it !in occupied }
        return order.take(count.coerceIn(0, PER_BAR)).mapNotNull { slotFor(commandFor(number, it), keys) }
    }

    /** Menu "Spielmenüs 2": the game menu actions the first template has no room for (guild, social, achievements ...). */
    val GAME_MENU_2_COMMANDS: List<String> = listOf(
        "TOGGLEGUILDTAB", "TOGGLESOCIAL", "TOGGLEACHIEVEMENT", "TOGGLECOLLECTIONS", "TOGGLEENCOUNTERJOURNAL", "TOGGLEGAMEMENU",
    )

    /**
     * A ring template other than the action bars, as in the radial menu: [commands] in order (action ids of the catalog),
     * optionally followed by the field that shows the on-screen keyboard ("Chat").
     */
    class RingTemplate(
        @StringRes val nameRes: Int,
        val commands: List<String>,
        val withKeyboard: Boolean = false,
        @StringRes val hintRes: Int,
    ) {
        /** Name in the current app language. */
        val name: String get() = AppText.get(nameRes)

        /** The list of actions in the current app language. */
        val hint: String get() = AppText.get(hintRes)
    }

    /** The templates of the ring page besides the 8 action bars: the same as in the radial menu plus "Spielmenüs 2". */
    val RING_TEMPLATES: List<RingTemplate> = listOf(
        RingTemplate(R.string.game_tpl_name_game_menus, WowActionCatalog.GAME_MENU_COMMANDS, hintRes = R.string.game_tpl_hint_game_menus),
        RingTemplate(R.string.game_tpl_name_game_menus_2, GAME_MENU_2_COMMANDS, hintRes = R.string.game_tpl_hint_game_menus_2),
        RingTemplate(
            R.string.game_tpl_name_wow_full, WowActionCatalog.WOW_MENU_COMMANDS,
            hintRes = R.string.game_tpl_hint_wow_full,
        ),
        RingTemplate(R.string.game_tpl_name_wow_full_2, WowActionCatalog.WOW_MENU_2_COMMANDS, hintRes = R.string.game_tpl_hint_wow_full_2),
        RingTemplate(
            R.string.game_tpl_name_wow_rest, WowActionCatalog.WOW_MENU_REST_COMMANDS,
            hintRes = R.string.game_tpl_hint_wow_rest,
        ),
        RingTemplate(R.string.game_tpl_name_attacks, WowActionCatalog.ATTACK_COMMANDS, hintRes = R.string.game_tpl_hint_attacks),
        RingTemplate(R.string.game_tpl_name_interface, WowActionCatalog.INTERFACE_COMMANDS, hintRes = R.string.game_tpl_hint_interface),
        RingTemplate(R.string.game_tpl_name_chat, WowActionCatalog.CHAT_COMMANDS, withKeyboard = true, hintRes = R.string.game_tpl_hint_chat),
        RingTemplate(R.string.game_tpl_name_emotes, WowActionCatalog.EMOTE_COMMANDS, hintRes = R.string.game_tpl_hint_emotes),
    )

    /**
     * The fields of a ring with [count] segments for [template]: the first [count] entries in the order of the template.
     * The result always has exactly [count] entries; segments the template has no entry for (it is shorter than the ring)
     * stay empty. Fields name themselves (symbol and name of the action), the chat template's keyboard field keeps its label.
     */
    fun ringSlotsFor(template: RingTemplate, count: Int, keys: Map<String, BindingCombo>): List<RadialMenu.Slot> {
        val menu = if (template.withKeyboard) {
            WowActionCatalog.buildChatMenu("ring", template.name, keys)
        } else {
            WowActionCatalog.buildMenu("ring", template.name, template.commands, keys)
        }
        val wanted = count.coerceIn(0, RadialMenu.MAX_SLOTS)
        return (0 until wanted).map { index ->
            val slot = menu.slots.getOrNull(index) ?: return@map RadialMenu.Slot()
            // Empty label: the ring names the field itself (like the action bar templates).
            if (slot.wowAction != null) slot.label = ""
            slot
        }
    }

    /** Number of ring segments [template] cannot fill for a ring with [count] segments. */
    fun ringEmpty(template: RingTemplate, count: Int): Int {
        val size = template.commands.count { WowActionCatalog.find(it) != null } + if (template.withKeyboard) 1 else 0
        return (count - size).coerceAtLeast(0)
    }

    /**
     * True if [slots] hold something a template would destroy: a WoW action, a chat command, or an own label.
     * A fresh menu or ring (only number keys) does not count, so the first template applies without a question.
     */
    fun isCustomized(slots: List<RadialMenu.Slot>): Boolean =
        slots.any { it.wowAction != null || it.chatCommand != null || it.label.isNotBlank() }
}
