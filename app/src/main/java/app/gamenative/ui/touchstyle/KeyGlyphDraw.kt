package app.gamenative.ui.touchstyle

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.max

/**
 * Draws the labels of the touch keys: symbols from [KeyGlyphs] (as paths, built once, no allocation per frame), text and mixed
 * labels ("shift sign + 1"). One place for the font size rule ([TextFit]) in all styles. Only the draw thread calls (shared
 * helper objects). Logic without Android classes: [KeyGlyphs].
 *
 * Parameters used by all functions:
 * [diameter] short side of the element, [fitWidth] width the label may use, [maxSize] extra upper limit of the font size of the
 * caller (the old caps), [ring] ring and outer ring fields (smaller), [serif] styles F and G (serif font with outline through
 * [TouchStyleDraw.drawLabel]; the caller passes false for the current look and for emojis), [pressed] only matters for [serif].
 */
object KeyGlyphDraw {
    private const val REF = 48f
    private const val STROKE = 0.11f

    private class Shape(val stroke: Path, val fill: Path?)

    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val shapes: Array<Shape> by lazy { Array(Glyph.entries.size) { build(Glyph.entries[it]) } }

    // ---- Text and labels -----------------------------------------------------------------------------------------------

    /** Plain text, centered at ([cx], [cy]); font size by [TextFit], cut when even the smallest size does not fit. */
    @JvmStatic
    fun drawText(
        canvas: Canvas, paint: Paint, text: String?, cx: Float, cy: Float, diameter: Float, fitWidth: Float,
        maxSize: Float, ring: Boolean, color: Int, serif: Boolean, pressed: Boolean,
    ) {
        if (text == null || text.isEmpty() || fitWidth <= 0f) return
        var size = TextFit.size(diameter, fitWidth, unitWidth(paint, text, serif), ring, maxSize)
        var shown: String = text
        if (TextFit.needsCut(size, diameter, ring, maxSize)) {
            size = TextFit.minSize(diameter, ring, maxSize)
            val fixedSize = size
            shown = TextFit.cut(text, fitWidth) { unitWidth(paint, it, serif) * fixedSize }
        }
        if (size <= 0f) return
        paintText(canvas, paint, shown, cx, cy, size, color, serif, pressed)
    }

    /** A label with symbols (and maybe text pieces) centered at ([cx], [cy]). */
    @JvmStatic
    fun drawLabel(
        canvas: Canvas, paint: Paint, label: KeyLabel, cx: Float, cy: Float, diameter: Float, fitWidth: Float,
        maxSize: Float, ring: Boolean, color: Int, serif: Boolean, pressed: Boolean,
    ) {
        if (fitWidth <= 0f) return
        val parts = label.parts
        var units = 0f
        for (i in parts.indices) {
            if (i > 0) units += KeyGlyphs.PART_GAP
            val glyph = parts[i].glyph
            units += if (glyph != null) glyph.aspect * KeyGlyphs.GLYPH_HEIGHT else unitWidth(paint, parts[i].text, serif)
        }
        var size = TextFit.size(diameter, fitWidth, units, ring, maxSize)
        if (size <= 0f) return

        // Even the smallest size is too wide: the text pieces are cut, the symbols stay whole (rare, may allocate).
        var cutTexts: Array<String?>? = null
        if (label.hasText && TextFit.needsCut(size, diameter, ring, maxSize)) {
            size = TextFit.minSize(diameter, ring, maxSize)
            if (size <= 0f) return
            var fixedUnits = KeyGlyphs.PART_GAP * (parts.size - 1)
            var textPieces = 0
            for (part in parts) {
                val glyph = part.glyph
                if (glyph != null) fixedUnits += glyph.aspect * KeyGlyphs.GLYPH_HEIGHT else textPieces++
            }
            val perPiece = max(0f, fitWidth - fixedUnits * size) / max(1, textPieces)
            val fixedSize = size
            val cuts = arrayOfNulls<String>(parts.size)
            for (i in parts.indices) {
                if (parts[i].glyph == null) cuts[i] = TextFit.cut(parts[i].text, perPiece) { unitWidth(paint, it, serif) * fixedSize }
            }
            cutTexts = cuts
        }

        var total = KeyGlyphs.PART_GAP * (parts.size - 1) * size
        for (i in parts.indices) {
            total += partWidth(paint, parts[i], cutTexts?.get(i), size, serif)
        }
        val offset = if (serif) TouchStyleDraw.pressedLabelOffsetPx(pressed) else 0f
        val glyphSize = if (serif) size * TouchStyleDraw.symbolScale(pressed) else size
        val glyphColor = if (serif) TouchStyleDraw.textColor(pressed) else color
        var x = cx - total * 0.5f
        for (i in parts.indices) {
            val part = parts[i]
            val width = partWidth(paint, part, cutTexts?.get(i), size, serif)
            val glyph = part.glyph
            if (glyph != null) {
                drawGlyph(canvas, glyph, x + width * 0.5f + offset, cy + offset, glyphSize, glyphColor, serif)
            } else {
                paintText(canvas, paint, cutTexts?.get(i) ?: part.text, x + width * 0.5f, cy, size, color, serif, pressed)
            }
            x += width + KeyGlyphs.PART_GAP * size
        }
    }

