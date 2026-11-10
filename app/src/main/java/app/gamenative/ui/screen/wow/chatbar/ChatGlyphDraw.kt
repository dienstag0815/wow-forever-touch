package app.gamenative.ui.screen.wow.chatbar

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import app.gamenative.ui.touchstyle.Glyph
import app.gamenative.ui.touchstyle.KeyGlyphDraw

/**
 * Symbole der Chat-Knöpfe, gezeichnet (keine Texte): Mikrofon, Tastatur und Kreuz selbst, Enter und Tab als Tastensymbole der Touch-Elemente
 * ([KeyGlyphDraw]). Nur auf dem Hauptthread (eigene Pinsel).
 */
object ChatGlyphDraw {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val rect = RectF()

    /**
     * Zeichnet das Symbol von [action] (Kennung eines [ChatButton]) in der Mitte [cx], [cy] eines Knopfes mit der Kantenlänge [size] (der kleineren
     * Seite). [outlined]: dunkle Kontur wie bei den Beschriftungen der Stile F und G. Gibt false zurück, wenn [action] unbekannt ist (nichts gezeichnet).
     */
    @JvmStatic
    fun draw(canvas: Canvas, action: String?, cx: Float, cy: Float, size: Float, color: Int, outlined: Boolean): Boolean {
        val button = ChatButton.fromId(action) ?: return false
        when (button) {
            ChatButton.ENTER -> KeyGlyphDraw.drawGlyph(canvas, Glyph.ENTER, cx, cy, size * 0.5f, color, outlined)
            ChatButton.CHANNEL -> KeyGlyphDraw.drawGlyph(canvas, Glyph.TAB, cx, cy, size * 0.5f, color, outlined)
            ChatButton.MIC -> drawMic(canvas, cx, cy, size * 0.3f, color)
            ChatButton.KEYBOARD -> drawKeyboard(canvas, cx, cy, size * 0.3f, color)
            ChatButton.CANCEL -> drawCross(canvas, cx, cy, size * 0.22f, color)
        }
        return true
    }

    private fun prepareStroke(half: Float, color: Int) {
        stroke.color = color
        stroke.strokeWidth = half * 0.2f
    }

    /** Mikrofon: Kapsel, Bügel darunter, Fuß. [half] = halbe Kantenlänge des Symbols. */
    private fun drawMic(canvas: Canvas, cx: Float, cy: Float, half: Float, color: Int) {
        prepareStroke(half, color)
        rect.set(cx - half * 0.32f, cy - half * 0.95f, cx + half * 0.32f, cy + half * 0.15f)
        canvas.drawRoundRect(rect, half * 0.32f, half * 0.32f, stroke)
        rect.set(cx - half * 0.66f, cy - half * 0.5f, cx + half * 0.66f, cy + half * 0.5f)
        canvas.drawArc(rect, 0f, 180f, false, stroke)
        canvas.drawLine(cx, cy + half * 0.5f, cx, cy + half * 0.92f, stroke)
        canvas.drawLine(cx - half * 0.36f, cy + half * 0.92f, cx + half * 0.36f, cy + half * 0.92f, stroke)
    }

    /** Tastatur: Rahmen mit zwei Reihen Tasten und einer langen Leertaste. */
    private fun drawKeyboard(canvas: Canvas, cx: Float, cy: Float, half: Float, color: Int) {
        prepareStroke(half, color)
        rect.set(cx - half * 1.05f, cy - half * 0.7f, cx + half * 1.05f, cy + half * 0.7f)
        canvas.drawRoundRect(rect, half * 0.2f, half * 0.2f, stroke)
        val dot = half * 0.08f
        fill.color = color
        for (row in 0..1) {
            val y = cy - half * 0.28f + row * half * 0.34f
            for (col in -2..2) canvas.drawCircle(cx + col * half * 0.38f, y, dot, fill)
        }
        canvas.drawLine(cx - half * 0.5f, cy + half * 0.42f, cx + half * 0.5f, cy + half * 0.42f, stroke)
    }

    private fun drawCross(canvas: Canvas, cx: Float, cy: Float, half: Float, color: Int) {
        prepareStroke(half * 1.2f, color)
        canvas.drawLine(cx - half, cy - half, cx + half, cy + half, stroke)
        canvas.drawLine(cx + half, cy - half, cx - half, cy + half, stroke)
    }
}
