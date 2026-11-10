package app.gamenative.ui.screen.wow

import app.gamenative.ui.components.AppText
import android.content.Context
import app.gamenative.cooldown.CooldownStrip
import com.winlator.inputcontrols.ScrollWheelMath
import kotlin.math.abs

/**
 * Wischen in Listen wie am Handy: Das Addon TouchForever meldet im Scroll-Abschnitt des Streifens (siehe [CooldownStrip], Addon-Stand 3; ab Stand 6 immer, ob gescrollt wird, entscheidet nur die App über [SwipeScrollSetting])
 * die Rechtecke der scrollbaren Listen in offenen Fenstern (Ruf, Optionen, Questlog, Dialoge ...). Beginnt eine Berührung auf dem
 * Spielbild in so einem Rechteck und läuft senkrecht, wird daraus Mausrad an der Fingerposition (Entscheidung in
 * [app.gamenative.ui.screen.wow.klick.OneFingerGesture], Ausführung in TouchpadView).
 *
 * Reine Teile (ohne Android, getestet): [SwipeScrollMath], [ScrollSnapshot], [ScrollGeometry]. [ScrollGate] ist der Zustand des Prozesses,
 * [SwipeScrollSetting] die gespeicherte Einstellung (Schalter und Geschwindigkeit).
 */
object SwipeScrollMath {
    /** Ab dieser Strecke (dp) wird aus Berührung + Bewegung ein Wisch (statt Tippen / Halten). */
    const val THRESHOLD_DP = 10f

    /** Die senkrechte Strecke muss mindestens so viel größer als die waagrechte sein ("vertikal dominiert"). */
    const val DOMINANCE = 1.5f

    /** Fingerstrecke (dp) je Mausrad-Schritt bei Geschwindigkeit 1,0. */
    const val STEP_DP = 28f

    /** Mehr Nachlauf-Schritte als dies gibt es nicht (ein schneller Wisch soll die Liste nicht durchrauschen lassen). */
    const val FLING_MAX_TICKS = 6

    const val MIN_SPEED = ScrollWheelMath.MIN_SPEED
    const val MAX_SPEED = ScrollWheelMath.MAX_SPEED
    const val DEFAULT_SPEED = 1f

    @JvmStatic
    fun thresholdPx(density: Float): Float = THRESHOLD_DP * density

    /** Pixel Fingerstrecke je Mausrad-Schritt; Geschwindigkeit 2 = halbe Strecke = doppelt so viele Schritte. */
    @JvmStatic
    fun stepPx(density: Float, speed: Float): Float {
        val s = if (speed.isNaN()) DEFAULT_SPEED else speed.coerceIn(MIN_SPEED, MAX_SPEED)
        return maxOf(4f, STEP_DP * density / s)
    }

    /** Wischt die Bewegung (dx, dy seit dem Aufsetzen) senkrecht: weit genug und deutlich mehr senkrecht als waagrecht. */
    @JvmStatic
    fun isVerticalSwipe(dx: Float, dy: Float, thresholdPx: Float): Boolean =
        abs(dy) >= thresholdPx && abs(dy) >= DOMINANCE * abs(dx)

    /** Nächste Geschwindigkeit der Einstellung (reihum): 0,5 - 0,75 - 1 - 1,5 - 2 - 3 - 4 und von vorn. */
    @JvmStatic
    fun nextSpeed(current: Float): Float {
        val steps = SPEEDS
        val index = steps.indexOfFirst { it > current + 0.001f }
        return if (index < 0) steps.first() else steps[index]
    }

    @JvmStatic
    fun speedLabel(speed: Float): String {
        val rounded = Math.round(speed * 100f) / 100f
        val decimal = if (AppText.isGerman()) rounded.toString().replace('.', ',') else rounded.toString()
        return (if (rounded == rounded.toInt().toFloat()) rounded.toInt().toString() else decimal) + "x"
    }

    val SPEEDS = floatArrayOf(0.5f, 0.75f, 1f, 1.5f, 2f, 3f, 4f)
}

/** Die gespeicherte Einstellung "Wischen in Listen scrollt": Schalter (Standard an) und Geschwindigkeit (Faktor wie beim Scrollrad). */
object SwipeScrollSetting {
    private const val KEY_ENABLED = "touch_swipe_scroll"
    private const val KEY_SPEED = "touch_swipe_scroll_speed"

    @JvmStatic
    fun isEnabled(context: Context): Boolean = wowPrefs(context).getBoolean(KEY_ENABLED, true)

    @JvmStatic
    fun speed(context: Context): Float =
        wowPrefs(context).getFloat(KEY_SPEED, SwipeScrollMath.DEFAULT_SPEED).coerceIn(SwipeScrollMath.MIN_SPEED, SwipeScrollMath.MAX_SPEED)

    @JvmStatic
    fun stepPx(context: Context): Float = SwipeScrollMath.stepPx(context.resources.displayMetrics.density, speed(context))

    fun setEnabled(context: Context, on: Boolean) {
        wowPrefs(context).edit().putBoolean(KEY_ENABLED, on).apply()
        ScrollGate.setEnabled(on)
    }

