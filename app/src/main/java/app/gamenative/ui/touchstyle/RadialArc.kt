package app.gamenative.ui.touchstyle

import com.winlator.inputcontrols.StickRing
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Das gemeinsame Layout aller Radialmenüs der App (Kontextmenü beim Halten, "WoW-Menü" und die übrigen Radialmenüs der Knöpfe):
 * echte Ringsegmente wie am Stick-Ring (`StickRing`, Außenband `ZONE_OUTER`) auf einem Bogen von [ARC_DEG] Grad, nach oben offen.
 * Unten bleibt ein Keil von 360 - [ARC_DEG] Grad frei (dort liegt die Hand). Der Bogenwinkel steht nur hier: wer ihn ändert, ändert ihn
 * für alle Menüs. Rein, ohne Android, getestet; gezeichnet wird in [RadialSegmentDraw].
 *
 * - Winkel in Grad, 0 = rechts, 90 = unten, wachsend im Uhrzeigersinn (wie `Path.arcTo`); der Bogen ist um -90 (oben) zentriert.
 * - Jeder Ring ist ein Band der Dicke [Geometry.thickness]; die Segmente eines Rings haben denselben Winkelabstand (Teilung), höchstens
 *   die als Kappung übergebene Teilung, sonst Bogen / Anzahl. Der erste Ring steht innen, jeder weitere liegt im Abstand
 *   [Geometry.ringGap] außen daran (wie das Außenband des Sticks mit seinem `OUTER_GAP`).
 * - Ein Ring ist nur so eng, dass ein Segment auf Mittenhöhe mindestens [Geometry.minSegmentWidth] breit bleibt (viele Einträge: größerer
 *   Radius).
 * - Reihenfolge: [Order.SEQUENTIAL] setzt Eintrag 0 an das linke Bogenende, die übrigen im Uhrzeigersinn dahinter (feste Menüs: die
 *   Reihenfolge der Belegung bleibt erhalten); [Order.IMPORTANCE] setzt den ersten Eintrag oben in die Mitte, die nächsten abwechselnd
 *   rechts und links davon (Kontextmenü: wichtig = oben).
 */
object RadialArc {
    /** Der Bogen aller Radialmenüs in Grad. 270 = unten bleibt ein Keil von 90 Grad frei. Zum Ausprobieren: 180, 220, 270. */
    const val ARC_DEG = 270f

    /** Mitte des Bogens ohne Drehung: oben. */
    const val CENTER_DEG = -90f

    /** Einträge je Ring bei der Standardaufteilung ([ringSizes]); mehr kommen auf einen weiteren Ring außen. */
    const val FIRST_RING_CAP = 8

    /** Größte Teilung beim Kontextmenü: wenige Einträge rücken nicht über den ganzen Bogen auseinander. */
    const val MAX_STEP_DEG = 45f

    /** Größte Teilung bei den festen Menüs (ein Viertel des Bogens): wenige Felder füllen den Bogen fast aus. */
    const val WIDE_STEP_DEG = ARC_DEG / 4f

    /** Sichtbare Lücke zwischen zwei Nachbarn in Grad (wie die Trennung der Segmente am Stick). */
    const val SEPARATION_DEG = StickRing.SEGMENT_SEPARATION_DEGREES

    /** Schrittweite der Drehung, wenn der Bogen am Bildrand gedreht werden muss. */
    const val ROTATION_STEP_DEG = 15f

    /** Maße in dp. */
    const val THICKNESS_DP = 58f
    const val MIN_SEGMENT_W_DP = 56f
    const val MIN_MID_DP = 80f
    const val RING_GAP_DP = 6f

    enum class Order { SEQUENTIAL, IMPORTANCE }

    data class Geometry(val thickness: Float, val minSegmentWidth: Float, val minMid: Float, val ringGap: Float) {
        companion object {
            @JvmStatic
            fun forDensity(density: Float): Geometry =
                Geometry(THICKNESS_DP * density, MIN_SEGMENT_W_DP * density, MIN_MID_DP * density, RING_GAP_DP * density)
        }
    }

