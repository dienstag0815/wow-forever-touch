package app.gamenative.ui.touchstyle

import app.gamenative.ui.components.AppText
import app.gamenative.R
import androidx.annotation.StringRes
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max

/**
 * Wohin sich der Bogen eines Radialmenüs öffnet: die Richtung der Mitte des Bogens (der freie Keil liegt auf der Gegenseite, dort wo
 * der Finger oder der Bildrand ist). [AUTO] wählt nach dem Bildschirmbereich, die übrigen sind fest (Einstellung "Öffnet nach" am
 * Knopf). [id] steht im Profil (JSON-Feld `radialDirection`, fehlt bei [AUTO]).
 */
enum class RadialOpen(val id: String?, @StringRes val labelRes: Int, val rotationDeg: Float?) {
    AUTO(null, R.string.game_ctx_auto, null),
    UP("up", R.string.game_open_up, 0f),
    DOWN("down", R.string.game_open_down, 180f),
    LEFT("left", R.string.game_open_left, -90f),
    RIGHT("right", R.string.game_open_right, 90f),
    ;


    /** The name in the current app language. */
    val label: String get() = AppText.get(labelRes)

    companion object {
        /** Die Richtung zur gespeicherten [id]; fehlend oder unbekannt = [AUTO] (alte Profile). */
        @JvmStatic
        fun fromId(id: String?): RadialOpen {
            for (o in values()) if (o.id != null && o.id == id) return o
            return AUTO
        }
    }
}

/**
 * Wahl von Drehung und Mittelpunkt eines Radialmenüs am Bildschirm, rein und getestet (ohne Android). Die Mitte bleibt auf dem
 * Druckpunkt (Knopf, Finger); der Bogen dreht sich zur Bildschirmmitte hin: oben am Rand nach unten, am linken Rand nach rechts, am
 * rechten nach links, in den Ecken diagonal (auf 45 Grad gerastert), sonst nach oben. Der Mittelpunkt wird nur verschoben, wenn keine
 * Richtung auf den Bildschirm passt. Gemeinsam für das Kontextmenü ([app.gamenative.ui.screen.wow.context.ContextMenuLayout]) und die
 * Radialmenüs der Knöpfe.
 */
object RadialPlacement {
    /** So viel Verschiebung gilt noch als "passt ohne Verschieben" (Pixel). */
    const val FIT_EPS = 0.5f

    /** Die acht Hauptrichtungen als Drehung (0 = nach oben offen, 90 = nach rechts, 180 = nach unten, -90 = nach links, dazwischen diagonal). */
    private val MAIN: List<Float> = (0 until 8).map { signed(it * 45f) }

    /** Die Zwischenstufen in 15-Grad-Schritten, nur als Ausweg, wenn alle Hauptrichtungen den Finger verdecken würden. */
    private val FINE: List<Float> = (0 until 24).filter { it % 3 != 0 }.map { signed(it * 15f) }

    /** Das Ergebnis: die gedrehte Form ([RadialArc.Shape.rotationDeg]), ihr Mittelpunkt auf dem Bildschirm und die Verschiebung vom Druckpunkt. */
    class Choice(val shape: RadialArc.Shape, val centerX: Float, val centerY: Float, val shift: Float)

    /** Winkel in (-180, 180]. */
    @JvmStatic
    fun signed(deg: Float): Float {
        var d = RadialArc.normalise(deg)
        if (d > 180f) d -= 360f
        return d
    }

    /**
     * Die Drehung für [RadialOpen.AUTO] am Druckpunkt ([anchorX], [anchorY]): Zeigt der Rand näher als [reach] (die Reichweite des
     * Menüs vom Mittelpunkt), schiebt er den Bogen von sich weg, zur Bildschirmmitte hin; die Richtung wird auf 45 Grad gerastert.
     * Ohne Rand in Reichweite: 0 (nach oben).
     */
    @JvmStatic
    fun autoRotation(anchorX: Float, anchorY: Float, screenW: Float, screenH: Float, reach: Float): Float {
        val pushX = max(0f, reach - anchorX) - max(0f, reach - (screenW - anchorX))
        val pushY = max(0f, reach - anchorY) - max(0f, reach - (screenH - anchorY))
        if (abs(pushX) < 1f && abs(pushY) < 1f) return 0f
        // Richtung zur Bildschirmmitte hin: 0 = rechts, 90 = unten (wie die Winkel des Bogens)
        val towards = Math.toDegrees(atan2(pushY.toDouble(), pushX.toDouble())).toFloat()
        val snapped = Math.round(towards / 45f) * 45f
        return signed(snapped - RadialArc.CENTER_DEG)
    }

