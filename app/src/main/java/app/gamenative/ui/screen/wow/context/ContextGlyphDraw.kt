package app.gamenative.ui.screen.wow.context

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * Gezeichnete Symbole der festen Menüeinträge (Rückfall, wenn das Spiel-Symbol fehlt, und für alles ohne passendes Spiel-Symbol).
 * Alles Striche in einem Quadrat von -1 bis 1, skaliert auf die Kachelgröße. Eine Instanz je Ansicht, Hauptthread.
 */
class ContextGlyphDraw {
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val solid = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()
    private val rect = RectF()

    private var cx = 0f
    private var cy = 0f
    private var half = 0f

    private fun x(v: Float) = cx + v * half
    private fun y(v: Float) = cy + v * half

    private fun line(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float) {
        canvas.drawLine(x(x1), y(y1), x(x2), y(y2), stroke)
    }

    private fun circle(canvas: Canvas, px: Float, py: Float, r: Float, filled: Boolean = false) {
        canvas.drawCircle(x(px), y(py), r * half, if (filled) solid else stroke)
    }

    private fun arrowHead(canvas: Canvas, tipX: Float, tipY: Float, dirX: Float, dirY: Float) {
        // zwei kurze Striche vom Spitzenpunkt zurück, links und rechts von der Richtung (dirX, dirY) (Einheitsvektor)
        val back = 0.32f
        val side = 0.24f
        line(canvas, tipX, tipY, tipX - dirX * back - dirY * side, tipY - dirY * back + dirX * side)
        line(canvas, tipX, tipY, tipX - dirX * back + dirY * side, tipY - dirY * back - dirX * side)
    }

