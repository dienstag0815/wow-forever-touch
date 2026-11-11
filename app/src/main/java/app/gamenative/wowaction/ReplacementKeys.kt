package app.gamenative.wowaction

import com.winlator.inputcontrols.BindingCombo

/**
 * Ersatztasten für Aktionen ohne Taste in WoW: die Aktionsleisten 2 bis 5 (`MULTIACTIONBAR1..4BUTTON1..12`) haben ab Werk keine Taste, die
 * Touch-Felder darauf wären tot. Das Addon (TouchForeverBindings.lua, Tabelle `B.KEYS`) legt diese Kombinationen beim Anmelden als
 * Override-Bindungen auf die Aktionen, aber nur wenn der Spieler dafür keine eigene Taste hat; die App schickt dieselbe Kombination
 * ([WowActionCatalog] nimmt sie als Standardtaste, [com.winlator.inputcontrols.WowActionBindings] als Rückfall). Die Taste des Spielers
 * aus bindings-cache.wtf geht in der App vor ([WowActionResolver]).
 *
 * Quelle der Tabelle ist diese Datei; ein Test (ReplacementKeysTest) liest die Lua-Tabelle und vergleicht sie Eintrag für Eintrag.
 * Regeln: keine WoW-Standardbelegung, kein ALT-F4, nichts von dem, was TouchForever schon belegt (CTRL-SHIFT-F1..F12, ALT-CTRL-F1..F12,
 * ALT-SHIFT-F1..F12 des Kontextmenüs: alle F-Tasten-Kombinationen sind vergeben, daher Ziffern- und Navigationstasten); kein ALT-CTRL mit
 * Ziffern (auf manchen Layouts AltGr), kein ALT-TAB/ALT-LEERTASTE/ALT-ENTER; höchstens drei Tasten je Kombination ([BindingCombo.MAX_BINDINGS]).
 */
object ReplacementKeys {
    const val PER_BAR = 12

    /** Aktionsleisten mit Ersatztasten: Leiste 2 (unten links) bis 5 (rechts 2), Befehle `MULTIACTIONBAR1..4BUTTONn`. */
    const val BARS = 4

    private val NUMBER_ROW = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "-", "=")
    private val NAVIGATION_ROW = listOf("INSERT", "DELETE", "HOME", "END", "PAGEUP", "PAGEDOWN", "UP", "DOWN", "LEFT", "RIGHT", "BACKSPACE", "`")

    /** Taste von Platz [place] (1..12) der Leiste [bar] (1..4 = MULTIACTIONBAR1..4) in WoW-Schreibweise. */
    private fun keyOf(bar: Int, place: Int): String = when (bar) {
        1 -> "ALT-SHIFT-${NUMBER_ROW[place - 1]}"
        2 -> "CTRL-SHIFT-${NUMBER_ROW[place - 1]}"
        3 -> "ALT-SHIFT-${NAVIGATION_ROW[place - 1]}"
        else -> "CTRL-SHIFT-${NAVIGATION_ROW[place - 1]}"
    }

    /** Befehl -> Taste (WoW-Schreibweise), in der Reihenfolge Leiste 2.., Platz 1..12. */
    val KEYS: Map<String, String> = buildMap {
        for (bar in 1..BARS) for (place in 1..PER_BAR) put("MULTIACTIONBAR${bar}BUTTON$place", keyOf(bar, place))
    }

    /** Ersatztaste von [command] in WoW-Schreibweise, null wenn die Aktion keine hat. */
    @JvmStatic
    fun keyFor(command: String?): String? = if (command == null) null else KEYS[command]

    /** Ersatztaste von [command] zum Senden, null wenn die Aktion keine hat. */
    @JvmStatic
    fun comboFor(command: String?): BindingCombo? = keyFor(command)?.let { WowKeys.toCombo(it) }
}
