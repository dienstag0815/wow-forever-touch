package app.gamenative.ui.touchstyle

import androidx.annotation.StringRes
import app.gamenative.R
import app.gamenative.ui.components.AppText
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Aussehen der Touch-Elemente (Auftrag tastenoptik, Phase 2a). Dieser Teil ist reine Rechnung ohne Android-Klassen und deshalb
 * mit JUnit prüfbar: Stile, Farben, Deckkraft je Zustand, Kontrastfaktor, Ruhe-Timer. Das Zeichnen steht in [TouchStyleDraw].
 *
 * - [TouchStyle.CURRENT]: das frühere Aussehen (weiße Umrisse), unverändert; nur noch auf Wunsch (Standard ist [TouchStyle.G]).
 * - [TouchStyle.F]: "Classic ruhig": abgeschrägte Rahmen in gedämpftem Graubraun, dunkle halbtransparente Fläche, Gold nur für
 *   Schrift und den gedrückten Zustand.
 * - [TouchStyle.G]: wie F plus Touch-Muster: Ruhe (28 % Füllung, 60 % Kante), aktiv (53 %), gedrückt (80 %, Symbol 25 % kleiner,
 *   Kante warmgold); Joystick als weiche Fläche.
 *
 * Alle Werte stammen aus `tools/tastenoptik_vorschau.py` (Klassen Ruhig und Touch); Kantenbreiten und Schatten dort in
 * Tafel-Pixeln (1,3 Pixel je dp), hier in dp.
 */
enum class TouchStyle(val id: String, @StringRes val labelRes: Int) {
    CURRENT("aktuell", R.string.game_qm_row_style_current),
    F("f", R.string.game_style_f),
    G("g", R.string.game_style_g),
    ;

    /** The name in the current app language. */
    val label: String get() = AppText.get(labelRes)

    /** Reihum, für die Zeile im Seitenmenü. */
    fun next(): TouchStyle = entries[(ordinal + 1) % entries.size]

