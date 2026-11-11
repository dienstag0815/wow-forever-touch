package app.gamenative.ui.screen.wow.context

import app.gamenative.ui.screen.wow.AddonOptionsKey
import app.gamenative.wowaction.WowActionCatalog
import app.gamenative.wowaction.WowKeys
import com.winlator.inputcontrols.BindingCombo
import com.winlator.inputcontrols.WowActionBindings
import com.winlator.xserver.XServer
import java.util.concurrent.Executors
import timber.log.Timber

/**
 * Die Tasten des Kontextmenüs. Das Addon (TouchForeverContext.lua, C.KEYS) legt sie als Override-Bindungen auf feste Knöpfe; die App
 * drückt sie, ein Druck = eine Aktion. Die Belegung steht auf beiden Seiten und ein Test vergleicht sie.
 *
 * Kollisionsfrei zu allem, was die App und das Addon sonst belegen: Strg+Umschalt F1..F12 (Addon-Bindungen und Neuladen),
 * Alt+Strg F1..F3 und Alt+Strg+Pos1 (Menüs), die Zahlenreihe (Aktionsleiste 1). Zwei Zusatztasten plus eine Taste ist das Äußerste, was eine
 * Kombination der App tragen kann (BindingCombo.MAX_BINDINGS 3).
 *
 * Nie Alt zusammen mit F4 (und nichts anderes, was unter Windows/Wine das Fenster schließt oder wechselt): Alt+F4 schließt das Spiel, auch mit
 * Umschalt oder Strg dazu. "Einladen" lag auf Alt+Umschalt+F4 und beendete das Spiel; jetzt Alt+Strg+Einfg. SystemKeysTest prüft alle Tabellen.
 * Alt+Strg+Einfg/Pos1 sind frei: die Ersatztasten der Leisten 4 und 5 haben Alt+Umschalt bzw. Strg+Umschalt mit denselben Navigationstasten.
 */
object ContextKeys {
    /** Taste je Eintrag der Zauberlisten (Index 0 bis 7): "Feind-Zauber i" und "Freund-Zauber i" teilen sich die Taste. */
    val SPELL_KEYS: List<String> = (5..12).map { "ALT-CTRL-F$it" }

    const val ATTACK = "ALT-SHIFT-F1"

    /**
     * Öffnet das Kontextmenü des Spiels für das Ziel (dasselbe wie der Rechtsklick auf das Zielbild: Flüstern, Einladen, Handeln,
     * Betrachten, Folgen, Fokus, Zielmarkierung ...). Nur das Öffnen läuft über die Taste; die Einträge tippt man im Spiel an.
     */
    const val POPUP = "ALT-SHIFT-F2"

    /** Flüstern: öffnet das Chatfeld mit "/w Name " für das Ziel (danach tippt man oder die Spracheingabe der App hängt ihren Text an). */
    const val WHISPER = "ALT-SHIFT-F3"

    /** Das Ziel in die Gruppe einladen. Nicht F4: Alt+F4 schließt unter Windows/Wine das Fenster, auch mit Zusatztasten. */
    const val INVITE = "ALT-CTRL-INSERT"

    /** Dem Ziel folgen (/follow). */
    const val FOLLOW = "ALT-SHIFT-F5"

    /** Mit dem Ziel handeln. */
    const val TRADE = "ALT-SHIFT-F6"

    /** Das Ziel betrachten (Inspect). */
    const val INSPECT = "ALT-SHIFT-F7"

    /** Das Ziel in die Freundesliste aufnehmen. */
    const val FRIEND = "ALT-SHIFT-F8"

    /** Der Gegenstand am Cursor geht zurück an seinen Platz (ClearCursor). Liegt eine Quest unter dem Zeiger: [QUEST_TRACK]. */
    const val CLEAR_CURSOR = "ALT-SHIFT-F9"

