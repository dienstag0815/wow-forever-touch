package app.gamenative.wowaction

import app.gamenative.ui.components.AppText
import app.gamenative.R
import com.winlator.inputcontrols.Binding
import com.winlator.inputcontrols.BindingCombo
import java.io.File

/** Translation of WoW key names (as written in bindings-cache.wtf, e.g. "SHIFT-TAB", "NUMPAD1", "BUTTON3") to [Binding]s. */
object WowKeys {
    private val SIMPLE: Map<String, Binding> = buildMap<String, Binding> {
        for (c in 'A'..'Z') put(c.toString(), Binding.valueOf("KEY_$c"))
        for (d in '0'..'9') put(d.toString(), Binding.valueOf("KEY_$d"))
        for (n in 1..12) put("F$n", Binding.valueOf("KEY_F$n"))
        for (n in 0..9) put("NUMPAD$n", Binding.valueOf("KEY_KP_$n"))
        put("NUMPADPLUS", Binding.KEY_KP_ADD)
        put("NUMPADMINUS", Binding.KEY_KP_SUBTRACT)
        put("NUMPADMULTIPLY", Binding.KEY_KP_MULTIPLY)
        put("NUMPADDIVIDE", Binding.KEY_KP_DIVIDE)
        put("NUMPADDECIMAL", Binding.KEY_KP_DEL)
        put("ESCAPE", Binding.KEY_ESC)
        put("ENTER", Binding.KEY_ENTER)
        put("TAB", Binding.KEY_TAB)
        put("SPACE", Binding.KEY_SPACE)
        put("BACKSPACE", Binding.KEY_BKSP)
        put("DELETE", Binding.KEY_DEL)
        put("INSERT", Binding.KEY_INSERT)
        put("HOME", Binding.KEY_HOME)
        put("END", Binding.KEY_END)
        put("PAGEUP", Binding.KEY_PG_UP)
        put("PAGEDOWN", Binding.KEY_PG_DOWN)
        put("UP", Binding.KEY_UP)
        put("DOWN", Binding.KEY_DOWN)
        put("LEFT", Binding.KEY_LEFT)
        put("RIGHT", Binding.KEY_RIGHT)
        put("CAPSLOCK", Binding.KEY_CAPS_LOCK)
        put("NUMLOCK", Binding.KEY_NUM_LOCK)
        put("PRINTSCREEN", Binding.KEY_PRTSCN)
        put("`", Binding.KEY_GRAVE)
        put("-", Binding.KEY_MINUS)
        put("=", Binding.KEY_EQUAL)
        put("[", Binding.KEY_BRACKET_LEFT)
        put("]", Binding.KEY_BRACKET_RIGHT)
        put("\\", Binding.KEY_BACKSLASH)
        put(";", Binding.KEY_SEMICOLON)
        put("'", Binding.KEY_APOSTROPHE)
        put(",", Binding.KEY_COMMA)
        put(".", Binding.KEY_PERIOD)
        put("/", Binding.KEY_SLASH)
        put("BUTTON1", Binding.MOUSE_LEFT_BUTTON)
        put("BUTTON2", Binding.MOUSE_RIGHT_BUTTON)
        put("BUTTON3", Binding.MOUSE_MIDDLE_BUTTON)
        put("MOUSEWHEELUP", Binding.MOUSE_SCROLL_UP)
        put("MOUSEWHEELDOWN", Binding.MOUSE_SCROLL_DOWN)
    }

    private val MODIFIERS: List<Pair<String, Binding>> = listOf(
        "ALT-" to Binding.KEY_ALT_L,
        "CTRL-" to Binding.KEY_CTRL_L,
        "SHIFT-" to Binding.KEY_SHIFT_L,
    )

    /**
     * Key combination for a WoW key such as "CTRL-SHIFT-5". Null if the key has no equivalent here (e.g. mouse button 4,
     * "LSHIFT" alone) or the combination would need more than [BindingCombo.MAX_BINDINGS] keys.
     */
    fun toCombo(wowKey: String): BindingCombo? {
        var rest = wowKey.trim().uppercase()
        if (rest.isEmpty()) return null
        val bindings = ArrayList<Binding>()
        var found = true
        while (found) {
            found = false
            for ((prefix, binding) in MODIFIERS) {
                if (rest.length > prefix.length && rest.startsWith(prefix)) {
                    if (binding !in bindings) bindings.add(binding)
                    rest = rest.substring(prefix.length)
                    found = true
                    break
                }
            }
        }
        val key = SIMPLE[rest] ?: return null
        bindings.add(key)
        if (bindings.size > BindingCombo.MAX_BINDINGS) return null
        return BindingCombo.fromBindings(bindings)
    }

    /** Number of modifier keys (ALT-, CTRL-, SHIFT-) in front of the key. */
    fun modifierCount(wowKey: String): Int {
        var rest = wowKey.trim().uppercase()
        var count = 0
        var found = true
        while (found) {
            found = false
            for ((prefix, _) in MODIFIERS) {
                if (rest.length > prefix.length && rest.startsWith(prefix)) {
                    rest = rest.substring(prefix.length)
                    count++
                    found = true
                    break
                }
            }
        }
        return count
    }
}

/**
 * The player's own key bindings from WoW's `bindings-cache.wtf`: lines `bind <KEY> <COMMAND>`; the key may start with the
 * modifiers `ALT-`, `CTRL-`, `SHIFT-`. A line `bind <KEY>` without command frees the key again. Unknown lines are ignored.
 */
