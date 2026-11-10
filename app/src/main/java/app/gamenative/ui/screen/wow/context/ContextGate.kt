package app.gamenative.ui.screen.wow.context

import android.content.Context
import androidx.annotation.StringRes
import app.gamenative.R
import app.gamenative.ui.components.AppText
import app.gamenative.cooldown.CooldownStrip
import app.gamenative.ui.screen.wow.wowPrefs

/**
 * Kontextmenü beim Gedrückthalten auf einer Einheit (Spieler, NPC, Gegner): die Einstellung. "Automatisch" nimmt die Zauber, die das
 * Addon aus dem Zauberbuch auf Tasten legt (höchster Rang, nach Feind/Freund getrennt); "Aktionsleiste 1" nimmt die Zauber der Plätze
 * 1 bis 12. Beide zum Vergleichen, umschaltbar im Seitenmenü (wirkt sofort).
 */
enum class ContextMode(val id: String, @StringRes val labelRes: Int) {
    OFF("aus", R.string.game_off),
    AUTO("auto", R.string.game_ctx_auto),
    BAR("leiste", R.string.game_ctx_bar),
    ;

    /** The name in the current app language. */
    val label: String get() = AppText.get(labelRes)

    /** Reihum für die Zeile im Seitenmenü und in den Launcher-Einstellungen. */
    fun next(): ContextMode = entries[(ordinal + 1) % entries.size]

    companion object {
        /** Gespeicherte Kennung; unbekannt oder leer = Automatisch (Standard). */
        fun fromId(id: String?): ContextMode = entries.firstOrNull { it.id == id } ?: AUTO
    }
}

/** Die gespeicherte Einstellung "Kontextmenü". */
object ContextSetting {
    private const val KEY_MODE = "touch_context_mode"

    @JvmStatic
    fun mode(context: Context): ContextMode = ContextMode.fromId(wowPrefs(context).getString(KEY_MODE, null))

    fun setMode(context: Context, mode: ContextMode) {
        wowPrefs(context).edit().putString(KEY_MODE, mode.id).apply()
        ContextGate.setMode(mode)
    }

    /** Eine Zeile für das Seitenmenü. */
    fun label(mode: ContextMode): String = AppText.get(R.string.game_ctx_line, mode.label)

    private const val KEY_TAP_VIEW = "touch_tap_view_items"

    /** "Tippen in Taschen nimmt nicht auf": ein Tipp auf einen Gegenstand zeigt ihn nur (Tooltip mit Vergleich). Standard an. */
    @JvmStatic
    fun tapViewOnly(context: Context): Boolean = wowPrefs(context).getBoolean(KEY_TAP_VIEW, true)

    fun setTapViewOnly(context: Context, on: Boolean) {
        wowPrefs(context).edit().putBoolean(KEY_TAP_VIEW, on).apply()
        ContextGate.setTapViewOnly(on)
    }

    fun tapViewLabel(on: Boolean): String = AppText.get(if (on) R.string.game_ctx_tapview_on else R.string.game_ctx_tapview_off)
}

/** Der zuletzt gelesene Kontext mit der Zeit, zu der der Lesedienst ihn zuletzt (unverändert oder neu) sah. */
class ContextSnapshot(val data: ContextData, receivedMs: Long) {
    @Volatile var receivedMs: Long = receivedMs
}

/**
 * Entscheidung beim Ablauf der Haltezeit: Kontextmenü oder wie bisher die linke Maustaste halten. Rein, getestet.
 *
 * Das Menü kommt nur, wenn der Kontext frisch ist: nach dem Aufsetzen springt der Mauszeiger an die Fingerstelle, das Spiel braucht
 * ein Bild, bis `mouseover` stimmt, und das Addon ein weiteres, bis der Streifen es zeigt. Ein Wert, der älter ist als das Aufsetzen
 * plus [SETTLE_MS], kann noch die Einheit von vorher meinen; ein zu alter Wert ([MAX_AGE_MS]) heißt: der Streifen kommt nicht mehr an.
 * In beiden Fällen gilt wie bisher "Halten = linke Maustaste", nie ein Menü auf Verdacht.
 */
object ContextDecision {
    /** So lange darf der letzte Streifenwert her sein (der Lesedienst liest etwa alle 33 ms). */
    const val MAX_AGE_MS = 350L

    /** Mindestzeit zwischen Aufsetzen und gelesenem Wert: Mauszeiger setzen, Bild des Spiels, Bild mit dem Streifen. */
    const val SETTLE_MS = 120L

    @JvmStatic
    fun opensMenu(mode: ContextMode, data: ContextData?, receivedMs: Long, downMs: Long, nowMs: Long): Boolean {
        if (mode == ContextMode.OFF || data == null) return false
        if (nowMs - receivedMs > MAX_AGE_MS) return false
        if (receivedMs < downMs + SETTLE_MS) return false
        // Ein Gegenstand in Tasche, Bank oder Charakterfenster unter dem Zeiger: Menü (auch mit hängendem Cursor-Gegenstand).
        if (data.focus.isItem) return true
        val unit = data.unit
        // Nur die Einheit unter dem Zeiger: der Rückfall auf das Ziel gilt hier nicht (Halten auf dem Boden mit gewähltem Ziel
        // soll kein Menü des Ziels öffnen). Die eigene Figur unter dem Finger (mouseover ist der Spieler) öffnet das Selbst-Menü.
        return unit.exists && unit.source == UnitSource.MOUSEOVER
    }