    private fun partWidth(paint: Paint, part: LabelPart, cut: String?, size: Float, serif: Boolean): Float {
        val glyph = part.glyph
        return if (glyph != null) glyph.aspect * KeyGlyphs.GLYPH_HEIGHT * size else unitWidth(paint, cut ?: part.text, serif) * size
    }

    /** Width of [text] at font size 1. */
    private fun unitWidth(paint: Paint, text: String, serif: Boolean): Float {
        if (serif) paint.typeface = TouchStyleDraw.typeface()
        paint.textSize = REF
        val width = paint.measureText(text) / REF
        if (serif) paint.typeface = null
        return width
    }

    private fun paintText(canvas: Canvas, paint: Paint, text: String, cx: Float, cy: Float, size: Float, color: Int, serif: Boolean, pressed: Boolean) {
        if (serif) {
            TouchStyleDraw.drawLabel(canvas, text, cx, cy, size, pressed)
        } else {
            paint.textSize = size
            paint.textAlign = Paint.Align.CENTER
            paint.style = Paint.Style.FILL
            paint.color = color
            canvas.drawText(text, cx, cy - ((paint.descent() + paint.ascent()) * 0.5f), paint)
        }
    }

    // ---- Symbols -------------------------------------------------------------------------------------------------------

    /**
     * One symbol centered at ([cx], [cy]); [size] is the font size it stands next to (the symbol is [KeyGlyphs.GLYPH_HEIGHT]
     * of it high). [outlined]: dark contour like the serif labels of styles F and G.
     */
    @JvmStatic
    fun drawGlyph(canvas: Canvas, glyph: Glyph, cx: Float, cy: Float, size: Float, color: Int, outlined: Boolean) {
        val scale = size * KeyGlyphs.GLYPH_HEIGHT
        if (scale <= 1f) return
        val shape = shapes[glyph.ordinal]
        val p = glyphPaint
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(scale, scale)
        if (outlined) {
            val outline = 2f * TouchStyleDraw.labelOutlinePx() / scale
            p.color = TouchStyleMath.argb(235, 0, 0, 0)
            p.style = Paint.Style.STROKE
            p.strokeWidth = STROKE + outline
            canvas.drawPath(shape.stroke, p)
            if (shape.fill != null) {
                p.style = Paint.Style.FILL_AND_STROKE
                p.strokeWidth = outline
                canvas.drawPath(shape.fill, p)
            }
        }
        p.color = color
        p.style = Paint.Style.STROKE
        p.strokeWidth = STROKE
        canvas.drawPath(shape.stroke, p)
        if (shape.fill != null) {
            p.style = Paint.Style.FILL
            canvas.drawPath(shape.fill, p)
        }
        canvas.restore()
    }

    // ---- Shapes (unit box: height 1, width = Glyph.aspect, centered on 0, y down) --------------------------------------------

    private fun build(glyph: Glyph): Shape {
        val stroke = Path()
        var fill: Path? = null
        when (glyph) {
            Glyph.SPACE -> {
                // open box under the line, like the key cap sign
                stroke.moveTo(-0.7f, -0.17f)
                stroke.lineTo(-0.7f, 0.17f)
                stroke.lineTo(0.7f, 0.17f)
                stroke.lineTo(0.7f, -0.17f)
            }
            Glyph.TAB -> {
                stroke.moveTo(-0.58f, 0f)
                stroke.lineTo(0.32f, 0f)
                stroke.moveTo(0.06f, -0.28f)
                stroke.lineTo(0.34f, 0f)
                stroke.lineTo(0.06f, 0.28f)
                stroke.moveTo(0.58f, -0.34f)
                stroke.lineTo(0.58f, 0.34f)
            }
            Glyph.SHIFT -> {
                stroke.moveTo(0f, -0.42f)
                stroke.lineTo(-0.4f, 0.02f)
                stroke.lineTo(-0.19f, 0.02f)
                stroke.lineTo(-0.19f, 0.4f)
                stroke.lineTo(0.19f, 0.4f)
                stroke.lineTo(0.19f, 0.02f)
                stroke.lineTo(0.4f, 0.02f)
                stroke.close()
            }
            Glyph.ENTER -> {
                stroke.moveTo(0.4f, -0.34f)
                stroke.lineTo(0.4f, 0.1f)
                stroke.lineTo(-0.4f, 0.1f)
                stroke.moveTo(-0.14f, -0.16f)
                stroke.lineTo(-0.4f, 0.1f)
                stroke.lineTo(-0.14f, 0.36f)
            }
            Glyph.BACKSPACE -> {
                stroke.moveTo(-0.62f, 0f)
                stroke.lineTo(-0.28f, -0.36f)
                stroke.lineTo(0.6f, -0.36f)
                stroke.lineTo(0.6f, 0.36f)
                stroke.lineTo(-0.28f, 0.36f)
                stroke.close()
                stroke.moveTo(-0.02f, -0.14f)
                stroke.lineTo(0.3f, 0.14f)
                stroke.moveTo(0.3f, -0.14f)
                stroke.lineTo(-0.02f, 0.14f)
            }
            Glyph.ARROW_UP -> arrow(stroke, 0f, 0f, 0)
            Glyph.ARROW_RIGHT -> arrow(stroke, 0f, 0f, 1)
            Glyph.ARROW_DOWN -> arrow(stroke, 0f, 0f, 2)
            Glyph.ARROW_LEFT -> arrow(stroke, 0f, 0f, 3)
            Glyph.MOUSE_LEFT, Glyph.MOUSE_RIGHT, Glyph.MOUSE_MIDDLE -> {
                mouseBody(stroke, 0f, withCenterLine = glyph != Glyph.MOUSE_MIDDLE)
                fill = when (glyph) {
                    Glyph.MOUSE_LEFT -> mouseButton(0f, left = true)
                    Glyph.MOUSE_RIGHT -> mouseButton(0f, left = false)
                    else -> mouseWheel(0f)
                }
            }
            Glyph.WHEEL_UP, Glyph.WHEEL_DOWN -> {
                mouseBody(stroke, -0.2f, withCenterLine = false)
                fill = mouseWheel(-0.2f)
                arrow(stroke, 0.34f, 0f, if (glyph == Glyph.WHEEL_UP) 0 else 2, 0.3f, 0.17f)
            }
        }
        return Shape(stroke, fill)
    }

