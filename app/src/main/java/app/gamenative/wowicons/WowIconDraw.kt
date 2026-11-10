package app.gamenative.wowicons

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader

/** Draws an icon bitmap into an oval, rounded rectangle or rectangle (smooth edges through a bitmap shader). UI thread only. */
object WowIconDraw {
    const val KIND_RECT = 0
    const val KIND_ROUND = 1
    const val KIND_OVAL = 2

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val matrix = Matrix()
    private val rect = RectF()
    private var shaderBitmap: Bitmap? = null
    private var shader: BitmapShader? = null
    private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    /**
     * Draws [bitmap] into [left, top, right, bottom]. [radius] is the corner radius for [KIND_ROUND]. [alpha] 0..255.
     * Returns false (nothing drawn) for an empty target or a recycled bitmap.
     */
    @JvmStatic
    fun draw(
        canvas: Canvas,
        bitmap: Bitmap,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        kind: Int,
        radius: Float,
        alpha: Int,
    ): Boolean {
        if (bitmap.isRecycled || right - left < 2f || bottom - top < 2f) return false
        var s = shader
        if (s == null || shaderBitmap !== bitmap) {
            s = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            shader = s
            shaderBitmap = bitmap
        }
        matrix.reset()
        matrix.setScale((right - left) / bitmap.width, (bottom - top) / bitmap.height)
        matrix.postTranslate(left, top)
        s.setLocalMatrix(matrix)
        paint.shader = s
        paint.alpha = alpha.coerceIn(0, 255)
        rect.set(left, top, right, bottom)
        when (kind) {
            KIND_OVAL -> canvas.drawOval(rect, paint)
            KIND_ROUND -> canvas.drawRoundRect(rect, radius, radius, paint)
            else -> canvas.drawRect(rect, paint)
        }
        paint.shader = null
        return true
    }

    /**
     * Small label under or over a symbol (white with a dark outline, readable on any picture), centred on ([centerX]) with
     * its baseline at [baselineY]. Main thread (onDraw) only: the paint is shared.
     */
    @JvmStatic
    fun drawCaption(canvas: Canvas, text: String, centerX: Float, baselineY: Float, textSize: Float) {
        if (text.isEmpty() || textSize < 4f) return
        captionPaint.textSize = textSize
        captionPaint.style = Paint.Style.STROKE
        captionPaint.strokeWidth = textSize * 0.22f
        captionPaint.color = 0xCC000000.toInt()
        canvas.drawText(text, centerX, baselineY, captionPaint)
        captionPaint.style = Paint.Style.FILL
        captionPaint.color = 0xFFFFFFFF.toInt()
        canvas.drawText(text, centerX, baselineY, captionPaint)
    }
}