    /**
     * Ein Segment: Eintrag [index], Ring [ring] (0 = innen), Anfangswinkel und Öffnung in Grad (für `Path.arcTo`), innerer und äußerer
     * Radius. Alles um den Mittelpunkt des Menüs.
     */
    data class Segment(
        val index: Int,
        val ring: Int,
        val startDeg: Float,
        val sweepDeg: Float,
        val inner: Float,
        val outer: Float,
    ) {
        val midDeg: Float get() = startDeg + sweepDeg / 2f
        val midRadius: Float get() = (inner + outer) / 2f
        fun midX(): Float = (midRadius * cos(Math.toRadians(midDeg.toDouble()))).toFloat()
        fun midY(): Float = (midRadius * sin(Math.toRadians(midDeg.toDouble()))).toFloat()
    }

    /** Ein Ring: die Einträge [first] bis [first] + [count] - 1, Radien und Teilung in Grad. */
    data class Ring(val first: Int, val count: Int, val inner: Float, val outer: Float, val pitchDeg: Float)

    data class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float)

    /** Das Ergebnis um (0, 0): ein Segment je Eintrag (in der Reihenfolge der Einträge), die Ringe, Drehung und Bogen. */
    class Shape(val segments: List<Segment>, val rings: List<Ring>, val rotationDeg: Float, val arcDeg: Float) {
        /** Äußerster Radius (0 ohne Segmente). */
        val outerRadius: Float get() = rings.lastOrNull()?.outer ?: 0f

        /** Das umschließende Rechteck aller Segmente um (0, 0). */
        fun bounds(): Bounds = RadialArc.boundsOf(segments)
    }

    /** Das umschließende Rechteck der [segments] um (0, 0); ohne Segmente ein leeres Rechteck bei (0, 0). */
    @JvmStatic
    fun boundsOf(segments: List<Segment>): Bounds {
        var l = Float.MAX_VALUE
        var t = Float.MAX_VALUE
        var r = -Float.MAX_VALUE
        var b = -Float.MAX_VALUE
        fun add(radius: Float, deg: Float) {
            val rad = Math.toRadians(deg.toDouble())
            val x = (radius * cos(rad)).toFloat()
            val y = (radius * sin(rad)).toFloat()
            l = min(l, x)
            r = max(r, x)
            t = min(t, y)
            b = max(b, y)
        }
        for (s in segments) {
            val end = s.startDeg + s.sweepDeg
            add(s.outer, s.startDeg)
            add(s.outer, end)
            add(s.inner, s.startDeg)
            add(s.inner, end)
            // die Achsenrichtungen, die das Segment überstreicht, bestimmen den äußersten Punkt des Bogens
            var axis = Math.ceil(s.startDeg / 90.0).toFloat() * 90f
            while (axis < end) {
                add(s.outer, axis)
                axis += 90f
            }
        }
        return if (segments.isEmpty()) Bounds(0f, 0f, 0f, 0f) else Bounds(l, t, r, b)
    }

    /** Abstand des Punktes ([dx], [dy]) (vom Mittelpunkt) zum Ringsegment [s]: 0 innerhalb, sonst bis zum nächsten Randpunkt. */
    @JvmStatic
    fun distanceTo(s: Segment, dx: Float, dy: Float): Float {
        val r = hypot(dx, dy)
        val phi = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        val mid = s.midDeg
        // Winkel des Punktes relativ zur Mitte des Segments, in (-180, 180]
        var rel = normalise(phi - mid)
        if (rel > 180f) rel -= 360f
        val half = s.sweepDeg / 2f
        val clampedRel = rel.coerceIn(-half, half)
        val clampedR = r.coerceIn(s.inner, s.outer)
        val nearestDeg = Math.toRadians((mid + clampedRel).toDouble())
        val nx = clampedR * cos(nearestDeg)
        val ny = clampedR * sin(nearestDeg)
        return hypot(dx - nx, dy - ny).toFloat()
    }

    /** Einträge je Ring für [count] Einträge: Ringe zu je [FIRST_RING_CAP], der letzte mit dem Rest. */
    @JvmStatic
    fun ringSizes(count: Int): List<Int> {
        if (count <= 0) return emptyList()
        val sizes = ArrayList<Int>()
        var left = count
        while (left > 0) {
            val take = min(FIRST_RING_CAP, left)
            sizes.add(take)
            left -= take
        }
        return sizes
    }

    /** Teilung (Winkelabstand der Nachbarn) für [n] Einträge auf dem Bogen [arcDeg], höchstens [maxStep]. */
    @JvmStatic
    fun pitchFor(n: Int, arcDeg: Float, maxStep: Float): Float = if (n <= 0) 0f else min(maxStep, arcDeg / n)

    /**
     * Die Winkel der Mitten der [n] Segmente eines Rings (Grad), in der Reihenfolge der Einträge. [centerDeg] ist die Mitte des Bogens
     * (-90 = oben). [Order.SEQUENTIAL]: von links nach rechts im Uhrzeigersinn. [Order.IMPORTANCE]: erster Eintrag am Platz der Mitte
     * (bei gerader Zahl rechts von der Mitte), dann abwechselnd links und rechts nach außen.
     */
    @JvmStatic
    fun slotAngles(n: Int, arcDeg: Float, centerDeg: Float, maxStep: Float, order: Order): List<Float> {
        if (n <= 0) return emptyList()
        val pitch = pitchFor(n, arcDeg, maxStep)
        val mid = (n - 1) / 2f
        val places = when (order) {
            Order.SEQUENTIAL -> (0 until n).toList()
            Order.IMPORTANCE -> (0 until n).sortedWith(compareBy<Int> { abs(it - mid) }.thenByDescending { it })
        }
        return places.map { p -> centerDeg + (p - mid) * pitch }
    }

    /** Die Ringgrößen, die [count] Einträge brauchen, wenn [ringCounts] nicht passt (Summe ungleich [count] oder ein Ring leer). */
    @JvmStatic
    fun validRingCounts(count: Int, ringCounts: List<Int>?): List<Int> {
        if (ringCounts != null && ringCounts.isNotEmpty() && ringCounts.all { it > 0 } && ringCounts.sum() == count) return ringCounts
        return ringSizes(count)
    }

    /**
     * Das Layout um (0, 0). [ringCounts]: Einträge je Ring von innen nach außen (null = [ringSizes]); [rotationDeg]: Drehung des Bogens
     * aus der Grundlage "offen nach unten, Mitte oben".
     */
    @JvmStatic
    @JvmOverloads
    fun build(
        count: Int,
        geometry: Geometry,
        order: Order,
        maxStep: Float,
        arcDeg: Float = ARC_DEG,
        rotationDeg: Float = 0f,
        ringCounts: List<Int>? = null,
    ): Shape {
        if (count <= 0) return Shape(emptyList(), emptyList(), rotationDeg, arcDeg)
        val sizes = validRingCounts(count, ringCounts)
        val centerDeg = CENTER_DEG + rotationDeg
        val segments = ArrayList<Segment>(count)
        val rings = ArrayList<Ring>(sizes.size)
        var first = 0
        var previousOuter = 0f
        for ((k, n) in sizes.withIndex()) {
            val pitch = pitchFor(n, arcDeg, maxStep)
            val sweep = max(1f, pitch - SEPARATION_DEG)
            // Mittenradius: so, dass das Segment auf Mittenhöhe breit genug ist, und hinter dem Ring davor.
            val halfSweep = Math.toRadians((sweep / 2f).toDouble())
            val forWidth = (geometry.minSegmentWidth / (2.0 * sin(min(halfSweep, Math.PI / 2.0)))).toFloat()
            val behind = if (k == 0) geometry.minMid else previousOuter + geometry.ringGap + geometry.thickness / 2f
            val mid = max(forWidth, behind)
            val inner = mid - geometry.thickness / 2f
            val outer = mid + geometry.thickness / 2f
            val angles = slotAngles(n, arcDeg, centerDeg, maxStep, order)
            for (i in 0 until n) {
                segments.add(Segment(first + i, k, angles[i] - sweep / 2f, sweep, inner, outer))
            }
            rings.add(Ring(first, n, inner, outer, pitch))
            first += n
            previousOuter = outer
        }
        return Shape(segments, rings, rotationDeg, arcDeg)
    }

    /** Winkel in [0, 360). */
    @JvmStatic
    fun normalise(deg: Float): Float {
        var d = deg % 360f
        if (d < 0f) d += 360f
        return d
    }

    /** Kleinster Winkelabstand zweier Richtungen (0..180). */
    @JvmStatic
    fun angleDiff(a: Float, b: Float): Float {
        val d = abs(normalise(a) - normalise(b))
        return if (d > 180f) 360f - d else d
    }

    /**
     * Index des Eintrags in Richtung und Abstand ([dx], [dy]) vom Mittelpunkt, sonst -1. Innerhalb von [dead] nichts. Je Ring zählt der
     * Eintrag, dessen Mitte der Richtung am nächsten liegt (höchstens eine halbe Teilung daneben: der freie Keil unten trifft also
     * nichts). Von diesen gewinnt der Ring, dessen Band dem Punkt am nächsten liegt: im Band selbst gewinnt es, im Loch innen der
     * innerste Ring, außerhalb des äußersten Bandes noch bis zu einer Banddicke weit.
     */
    @JvmStatic
    fun indexAt(shape: Shape, dx: Float, dy: Float, dead: Float): Int {
        if (shape.segments.isEmpty()) return -1
        val r = hypot(dx, dy)
        if (r < dead) return -1
        val phi = normalise(Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat())
        var found = -1
        var foundDistance = Float.MAX_VALUE
        for (ring in shape.rings) {
            var near = -1
            var nearDiff = Float.MAX_VALUE
            for (i in ring.first until ring.first + ring.count) {
                val diff = angleDiff(phi, shape.segments[i].midDeg)
                if (diff < nearDiff) {
                    nearDiff = diff
                    near = i
                }
            }
            if (near < 0 || nearDiff > ring.pitchDeg / 2f + 0.001f) continue
            val thickness = ring.outer - ring.inner
            val distance = when {
                r < ring.inner -> ring.inner - r
                r > ring.outer -> r - ring.outer
                else -> 0f
            }
            if (r > ring.outer + thickness) continue
            if (distance < foundDistance) {
                foundDistance = distance
                found = near
            }
        }
        return found
    }

    /**
     * Wo Symbol und Name eines Segments stehen (alles um den Mittelpunkt des Menüs): Symbol-Mitte, Symbolkante, Mitte der Grundlinie
     * des Namens und seine größte Breite. Das Symbol ist kleiner als das Segment, der Name steht klein darunter.
     */
    data class Content(val iconX: Float, val iconY: Float, val iconSize: Float, val labelX: Float, val labelBaseline: Float, val labelWidth: Float)

    @JvmStatic
    fun contentFor(segment: Segment, geometry: Geometry): Content {
        val x = segment.midX()
        val y = segment.midY()
        val thickness = segment.outer - segment.inner
        val chord = (2.0 * segment.midRadius * sin(Math.toRadians((segment.sweepDeg / 2f).toDouble()))).toFloat()
        val iconSize = min(thickness * ICON_FRACTION, chord * 0.6f)
        return Content(
            iconX = x,
            iconY = y - thickness * 0.10f,
            iconSize = iconSize,
            labelX = x,
            labelBaseline = y + thickness * 0.34f,
            labelWidth = max(geometry.minSegmentWidth * 0.8f, min(chord, thickness * 1.25f)),
        )
    }

    /** Symbolgröße als Anteil der Banddicke: kleiner als das Segment (früher füllte das Symbol die Kachel). */
    const val ICON_FRACTION = 0.46f
}
