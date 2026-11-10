package app.gamenative.cooldown

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import app.gamenative.wowicons.ActionSlotMap
import kotlin.math.hypot

/**
 * Draws the cooldown of an action bar slot over the symbol of a button, ring segment or wheel field: a dark sector for the
 * time that is left (the lit part grows clockwise from 12 o'clock, as in WoW), the remaining time as text, and a red or blue
 * tint for "out of range" / "not enough resource". Main thread (onDraw) only: the helper objects are shared.
 */
object CooldownDraw {
    const val KIND_RECT = 0
    const val KIND_ROUND = 1
    const val KIND_OVAL = 2

    /** Below this side length (pixels) the number is left out; the sector alone is shown. */
    private const val MIN_TEXT_SIDE = 22f

    private const val VEIL_COLOR = -0x58000000 // 0xA8000000
    private const val RANGE_TINT = 0x66E02020
    private const val RESOURCE_TINT = 0x664060FF

    private val veilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = VEIL_COLOR }
    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val rect = RectF()
    private val circle = RectF()
    private val clip = Path()

    /**
     * Draws the state of [action] (a WoW action id such as `ACTIONBUTTON3`) into the square or shape ([left], [top],
     * [right], [bottom]); [kind] and [radius] are those of the symbol below it (as for [app.gamenative.wowicons.WowIconDraw]).
     * Returns whether anything was drawn. Does nothing for other actions, without the strip, or for an idle slot.
     */
    @JvmStatic
    @JvmOverloads
    fun draw(canvas: Canvas, action: String?, left: Float, top: Float, right: Float, bottom: Float, kind: Int, radius: Float = 0f): Boolean {
        if (action == null) return false
        val slot = ActionSlotMap.slotFor(action) ?: return false
        val cell = CooldownService.stateFor(slot) ?: return false
        val width = right - left
        val height = bottom - top
        if (width < 4f || height < 4f) return false
        val cooling = cell.cooling && (!cell.globalOnly || CooldownService.showGlobal)
        val tint = when {
            cell.outOfRange -> RANGE_TINT
            cell.noResource -> RESOURCE_TINT
            else -> 0
        }
        if (!cooling && tint == 0) return false

        rect.set(left, top, right, bottom)
        if (tint != 0) {
            tintPaint.color = tint
            drawShape(canvas, kind, radius, tintPaint)
        }
        if (cooling) {
            drawSector(canvas, kind, radius, CooldownMath.sweepStart(cell.fraction), CooldownMath.sweepAngle(cell.fraction))
            val side = minOf(width, height)
            if (!cell.globalOnly && side >= MIN_TEXT_SIDE) drawText(canvas, CooldownMath.format(cell.remaining), (left + right) / 2f, (top + bottom) / 2f, side)
        }
        return true
    }

    private fun drawShape(canvas: Canvas, kind: Int, radius: Float, paint: Paint) {
        when (kind) {
            KIND_OVAL -> canvas.drawOval(rect, paint)
            KIND_ROUND -> canvas.drawRoundRect(rect, radius, radius, paint)
            else -> canvas.drawRect(rect, paint)
        }
    }

    private fun drawSector(canvas: Canvas, kind: Int, radius: Float, start: Float, sweep: Float) {
        if (sweep <= 0f) return
        if (kind == KIND_OVAL) {
            canvas.drawArc(rect, start, sweep, true, veilPaint)
            return
        }
        // A pie of a circle that covers the whole shape, cut to the shape.
        clip.reset()
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW)
        val cx = rect.centerX()
        val cy = rect.centerY()
        val reach = hypot(rect.width(), rect.height()) / 2f + 1f
        circle.set(cx - reach, cy - reach, cx + reach, cy + reach)
        canvas.save()
        canvas.clipPath(clip)
        canvas.drawArc(circle, start, sweep, true, veilPaint)
        canvas.restore()
    }

    private fun drawText(canvas: Canvas, text: String, cx: Float, cy: Float, side: Float) {
        if (text.isEmpty()) return
        var size = side * 0.46f
        textPaint.textSize = size
        val maxWidth = side * 0.92f
        while (textPaint.measureText(text) > maxWidth && size > 6f) {
            size *= 0.9f
            textPaint.textSize = size
        }
        val baseline = cy - (textPaint.descent() + textPaint.ascent()) * 0.5f
        textPaint.style = Paint.Style.STROKE
        textPaint.strokeWidth = size * 0.2f
        textPaint.color = 0xDD000000.toInt()
        canvas.drawText(text, cx, baseline, textPaint)
        textPaint.style = Paint.Style.FILL
        textPaint.color = 0xFFFFFFFF.toInt()
        canvas.drawText(text, cx, baseline, textPaint)
    }
}
