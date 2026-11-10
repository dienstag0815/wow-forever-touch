package app.gamenative.ui.screen.wow.context

import app.gamenative.ui.touchstyle.RadialArc
import app.gamenative.ui.touchstyle.RadialOpen
import app.gamenative.ui.touchstyle.RadialPlacement
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Ein Rechteck in Pixeln der Touch-Ansicht. */
data class MenuRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    fun centerX(): Float = (left + right) * 0.5f
    fun centerY(): Float = (top + bottom) * 0.5f

    fun contains(x: Float, y: Float, slop: Float = 0f): Boolean =
        x >= left - slop && x <= right + slop && y >= top - slop && y <= bottom + slop

    fun shifted(dx: Float, dy: Float): MenuRect = MenuRect(left + dx, top + dy, right + dx, bottom + dy)

    /** Fläche der Überdeckung mit [other] (0 ohne Überdeckung). */
    fun overlapArea(other: MenuRect): Float {
        val w = min(right, other.right) - max(left, other.left)
        val h = min(bottom, other.bottom) - max(top, other.top)
        return if (w > 0f && h > 0f) w * h else 0f
    }
}

/**
 * Anordnung des Kontextmenüs um den Druckpunkt (der Finger ruht in der Mitte): echte Ringsegmente wie am Stick-Ring auf einem Bogen von
 * [ARC_DEG] Grad, der nach oben offen ist; unten bleibt ein Keil frei, dort liegt die Hand und verdeckt sonst die Einträge. Die
 * Geometrie der Ringe steht in [RadialArc] und ist dieselbe wie bei allen anderen Radialmenüs der App; hier kommen Druckpunkt, Bildrand
 * und freizuhaltende Flächen dazu. Rein, getestet.
 *
 * - Die Segmente eines Rings haben denselben Winkelabstand (höchstens [MAX_STEP_DEG]). Der wichtigste Eintrag (der erste) liegt oben in der
 *   Mitte, die nächsten abwechselnd rechts und links davon: wichtig = innen und oben ([RadialArc.Order.IMPORTANCE]).
 * - Standardmäßig höchstens [FIRST_RING_CAP] Einträge je Ring; mehr kommen auf einen zweiten Ring außen darüber, wie das äußere Band am
 *   Stick (`StickRing.ZONE_OUTER`, getrennt durch eine Lücke). Wer die Ringe selbst aufteilen will (Mini-Menü: innen Aktionen, außen
 *   Zusätze), übergibt die Einträge je Ring.
 * - Die Mitte bleibt auf dem Druckpunkt, der Bogen dreht sich zur Bildschirmmitte hin ([RadialPlacement]): oben am Rand nach unten, am
 *   linken Rand nach rechts, am rechten nach links, in den Ecken diagonal, sonst nach oben. Verschoben wird der Mittelpunkt nur, wenn
 *   keine Richtung ganz auf den Bildschirm passt.
 * - Bereiche, die frei bleiben sollen (Zielfenster, Tooltip), werden gemieden, soweit das mit einer kleinen Verschiebung geht.
 * - Loslassen direkt am Druckpunkt ([Geometry.dead]) wählt nichts. Sonst trifft das Segment unter dem Finger oder, im Winkel dazwischen
 *   und etwas außerhalb, der Eintrag in dieser Richtung ([indexAt], siehe [RadialArc.indexAt]).
 */
object ContextMenuLayout {
    /** Maße in dp. */
    const val MARGIN_DP = 8f

    /** Um diesen Radius um den Druckpunkt wählt Loslassen nichts. */
    const val DEAD_DP = 34f

    /** So weit darf der Ring für freizuhaltende Flächen verschoben werden. */
    const val MAX_SHIFT_DP = 150f

    /** Nicht mehr benutzt (der Bogen dreht sich, bevor der Mittelpunkt wandert, siehe [RadialPlacement.choose]); bleibt für die Geometrie. */
    const val MAX_CENTER_SHIFT_DP = 130f

    /** Der Bogen in Grad: überall derselbe wie bei allen Radialmenüs ([RadialArc.ARC_DEG]); 270 = unten bleibt ein Keil von 90 Grad frei. */
    const val ARC_DEG = RadialArc.ARC_DEG

    /** Größter Winkelabstand zweier Nachbarn auf dem Bogen (wenige Einträge rücken nicht über den ganzen Bogen auseinander). */
    const val MAX_STEP_DEG = RadialArc.MAX_STEP_DEG

    /** Einträge je Ring bei der Standardaufteilung. */
    const val FIRST_RING_CAP = RadialArc.FIRST_RING_CAP