    /** Arrow in [direction] 0 up, 1 right, 2 down, 3 left; centered on ([cx], [cy]), [halfLength] shaft, [halfWidth] head wings. */
    private fun arrow(path: Path, cx: Float, cy: Float, direction: Int, halfLength: Float = 0.38f, halfWidth: Float = 0.27f) {
        val ux = if (direction == 1) 1f else if (direction == 3) -1f else 0f
        val uy = if (direction == 0) -1f else if (direction == 2) 1f else 0f
        val vx = -uy
        val vy = ux
        val tipX = cx + ux * halfLength
        val tipY = cy + uy * halfLength
        val backX = tipX - ux * halfWidth
        val backY = tipY - uy * halfWidth
        path.moveTo(cx - ux * halfLength, cy - uy * halfLength)
        path.lineTo(tipX, tipY)
        path.moveTo(backX + vx * halfWidth, backY + vy * halfWidth)
        path.lineTo(tipX, tipY)
        path.lineTo(backX - vx * halfWidth, backY - vy * halfWidth)
    }

    private const val MOUSE_HALF_WIDTH = 0.28f
    private const val MOUSE_TOP = -0.45f
    private const val MOUSE_BOTTOM = 0.45f
    private const val MOUSE_SPLIT = -0.06f

    private fun mouseBody(path: Path, mx: Float, withCenterLine: Boolean) {
        val r = RectF(mx - MOUSE_HALF_WIDTH, MOUSE_TOP, mx + MOUSE_HALF_WIDTH, MOUSE_BOTTOM)
        path.addRoundRect(r, 0.26f, 0.26f, Path.Direction.CW)
        path.moveTo(mx - MOUSE_HALF_WIDTH, MOUSE_SPLIT)
        path.lineTo(mx + MOUSE_HALF_WIDTH, MOUSE_SPLIT)
        if (withCenterLine) {
            path.moveTo(mx, MOUSE_TOP)
            path.lineTo(mx, MOUSE_SPLIT)
        }
    }

    /** The pressed button of the mouse: the upper left or right quarter of the body. */
    private fun mouseButton(mx: Float, left: Boolean): Path {
        val body = Path()
        body.addRoundRect(RectF(mx - MOUSE_HALF_WIDTH, MOUSE_TOP, mx + MOUSE_HALF_WIDTH, MOUSE_BOTTOM), 0.26f, 0.26f, Path.Direction.CW)
        val half = Path()
        if (left) half.addRect(mx - MOUSE_HALF_WIDTH, MOUSE_TOP, mx, MOUSE_SPLIT, Path.Direction.CW)
        else half.addRect(mx, MOUSE_TOP, mx + MOUSE_HALF_WIDTH, MOUSE_SPLIT, Path.Direction.CW)
        val result = Path()
        // If the path operation fails the whole half is filled, which still shows the side.
        if (!result.op(body, half, Path.Op.INTERSECT)) return half
        return result
    }

    /** The wheel of the mouse as a small filled pill in the upper part. */
    private fun mouseWheel(mx: Float): Path {
        val wheel = Path()
        wheel.addRoundRect(RectF(mx - 0.06f, -0.37f, mx + 0.06f, -0.15f), 0.06f, 0.06f, Path.Direction.CW)
        return wheel
    }
}