    /** Zeichnet [glyph] in ein Quadrat der Kantenlänge [size] um ([centerX], [centerY]) in der Farbe [argb]. */
    fun draw(canvas: Canvas, glyph: ContextGlyph, centerX: Float, centerY: Float, size: Float, argb: Int) {
        cx = centerX
        cy = centerY
        half = size / 2f
        stroke.color = argb
        stroke.strokeWidth = size * 0.09f
        solid.color = argb
        when (glyph) {
            ContextGlyph.ATTACK -> {
                // gekreuzte Schwerter mit Parierstangen
                line(canvas, -0.8f, 0.8f, 0.75f, -0.75f)
                line(canvas, 0.8f, 0.8f, -0.75f, -0.75f)
                line(canvas, -0.62f, 0.3f, -0.3f, 0.62f)
                line(canvas, 0.62f, 0.3f, 0.3f, 0.62f)
            }
            ContextGlyph.TARGET -> {
                circle(canvas, 0f, 0f, 0.5f)
                circle(canvas, 0f, 0f, 0.08f, filled = true)
                line(canvas, 0f, -0.95f, 0f, -0.3f)
                line(canvas, 0f, 0.95f, 0f, 0.3f)
                line(canvas, -0.95f, 0f, -0.3f, 0f)
                line(canvas, 0.95f, 0f, 0.3f, 0f)
            }
            ContextGlyph.LOOT -> {
                // Beutel: Körper, Hals, Band
                path.reset()
                path.moveTo(x(-0.35f), y(-0.35f))
                path.cubicTo(x(-1.0f), y(0.05f), x(-0.85f), y(0.85f), x(0f), y(0.85f))
                path.cubicTo(x(0.85f), y(0.85f), x(1.0f), y(0.05f), x(0.35f), y(-0.35f))
                path.close()
                canvas.drawPath(path, stroke)
                line(canvas, -0.38f, -0.35f, -0.5f, -0.78f)
                line(canvas, 0.38f, -0.35f, 0.5f, -0.78f)
                line(canvas, -0.5f, -0.78f, 0.5f, -0.78f)
                line(canvas, -0.38f, -0.35f, 0.38f, -0.35f)
            }
            ContextGlyph.SKIN -> {
                // Messer: Griff und gebogene Klinge
                line(canvas, -0.85f, 0.85f, -0.3f, 0.3f)
                path.reset()
                path.moveTo(x(-0.3f), y(0.3f))
                path.quadTo(x(0.2f), y(-0.35f), x(0.85f), y(-0.85f))
                path.quadTo(x(0.6f), y(0.0f), x(0.05f), y(0.55f))
                path.close()
                canvas.drawPath(path, stroke)
            }
            ContextGlyph.TALK -> {
                // Sprechblase mit drei Punkten
                rect.set(x(-0.85f), y(-0.75f), x(0.85f), y(0.3f))
                canvas.drawRoundRect(rect, 0.3f * half, 0.3f * half, stroke)
                path.reset()
                path.moveTo(x(-0.35f), y(0.3f))
                path.lineTo(x(-0.55f), y(0.85f))
                path.lineTo(x(0.1f), y(0.3f))
                canvas.drawPath(path, stroke)
                circle(canvas, -0.4f, -0.22f, 0.07f, filled = true)
                circle(canvas, 0f, -0.22f, 0.07f, filled = true)
                circle(canvas, 0.4f, -0.22f, 0.07f, filled = true)
            }
            ContextGlyph.MOVE -> {
                // Pfeilkreuz
                line(canvas, -0.85f, 0f, 0.85f, 0f)
                line(canvas, 0f, -0.85f, 0f, 0.85f)
                arrowHead(canvas, 0.85f, 0f, 1f, 0f)
                arrowHead(canvas, -0.85f, 0f, -1f, 0f)
                arrowHead(canvas, 0f, -0.85f, 0f, -1f)
                arrowHead(canvas, 0f, 0.85f, 0f, 1f)
            }
            ContextGlyph.SPLIT -> {
                // ein Stapel teilt sich in zwei
                line(canvas, -0.8f, 0f, -0.1f, 0f)
                line(canvas, -0.1f, 0f, 0.55f, -0.5f)
                line(canvas, -0.1f, 0f, 0.55f, 0.5f)
                arrowHead(canvas, 0.8f, -0.65f, 0.8f, -0.6f)
                arrowHead(canvas, 0.8f, 0.65f, 0.8f, 0.6f)
            }
            ContextGlyph.DISCARD -> {
                // Mülleimer
                line(canvas, -0.7f, -0.45f, 0.7f, -0.45f)
                path.reset()
                path.moveTo(x(-0.2f), y(-0.45f))
                path.lineTo(x(-0.2f), y(-0.75f))
                path.lineTo(x(0.2f), y(-0.75f))
                path.lineTo(x(0.2f), y(-0.45f))
                canvas.drawPath(path, stroke)
                path.reset()
                path.moveTo(x(-0.5f), y(-0.35f))
                path.lineTo(x(-0.4f), y(0.85f))
                path.lineTo(x(0.4f), y(0.85f))
                path.lineTo(x(0.5f), y(-0.35f))
                canvas.drawPath(path, stroke)
                line(canvas, -0.15f, -0.1f, -0.15f, 0.6f)
                line(canvas, 0.15f, -0.1f, 0.15f, 0.6f)
            }
            ContextGlyph.LINK -> {
                // Kette: zwei gedrehte Glieder
                canvas.save()
                canvas.rotate(-45f, x(0f), y(0f))
                rect.set(x(-0.95f), y(-0.36f), x(0.15f), y(0.36f))
                canvas.drawRoundRect(rect, 0.36f * half, 0.36f * half, stroke)
                rect.set(x(-0.15f), y(-0.36f), x(0.95f), y(0.36f))
                canvas.drawRoundRect(rect, 0.36f * half, 0.36f * half, stroke)
                canvas.restore()
            }
            ContextGlyph.COMPARE -> {
                // Waage
                line(canvas, 0f, -0.85f, 0f, 0.7f)
                line(canvas, -0.45f, 0.8f, 0.45f, 0.8f)
                line(canvas, -0.8f, -0.5f, 0.8f, -0.5f)
                line(canvas, -0.8f, -0.5f, -1.0f, 0.1f)
                line(canvas, -0.8f, -0.5f, -0.6f, 0.1f)
                line(canvas, -1.0f, 0.1f, -0.6f, 0.1f)
                line(canvas, 0.8f, -0.5f, 0.6f, 0.1f)
                line(canvas, 0.8f, -0.5f, 1.0f, 0.1f)
                line(canvas, 0.6f, 0.1f, 1.0f, 0.1f)
            }
            ContextGlyph.USE -> {
                // Hand mit ausgestrecktem Finger
                rect.set(x(-0.18f), y(-0.9f), x(0.12f), y(0.05f))
                canvas.drawRoundRect(rect, 0.15f * half, 0.15f * half, stroke)
                rect.set(x(-0.6f), y(-0.05f), x(0.6f), y(0.9f))
                canvas.drawRoundRect(rect, 0.25f * half, 0.25f * half, stroke)
                line(canvas, -0.2f, 0.3f, 0.2f, 0.3f)
            }
            ContextGlyph.EQUIP -> shield(canvas)
            ContextGlyph.UNEQUIP -> {
                shield(canvas)
                line(canvas, -0.4f, 0.05f, 0.4f, 0.05f)
            }
            ContextGlyph.SELL -> {
                circle(canvas, 0f, 0f, 0.75f)
                circle(canvas, 0f, 0f, 0.45f)
            }
            ContextGlyph.TRANSFER -> {
                line(canvas, -0.8f, -0.32f, 0.8f, -0.32f)
                arrowHead(canvas, 0.8f, -0.32f, 1f, 0f)
                line(canvas, 0.8f, 0.32f, -0.8f, 0.32f)
                arrowHead(canvas, -0.8f, 0.32f, -1f, 0f)
            }
            ContextGlyph.MIC -> {
                // Mikrofon: Kapsel, Bügel darunter, Fuß
                rect.set(x(-0.3f), y(-0.9f), x(0.3f), y(0.15f))
                canvas.drawRoundRect(rect, 0.3f * half, 0.3f * half, stroke)
                rect.set(x(-0.62f), y(-0.5f), x(0.62f), y(0.5f))
                canvas.drawArc(rect, 0f, 180f, false, stroke)
                line(canvas, 0f, 0.5f, 0f, 0.88f)
                line(canvas, -0.35f, 0.88f, 0.35f, 0.88f)
            }
            ContextGlyph.INVITE -> {
                // Person mit Plus
                circle(canvas, -0.3f, -0.45f, 0.3f)
                path.reset()
                path.moveTo(x(-0.9f), y(0.85f))
                path.quadTo(x(-0.3f), y(-0.1f), x(0.15f), y(0.85f))
                canvas.drawPath(path, stroke)
                line(canvas, 0.55f, -0.25f, 0.55f, 0.45f)
                line(canvas, 0.2f, 0.1f, 0.9f, 0.1f)
            }
            ContextGlyph.FOLLOW -> {
                // drei Pfeilspitzen hintereinander: hinterher gehen
                arrowHead(canvas, 0.85f, 0f, 1f, 0f)
                arrowHead(canvas, 0.2f, 0f, 1f, 0f)
                arrowHead(canvas, -0.45f, 0f, 1f, 0f)
            }
            ContextGlyph.WAVE -> {
                // Hand mit vier Fingern und zwei Winkbögen
                rect.set(x(-0.5f), y(0.0f), x(0.4f), y(0.9f))
                canvas.drawRoundRect(rect, 0.25f * half, 0.25f * half, stroke)
                line(canvas, -0.38f, 0.0f, -0.38f, -0.65f)
                line(canvas, -0.13f, 0.0f, -0.13f, -0.8f)
                line(canvas, 0.12f, 0.0f, 0.12f, -0.75f)
                line(canvas, 0.3f, 0.0f, 0.3f, -0.55f)
                line(canvas, 0.62f, -0.35f, 0.85f, -0.55f)
                line(canvas, 0.62f, -0.05f, 0.92f, -0.1f)
            }
            ContextGlyph.FRIEND -> {
                // Herz
                path.reset()
                path.moveTo(x(0f), y(0.8f))
                path.cubicTo(x(-1.0f), y(0.1f), x(-0.8f), y(-0.7f), x(0f), y(-0.25f))
                path.cubicTo(x(0.8f), y(-0.7f), x(1.0f), y(0.1f), x(0f), y(0.8f))
                path.close()
                canvas.drawPath(path, stroke)
            }
            ContextGlyph.INSPECT -> {
                // Lupe
                circle(canvas, -0.15f, -0.15f, 0.55f)
                line(canvas, 0.25f, 0.25f, 0.85f, 0.85f)
            }
            ContextGlyph.MORE -> {
                circle(canvas, -0.55f, 0f, 0.14f, filled = true)
                circle(canvas, 0f, 0f, 0.14f, filled = true)
                circle(canvas, 0.55f, 0f, 0.14f, filled = true)
            }
        }
    }

    private fun shield(canvas: Canvas) {
        path.reset()
        path.moveTo(x(-0.7f), y(-0.75f))
        path.lineTo(x(0.7f), y(-0.75f))
        path.lineTo(x(0.7f), y(0.1f))
        path.quadTo(x(0.7f), y(0.7f), x(0f), y(0.92f))
        path.quadTo(x(-0.7f), y(0.7f), x(-0.7f), y(0.1f))
        path.close()
        canvas.drawPath(path, stroke)
    }
}