    /** Schrittweite der Drehung, wenn der Bogen am Bildrand gedreht werden muss. */
    const val ROTATION_STEP_DEG = RadialArc.ROTATION_STEP_DEG

    data class Geometry(
        val arc: RadialArc.Geometry,
        val margin: Float,
        val dead: Float,
        val maxShift: Float,
        val maxCenterShift: Float,
    ) {
        companion object {
            fun forDensity(density: Float): Geometry = Geometry(
                RadialArc.Geometry.forDensity(density), MARGIN_DP * density, DEAD_DP * density, MAX_SHIFT_DP * density,
                MAX_CENTER_SHIFT_DP * density,
            )
        }
    }

    /**
     * Das Ergebnis: die Ringsegmente ([shape], um den Mittelpunkt des Rings), Mittelpunkt, Druckpunkt. Die Drehung des Bogens aus der
     * Grundlage "offen nach unten" ([RadialArc.Shape.rotationDeg]; 0 = nicht gedreht) steht in der Form.
     */
    class Result(
        val shape: RadialArc.Shape,
        val centerX: Float,
        val centerY: Float,
        val anchorX: Float,
        val anchorY: Float,
    ) {
        val segments: List<RadialArc.Segment> get() = shape.segments
        val rotationDeg: Float get() = shape.rotationDeg
        val arcDeg: Float get() = shape.arcDeg

        /** Der Ring wurde vom Druckpunkt weg verschoben (Bildrand oder freizuhaltende Fläche). */
        val shifted: Boolean get() = centerX != anchorX || centerY != anchorY

        /** Das Rechteck, das ein Segment auf dem Bildschirm umschließt (Mittelpunkt eingerechnet). */
        fun rectOf(index: Int): MenuRect? {
            val seg = segments.getOrNull(index) ?: return null
            val b = RadialArc.boundsOf(listOf(seg))
            return MenuRect(b.left + centerX, b.top + centerY, b.right + centerX, b.bottom + centerY)
        }
    }

    /** Einträge je Ring für [count] Einträge: Ringe zu je [FIRST_RING_CAP], der letzte mit dem Rest. */
    @JvmStatic
    fun ringSizes(count: Int): List<Int> = RadialArc.ringSizes(count)

    /**
     * Flächen, die das Menü nach Möglichkeit nicht verdeckt: die Einheitenfenster oben links (Spieler, Ziel) und der Tooltip der
     * Einheit rechts davon, wie das Spiel sie standardmäßig anordnet (Anteile des Bildes, aus Bildschirmfotos der Entwicklung).
     */
    @JvmStatic
    fun defaultKeepClear(screenW: Float, screenH: Float): List<MenuRect> = listOf(
        MenuRect(0.03f * screenW, 0f, 0.47f * screenW, 0.15f * screenH),
        MenuRect(0.58f * screenW, 0.16f * screenH, 0.78f * screenW, 0.30f * screenH),
    )

    private fun shapeFor(count: Int, g: Geometry, arcDeg: Float, rotation: Float, ringCounts: List<Int>?): RadialArc.Shape =
        RadialArc.build(count, g.arc, RadialArc.Order.IMPORTANCE, MAX_STEP_DEG, arcDeg, rotation, ringCounts)

    private fun boundsRect(shape: RadialArc.Shape): MenuRect {
        val b = shape.bounds()
        return MenuRect(b.left, b.top, b.right, b.bottom)
    }

    /** Ob ein Segment (Mittelpunkt des Rings bei [cx], [cy]) näher als [dead] am Druckpunkt liegt. */
    private fun onFinger(shape: RadialArc.Shape, cx: Float, cy: Float, anchorX: Float, anchorY: Float, dead: Float): Boolean =
        shape.segments.any { RadialArc.distanceTo(it, anchorX - cx, anchorY - cy) < dead }