class WowBindings private constructor(private val commandByKey: Map<String, String>) {
    /** WoW keys bound to [command], in the order of the file. */
    fun keysFor(command: String): List<String> =
        commandByKey.entries.filter { it.value == command }.map { it.key }

    val size: Int get() = commandByKey.size

    /** [overlay] wins: every command that has keys in [overlay] loses the keys it had in this file. */
    fun overlayedWith(overlay: WowBindings): WowBindings {
        val overlayCommands = overlay.commandByKey.values.toSet()
        val merged = LinkedHashMap<String, String>()
        for ((key, command) in commandByKey) {
            if (command !in overlayCommands && key !in overlay.commandByKey) merged[key] = command
        }
        merged.putAll(overlay.commandByKey)
        return WowBindings(merged)
    }

    companion object {
        val EMPTY = WowBindings(emptyMap())

        fun parse(text: String): WowBindings {
            val map = LinkedHashMap<String, String>()
            for (raw in text.removePrefix("﻿").lineSequence()) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("--") || line.startsWith("//") || line.startsWith("#")) continue
                val parts = line.split(Regex("\\s+"))
                if (!parts[0].equals("bind", ignoreCase = true) || parts.size < 2) continue
                val key = parts[1].uppercase()
                if (parts.size == 2) {
                    map.remove(key)
                } else {
                    // A key holds one command; binding it again replaces it (keeps the file order of the latest line).
                    map.remove(key)
                    map[key] = parts[2].uppercase()
                }
            }
            return WowBindings(map)
        }
    }
}

/** Finds and reads the binding files below the WoW folder. */
object WowBindingsLoader {
    const val FILE_NAME = "bindings-cache.wtf"
    private val FLAVOR_DIRS = listOf("_forever_", "_classic_beta_")

    /**
     * Reads the key bindings: account-wide file of the account used most recently, overlaid with the character-specific file
     * (`<account>/<realm>/<character>/bindings-cache.wtf`) if that is newer than the account file. Null if no file exists.
     */
    fun load(gameRoot: File): WowBindings? {
        for (flavor in FLAVOR_DIRS) {
            val accountRoot = File(gameRoot, "$flavor/WTF/Account")
            val accounts = accountRoot.listFiles { f -> f.isDirectory && !f.name.equals("SavedVariables", ignoreCase = true) }
                ?: continue
            // The account that has the most recently written binding file (several Battle.net accounts on one install).
            val best = accounts.mapNotNull { account -> candidateFiles(account).takeIf { it.isNotEmpty() }?.let { account to it } }
                .maxByOrNull { (_, files) -> files.maxOf { it.lastModified() } }
                ?: continue
            return readAccount(best.first)
        }
        return null
    }

    private fun candidateFiles(account: File): List<File> {
        val files = ArrayList<File>()
        File(account, FILE_NAME).takeIf { it.isFile }?.let(files::add)
        account.listFiles { f -> f.isDirectory && !f.name.equals("SavedVariables", ignoreCase = true) }?.forEach { realm ->
            realm.listFiles { f -> f.isDirectory }?.forEach { character ->
                File(character, FILE_NAME).takeIf { it.isFile }?.let(files::add)
            }
        }
        return files
    }

    private fun readAccount(account: File): WowBindings? {
        val accountFile = File(account, FILE_NAME).takeIf { it.isFile }
        val characterFile = candidateFiles(account).filter { it != accountFile }.maxByOrNull { it.lastModified() }
        var result: WowBindings? = accountFile?.let { read(it) }
        if (characterFile != null && (accountFile == null || characterFile.lastModified() >= accountFile.lastModified())) {
            val characterBindings = read(characterFile)
            if (characterBindings != null) result = result?.overlayedWith(characterBindings) ?: characterBindings
        }
        return result
    }

    private fun read(file: File): WowBindings? =
        try {
            WowBindings.parse(file.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            null
        }
}

/** Where a resolved key comes from. */
enum class WowKeySource { WOW_FILE, DEFAULT }

/** Key of an action: [wowKey] in WoW notation for display ("CTRL-M"), [combo] to send. */
data class ResolvedKey(val wowKey: String, val combo: BindingCombo, val source: WowKeySource)

object WowActionResolver {
    /**
     * Key of [action]: the player's key from [bindings] (the one with the fewest modifiers that can be sent), else the
     * standard key; null if neither exists. A command missing in the file counts as "standard key" (the cache is only
     * written once the player changed or saved bindings, and an unbound action cannot be told from an unchanged one).
     */
    fun resolve(action: WowAction, bindings: WowBindings?): ResolvedKey? {
        if (bindings != null) {
            val fromFile = bindings.keysFor(action.command)
                .mapNotNull { key -> WowKeys.toCombo(key)?.let { key to it } }
                .minByOrNull { (key, _) -> WowKeys.modifierCount(key) }
            if (fromFile != null) return ResolvedKey(fromFile.first, fromFile.second, WowKeySource.WOW_FILE)
        }
        val default = action.defaultKey ?: return null
        val combo = WowKeys.toCombo(default) ?: return null
        return ResolvedKey(default, combo, WowKeySource.DEFAULT)
    }

    fun resolveAll(bindings: WowBindings?): Map<String, ResolvedKey> {
        val result = LinkedHashMap<String, ResolvedKey>()
        for (action in WowActionCatalog.all) resolve(action, bindings)?.let { result[action.command] = it }
        return result
    }

    /** "M", "CTRL-M" or "nicht belegt" for display next to the action name. */
    fun keyText(resolved: ResolvedKey?): String = resolved?.wowKey ?: AppText.get(R.string.game_unbound)
}
