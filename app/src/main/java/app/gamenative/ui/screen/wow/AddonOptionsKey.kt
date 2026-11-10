package app.gamenative.ui.screen.wow

import app.gamenative.wowaction.WowActionCatalog
import app.gamenative.wowaction.WowKeys
import com.winlator.inputcontrols.BindingCombo
import com.winlator.inputcontrols.WowActionBindings
import com.winlator.xserver.XKeycode

/**
 * "Addon-Einstellungen öffnen" im Seitenmenü: schickt die Taste der Bindung WFB_OPTIONS des Addons (Tastenkombination mit Strg und
 * Umschalt) an das Spiel, ohne dass der Spieler etwas tippen muss. Dieselbe Aktion steht im Aktionskatalog (Touchknopf, Kreismenü).
 *
 * Die Taste kommt wie bei jeder WoW-Aktion aus den aufgelösten Tasten des Spielers ([WowActionBindings]); fehlt sie dort, gilt die
 * Standardtaste des Katalogs. Rein, ohne Android-Klassen: getestet.
 */
internal object AddonOptionsKey {
    /** Id der Katalogaktion (Bindung des Addons). */
    const val ACTION_ID = "WFB_OPTIONS"

    /** Zeit, die die Taste gehalten wird, damit das Spiel sie sicher liest. */
    const val HOLD_MS = 60L

    /** Die Taste jetzt: die aufgelöste des Spielers, sonst die Standardtaste des Katalogs; null, wenn keine sendbar ist. */
    fun combo(): BindingCombo? {
        val standard = WowActionCatalog.find(ACTION_ID)?.defaultKey?.let { WowKeys.toCombo(it) }
        return WowActionBindings.resolve(ACTION_ID, standard)
    }

    /**
     * Die Tasten in der Reihenfolge des Drückens (Strg, Umschalt, dann die Haupttaste). Leer, wenn die Kombination fehlt, eine
     * Folge ist oder keine echte Tastaturtaste enthält (zum Beispiel nur Maus oder Gamepad).
     */
    fun keyCodes(combo: BindingCombo?): List<XKeycode> {
        if (combo == null || combo.isEmpty || combo.isSequence) return emptyList()
        val codes = combo.bindings.map { it.keycode }
        // Eine Kombination, die auch nur eine Nicht-Tastaturtaste enthält, ist nicht sendbar: halb gesendet wäre sie falsch.
        if (codes.any { it == XKeycode.KEY_NONE }) return emptyList()
        return codes
    }

    /** Drückt die Tasten der Reihe nach, hält sie [HOLD_MS] und lässt sie in umgekehrter Reihenfolge los. */
    fun send(codes: List<XKeycode>, press: (XKeycode) -> Unit, release: (XKeycode) -> Unit, pause: (Long) -> Unit) {
        if (codes.isEmpty()) return
        for (code in codes) press(code)
        pause(HOLD_MS)
        for (code in codes.asReversed()) release(code)
    }
}
