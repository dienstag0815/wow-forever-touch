package app.gamenative.wowaction

import app.gamenative.ui.components.AppText
import app.gamenative.R
import com.winlator.inputcontrols.Binding
import com.winlator.inputcontrols.BindingCombo

/**
 * What a ring key, a wheel field or a button without own text shows on the screen. Pure Kotlin (no Android classes), one
 * function for the key ring, the radial wheel and the buttons, so every place names a key the same way.
 *
 * Order of the rules: own label, then the WoW action (emoji or vector symbol), then a chat command, then the short name
 * of the key (combination).
 */
object BindingCaption {
    /** Longest automatic text in code points (emojis count once); longer texts are cut. */
    const val MAX_LENGTH = 8

    /**
     * True: a WoW action shows its emoji (if it has one), false: its vector symbol. Set at the start of the game screen from
     * the preference; kept here so the drawing code does not read the preferences once per frame.
     */
    @JvmField
    @Volatile
    var emojiMode: Boolean = true

    /**
     * Result of [forSlot]: [text] is always filled (also for the symbol case, it is the name then); [symbol] is the name of
     * the vector symbol to draw instead of the text (null = draw [text]); [automatic] tells that it was not an own label.
     */
    data class Caption(val text: String, val symbol: String? = null, val automatic: Boolean = true) {
        fun isEmpty(): Boolean = text.isEmpty() && symbol == null
    }

    /**
     * Caption of a field.
     *
     * @param label         own label of the field (may be empty or the creation default "1" to "8")
     * @param wowAction     id of the WoW action stored in the field, or null
     * @param chatCommand   free chat text stored in the field, or null
     * @param combo         the key (combination) of the field
     * @param segmentNumber 1-based number of the ring segment (0 = not a ring segment); a label equal to it that does not
     *                      match the key counts as left over from the creation and is replaced by the automatic text
     * @param emoji         [emojiMode] by default
     */
    @JvmStatic
    @JvmOverloads
    fun forSlot(
        label: String?,
        wowAction: String?,
        chatCommand: String?,
        combo: BindingCombo?,
        segmentNumber: Int = 0,
        emoji: Boolean = emojiMode,
    ): Caption {
        if (isOwnLabel(label, wowAction, chatCommand, combo, segmentNumber)) {
            return Caption(StandardNames.slotLabel(label!!.trim(), wowAction), null, automatic = false)
        }
        val action = WowActionCatalog.find(wowAction)
        if (action != null) {
            val emojiText = if (emoji) action.emoji else null
            if (emojiText != null) return Caption(emojiText)
            return Caption(fit(action.wheelLabel), action.symbol)
        }
        if (!chatCommand.isNullOrBlank()) return Caption(forChatCommand(chatCommand))
        return Caption(forCombo(combo))
    }

    /** Text only (symbol case: the name of the action); for places that cannot draw a vector symbol. */
    @JvmStatic
    @JvmOverloads
    fun textForSlot(
        label: String?,
        wowAction: String?,
        chatCommand: String?,
        combo: BindingCombo?,
        segmentNumber: Int = 0,
        emoji: Boolean = emojiMode,
    ): String = forSlot(label, wowAction, chatCommand, combo, segmentNumber, emoji).text

    /**
     * True when [label] was typed by the player. Empty labels are not own. A label that only repeats the number of the
     * ring segment (the default of older rings) is not own either unless it really is the number key of that segment.
     */
    @JvmStatic
    fun isOwnLabel(
        label: String?,
        wowAction: String?,
        chatCommand: String?,
        combo: BindingCombo?,
        segmentNumber: Int = 0,
    ): Boolean {
        val trimmed = label?.trim().orEmpty()
        if (trimmed.isEmpty()) return false
        if (segmentNumber > 0 && trimmed == segmentNumber.toString()) {
            val numberKey = combo != null && combo.size() == 1 && combo.primaryBinding == numberKeyOf(segmentNumber)
            val somethingElse = !wowAction.isNullOrEmpty() || !chatCommand.isNullOrBlank()
            if (!numberKey || somethingElse) return false
        }
        return true
    }

