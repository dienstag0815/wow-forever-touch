package app.gamenative.ui.screen.wow

import android.content.Context
import androidx.annotation.StringRes
import app.gamenative.R
import app.gamenative.ui.components.AppText
import app.gamenative.cooldown.CooldownStrip
import com.winlator.inputcontrols.Binding
import com.winlator.inputcontrols.ControlElement

/**
 * Touch-Elemente über offenen Spielfenstern ausblenden (Taschen, Karte, Charakter, Spielmenü ...), damit man dort mehr Fläche
 * zum Tippen hat. Das Addon TouchForever meldet die Rechtecke der offenen großen Fenster im Abschnitt "Fenster" des Streifens
 * (siehe [CooldownStrip]); der Lesedienst ([app.gamenative.cooldown.CooldownService]) füttert [WindowGate] damit, die
 * Steuerungsansicht fragt beim Zeichnen und bei jeder Berührung.
 *
 * Reine Teile (ohne Android, getestet): [WindowHideMode], [WindowSnapshot], [WindowFeed], [WindowGeometry], [WindowHideRule],
 * [WindowHideTracker]. [WindowGate] ist der Zustand des Prozesses.
 */
enum class WindowHideMode(val key: String, @StringRes val labelRes: Int, @StringRes val descriptionRes: Int) {
    COVERED("covered", R.string.game_hide_covered, R.string.game_hide_covered_desc),
    ALL("all", R.string.game_hide_all, R.string.game_hide_all_desc),
    OFF("off", R.string.game_off, R.string.game_hide_off_desc),
    ;

    /** The name in the current app language. */
    val label: String get() = AppText.get(labelRes)

    /** The explanation in the current app language. */
    val description: String get() = AppText.get(descriptionRes)

    fun next(): WindowHideMode = entries[(ordinal + 1) % entries.size]