    /** Den Gegenstand unter dem Zeiger im Chat verlinken; liegt keiner da, die Quest unter dem Zeiger (Addon ab Build 11). */
    const val LINK_ITEM = "ALT-SHIFT-F10"

    /** Den Vergleich mit der angelegten Ausrüstung zeigen. Liegt eine Quest unter dem Zeiger: [QUEST_SHARE]. */
    const val COMPARE = "ALT-SHIFT-F11"

    /**
     * Quest-Menü: dieselben Tasten, die Tabelle bleibt gleich (alle F-Tasten-Kombinationen sind belegt). Das Addon wertet die Quest unter dem
     * Zeiger erst nach dem Gegenstand; ein Gegenstandsplatz unter dem Zeiger behält die alte Bedeutung. Verlinken: [LINK_ITEM].
     */
    const val QUEST_LINK = LINK_ITEM

    /** Quest mit der Gruppe teilen (die Taste des Vergleichs; sie gilt nur, solange eine Quest unter dem Zeiger liegt). */
    const val QUEST_SHARE = COMPARE

    /** Quest verfolgen / nicht mehr verfolgen (die Taste "Cursor leeren"; sie gilt nur, solange eine Quest unter dem Zeiger liegt). */
    const val QUEST_TRACK = CLEAR_CURSOR

    /** Wegwerfen: das Addon fragt mit eigenem Ja/Nein nach, erst "Ja" löscht (nie ohne Rückfrage). */
    const val DISCARD = "ALT-SHIFT-F12"

    /** Alle festen Aktionstasten in der Reihenfolge der Tabelle im Addon. */
    val ACTION_KEYS: List<String> =
        listOf(ATTACK, POPUP, WHISPER, INVITE, FOLLOW, TRADE, INSPECT, FRIEND, CLEAR_CURSOR, LINK_ITEM, COMPARE, DISCARD)

    /** Jede Taste, die das Kontextmenü belegt. */
    val ALL: List<String> = SPELL_KEYS + ACTION_KEYS

    @JvmStatic
    fun spellKey(index: Int): String? = SPELL_KEYS.getOrNull(index)

    /** Die Taste des Platzes [slot] (1..12) der Aktionsleiste 1: die aufgelöste des Spielers, sonst die Standardtaste. */
    @JvmStatic
    fun barCombo(slot: Int): BindingCombo? = wowCombo("ACTIONBUTTON$slot")

    /** Die Taste der Aktion [id] des Spiels (z. B. OPENALLBAGS): die aufgelöste des Spielers, sonst die Standardtaste; null = keine bekannt. */
    @JvmStatic
    fun wowCombo(id: String?): BindingCombo? {
        if (id == null) return null
        val standard = WowActionCatalog.find(id)?.defaultKey?.let { WowKeys.toCombo(it) }
        return WowActionBindings.resolve(id, standard)
    }

    @JvmStatic
    fun combo(key: String?): BindingCombo? = if (key == null) null else WowKeys.toCombo(key)
}

/** Drückt Tasten des Kontextmenüs auf dem X-Server, einen Druck nach dem anderen und nie auf dem Touch-Thread (die Taste wird gehalten). */
object ContextKeySender {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ContextKeys").apply { isDaemon = true }
    }

    /** Wahr, wenn die Kombination sendbar ist und in die Warteschlange kam. */
    @JvmStatic
    fun send(server: XServer?, combo: BindingCombo?): Boolean {
        if (server == null) return false
        val codes = AddonOptionsKey.keyCodes(combo)
        if (codes.isEmpty()) return false
        try {
            executor.execute {
                try {
                    AddonOptionsKey.send(
                        codes = codes,
                        press = { server.injectKeyPress(it) },
                        release = { server.injectKeyRelease(it) },
                        pause = { Thread.sleep(it) },
                    )
                } catch (e: Exception) {
                    Timber.w(e, "Kontextmenü: Taste nicht gesendet")
                }
            }
        } catch (e: java.util.concurrent.RejectedExecutionException) {
            return false
        }
        return true
    }
}