    private fun numberKeyOf(number: Int): Binding? = when (number) {
        1 -> Binding.KEY_1
        2 -> Binding.KEY_2
        3 -> Binding.KEY_3
        4 -> Binding.KEY_4
        5 -> Binding.KEY_5
        6 -> Binding.KEY_6
        7 -> Binding.KEY_7
        8 -> Binding.KEY_8
        else -> null
    }

    /** "/dance" gives "dance", "/s Hallo" gives "s Hallo" (cut to [MAX_LENGTH]). */
    @JvmStatic
    fun forChatCommand(command: String?): String {
        val text = command?.trim().orEmpty().removePrefix("/")
        return fit(text)
    }

    /** Short name of a key combination: "⇧1", "Strg+C", sequences "1→2". Empty for no key. */
    @JvmStatic
    fun forCombo(combo: BindingCombo?): String {
        if (combo == null || combo.isEmpty) return ""
        val bindings = combo.bindings
        if (bindings.size == 1) return fit(forBinding(bindings[0]))
        val separator = if (combo.isSequence) "→" else "+"
        val builder = StringBuilder()
        for ((index, binding) in bindings.withIndex()) {
            val name = forBinding(binding)
            if (index > 0) {
                val previous = bindings[index - 1]
                // The shift sign sits directly in front of the key ("⇧1"), everything else is joined with the separator.
                val joinTight = !combo.isSequence && previous.isShift()
                if (!joinTight) builder.append(separator)
            }
            builder.append(name)
        }
        return fit(builder.toString())
    }

    private fun Binding.isShift() = this == Binding.KEY_SHIFT_L || this == Binding.KEY_SHIFT_R

    /** Short name of one key. */
    @JvmStatic
    fun forBinding(binding: Binding?): String {
        if (binding == null) return ""
        return when (binding) {
            Binding.NONE -> ""
            Binding.MOUSE_LEFT_BUTTON -> "LMB"
            Binding.MOUSE_RIGHT_BUTTON -> "RMB"
            Binding.MOUSE_MIDDLE_BUTTON -> "MMB"
            Binding.MOUSE_MOVE_LEFT -> "M←"
            Binding.MOUSE_MOVE_RIGHT -> "M→"
            Binding.MOUSE_MOVE_UP -> "M↑"
            Binding.MOUSE_MOVE_DOWN -> "M↓"
            Binding.MOUSE_SCROLL_UP -> "⇞"
            Binding.MOUSE_SCROLL_DOWN -> "⇟"
            Binding.KEY_UP -> "↑"
            Binding.KEY_DOWN -> "↓"
            Binding.KEY_LEFT -> "←"
            Binding.KEY_RIGHT -> "→"
            Binding.KEY_ENTER -> "↵"
            Binding.KEY_ESC -> "Esc"
            Binding.KEY_BKSP -> "Bksp"
            Binding.KEY_DEL -> AppText.get(R.string.game_key_del)
            Binding.KEY_TAB -> "Tab"
            Binding.KEY_SPACE -> "Space"
            Binding.KEY_CTRL_L, Binding.KEY_CTRL_R -> AppText.get(R.string.game_key_ctrl)
            Binding.KEY_SHIFT_L, Binding.KEY_SHIFT_R -> "⇧"
            Binding.KEY_ALT_L, Binding.KEY_ALT_R -> "Alt"
            Binding.KEY_HOME -> AppText.get(R.string.game_key_home)
            Binding.KEY_END -> AppText.get(R.string.game_key_end)
            Binding.KEY_INSERT -> AppText.get(R.string.game_key_ins)
            Binding.KEY_PRTSCN -> AppText.get(R.string.game_key_prtsc)
            Binding.KEY_PG_UP -> AppText.get(R.string.game_key_pgup)
            Binding.KEY_PG_DOWN -> AppText.get(R.string.game_key_pgdn)
            Binding.KEY_CAPS_LOCK -> "Caps"
            Binding.KEY_NUM_LOCK -> "Num"
            Binding.KEY_KP_DIVIDE -> "NP/"
            Binding.KEY_KP_MULTIPLY -> "NP*"
            Binding.KEY_KP_SUBTRACT -> "NP-"
            Binding.KEY_KP_ADD -> "NP+"
            Binding.KEY_KP_DEL -> "NP."
            Binding.OPEN_NAVIGATION_MENU -> AppText.get(R.string.game_key_menu)
            Binding.SHOW_KEYBOARD -> AppText.get(R.string.game_key_keyboard)
            Binding.ALT_ENTER -> "Alt↵"
            Binding.OPEN_RADIAL_MENU -> AppText.get(R.string.game_key_wheel)
            Binding.GYRO_MODIFIER -> "Gyro"
            Binding.VOICE_CHAT -> AppText.get(R.string.game_key_voice)
            Binding.OPEN_CONTROLS_EDITOR -> AppText.get(R.string.game_key_edit)
            else -> generic(binding)
        }
    }