    fun setSpeed(context: Context, speed: Float) {
        wowPrefs(context).edit().putFloat(KEY_SPEED, speed.coerceIn(SwipeScrollMath.MIN_SPEED, SwipeScrollMath.MAX_SPEED)).apply()
    }
}

/** Die Scrollbereiche, wie das Addon sie zuletzt meldete, als [WindowSnapshot] (gleiche Rechteck-Codes und Bildgrößen wie bei den Fenstern). */
object ScrollSnapshot {
    /** Aus einem gelesenen Streifen; null, wenn der Streifen keine Scrollbereiche meldet (alter Addon, aus) oder keiner da ist. */
    @JvmStatic
    fun from(decoded: CooldownStrip.Decoded, pictureWidth: Int, pictureHeight: Int, screenWidth: Int, screenHeight: Int): WindowSnapshot? {
        if (!decoded.scrollKnown || decoded.scrollCount <= 0) return null
        val count = minOf(decoded.scrollCount, CooldownStrip.SCROLL_MAX)
        return WindowSnapshot(count, decoded.scrollAreas.copyOf(count * 4), pictureWidth, pictureHeight, screenWidth, screenHeight)
    }
}

object ScrollGeometry {
    /**
     * Ob der Punkt ([x], [y]) in Pixeln des X-Bildschirms in einem der Scrollbereiche liegt. Das Spielbild liegt an der oberen linken
     * Ecke des X-Bildschirms (wie bei [WindowGeometry]); die Codes 0..255 sind Anteile des Spielbilds. Die Reihenfolge der Bereiche
     * (kleinster zuerst) spielt für ein Ja/Nein keine Rolle.
     */
    @JvmStatic
    fun contains(snapshot: WindowSnapshot, x: Float, y: Float): Boolean = indexAt(snapshot, x, y) >= 0

    /** Index des ersten (kleinsten) Bereichs, der den Punkt enthält, sonst -1. */
    @JvmStatic
    fun indexAt(snapshot: WindowSnapshot, x: Float, y: Float): Int {
        for (i in 0 until snapshot.count) {
            val l = snapshot.codes[4 * i] / 255f * snapshot.pictureWidth
            val t = snapshot.codes[4 * i + 1] / 255f * snapshot.pictureHeight
            val r = snapshot.codes[4 * i + 2] / 255f * snapshot.pictureWidth
            val b = snapshot.codes[4 * i + 3] / 255f * snapshot.pictureHeight
            if (x >= l && x <= r && y >= t && y <= b) return i
        }
        return -1
    }
}

/**
 * Zustand der Scrollbereiche, ein Objekt für den Prozess. Der Lesedienst ([app.gamenative.cooldown.CooldownService]) füttert ihn mit
 * jedem gelesenen Streifen ([onStrip]); die Touch-Ansicht fragt [contains] beim Aufsetzen eines Fingers. Meldet der Streifen die Bereiche
 * einmal nicht (beschädigter Abschnitt), bleibt der Stand [WindowFeed.FORGET_AFTER_MS] stehen, danach gelten sie als unbekannt.
 */
object ScrollGate {
    private val lock = Any()
    private val feed = WindowFeed()

    @Volatile private var enabledValue = true
    @Volatile private var loaded = false
    @Volatile private var snapshot: WindowSnapshot? = null

    /** Lädt den Schalter einmal (Start einer Spielsitzung). */
    @JvmStatic
    fun init(context: Context) {
        if (loaded) return
        enabledValue = SwipeScrollSetting.isEnabled(context)
        loaded = true
    }

    @JvmStatic
    fun enabled(): Boolean = enabledValue

    fun setEnabled(on: Boolean) {
        enabledValue = on
        loaded = true
        if (!on) clear()
    }

    /** Ob der Lesedienst für dieses Signal arbeiten soll, auch wenn die Abklingzeit-Anzeige aus ist. */
    @JvmStatic
    fun needsSignal(): Boolean = enabledValue

    /** Vom Lesedienst (Present-Thread): ein gelesener Streifen. */
    @JvmStatic
    fun onStrip(decoded: CooldownStrip.Decoded, pictureWidth: Int, pictureHeight: Int, screenWidth: Int, screenHeight: Int, nowMs: Long) {
        if (!enabledValue) return
        val next = ScrollSnapshot.from(decoded, pictureWidth, pictureHeight, screenWidth, screenHeight)
        synchronized(lock) {
            if (feed.update(decoded.scrollKnown, next, nowMs)) snapshot = feed.current
        }
    }

    /** Streifen verloren oder Funktion aus: es gibt keine Scrollbereiche. */
    @JvmStatic
    fun clear() {
        synchronized(lock) {
            feed.clear()
            snapshot = null
        }
    }

    /** Ob der Punkt (Pixel des X-Bildschirms) in einem gemeldeten Scrollbereich liegt. Jeder Thread. */
    @JvmStatic
    fun contains(screenX: Float, screenY: Float): Boolean {
        val s = snapshot ?: return false
        return enabledValue && ScrollGeometry.contains(s, screenX, screenY)
    }

    /** Ob gerade Scrollbereiche gemeldet sind (für Tests und die Statuszeile). */
    @JvmStatic
    fun hasAreas(): Boolean = snapshot != null
}