    companion object {
        val DEFAULT = COVERED

        fun fromKey(key: String?): WindowHideMode = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/**
 * Die offenen Fenster, wie das Addon sie zuletzt meldete: [count] Rechtecke, je vier Codes 0..255 (links, oben, rechts, unten) als
 * Anteil des Spielbilds (Ursprung links oben). Dazu die Größe des Spielbilds und des X-Bildschirms zur Lesezeit, damit die
 * Rechtecke auf die Ansicht abgebildet werden können (Letterbox).
 */
class WindowSnapshot(
    val count: Int,
    val codes: IntArray,
    val pictureWidth: Int,
    val pictureHeight: Int,
    val screenWidth: Int,
    val screenHeight: Int,
) {
    fun sameAs(other: WindowSnapshot): Boolean =
        count == other.count && pictureWidth == other.pictureWidth && pictureHeight == other.pictureHeight &&
            screenWidth == other.screenWidth && screenHeight == other.screenHeight &&
            (0 until count * 4).all { codes[it] == other.codes[it] }

    companion object {
        /** Aus einem gelesenen Streifen; null, wenn der Streifen keine Fenster meldet (alter Addon) oder keines offen ist. */
        fun from(decoded: CooldownStrip.Decoded, pictureWidth: Int, pictureHeight: Int, screenWidth: Int, screenHeight: Int): WindowSnapshot? {
            if (!decoded.windowsKnown || decoded.windowCount <= 0) return null
            val count = minOf(decoded.windowCount, CooldownStrip.WIN_MAX)
            return WindowSnapshot(
                count, decoded.windows.copyOf(count * 4), pictureWidth, pictureHeight, screenWidth, screenHeight,
            )
        }
    }
}

/**
 * Hält den letzten gemeldeten Stand. Meldet der Streifen die Fenster einmal nicht (beschädigter Abschnitt, ein Bild ohne
 * Streifen), bleibt der Stand [forgetAfterMs] stehen, danach gelten die Fenster als unbekannt (nichts wird ausgeblendet).
 * Die Zeit kommt vom Aufrufer (Millisekunden der Lesedurchgänge). Nicht thread-sicher: der Aufrufer sperrt.
 */
class WindowFeed(private val forgetAfterMs: Long = FORGET_AFTER_MS) {
    var current: WindowSnapshot? = null
        private set
    private var unknownSince = NONE

    /**
     * Ein Durchgang: [known] = der Streifen sagt etwas über Fenster, [next] = die offenen Fenster (null = keines).
     * Gibt true zurück, wenn sich [current] geändert hat.
     */
    fun update(known: Boolean, next: WindowSnapshot?, nowMs: Long): Boolean {
        if (known) {
            unknownSince = NONE
            val old = current
            if (next == null) {
                current = null
                return old != null
            }
            if (old != null && old.sameAs(next)) return false
            current = next
            return true
        }
        if (current == null) return false
        if (unknownSince == NONE) unknownSince = nowMs
        if (nowMs - unknownSince >= forgetAfterMs) {
            current = null
            unknownSince = NONE
            return true
        }
        return false
    }

    /** Streifen verloren oder Funktion aus: alles vergessen. Gibt true zurück, wenn vorher Fenster galten. */
    fun clear(): Boolean {
        unknownSince = NONE
        val had = current != null
        current = null
        return had
    }

    companion object {
        private const val NONE = Long.MIN_VALUE

        /** So lange bleibt der Stand ohne Meldung gültig (drei, vier Durchgänge; kürzer als eine Handbewegung). */
        const val FORGET_AFTER_MS = 400L
    }
}

/** Fensterrechtecke in Pixeln der Ansicht: je vier Werte (links, oben, rechts, unten). */
class WindowViewRects(@JvmField val count: Int, @JvmField val rects: FloatArray)

object WindowGeometry {
    /**
     * Bildet die Rechtecke des Spielbilds auf die Ansicht ab, wie der Renderer das Bild zeigt (ViewTransformation): gleichmäßig
     * skaliert, im nicht-Vollbild-Fall mittig mit Rand oben/unten oder links/rechts; im Vollbild ohne Versatz (so rechnet auch
     * TouchMouse). Das Spielbild liegt an der oberen linken Ecke des X-Bildschirms.
     */
    @JvmStatic
    fun toView(snapshot: WindowSnapshot, viewWidth: Int, viewHeight: Int, fullscreen: Boolean): WindowViewRects {
        val sw = snapshot.screenWidth
        val sh = snapshot.screenHeight
        if (viewWidth <= 0 || viewHeight <= 0 || sw <= 0 || sh <= 0) return WindowViewRects(0, FloatArray(0))
        val aspect = minOf(viewWidth.toFloat() / sw, viewHeight.toFloat() / sh)
        val offX = if (fullscreen) 0f else (viewWidth - sw * aspect) * 0.5f
        val offY = if (fullscreen) 0f else (viewHeight - sh * aspect) * 0.5f
        val out = FloatArray(snapshot.count * 4)
        for (i in 0 until snapshot.count) {
            out[4 * i] = offX + snapshot.codes[4 * i] / 255f * snapshot.pictureWidth * aspect
            out[4 * i + 1] = offY + snapshot.codes[4 * i + 1] / 255f * snapshot.pictureHeight * aspect
            out[4 * i + 2] = offX + snapshot.codes[4 * i + 2] / 255f * snapshot.pictureWidth * aspect
            out[4 * i + 3] = offY + snapshot.codes[4 * i + 3] / 255f * snapshot.pictureHeight * aspect
        }
        return WindowViewRects(snapshot.count, out)
    }

    /** Anteil (0..1) der Fläche des Rechtecks [l], [t], [r], [b], den die Fenster zusammen bedecken (Vereinigung, nichts doppelt). */
    @JvmStatic
    fun coveredShare(l: Float, t: Float, r: Float, b: Float, windows: WindowViewRects): Float {
        val area = (r - l) * (b - t)
        if (area <= 0f || windows.count == 0) return 0f
        val n = windows.count
        // Häufige Fälle ohne Speicher: kein Fenster oder genau eines schneidet das Rechteck.
        var hits = 0
        var single = 0f
        for (i in 0 until n) {
            val w = maxOf(0f, minOf(r, windows.rects[4 * i + 2]) - maxOf(l, windows.rects[4 * i]))
            val h = maxOf(0f, minOf(b, windows.rects[4 * i + 3]) - maxOf(t, windows.rects[4 * i + 1]))
            if (w > 0f && h > 0f) {
                hits++
                single = w * h
            }
        }
        if (hits == 0) return 0f
        if (hits == 1) return (single / area).coerceIn(0f, 1f)
        // Das Rechteck mit jedem Fenster geschnitten.
        val cl = FloatArray(n)
        val ct = FloatArray(n)
        val cr = FloatArray(n)
        val cb = FloatArray(n)
        var m = 0
        for (i in 0 until n) {
            val x0 = maxOf(l, windows.rects[4 * i])
            val y0 = maxOf(t, windows.rects[4 * i + 1])
            val x1 = minOf(r, windows.rects[4 * i + 2])
            val y1 = minOf(b, windows.rects[4 * i + 3])
            if (x1 > x0 && y1 > y0) {
                cl[m] = x0
                ct[m] = y0
                cr[m] = x1
                cb[m] = y1
                m++
            }
        }
        if (m == 0) return 0f
        // Vereinigung: senkrechte Streifen zwischen den x-Kanten, in jedem die Länge der vereinigten y-Intervalle.
        val xs = FloatArray(2 * m)
        for (i in 0 until m) {
            xs[2 * i] = cl[i]
            xs[2 * i + 1] = cr[i]
        }
        xs.sort()
        var covered = 0f
        for (k in 0 until xs.size - 1) {
            val sx0 = xs[k]
            val sx1 = xs[k + 1]
            if (sx1 <= sx0) continue
            val mid = (sx0 + sx1) * 0.5f
            var length = 0f
            var top = Float.NEGATIVE_INFINITY
            var bottom = Float.NEGATIVE_INFINITY
            // Nach oben sortierte Intervalle der Fenster, die diesen Streifen schneiden (m <= 4: einfaches Einfügen).
            val order = (0 until m).filter { cl[it] <= mid && cr[it] >= mid }.sortedBy { ct[it] }
            for (i in order) {
                if (ct[i] > bottom) {
                    if (bottom > top) length += bottom - top
                    top = ct[i]
                    bottom = cb[i]
                } else if (cb[i] > bottom) {
                    bottom = cb[i]
                }
            }
            if (bottom > top) length += bottom - top
            covered += length * (sx1 - sx0)
        }
        return (covered / area).coerceIn(0f, 1f)
    }
}

/** Die Regel: wann deckt ein Fenster ein Element so, dass es ausgeblendet wird. Rein. */
object WindowHideRule {
    /**
     * Ab mehr als diesem Anteil der Fläche des Elements gilt es als überdeckt. Früher 30 %: ein großer Stick, der nur mit seiner
     * Oberkante in ein Fenster ragte (Questdialog, "Cancel" unten im Fenster), blieb stehen und verdeckte den Knopf. 10 % gilt für
     * alle Elemente und alle Fenstergruppen gleich (die Gruppe ("kind") wertet der Launcher nicht aus).
     */
    const val COVER_SHARE = 0.10f

    /**
     * Ob die Fenster das Element in der Betriebsart [mode] verdecken: [COVERED] nach Flächenanteil (echt größer als
     * [COVER_SHARE]), [ALL] sobald ein Fenster offen ist, [OFF] nie.
     */
    @JvmStatic
    fun covers(mode: WindowHideMode, share: Float, windowCount: Int): Boolean = when (mode) {
        WindowHideMode.OFF -> false
        WindowHideMode.ALL -> windowCount > 0
        WindowHideMode.COVERED -> windowCount > 0 && share > COVER_SHARE
    }

    /** Escape- und Tastatur-Knopf bleiben immer (dieselbe Regel wie im Login, [keepsInLogin]). */
    @JvmStatic
    fun alwaysKept(type: ControlElement.Type, primary: Binding?): Boolean = keepsInLogin(type, primary)

    /**
     * Ob die offenen Fenster ein Element ausblenden, mit der Option des Elements [rule] ([WindowVisibility], null = Automatisch):
     * Automatisch ist die Regel von [covers] (Escape und Tastatur, [essential], bleiben); "Immer sichtbar" blendet nie aus, auch nicht im
     * Modus [WindowHideMode.ALL]; "Ausblenden" blendet auch dann aus, wenn das Element sonst bliebe, nur ein Escape-/Tastatur-Knopf
     * ([essential]) bleibt, wenn sonst keiner sichtbar bliebe ([safeKeepers] = Zahl der Escape-/Tastatur-Knöpfe, die nicht auf Ausblenden
     * stehen; der Spieler darf sich nicht aussperren). Im Modus Aus und ohne offenes Fenster gilt die Option nicht.
     */
    @JvmStatic
    fun hides(rule: String?, mode: WindowHideMode, share: Float, windowCount: Int, essential: Boolean, safeKeepers: Int): Boolean {
        if (mode == WindowHideMode.OFF || windowCount <= 0) return false
        return when (WindowVisibility.fromKey(rule)) {
            WindowVisibility.KEEP -> false
            WindowVisibility.HIDE -> !essential || safeKeepers > 0
            WindowVisibility.AUTO -> !essential && covers(mode, share, windowCount)
        }
    }

    /** Ob ein Escape-/Tastatur-Knopf mit der Option [rule] trotz "Ausblenden" bleiben muss, weil [safeKeepers] = 0 (Hinweis im Editor). */
    @JvmStatic
    fun lockedOut(rule: String?, essential: Boolean, safeKeepers: Int): Boolean =
        essential && WindowVisibility.fromKey(rule) == WindowVisibility.HIDE && safeKeepers <= 0
}

/** Option "Bei offenen Fenstern" eines Elements. Gespeichert wird nur "keep" und "hide"; fehlt das Feld, gilt Automatisch (alte Profile). */
enum class WindowVisibility(val key: String?, @StringRes val labelRes: Int) {
    AUTO(null, R.string.game_ctx_auto),
    KEEP("keep", R.string.game_vis_keep),
    HIDE("hide", R.string.game_vis_hide),
    ;


    /** The name in the current app language. */
    val label: String get() = AppText.get(labelRes)

    companion object {
        @JvmStatic
        fun fromKey(key: String?): WindowVisibility = entries.firstOrNull { it.key != null && it.key == key } ?: AUTO

        /** Die zu speichernde Kennung: null (Automatisch) bei unbekanntem Wert. */
        @JvmStatic
        fun normalize(key: String?): String? = fromKey(key).key
    }
}

/**
 * Pro Element: ausblenden sofort, einblenden erst nach [showDelayMs] ohne Überdeckung (gegen Flackern, wenn ein Fenster kurz
 * zugeht und wieder aufgeht oder der Rand eines Fensters über einem Knopf wandert). Ein Element, auf dem gerade ein Finger liegt
 * ([held]), wird nie mittendrin ausgeblendet; es verschwindet erst nach dem Loslassen. Die Zeit kommt vom Aufrufer. Nur der
 * Hauptthread benutzt das Objekt.
 */
class WindowHideTracker(private val showDelayMs: Long = SHOW_DELAY_MS) {
    private class State {
        var hidden = false
        var clearSince = NONE
        var pass = 0
    }

    private val states = java.util.IdentityHashMap<Any, State>()
    private var pass = 0

    /** Beginn eines Zeichendurchgangs über alle Elemente; [endPass] vergisst, was darin nicht gefragt wurde (entfernte Elemente). */
    fun beginPass() {
        pass++
    }

    fun endPass() {
        val it = states.values.iterator()
        while (it.hasNext()) if (it.next().pass != pass) it.remove()
    }

    /**
     * Ob [key] jetzt ausgeblendet ist. [covered]: die Regel sagt "überdeckt"; [held]: ein Finger liegt auf dem Element.
     */
    fun hidden(key: Any, covered: Boolean, held: Boolean, nowMs: Long): Boolean {
        var state = states[key]
        if (state == null) {
            if (!covered) return false
            state = State()
            states[key] = state
        }
        state.pass = pass
        if (covered) {
            state.hidden = true
            state.clearSince = NONE
        } else if (state.hidden) {
            if (state.clearSince == NONE) state.clearSince = nowMs
            if (nowMs - state.clearSince >= showDelayMs) {
                state.hidden = false
                state.clearSince = NONE
            }
        }
        if (!state.hidden) {
            states.remove(key)
            return false
        }
        return !held
    }

    /**
     * Wann (in ms ab [nowMs]) das nächste Element wieder erscheint, oder -1: nichts wartet. Die Ansicht zeichnet dann noch
     * einmal, ohne dass sich sonst etwas ändert.
     */
    fun nextChangeIn(nowMs: Long): Long {
        var wait = -1L
        for (state in states.values) {
            if (!state.hidden || state.clearSince == NONE) continue
            val left = maxOf(0L, state.clearSince + showDelayMs - nowMs)
            if (wait < 0 || left < wait) wait = left
        }
        return wait
    }

    fun reset() = states.clear()

    companion object {
        private const val NONE = Long.MIN_VALUE

        /** So lange muss ein Element frei sein, bevor es wieder erscheint. */
        const val SHOW_DELAY_MS = 150L
    }
}

/**
 * Zustand des Ausblendens über Fenstern, ein Objekt für den Prozess. Der Lesedienst füttert ihn mit jedem gelesenen Streifen
 * ([onStrip]); die Ansicht fragt [viewRects] und [mode] beim Zeichnen und bei Berührungen.
 */
object WindowGate {
    private const val KEY_MODE = "touch_hide_windows"

    private val lock = Any()
    private val feed = WindowFeed()

    @Volatile private var modeValue = WindowHideMode.DEFAULT
    @Volatile private var loaded = false
    @Volatile private var snapshot: WindowSnapshot? = null

    // Letzte Abbildung auf die Ansicht (Hauptthread).
    private var cacheSnapshot: WindowSnapshot? = null
    private var cacheWidth = 0
    private var cacheHeight = 0
    private var cacheFullscreen = false
    private var cacheRects: WindowViewRects? = null

    /** Gespeicherte Betriebsart, Standard: [WindowHideMode.COVERED]. */
    internal fun savedMode(context: Context): WindowHideMode = WindowHideMode.fromKey(wowPrefs(context).getString(KEY_MODE, null))

    /** Lädt die Betriebsart einmal (Start einer Spielsitzung). */
    @JvmStatic
    fun init(context: Context) {
        if (loaded) return
        modeValue = savedMode(context)
        loaded = true
    }

    @JvmStatic
    fun mode(): WindowHideMode = modeValue

    /** Setzt und speichert die Betriebsart; Gibt true zurück, wenn sich etwas geändert hat. */
    fun setMode(context: Context, mode: WindowHideMode): Boolean {
        wowPrefs(context).edit().putString(KEY_MODE, mode.key).apply()
        val changed = modeValue != mode
        modeValue = mode
        loaded = true
        if (mode == WindowHideMode.OFF) synchronized(lock) {
            feed.clear()
            snapshot = null
        }
        return changed
    }

    /** Ob der Lesedienst für dieses Signal arbeiten soll, auch wenn die Abklingzeit-Anzeige aus ist. */
    @JvmStatic
    fun needsSignal(): Boolean = modeValue != WindowHideMode.OFF

    /**
     * Vom Lesedienst (Present-Thread): ein gelesener Streifen. Gibt true zurück, wenn sich die Fenster geändert haben und die
     * Ansicht neu zeichnen soll.
     */
    @JvmStatic
    fun onStrip(decoded: CooldownStrip.Decoded, pictureWidth: Int, pictureHeight: Int, screenWidth: Int, screenHeight: Int, nowMs: Long): Boolean {
        if (modeValue == WindowHideMode.OFF) return false
        val next = WindowSnapshot.from(decoded, pictureWidth, pictureHeight, screenWidth, screenHeight)
        synchronized(lock) {
            val changed = feed.update(decoded.windowsKnown, next, nowMs)
            if (changed) snapshot = feed.current
            return changed
        }
    }

    /** Streifen verloren: die Fenster sind unbekannt, nichts bleibt ausgeblendet. Gibt true zurück, wenn vorher welche galten. */
    @JvmStatic
    fun clear(): Boolean {
        synchronized(lock) {
            val changed = feed.clear()
            snapshot = null
            return changed
        }
    }

    /** Ob gerade Fenster gemeldet sind. */
    @JvmStatic
    fun hasWindows(): Boolean = snapshot != null

    /** Die Fenster in Pixeln der Ansicht, oder null, wenn keine gemeldet sind. Nur Hauptthread (hält die letzte Abbildung). */
    @JvmStatic
    fun viewRects(viewWidth: Int, viewHeight: Int, fullscreen: Boolean): WindowViewRects? {
        val s = snapshot ?: return null
        if (s === cacheSnapshot && viewWidth == cacheWidth && viewHeight == cacheHeight && fullscreen == cacheFullscreen) return cacheRects
        val rects = WindowGeometry.toView(s, viewWidth, viewHeight, fullscreen)
        cacheSnapshot = s
        cacheWidth = viewWidth
        cacheHeight = viewHeight
        cacheFullscreen = fullscreen
        cacheRects = rects
        return rects
    }
}