    @JvmStatic
    @JvmOverloads
    fun compute(
        count: Int,
        anchorX: Float,
        anchorY: Float,
        screenW: Float,
        screenH: Float,
        geometry: Geometry,
        keepClear: List<MenuRect> = emptyList(),
        arcDeg: Float = ARC_DEG,
        ringCounts: List<Int>? = null,
        direction: RadialOpen = RadialOpen.AUTO,
    ): Result {
        if (count <= 0) return Result(shapeFor(0, geometry, arcDeg, 0f, null), anchorX, anchorY, anchorX, anchorY)

        // Mitte auf dem Druckpunkt, Bogen zur Bildschirmmitte gedreht; verschoben nur, wenn keine Richtung passt.
        val choice = RadialPlacement.choose(
            { rotation -> shapeFor(count, geometry, arcDeg, rotation, ringCounts) },
            anchorX, anchorY, screenW, screenH, geometry.margin, geometry.dead, direction,
        )
        val layout = choice.shape
        val bounds = boundsRect(layout)
        var cx = choice.centerX
        var cy = choice.centerY

        if (keepClear.isNotEmpty()) {
            val rects = layout.segments.map { seg ->
                val b = RadialArc.boundsOf(listOf(seg))
                MenuRect(b.left, b.top, b.right, b.bottom)
            }
            var best = coverScore(layout, rects, cx, cy, anchorX, anchorY, keepClear, geometry)
            if (best > 0f) {
                val baseX = cx
                val baseY = cy
                var bestShift = 0f
                val step = max(6f, geometry.maxShift / 12f)
                var dy = -geometry.maxShift
                while (dy <= geometry.maxShift) {
                    var dx = -geometry.maxShift
                    while (dx <= geometry.maxShift) {
                        val t = fitCenter(bounds, anchorX + dx, anchorY + dy, screenW, screenH, geometry)
                        val shift = hypot(t.first - baseX, t.second - baseY)
                        // Nur Mittelpunkte, die den Ring nicht weiter als maxShift von der ersten Wahl wegrücken.
                        if (shift <= geometry.maxShift) {
                            val s = coverScore(layout, rects, t.first, t.second, anchorX, anchorY, keepClear, geometry)
                            // Weniger Überdeckung gewinnt; bei gleicher Überdeckung die kleinere Verschiebung.
                            if (s < best - 0.5f || (s <= best + 0.5f && shift < bestShift)) {
                                best = s
                                bestShift = shift
                                cx = t.first
                                cy = t.second
                            }
                        }
                        dx += step
                    }
                    dy += step
                }
            }
        }
        return Result(layout, cx, cy, anchorX, anchorY)
    }

    /**
     * Überdeckung der freizuhaltenden Flächen durch die Segmente (ihre umschließenden Rechtecke [rects], um ([x], [y]) verschoben); ein
     * Segment unter dem Finger zählt riesig.
     */
    private fun coverScore(
        shape: RadialArc.Shape,
        rects: List<MenuRect>,
        x: Float,
        y: Float,
        anchorX: Float,
        anchorY: Float,
        keepClear: List<MenuRect>,
        g: Geometry,
    ): Float {
        var area = 0f
        for (rect in rects) {
            val moved = rect.shifted(x, y)
            for (zone in keepClear) area += moved.overlapArea(zone)
        }
        return area + (if (onFinger(shape, x, y, anchorX, anchorY, g.dead)) 1e9f else 0f)
    }

    /** Der Mittelpunkt, der dem Wunsch ([wantX], [wantY]) am nächsten kommt und alle Segmente ([bounds]) im Bild (Rand [Geometry.margin]) lässt. */
    private fun fitCenter(bounds: MenuRect, wantX: Float, wantY: Float, screenW: Float, screenH: Float, g: Geometry): Pair<Float, Float> {
        val minCx = g.margin - bounds.left
        val maxCx = screenW - g.margin - bounds.right
        val minCy = g.margin - bounds.top
        val maxCy = screenH - g.margin - bounds.bottom
        // Passt der Ring nicht auf den Bildschirm (kleiner Schirm, viele Einträge), steht er in der Mitte.
        val x = if (minCx <= maxCx) wantX.coerceIn(minCx, maxCx) else screenW / 2f - (bounds.left + bounds.right) / 2f
        val y = if (minCy <= maxCy) wantY.coerceIn(minCy, maxCy) else screenH / 2f - (bounds.top + bounds.bottom) / 2f
        return Pair(x, y)
    }

    /**
     * Index des Eintrags unter dem Punkt, sonst -1.
     *
     * 1. Am Druckpunkt (innerhalb [Geometry.dead]) nichts: Loslassen ohne Bewegen löst nichts aus.
     * 2. Sonst [RadialArc.indexAt] um den Mittelpunkt des Rings: das Segment unter dem Punkt, im Winkel dazwischen (höchstens eine halbe
     *    Teilung daneben) und etwas außerhalb des Bandes der Eintrag in dieser Richtung; der freie Keil unten trifft nichts.
     */
    @JvmStatic
    fun indexAt(layout: Result, x: Float, y: Float, geometry: Geometry): Int {
        if (layout.segments.isEmpty()) return -1
        if (hypot(x - layout.anchorX, y - layout.anchorY) < geometry.dead) return -1
        return RadialArc.indexAt(layout.shape, x - layout.centerX, y - layout.centerY, 0f)
    }
}
