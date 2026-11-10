package app.gamenative.ui.screen.wow.klick

import app.gamenative.R
import app.gamenative.ui.components.AppText
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import app.gamenative.ui.touchstyle.RadialArc
import app.gamenative.ui.touchstyle.RadialSegmentDraw
import app.gamenative.ui.touchstyle.TouchRole
import app.gamenative.ui.touchstyle.TouchStyle
import app.gamenative.ui.touchstyle.TouchStyleMath
import app.gamenative.ui.touchstyle.TouchStyleSettings

/**
 * Draws the click wheel around the finger in the one look of all radial menus: four ring segments for the sides (as at the stick ring
 * and the other radial menus, [RadialSegmentDraw]) and a round middle field. The fields and their hit areas are unchanged
 * ([ClickWheel.fieldAt]: the four sides split at the diagonals, the middle inside [ClickWheel.Geometry.innerRadius]). One instance per
 * view, reused every frame.
 */
class ClickWheelPainter(private val density: Float, scaledDensity: Float) {
    private val frame = RadialSegmentDraw()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = LABEL_SP * scaledDensity
        color = Color.WHITE
    }
    private val caption = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 14f * scaledDensity
        isFakeBoldText = true
        color = Color.rgb(255, 214, 120)
        setShadowLayer(4f * density, 0f, 0f, Color.BLACK)
    }
    private val labelSize = LABEL_SP * scaledDensity
    private val minLabelSize = MIN_LABEL_SP * scaledDensity

    private var cachedInner = -1f
    private var cachedOuter = -1f
    private var sides: List<Pair<WheelDirection, RadialArc.Segment>> = emptyList()

    /** The current assignment to draw; the caller sets it from the stored settings. Default: standard assignment. */
    var fields: List<ClickWheelField> = ClickWheel.FIELDS

    fun draw(canvas: Canvas, cx: Float, cy: Float, geometry: ClickWheel.Geometry, selected: ClickWheelField?) {
        val style = TouchStyleSettings.style
        val outer = geometry.outerRadius
        val inner = geometry.innerRadius
        val selectedDirection = selected?.direction
        val primaryRgb = 0xFFFFFF

        for ((direction, segment) in sidesFor(inner, outer)) {
            val active = direction == selectedDirection
            frame.drawFrame(canvas, cx, cy, segment, active, 1f, density, primaryRgb)
            val field = fields.firstOrNull { it.direction == direction } ?: continue
            drawLabel(canvas, field.label, cx + segment.midX(), cy + segment.midY(), direction, outer - inner, active, style)
        }

        // The middle field: a round plate like the middle of the other radial menus; gold edge when the finger rests on it.
        val centreActive = selectedDirection == WheelDirection.CENTER
        val plate = if (centreActive) RadialSegmentDraw.chipFill(style)
        else if (style == TouchStyle.CURRENT) IDLE_FILL else TouchStyleMath.fillRgb(style, TouchRole.ROUND, false)
        fill.color = TouchStyleMath.withAlpha(plate, if (centreActive) 235 else 205)
        canvas.drawCircle(cx, cy, inner - GAP_DP * density * 0.5f, fill)
        edge.strokeWidth = (if (centreActive) 2.4f else 1.4f) * density
        edge.color = TouchStyleMath.withAlpha(
            if (centreActive) RadialSegmentDraw.accent(style) else edgeRgb(style),
            if (centreActive) 255 else 200,
        )
        canvas.drawCircle(cx, cy, inner - GAP_DP * density * 0.5f, edge)

        // Name of the field under the finger above the circle; the finger hides the centre label.
        val text = selected?.label ?: AppText.get(R.string.game_cancel)
        canvas.drawText(text, cx, cy - outer - 8f * density, caption)
    }

    private fun edgeRgb(style: TouchStyle): Int = when (style) {
        TouchStyle.CURRENT -> 0xFFFFFF
        TouchStyle.F -> TouchStyleMath.F_EDGE_LIGHT
        TouchStyle.G -> TouchStyleMath.G_BRONZE
    }

    /** The four side segments (one ring, 90 degrees each minus the usual gap) for the radii; rebuilt only when the radii change. */
    private fun sidesFor(inner: Float, outer: Float): List<Pair<WheelDirection, RadialArc.Segment>> {
        if (inner != cachedInner || outer != cachedOuter) {
            cachedInner = inner
            cachedOuter = outer
            val sweep = 90f - RadialArc.SEPARATION_DEG
            sides = SIDE_ANGLES.mapIndexed { index, (direction, mid) ->
                direction to RadialArc.Segment(index, 0, mid - sweep / 2f, sweep, inner, outer)
            }
        }
        return sides
    }

    /** The name of a field on its segment; shrunk so it stays inside the band (sides) or the chord (top, bottom). */
    private fun drawLabel(canvas: Canvas, text: String, x: Float, y: Float, direction: WheelDirection, band: Float, active: Boolean, style: TouchStyle) {
        label.color = RadialSegmentDraw.textColor(style, active)
        label.isFakeBoldText = active
        val maxWidth = if (direction == WheelDirection.LEFT || direction == WheelDirection.RIGHT) band * 0.92f else band * 1.15f
        var size = labelSize
        label.textSize = size
        while (label.measureText(text) > maxWidth && size > minLabelSize) {
            size -= 0.5f * density
            label.textSize = size
        }
        canvas.drawText(text, x, y - (label.descent() + label.ascent()) * 0.5f, label)
    }

    private companion object {
        const val LABEL_SP = 12f
        const val MIN_LABEL_SP = 8f
        const val GAP_DP = 4f
        const val IDLE_FILL = 0x0A101C

        /** Middle angle of each side segment (0 = right, 90 = down): the quadrants of [ClickWheel.fieldAt]. */
        val SIDE_ANGLES: List<Pair<WheelDirection, Float>> = listOf(
            WheelDirection.UP to -90f,
            WheelDirection.RIGHT to 0f,
            WheelDirection.DOWN to 90f,
            WheelDirection.LEFT to 180f,
        )
    }
}