    /** Der Mittelpunkt, der dem Wunsch ([wantX], [wantY]) am nächsten kommt und alle Segmente ([b], um (0, 0)) im Bild (Rand [margin]) lässt. */
    @JvmStatic
    fun fitCenter(b: RadialArc.Bounds, wantX: Float, wantY: Float, screenW: Float, screenH: Float, margin: Float): Pair<Float, Float> {
        val minCx = margin - b.left
        val maxCx = screenW - margin - b.right
        val minCy = margin - b.top
        val maxCy = screenH - margin - b.bottom
        // Passt der Ring nicht auf den Bildschirm (kleiner Schirm, viele Einträge), steht er in der Mitte.
        val x = if (minCx <= maxCx) wantX.coerceIn(minCx, maxCx) else screenW / 2f - (b.left + b.right) / 2f
        val y = if (minCy <= maxCy) wantY.coerceIn(minCy, maxCy) else screenH / 2f - (b.top + b.bottom) / 2f
        return Pair(x, y)
    }

    /**
     * Die Richtungen in der Reihenfolge der Vorliebe: die acht Hauptrichtungen nach Abstand zur gewünschten Drehung [preferred], danach
     * die Zwischenstufen. Die ersten acht gehören zusammen (siehe [choose]).
     */
    @JvmStatic
    fun candidates(preferred: Float): List<Float> {
        val byPreference = compareBy<Float>({ RadialArc.angleDiff(it, preferred) }, { it })
        return MAIN.sortedWith(byPreference) + FINE.sortedWith(byPreference)
    }

    /**
     * Wählt Drehung und Mittelpunkt. [buildShape] baut die Form zu einer Drehung (um (0, 0)). Der Druckpunkt ([anchorX], [anchorY]) soll
     * der Mittelpunkt bleiben:
     * 1. Die Hauptrichtungen werden in der Reihenfolge ihrer Vorliebe geprüft ([candidates]; bei [RadialOpen.AUTO] nach
     *    [autoRotation], sonst die feste Richtung zuerst). Die erste, die ohne Verschieben ganz auf den Bildschirm passt (Rand [margin])
     *    und den Finger frei lässt ([dead]), gewinnt. Eine feste Richtung gilt also, außer sie liefe aus dem Bild: dann die nächste,
     *    die passt.
     * 2. Passt keine, gewinnt die mit der kleinsten Verschiebung (jeder Schritt Abstand zur Vorliebe zählt ein wenig als Verschiebung,
     *    bei fester Richtung mehr). Richtungen, die nach der Verschiebung den Finger verdecken, kommen erst danach, die
     *    Zwischenstufen vor ihnen.
     */
    @JvmStatic
    fun choose(
        buildShape: (Float) -> RadialArc.Shape,
        anchorX: Float,
        anchorY: Float,
        screenW: Float,
        screenH: Float,
        margin: Float,
        dead: Float,
        direction: RadialOpen,
    ): Choice {
        val reference = buildShape(0f)
        val preferred = direction.rotationDeg ?: autoRotation(anchorX, anchorY, screenW, screenH, reference.outerRadius + margin)
        val biasPerStep = reference.outerRadius * (if (direction == RadialOpen.AUTO) 0.05f else 0.2f)
        val order = candidates(preferred)
        var best: Choice? = null
        var bestScore = Float.MAX_VALUE
        for ((i, rotation) in order.withIndex()) {
            val shape = if (rotation == 0f) reference else buildShape(rotation)
            val center = fitCenter(shape.bounds(), anchorX, anchorY, screenW, screenH, margin)
            val shift = hypot(center.first - anchorX, center.second - anchorY)
            val blocked = shape.segments.any { RadialArc.distanceTo(it, anchorX - center.first, anchorY - center.second) < dead }
            if (!blocked && shift <= FIT_EPS) return Choice(shape, center.first, center.second, shift)
            val tier = if (blocked) 2f else if (i < MAIN.size) 0f else 1f
            val steps = RadialArc.angleDiff(rotation, preferred) / 45f
            val score = tier * 1e6f + shift + steps * biasPerStep
            if (score < bestScore) {
                bestScore = score
                best = Choice(shape, center.first, center.second, shift)
            }
        }
        return best ?: Choice(reference, anchorX, anchorY, 0f)
    }
}
