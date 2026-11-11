package app.gamenative.wowicons

import android.content.Context

/**
 * Signal von der App an das Addon: "Standardlayout aktiv". Das Addon legt dann einmal die Edit-Mode-Vorlage "Touch Forever" an
 * (Fenster so, dass die Touchelemente des Standardlayouts nichts Wichtiges verdecken). Ohne Signal tut das Addon nichts von selbst;
 * der Spieler kann die Vorlage dann nur in /tf einschalten.
 *
 * Weg: [AddonSetup] schreibt bei jedem Start die kleine Datei TouchForeverLang.lua (dieselbe wie für die Sprache). Ist das Signal
 * an, steht darin zusätzlich `ns.layoutStandard = true`. Rein, ohne Android-Klassen: [fileLines] und [isOn] sind getestet.
 *
 * Der Schalter selbst ist ein Eintrag in den Einstellungen der App ([KEY]). Wer das Standardlayout wählt (Erststart-Dialog,
 * Vorlagenwahl), ruft [set] auf; wer ein eigenes Layout wählt, ruft [set] mit false auf.
 */
object AddonLayoutSignal {
    /** Name des Eintrags in den Einstellungen "wow_forever" (Boolean). */
    const val KEY = "layout_standard"

    private const val PREFS = "wow_forever"

    /** Ist das Signal an? Fehlender Eintrag = aus. */
    @JvmStatic
    fun isOn(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    /** Setzt das Signal; wirkt beim nächsten Spielstart (die Datei wird dann neu geschrieben). */
    @JvmStatic
    fun set(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
    }

    /** Die Zeilen, die zum Signal in der Lua-Datei stehen: leer, wenn aus (das Addon liest nil als "kein Signal"). */
    @JvmStatic
    fun fileLines(on: Boolean): String = if (on) "if ns then ns.layoutStandard = true end\n" else ""
}
