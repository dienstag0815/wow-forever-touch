package app.gamenative.ui.touchstyle

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * Zeichnet die Rahmen der Ringsegmente aller Radialmenüs (Kontextmenü, "WoW-Menü" und die übrigen Radialmenüs) im gewählten Stil der
 * Touch-Elemente, damit es ein Design gibt: Aktuell (dunkle Fläche mit weißem Rand, der Auswahlton ist das Gold des Sticks),
 * F und G (Rahmen aus [TouchStyleDraw.drawSegment], derselbe Baustein wie die Segmente des Stick-Rings). Die Anordnung kommt aus
 * [RadialArc]. Eine Instanz je Ansicht, Hauptthread.
 */
class RadialSegmentDraw {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()
    private val outerRect = RectF()
    private val innerRect = RectF()
    private val layerRect = RectF()

    /**
     * Rahmen des Segments [s] um den Mittelpunkt ([cx], [cy]). [selected]: der Finger liegt darauf (Hervorhebung). [faded]: 1 = voll,
     * kleiner = abgedunkelt (nicht möglich oder kaum sinnvoll). [density] bestimmt die Kantenbreite im Stil Aktuell. [edgeRgb]:
     * Farbe des Randes im Stil Aktuell (-1 = weiß), die Stile F und G haben ihre eigenen.
     */
    fun drawFrame(canvas: Canvas, cx: Float, cy: Float, s: RadialArc.Segment, selected: Boolean, faded: Float, density: Float, edgeRgb: Int = -1) {
        val style = TouchStyleSettings.style
        val layer = faded < 0.999f
        if (layer) {
            val b = RadialArc.boundsOf(listOf(s))
            layerRect.set(cx + b.left - 4f * density, cy + b.top - 4f * density, cx + b.right + 4f * density, cy + b.bottom + 4f * density)
            canvas.saveLayerAlpha(layerRect, (255 * faded).toInt().coerceIn(0, 255))
        }
        if (style != TouchStyle.CURRENT) {
            TouchStyleDraw.drawSegment(canvas, cx, cy, s.inner, s.outer, s.startDeg, s.sweepDeg, selected, selected, 0)
        } else {
            sector(cx, cy, s)
            val contrast = TouchStyleSettings.contrast
            fill.color = TouchStyleMath.withAlpha(
                if (selected) SELECTED_FILL else IDLE_FILL,
                TouchStyleMath.scaleAlpha(if (selected) 235 else 205, contrast),
            )
            canvas.drawPath(path, fill)
            edge.strokeWidth = (if (selected) 2.4f else 1.3f) * density
            edge.color = TouchStyleMath.withAlpha(
                if (selected) GOLD else (if (edgeRgb >= 0) edgeRgb else 0xFFFFFF),
                TouchStyleMath.scaleAlpha(if (selected) 255 else 170, contrast),
            )
            canvas.drawPath(path, edge)
        }
        if (layer) canvas.restore()
    }

    private fun sector(cx: Float, cy: Float, s: RadialArc.Segment) {
        outerRect.set(cx - s.outer, cy - s.outer, cx + s.outer, cy + s.outer)
        innerRect.set(cx - s.inner, cy - s.inner, cx + s.inner, cy + s.inner)
        path.reset()
        path.arcTo(outerRect, s.startDeg, s.sweepDeg)
        path.arcTo(innerRect, s.startDeg + s.sweepDeg, -s.sweepDeg)
        path.close()
    }

    companion object {
        private const val IDLE_FILL = 0x0A101C
        private const val SELECTED_FILL = 0x3A2E10
        private const val GOLD = 0xF0BE46

        /** Auswahlton (RGB) im gewählten Stil: Gold im Stil Aktuell, sonst der gedrückte Rand des Stils. */
        @JvmStatic
        fun accent(style: TouchStyle): Int = when (style) {
            TouchStyle.CURRENT -> GOLD
            else -> TouchStyleMath.pressedRingRgb(style)
        }

        /** Schriftfarbe der Beschriftung (ARGB): weiß im Stil Aktuell, sonst die Schriftfarbe des Stils. */
        @JvmStatic
        fun textColor(style: TouchStyle, selected: Boolean): Int =
            if (style == TouchStyle.CURRENT) 0xFFFFFFFF.toInt() else TouchStyleMath.textColor(style, selected)

        /** Füllung (RGB) einer Namensschild-Fläche im gewählten Stil, ausgewählt. */
        @JvmStatic
        fun chipFill(style: TouchStyle): Int = when (style) {
            TouchStyle.CURRENT -> SELECTED_FILL
            else -> TouchStyleMath.fillRgb(style, TouchRole.SLOT, true)
        }
    }
}