    /** Letters, digits, F keys, NP digits, punctuation and gamepad names from the enum name. */
    private fun generic(binding: Binding): String {
        val name = binding.name
        if (name.startsWith("KEY_KP_")) return "NP" + name.removePrefix("KEY_KP_")
        if (name.startsWith("KEY_")) return binding.toString().removePrefix("KEY ").uppercase()
        if (name.startsWith("GAMEPAD_BUTTON_")) {
            return when (val rest = name.removePrefix("GAMEPAD_BUTTON_")) {
                "L1" -> "LB"
                "R1" -> "RB"
                "L2" -> "LT"
                "R2" -> "RT"
                "SELECT" -> "Sel"
                "START" -> "Start"
                else -> rest
            }
        }
        if (name.startsWith("GAMEPAD_DPAD_")) return "D" + arrowOf(name.removePrefix("GAMEPAD_DPAD_"))
        if (name.startsWith("GAMEPAD_LEFT_THUMB_")) return "LS" + arrowOf(name.removePrefix("GAMEPAD_LEFT_THUMB_"))
        if (name.startsWith("GAMEPAD_RIGHT_THUMB_")) return "RS" + arrowOf(name.removePrefix("GAMEPAD_RIGHT_THUMB_"))
        return binding.toString()
    }

    private fun arrowOf(direction: String) = when (direction) {
        "UP" -> "↑"
        "DOWN" -> "↓"
        "LEFT" -> "←"
        "RIGHT" -> "→"
        else -> direction
    }

    /** Emoji of a WoW action when the emoji mode is on and the action has one, else null (called from the drawing code). */
    @JvmStatic
    fun emojiForAction(wowAction: String?): String? = if (emojiMode) WowActionCatalog.emojiOf(wowAction) else null

    /** True if [text] holds a colour emoji (symbols and pictographs, dingbats, variation selector 16), not just arrows or letters. */
    @JvmStatic
    fun containsEmoji(text: String?): Boolean {
        if (text.isNullOrEmpty()) return false
        var index = 0
        while (index < text.length) {
            val cp = text.codePointAt(index)
            if (cp >= 0x1F000 || cp == 0xFE0F || cp in 0x2600..0x27BF || cp in 0x2B05..0x2B55) return true
            index += Character.charCount(cp)
        }
        return false
    }

    /** Cuts [text] to [max] code points without splitting an emoji (surrogate pair). */
    @JvmStatic
    @JvmOverloads
    fun fit(text: String, max: Int = MAX_LENGTH): String {
        if (text.codePointCount(0, text.length) <= max) return text
        val end = text.offsetByCodePoints(0, max)
        return text.substring(0, end)
    }

    /** Emojis offered by the picker next to the label field (about 40, game related). */
    val EMOJI_CHOICES: List<String> = listOf(
        "🗺️", "🎒", "📜", "🧍", "📖", "🌟", "⚒️", "👥", "🛡️", "🏆",
        "🐾", "⚙️", "🦘", "🏃", "🎯", "✋", "🐎", "🪑", "👋", "💃",
        "🎉", "😂", "🙇", "🙏", "⚔️", "🏹", "🔮", "🧪", "❤️", "💀",
        "🔥", "❄️", "⚡", "🍖", "💰", "🔑", "📦", "🎵", "🚪", "⭐",
        "🏠", "💬",
    )
}
