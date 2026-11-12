package app.gamenative.ui.screen.wow

import android.content.Context
import com.winlator.inputcontrols.Binding
import com.winlator.inputcontrols.ControlElement

/**
 * "In der Welt"-Erkennung für das Ausblenden der Touch-Steuerung im Login.
 *
 * Signal: der Streifen des Addons TouchForever (siehe CooldownStrip). Das Addon legt ihn erst bei PLAYER_LOGIN an, also wenn
 * der Charakter in der Welt steht, und hängt ihn an UIParent; beim Ausloggen zur Charakterauswahl verschwindet er mit der
 * Oberfläche. Login, Warteschlange und Charakterauswahl zeigen ihn nie. Ein Streifen, der gelesen wird, heißt also "in der Welt".
 *
 * Die Zeit läuft in Bildern, nicht in Wanduhrzeit: Pausiert das Spiel (Seitenmenü offen), kommen keine Bilder, und die Pause
 * zählt nicht zur Zeit ohne Streifen.
 */
internal class WorldPresence(
    private val graceMs: Long = LOSE_GRACE_MS,
    private val pauseGapMs: Long = PAUSE_GAP_MS,
) {
    private var lastReadMs = NONE
    private var lastPassMs = NONE

    /**
     * Ein Durchgang des Leseverfahrens zur Zeit [nowMs]; [stripRead]: der Streifen wurde in diesem Durchgang gelesen.
     * Gibt zurück, ob das Spiel jetzt als "in der Welt" gilt.
     */
    fun update(nowMs: Long, stripRead: Boolean): Boolean {
        if (lastPassMs != NONE && lastReadMs != NONE) {
            val gap = nowMs - lastPassMs
            // Lange Lücke zwischen zwei Durchgängen = das Spiel war angehalten: die Lücke zählt nicht als "Streifen fehlt".
            if (gap > pauseGapMs) lastReadMs = minOf(nowMs, lastReadMs + gap)
        }
        lastPassMs = nowMs
        if (stripRead) lastReadMs = nowMs
        return inWorld(lastReadMs, nowMs, graceMs)
    }

    fun reset() {
        lastReadMs = NONE
        lastPassMs = NONE
    }

    companion object {
        private const val NONE = Long.MIN_VALUE

        /** So lange nach dem letzten gelesenen Streifen (Ladebildschirm, kurzer Aussetzer) gilt man noch als in der Welt. */
        const val LOSE_GRACE_MS = 6_000L

        /** Ab dieser Lücke zwischen zwei Durchgängen gilt das Spiel als angehalten. */
        const val PAUSE_GAP_MS = 1_500L

        /** Rein: in der Welt, wenn der Streifen je gelesen wurde und das höchstens [graceMs] her ist (die Grenze selbst zählt noch). */
        fun inWorld(lastReadMs: Long, nowMs: Long, graceMs: Long): Boolean = lastReadMs != NONE && nowMs - lastReadMs <= graceMs
    }
}

/**
 * Sichtbarkeit der Touch-Steuerung (Knöpfe, Sticks, Ring): sichtbar, wenn das Ausblenden aus ist, der Notausgang
 * "jetzt zeigen" gedrückt wurde oder das Spiel in der Welt ist. Rein.
 */
internal fun touchVisible(hideInLogin: Boolean, forceShown: Boolean, inWorld: Boolean): Boolean =
    !hideInLogin || forceShown || inWorld

/**
 * Belegungen, deren Knopf im Login und in der Charakterauswahl sichtbar bleibt: Escape und Bildschirmtastatur. Die Wahl nach der
 * Belegung (statt einer Eigenschaft je Element) gilt für jedes Profil, auch für "Mein Layout" und schon installierte Kopien, ohne
 * das Profilformat zu ändern oder Profile umzuschreiben; die mitgelieferten Profile (controls-6, controls-7) haben beide Knöpfe.
 * Weitere Belegungen (z. B. das Menü OPEN_NAVIGATION_MENU) lassen sich hier ergänzen.
 */
internal val LOGIN_KEPT_BINDINGS: Set<Binding> = setOf(Binding.KEY_ESC, Binding.SHOW_KEYBOARD)

/** Rein: bleibt ein Element mit [type] und der ersten Belegung [primary] im Login sichtbar? Nur Knöpfe, keine Sticks und Räder. */
internal fun keepsInLogin(type: ControlElement.Type, primary: Binding?): Boolean =
    type == ControlElement.Type.BUTTON && primary != null && primary in LOGIN_KEPT_BINDINGS

/**
 * Zustand des Ausblendens, ein Objekt für den Prozess. Der Lesedienst ([app.gamenative.cooldown.CooldownService]) füttert ihn mit
 * jedem Durchgang; die Steuerungsansicht fragt [isHidden] beim Zeichnen und bei jeder Berührung (nur ein Feldzugriff).
 */