    companion object {
        /** Standard für neue Installationen, Backups ohne Angabe und unbekannte Kennungen: das WoW-Thema (G). Wer "aktuell" wählt, behält es. */
        val DEFAULT: TouchStyle = G

        /** Gespeicherte Kennung; unbekannt oder leer = [DEFAULT]. */
        fun fromId(id: String?): TouchStyle = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/** Wozu ein gezeichneter Rahmen gehört; bestimmt Füllung, Kante, Schatten und Abschrägung. */
enum class TouchRole {
    /** Aktionsknopf / Knopf mit Symbol oder quadratischer Textknopf (abgeschrägt). */
    SLOT,

    /** Schmaler Textknopf (abgeschrägt, Abschrägung nach der kurzen Seite). */
    TEXT,

    /** Runder oder ovaler Knopf. */
    ROUND,

    /** Tafel um mehrere Felder und Mausrad. */
    PANEL,

    /** Grundkreis eines Sticks. */
    BASE,

    /** Daumen des Sticks / Mitte des Rings. */
    THUMB,

    /** Segment eines Tastenrings oder Bogenknopf. */
    SEGMENT,
}

/** Schatten außerhalb einer Form: Versatz nach unten, Unschärfe (beide in dp) und Deckkraft 0..255. */
data class TouchShadow(val dyDp: Float, val blurDp: Float, val alpha: Int)

object TouchStyleMath {
    const val CONTRAST_MIN = 0.7f
    const val CONTRAST_MAX = 1.5f
    const val CONTRAST_DEFAULT = 1.0f

    /** Stufen des Kontrastreglers (70 % bis 150 %). */
    val CONTRAST_STEPS: List<Float> = listOf(0.7f, 0.8f, 0.9f, 1.0f, 1.1f, 1.2f, 1.3f, 1.4f, 1.5f)

    /** Ruhe nach dieser Zeit ohne Berührung (G). */
    const val REST_AFTER_MS = 4000L

    /** Übergang Ruhe zu aktiv bei Berührung (höchstens 100 ms) und zurück (langsamer, damit es nicht flackert). */
    const val FADE_UP_MS = 100L
    const val FADE_DOWN_MS = 400L

    // ---- Farben (RGB) -------------------------------------------------------------------------------------------------

    /** G: Stein, Bronze, Lichtkante, Schrift, warmes Gold (gedrückt). */
    const val G_FILL = 0x1C1A17
    const val G_BRONZE = 0x8C6B3E
    const val G_LIGHT = 0xC9A15A
    const val G_TEXT = 0xE8DCC0
    const val G_WARM = 0xE6B84F

    /** F: Kantenverlauf, Schrift (Gold), gedrückter Rand. */
    const val F_EDGE_LIGHT = 0x928676
    const val F_EDGE_DARK = 0x544B40
    const val F_TEXT = 0xE8C646
    const val F_HILITE = 0xFFD41E

    /** Muster der Mausrad-Rillen und Pfeile. */
    const val F_RIB = 0xAA9C86

    // ---- Kontrast -----------------------------------------------------------------------------------------------------

    /** Kontrastfaktor in den erlaubten Bereich (kein Wert, NaN oder Unsinn = 100 %). */
    fun clampContrast(contrast: Float): Float =
        if (contrast.isNaN()) CONTRAST_DEFAULT else contrast.coerceIn(CONTRAST_MIN, CONTRAST_MAX)

    /** Nächste Stufe des Reglers, reihum (150 % zurück auf 70 %); ein Wert zwischen den Stufen geht auf die nächsthöhere. */
    fun nextContrast(current: Float): Float {
        val c = clampContrast(current)
        val exact = CONTRAST_STEPS.indexOfFirst { abs(it - c) < 0.001f }
        if (exact >= 0) return CONTRAST_STEPS[(exact + 1) % CONTRAST_STEPS.size]
        val greater = CONTRAST_STEPS.indexOfFirst { it > c }
        return CONTRAST_STEPS[if (greater < 0) 0 else greater]
    }

    fun contrastLabel(contrast: Float): String = "${(clampContrast(contrast) * 100f).roundToInt()} %"

    /** Deckkraft [alpha] (0..255) mal Kontrastfaktor, begrenzt auf 0..255. */
    fun scaleAlpha(alpha: Int, contrast: Float): Int =
        (alpha * clampContrast(contrast)).roundToInt().coerceIn(0, 255)

    // ---- Zustände -----------------------------------------------------------------------------------------------------

    fun lerp(a: Int, b: Int, t: Float): Int = (a + (b - a) * t.coerceIn(0f, 1f)).roundToInt()

    /**
     * Füllung (RGB ohne Alpha) einer Rolle. F hat je Rolle leicht andere Töne und im gedrückten Zustand einen wärmeren;
     * G hat überall den Stein.
     */
    fun fillRgb(style: TouchStyle, role: TouchRole, pressed: Boolean): Int = when (style) {
        TouchStyle.G -> G_FILL
        TouchStyle.F -> when (role) {
            TouchRole.SLOT -> if (pressed) 0x0E0D0A else 0x08090C
            TouchRole.PANEL -> 0x0B0B0E
            TouchRole.ROUND -> if (pressed) 0x1A1710 else 0x0A0A0C
            TouchRole.TEXT -> if (pressed) 0x201C13 else 0x0C0D11
            TouchRole.BASE -> 0x08080A
            TouchRole.THUMB -> if (pressed) 0x242016 else 0x141416
            TouchRole.SEGMENT -> if (pressed) 0x242014 else 0x0A0A0C
        }
        TouchStyle.CURRENT -> 0x000000
    }

    /**
     * Deckkraft der Füllung (0..255) vor dem Kontrastfaktor. [level] 0 = Ruhe, 1 = aktiv (nur G; F hat feste Werte).
     * G: Ruhe 28 % (72), aktiv 53 % (136), gedrückt 80 % (204); Tafel und Daumen bleiben auch beim Drücken bei Ruhe/aktiv.
     */
    fun baseFillAlpha(style: TouchStyle, role: TouchRole, pressed: Boolean, level: Float): Int = when (style) {
        TouchStyle.G -> when (role) {
            TouchRole.BASE -> 36
            TouchRole.PANEL, TouchRole.THUMB -> lerp(72, 136, level)
            else -> if (pressed) 204 else lerp(72, 136, level)
        }
        TouchStyle.F -> when (role) {
            TouchRole.SLOT -> if (pressed) 165 else 150
            TouchRole.PANEL -> 147
            TouchRole.ROUND -> if (pressed) 168 else 140
            TouchRole.TEXT -> if (pressed) 190 else 165
            TouchRole.BASE -> 58
            TouchRole.THUMB -> if (pressed) 175 else 150
            TouchRole.SEGMENT -> if (pressed) 150 else 118
        }
        TouchStyle.CURRENT -> 0
    }

    fun fillAlpha(style: TouchStyle, role: TouchRole, pressed: Boolean, level: Float, contrast: Float): Int =
        scaleAlpha(baseFillAlpha(style, role, pressed, level), contrast)

    /** Deckkraft der Kante (nicht gedrückt) vor dem Kontrastfaktor: G Ruhe 60 % (153), aktiv 88 % (225). */
    fun baseEdgeAlpha(style: TouchStyle, role: TouchRole, level: Float): Int = when (style) {
        TouchStyle.G -> if (role == TouchRole.BASE) lerp(120, 170, level) else lerp(153, 225, level)
        TouchStyle.F -> when (role) {
            TouchRole.BASE, TouchRole.SEGMENT -> 172
            TouchRole.THUMB -> 190
            else -> 205
        }
        TouchStyle.CURRENT -> 0
    }

    fun edgeAlpha(style: TouchStyle, role: TouchRole, level: Float, contrast: Float): Int =
        scaleAlpha(baseEdgeAlpha(style, role, level), contrast)

    /** Deckkraft des gedrückten Rands (warmes Gold bei G, Gelb bei F). Der Kontrast hebt ihn nur an, er ist schon fast voll. */
    fun pressedRingAlpha(style: TouchStyle, contrast: Float): Int =
        scaleAlpha(if (style == TouchStyle.G) 255 else 240, contrast)

    fun pressedRingRgb(style: TouchStyle): Int = if (style == TouchStyle.G) G_WARM else F_HILITE

    /** Kantenbreite in dp. */
    fun edgeWidthDp(style: TouchStyle, role: TouchRole): Float = when (style) {
        TouchStyle.G -> 1.5f
        TouchStyle.F -> when (role) {
            TouchRole.BASE -> 1.0f
            TouchRole.SEGMENT -> 0.92f
            TouchRole.THUMB, TouchRole.ROUND, TouchRole.TEXT -> 1.15f
            else -> 1.23f
        }
        TouchStyle.CURRENT -> 0f
    }

    /** Breite des gedrückten Randes in dp (innen an der Kante). */
    fun pressedRingWidthDp(style: TouchStyle): Float = if (style == TouchStyle.G) 1.8f else 1.3f

    /** Dunkle Haarlinie direkt innerhalb der Kante: Breite in dp und Deckkraft. */
    fun hairlineWidthDp(style: TouchStyle): Float = if (style == TouchStyle.G) 0.77f else 0.6f

    fun hairlineAlpha(style: TouchStyle, contrast: Float): Int = scaleAlpha(if (style == TouchStyle.G) 120 else 105, contrast)

    /** Schatten außerhalb der Form; null = keiner. Deckkraft vor dem Kontrastfaktor. */
    fun shadow(style: TouchStyle, role: TouchRole): TouchShadow? = when (style) {
        TouchStyle.G -> when (role) {
            TouchRole.BASE -> null
            TouchRole.PANEL -> TouchShadow(2.3f, 3.8f, 120)
            TouchRole.SEGMENT -> TouchShadow(1.15f, 2.3f, 100)
            else -> TouchShadow(1.5f, 3.1f, 115)
        }
        TouchStyle.F -> when (role) {
            TouchRole.BASE -> null
            TouchRole.PANEL -> TouchShadow(2.3f, 3.8f, 60)
            TouchRole.SEGMENT -> TouchShadow(1.15f, 1.9f, 50)
            else -> TouchShadow(1.5f, 2.3f, 55)
        }
        TouchStyle.CURRENT -> null
    }

    /** G zeichnet den Schatten nur außerhalb der Form (die halbtransparente Fläche bleibt sauber), F auch darunter. */
    fun shadowOutsideOnly(style: TouchStyle): Boolean = style == TouchStyle.G

    /** Abschrägung der Ecken als Anteil der kürzeren Seite (Aktionsknopf 9 %, Textknopf und Tafel 10 %; F etwas kräftiger). */
    fun chamferFraction(style: TouchStyle, role: TouchRole): Float = when (style) {
        TouchStyle.G -> if (role == TouchRole.SLOT) 0.09f else 0.10f
        TouchStyle.F -> when (role) {
            TouchRole.SLOT -> 0.14f
            TouchRole.TEXT -> 0.155f
            else -> 0.17f
        }
        TouchStyle.CURRENT -> 0f
    }

    /** Größe des Symbols/der Schrift im gedrückten Zustand: G 25 % kleiner. */
    fun symbolScale(style: TouchStyle, pressed: Boolean): Float = if (style == TouchStyle.G && pressed) 0.75f else 1f

    /** Deckkraft des Symbols (0..255): G Ruhe 82 %, aktiv und gedrückt 100 %; F immer voll (dunkler über die Tönung). */
    fun iconAlpha(style: TouchStyle, pressed: Boolean, level: Float): Int = when (style) {
        TouchStyle.G -> if (pressed) 255 else (255 * (0.82f + 0.18f * level.coerceIn(0f, 1f))).roundToInt()
        else -> 255
    }

    /** Tönung über dem Symbol, die die Farben dämpft (ARGB). F: 84; G: 50; gedrückt bei F dunkler. */
    fun iconTint(style: TouchStyle, pressed: Boolean): Int = when (style) {
        TouchStyle.G -> argb(50, 30, 27, 22)
        TouchStyle.F -> if (pressed) argb(132, 30, 27, 24) else argb(84, 30, 27, 24)
        TouchStyle.CURRENT -> 0
    }

    /** Schriftfarbe (ARGB, voll deckend). Gedrückt: F 80 % der Helligkeit, G unverändert. */
    fun textColor(style: TouchStyle, pressed: Boolean): Int = when (style) {
        TouchStyle.G -> 0xFF000000.toInt() or G_TEXT
        else -> {
            val k = if (pressed) 0.8f else 1f
            val r = ((F_TEXT shr 16 and 0xFF) * k).roundToInt()
            val g = ((F_TEXT shr 8 and 0xFF) * k).roundToInt()
            val b = ((F_TEXT and 0xFF) * k).roundToInt()
            argb(255, r, g, b)
        }
    }

    /** Schriftgröße gegenüber der bisherigen: 94 % (Serifenschrift), G gedrückt zusätzlich 75 %. */
    fun textScale(style: TouchStyle, pressed: Boolean): Float = 0.94f * symbolScale(style, pressed)

    fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        (a.coerceIn(0, 255) shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    /** RGB (0xRRGGBB) mit Alpha zu ARGB. */
    fun withAlpha(rgb: Int, alpha: Int): Int = (alpha.coerceIn(0, 255) shl 24) or (rgb and 0xFFFFFF)

    /** Glättung des Joystick-Profils (Smoothstep), wie in der Vorschau. */
    fun smoothstep(a: Float, b: Float, x: Float): Float {
        val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** G-Joystick, Unterlage: Deckkraft in [0, 1] bei Abstand [r] (0 = Mitte, 1 = Rand der Unterlage), vor dem Kontrast. */
    fun softUnderlayAlpha(r: Float): Float = UNDERLAY_PEAK * (1f - smoothstep(0.15f, 1f, r))

    /** G-Joystick, warmer Schein: Deckkraft bei Abstand [r] (0 = Mitte, 1 = Rand des Scheins), vor dem Kontrast. */
    fun softGlowAlpha(r: Float): Float = GLOW_PEAK * Math.pow((1f - smoothstep(0f, 1f, r)).toDouble(), 0.75).toFloat()

    /** Dunkle Unterlage unter dem Joystick auf hellem Grund: 33 % (Vorgabe 30 bis 35 %). */
    const val UNDERLAY_PEAK = 0.33f
    const val GLOW_PEAK = 0.35f

    /** Platzhalter des ruhenden Joysticks: 20 % Deckkraft. */
    const val PLACEHOLDER_ALPHA = 51
}

/**
 * Ruhe-Timer (G): nach [restAfterMs] ohne Berührung eines Elements gehen die Elemente in die Ruhe-Werte; bei Berührung sofort
 * wieder aktiv (Übergang höchstens [upMs]). Ein Finger, der auf einem Element liegt, hält die Elemente aktiv. Reine Rechnung über
 * die Zeitstempel, die der Aufrufer liefert (nur der Zeichenthread ruft [update] auf; [touch] darf vom Berührungsthread kommen).
 * [level] ist 0 in Ruhe, 1 aktiv, dazwischen im Übergang.
 */
class TouchRestTimer(
    private val restAfterMs: Long = TouchStyleMath.REST_AFTER_MS,
    private val upMs: Long = TouchStyleMath.FADE_UP_MS,
    private val downMs: Long = TouchStyleMath.FADE_DOWN_MS,
) {
    @Volatile private var lastActivityMs = Long.MIN_VALUE / 2
    private var lastSampleMs = Long.MIN_VALUE / 2
    private var current = 0f

    /** Aktuelle Stufe (0..1) nach dem letzten [update]. */
    val level: Float get() = current

    /** Ein Element wurde berührt (oder losgelassen): ab jetzt zählen 4 s. */
    fun touch(nowMs: Long) {
        lastActivityMs = nowMs
    }

    /** Neue Spielsitzung: zunächst aktiv, dann nach 4 s Ruhe. */
    fun reset(nowMs: Long) {
        lastActivityMs = nowMs
        lastSampleMs = nowMs
        current = 1f
    }

    /**
     * Ein Bild wird gezeichnet: gibt die Stufe (0 = Ruhe, 1 = aktiv) zurück. [held]: ein Finger liegt auf einem Element, das zählt
     * als Berührung.
     */
    fun update(nowMs: Long, held: Boolean): Float {
        if (held) lastActivityMs = nowMs
        val target = if (nowMs - lastActivityMs < restAfterMs) 1f else 0f
        // Der Übergang zählt erst ab der Berührung bzw. ab Beginn der Ruhe, nicht ab dem letzten Bild (das kann lange her sein).
        val from = if (target > current) maxOf(lastSampleMs, lastActivityMs) else maxOf(lastSampleMs, lastActivityMs + restAfterMs)
        val dt = (nowMs - from).coerceAtLeast(0L)
        lastSampleMs = nowMs
        current = when {
            target > current -> (current + dt.toFloat() / upMs).coerceAtMost(target)
            target < current -> (current - dt.toFloat() / downMs).coerceAtLeast(target)
            else -> current
        }
        return current
    }

    /**
     * Wann muss wieder gezeichnet werden, damit der Übergang weiterläuft oder die Ruhe eintritt: 0 = sofort (im Übergang),
     * sonst Millisekunden bis zum Beginn der Ruhe, -1 = nichts nötig (in Ruhe, oder aktiv ohne Ablauf).
     */
    fun nextRedrawIn(nowMs: Long, held: Boolean): Long {
        val target = if (held || nowMs - lastActivityMs < restAfterMs) 1f else 0f
        if (target != current) return 0L
        // Ein gehaltener Finger braucht keine Zeitmarke: beim Loslassen kommt die Berührung ohnehin als Zeichenanstoß.
        if (held) return -1L
        if (current >= 1f) return (restAfterMs - (nowMs - lastActivityMs)).coerceAtLeast(1L)
        return -1L
    }
}

/**
 * Zeichenzeit je Bild: Mittelwert über [window] Bilder, zum Vorher/Nachher-Vergleich im Log. Reine Rechnung (Nanosekunden).
 */
class FrameTimeStats(private val window: Int = 300) {
    private var count = 0
    private var sumNs = 0L
    private var maxNs = 0L

    /** Ein Bild hat [ns] gebraucht. Gibt nach jedem vollen Fenster die Auswertung zurück (Mittel, Größtwert in ms), sonst null. */
    fun add(ns: Long): Summary? {
        count++
        sumNs += ns
        if (ns > maxNs) maxNs = ns
        if (count < window) return null
        val s = Summary(count, sumNs / 1_000_000.0 / count, maxNs / 1_000_000.0)
        count = 0
        sumNs = 0L
        maxNs = 0L
        return s
    }

    data class Summary(val frames: Int, val meanMs: Double, val maxMs: Double)
}