    /**
     * Ob ein Tipp nur ansehen soll statt anklicken: Schalter an, der Zeiger liegt (frischer Wert, nach dem Aufsetzen gelesen) auf einem
     * belegten Platz in Tasche, Bank oder Charakterfenster, und der Cursor trägt nichts (trägt er etwas, ist der Tipp das Ablegen).
     * Im Zweifel (kein oder alter Wert) false: dann klickt der Tipp wie bisher.
     */
    @JvmStatic
    fun tapIsViewOnly(enabled: Boolean, data: ContextData?, receivedMs: Long, downMs: Long, nowMs: Long): Boolean {
        if (!enabled || data == null) return false
        if (nowMs - receivedMs > MAX_AGE_MS) return false
        if (receivedMs < downMs + SETTLE_MS) return false
        return data.focus.isItem && !data.focus.cursorHasItem
    }
}

/**
 * Zustand des Kontexts, ein Objekt für den Prozess. Der Lesedienst ([app.gamenative.cooldown.CooldownService]) füttert ihn mit jedem
 * gelesenen Streifen ([onStrip]); die Touch-Ansicht fragt beim Ablauf der Haltezeit ([snapshot], [ContextDecision]). Meldet der Streifen
 * den Kontext einmal nicht (beschädigter Abschnitt), bleibt der letzte Wert [FORGET_AFTER_MS] stehen und ist danach unbekannt.
 */
object ContextGate {
    private const val FORGET_AFTER_MS = 1_000L

    private val lock = Any()

    @Volatile private var modeValue = ContextMode.AUTO
    @Volatile private var tapViewValue = true
    @Volatile private var loaded = false
    @Volatile private var snap: ContextSnapshot? = null

    private var lastNibbles: IntArray? = null
    private var unknownSinceMs = Long.MIN_VALUE

    /** Lädt die Einstellung einmal (Start einer Spielsitzung). */
    @JvmStatic
    fun init(context: Context) {
        if (loaded) return
        modeValue = ContextSetting.mode(context)
        tapViewValue = ContextSetting.tapViewOnly(context)
        loaded = true
    }

    @JvmStatic
    fun mode(): ContextMode = modeValue

    fun setMode(mode: ContextMode) {
        modeValue = mode
        loaded = true
        if (mode == ContextMode.OFF) clear()
    }

    /** "Tippen in Taschen nimmt nicht auf" (wirkt nur, solange das Kontextmenü nicht aus ist). */
    @JvmStatic
    fun tapViewOnly(): Boolean = tapViewValue && modeValue != ContextMode.OFF

    fun setTapViewOnly(on: Boolean) {
        tapViewValue = on
        loaded = true
    }

    /** Ob der Lesedienst für dieses Signal arbeiten soll, auch wenn die Abklingzeit-Anzeige aus ist. */
    @JvmStatic
    fun needsSignal(): Boolean = modeValue != ContextMode.OFF

    /** Vom Lesedienst (Present-Thread): ein gelesener Streifen. */
    @JvmStatic
    fun onStrip(decoded: CooldownStrip.Decoded, nowMs: Long) {
        if (modeValue == ContextMode.OFF) return
        synchronized(lock) {
            if (!decoded.contextKnown) {
                if (unknownSinceMs == Long.MIN_VALUE) unknownSinceMs = nowMs
                if (nowMs - unknownSinceMs >= FORGET_AFTER_MS) clearLocked()
                return
            }
            unknownSinceMs = Long.MIN_VALUE
            val last = lastNibbles
            val current = snap
            if (last != null && current != null && last.contentEquals(decoded.contextNibbles)) {
                current.receivedMs = nowMs
                return
            }
            val data = ContextWire.decode(decoded.contextNibbles) ?: return
            lastNibbles = decoded.contextNibbles.copyOf()
            snap = ContextSnapshot(data, nowMs)
        }
    }

    /** Streifen verloren oder Funktion aus: es gibt keinen Kontext. */
    @JvmStatic
    fun clear() {
        synchronized(lock) { clearLocked() }
    }

    private fun clearLocked() {
        snap = null
        lastNibbles = null
        unknownSinceMs = Long.MIN_VALUE
    }

    /** Der letzte Kontext oder null. Jeder Thread. */
    @JvmStatic
    fun snapshot(): ContextSnapshot? = snap

    /** Für Tests: einen Kontext setzen, als hätte der Streifen ihn gemeldet. */
    @JvmStatic
    fun setForTest(data: ContextData?, nowMs: Long) {
        synchronized(lock) {
            lastNibbles = null
            snap = if (data == null) null else ContextSnapshot(data, nowMs)
        }
    }
}