object LoginTouchGate {
    private const val KEY_HIDE = "touch_hide_in_login"

    private val presence = WorldPresence()
    private val lock = Any()

    // Geschrieben unter [lock]; die Leser (Zeichnen, Berührung, Lesedienst) brauchen nur den aktuellen Wert.
    @Volatile private var hideInLogin = true
    @Volatile private var hidden = true
    private var forceShown = false
    @Volatile private var lastInWorld = false
    @Volatile private var loaded = false

    // Die Stufe "Ausfüllen" braucht die Welt-Erkennung auch ohne das Ausblenden: der Knopf "Anmeldedaten ausfüllen" verschwindet
    // in der Welt, und das Tippen bricht dort ab.
    @Volatile private var fillWanted = false

    /**
     * Das Spiel steht nach dem Streifen in der Welt (nur gültig, solange das Ausblenden an ist oder die Stufe "Ausfüllen" gilt
     * und der Streifen damit mitgelesen wird). Die Anmelde-Einträge tippen dann nichts: Buchstaben und Tab wären dort
     * Tastenbelegungen des Spiels.
     */
    fun inWorld(): Boolean = (hideInLogin || fillWanted) && lastInWorld

    /** Die Stufe "Ausfüllen" gilt: der Streifen wird auch ohne das Ausblenden gelesen. */
    fun setFillWanted(wanted: Boolean) {
        fillWanted = wanted
    }

    /** Gespeicherter Schalter, Standard: an. */
    internal fun hideEnabled(context: Context): Boolean = wowPrefs(context).getBoolean(KEY_HIDE, true)

    /** Lädt den Schalter einmal; [reset] ruft eine neue Spielsitzung. */
    @JvmStatic
    fun init(context: Context) {
        if (loaded) return
        reset(context)
    }

    /** Neue Spielsitzung: Notausgang und Zeitverlauf zurücksetzen, zunächst wie im Login (ausgeblendet, wenn der Schalter an ist). */
    @JvmStatic
    fun reset(context: Context) {
        val enabled = hideEnabled(context)
        val fill = LoginLevels.needsWorldSignal(LoginLevelStore.current(context))
        synchronized(lock) {
            hideInLogin = enabled
            fillWanted = fill
            forceShown = false
            lastInWorld = false
            presence.reset()
            hidden = !touchVisible(enabled, false, false)
            loaded = true
        }
    }

    /** Ob die Touch-Steuerung jetzt ausgeblendet ist (außer den Knöpfen von [keepInLogin]; Berührungen daneben wirken als Maus). */
    @JvmStatic
    fun isHidden(): Boolean = hidden

    /** Bleibt dieses Element trotz Ausblenden sichtbar und bedienbar (Escape- und Tastatur-Knopf)? */
    @JvmStatic
    fun keepInLogin(type: ControlElement.Type, primary: Binding?): Boolean = keepsInLogin(type, primary)

    /** Ob der Lesedienst für dieses Signal arbeiten soll, auch wenn die Abklingzeit-Anzeige aus ist. */
    @JvmStatic
    fun needsSignal(): Boolean = hideInLogin || fillWanted

    /** Vom Lesedienst (Present-Thread): ein Durchgang. Gibt true zurück, wenn sich die Sichtbarkeit geändert hat. */
    @JvmStatic
    fun onPass(nowMs: Long, stripRead: Boolean): Boolean {
        synchronized(lock) {
            val inWorld = presence.update(nowMs, stripRead)
            // In der Welt ist der Notausgang nicht mehr nötig; nach dem Ausloggen gilt wieder das Ausblenden.
            if (inWorld) forceShown = false
            return apply(inWorld)
        }
    }

    private fun apply(inWorld: Boolean): Boolean {
        lastInWorld = inWorld
        val next = !touchVisible(hideInLogin, forceShown, inWorld)
        val changed = next != hidden
        hidden = next
        return changed
    }

    /** Seitenmenü: Schalter "Touch-Steuerung im Login ausblenden". Gibt true zurück, wenn sich die Sichtbarkeit änderte. */
    fun setHideEnabled(context: Context, enabled: Boolean): Boolean {
        wowPrefs(context).edit().putBoolean(KEY_HIDE, enabled).apply()
        synchronized(lock) {
            hideInLogin = enabled
            loaded = true
            if (!enabled) forceShown = false
            return apply(lastInWorld)
        }
    }

    /** Notausgang "Touch-Steuerung jetzt zeigen". Gibt true zurück, wenn sich die Sichtbarkeit änderte. */
    fun showNow(): Boolean {
        synchronized(lock) {
            forceShown = true
            return apply(lastInWorld)
        }
    }
}
