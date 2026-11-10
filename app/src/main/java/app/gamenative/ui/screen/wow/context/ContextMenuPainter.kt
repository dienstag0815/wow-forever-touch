package app.gamenative.ui.screen.wow.context

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import app.gamenative.ui.touchstyle.RadialArc
import app.gamenative.ui.touchstyle.RadialSegmentDraw
import app.gamenative.ui.touchstyle.TouchStyle
import app.gamenative.ui.touchstyle.TouchStyleMath
import app.gamenative.ui.touchstyle.TouchStyleSettings
import app.gamenative.wowaction.WowActionCatalog
import app.gamenative.wowaction.WowSymbols
import app.gamenative.wowicons.WowIconDraw
import kotlin.math.cos
import kotlin.math.sin

/**
 * Zeichnet das Radialmenü als Ringsegmente im gewählten Stil der Touch-Elemente (derselbe Baustein wie der Stick-Ring und die übrigen
 * Radialmenüs, siehe [RadialSegmentDraw]); das Segment unter dem Finger leuchtet im Auswahlton des Stils und zeigt seinen vollen Namen
 * daneben. In jedem Segment steht das Symbol (Zauber: das Symbol aus dem Spiel; feste Einträge: Spiel-Symbol oder gezeichnetes Symbol),
 * etwas kleiner als das Segment, und klein darunter der Name; fehlt jedes Symbol, steht der Name groß im Segment (nie leer). Eine
 * Instanz je Ansicht, wird jedes Bild wiederverwendet.
 */
class ContextMenuPainter(private val density: Float, private val scaledDensity: Float) {
    private val frame = RadialSegmentDraw()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val caption = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 9f * scaledDensity
        setShadowLayer(3f * density, 0f, 0f, Color.BLACK)
    }
    private val big = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 11f * scaledDensity
        setShadowLayer(3f * density, 0f, 0f, Color.BLACK)
    }
    private val chip = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 12f * scaledDensity
        setShadowLayer(3f * density, 0f, 0f, Color.BLACK)
    }
    private val cooldown = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 13f * scaledDensity
        isFakeBoldText = true
        setShadowLayer(3f * density, 0f, 0f, Color.BLACK)
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rect = RectF()
    private val glyphs = ContextGlyphDraw()

    fun draw(canvas: Canvas, state: ContextMenuState, iconOf: (ContextEntry) -> Bitmap?, glyphIconOf: (ContextGlyph) -> Bitmap?) {
        val style = TouchStyleSettings.style
        // Stelle des Fingers beim Öffnen: kleiner Ring, damit man sieht, worauf das Menü sich bezieht.
        dot.strokeWidth = 1.5f * density
        dot.color = TouchStyleMath.withAlpha(RadialSegmentDraw.accent(style), 190)
        canvas.drawCircle(state.anchorX, state.anchorY, 22f * density, dot)

        val cx = state.layout.centerX
        val cy = state.layout.centerY
        for ((index, entry) in state.entries.withIndex()) {
            val seg = state.layout.segments.getOrNull(index) ?: continue
            val selected = index == state.selected && entry.enabled
            val faded = if (!entry.enabled) 0.45f else if (entry.dimmed) 0.7f else 1f
            frame.drawFrame(canvas, cx, cy, seg, selected, faded, density)

            val colour = RadialSegmentDraw.textColor(style, selected)
            val text = withFade(colour, faded)
            val content = RadialArc.contentFor(seg, state.geometry.arc)
            val iconSize = content.iconSize
            val iconX = cx + content.iconX
            val iconY = cy + content.iconY
            val iconLeft = iconX - iconSize / 2f
            val iconTop = iconY - iconSize / 2f

            val bitmap = iconOf(entry)
            val glyph = entry.glyph
            val gameGlyph = if (bitmap == null && glyph != null) glyphIconOf(glyph) else null
            val picture = bitmap ?: gameGlyph
            // Aktionen des Spiels ohne Bild aus dem Spiel (noch nicht geladen): das gezeichnete Symbol der Aktion
            val wowSymbol = if (picture == null && glyph == null) WowActionCatalog.find(entry.wowAction)?.symbol else null
            val hasSymbol = picture != null || glyph != null || wowSymbol != null
            if (wowSymbol != null) {
                WowSymbols.draw(canvas, wowSymbol, iconX, iconY, iconSize / 2f, text)
            } else if (picture != null) {
                WowIconDraw.draw(
                    canvas, picture, iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize,
                    WowIconDraw.KIND_ROUND, 5f * density, (255 * faded).toInt(),
                )
            } else if (glyph != null) {
                glyphs.draw(canvas, glyph, iconX, iconY, iconSize * 0.85f, text)
            }

            val timeText = if (entry.cooldown > 0f) ContextMenus.cooldownText(entry) else ""
            if (timeText.isNotEmpty()) {
                // Abklingzeit: das Symbol abdunkeln, die Zeit groß darüber
                shade.color = Color.argb(140, 0, 0, 0)
                rect.set(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
                canvas.drawRoundRect(rect, 5f * density, 5f * density, shade)
                cooldown.color = withFade(Color.rgb(255, 214, 120), faded)
                canvas.drawText(timeText, iconX, iconY + cooldown.textSize * 0.35f, cooldown)
            }

            val labelX = cx + content.labelX
            val labelBase = cy + content.labelBaseline
            if (hasSymbol) {
                // der Name klein darunter
                caption.color = withFade(text, faded)
                val shown = TextUtils.ellipsize(entry.label, caption, content.labelWidth, TextUtils.TruncateAt.END).toString()
                canvas.drawText(shown, labelX, labelBase, caption)
            } else {
                // kein Symbol (der Zauber ist noch nicht geladen): der Name groß, bis zu drei Zeilen, nie leer
                big.color = withFade(text, faded)
                val lines = wrap(entry.label, big, content.labelWidth, 3)
                val lineHeight = big.textSize * 1.15f
                var baseline = cy + seg.midY() - (lines.size - 1) * lineHeight / 2f + big.textSize * 0.35f
                if (timeText.isNotEmpty()) baseline += lineHeight * 0.4f
                for (line in lines) {
                    canvas.drawText(line, labelX, baseline, big)
                    baseline += lineHeight
                }
            }
        }

        // Das Segment unter dem Finger: voller Name als Schild außen daneben. Zuletzt, damit nichts es deckt.
        val selectedEntry = state.entries.getOrNull(state.selected)
        val selectedSeg = state.layout.segments.getOrNull(state.selected)
        if (selectedEntry != null && selectedSeg != null && selectedEntry.enabled) {
            drawName(canvas, state, selectedEntry.label, selectedSeg, style)
        }
    }

    /** Namensschild des gewählten Segments: außen am Segment in Richtung seiner Mitte, im Bild gehalten. */
    private fun drawName(canvas: Canvas, state: ContextMenuState, label: String, seg: RadialArc.Segment, style: TouchStyle) {
        chip.color = RadialSegmentDraw.textColor(style, true)
        val width = chip.measureText(label) + 20f * density
        val height = chip.textSize * 1.9f
        val rad = Math.toRadians(seg.midDeg.toDouble())
        val dirX = cos(rad).toFloat()
        val dirY = sin(rad).toFloat()
        val gap = 6f * density
        val ax = state.layout.centerX + dirX * (seg.outer + gap)
        val ay = state.layout.centerY + dirY * (seg.outer + gap)
        // Das Schild rückt von der Segmentkante nach außen: waagerecht um die halbe Breite, senkrecht um die halbe Höhe.
        var left = ax + dirX * width / 2f - width / 2f
        var top = ay + dirY * height / 2f - height / 2f
        left = left.coerceIn(4f * density, (state.screenW - width - 4f * density).coerceAtLeast(4f * density))
        top = top.coerceIn(4f * density, (state.screenH - height - 4f * density).coerceAtLeast(4f * density))
        rect.set(left, top, left + width, top + height)
        fill.color = TouchStyleMath.withAlpha(RadialSegmentDraw.chipFill(style), 245)
        canvas.drawRoundRect(rect, height / 2f, height / 2f, fill)
        edge.strokeWidth = 1.6f * density
        edge.color = TouchStyleMath.withAlpha(RadialSegmentDraw.accent(style), 255)
        canvas.drawRoundRect(rect, height / 2f, height / 2f, edge)
        canvas.drawText(label, rect.centerX(), rect.centerY() + chip.textSize * 0.35f, chip)
    }

    /** Zerlegt [text] an Leerzeichen in höchstens [maxLines] Zeilen der Breite [room]; die letzte Zeile wird bei Bedarf gekürzt. */
    private fun wrap(text: String, paint: TextPaint, room: Float, maxLines: Int): List<String> {
        val words = text.split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return listOf(text)
        val lines = ArrayList<String>()
        var current = ""
        for (word in words) {
            val tryLine = if (current.isEmpty()) word else "$current $word"
            if (current.isEmpty() || paint.measureText(tryLine) <= room) {
                current = tryLine
            } else {
                lines.add(current)
                current = word
            }
        }
        lines.add(current)
        val cut = if (lines.size > maxLines) {
            // zu viele Zeilen: die letzten zu einer zusammenfassen (wird gekürzt)
            lines.subList(0, maxLines - 1) + lines.subList(maxLines - 1, lines.size).joinToString(" ")
        } else {
            lines
        }
        return cut.map { TextUtils.ellipsize(it, paint, room, TextUtils.TruncateAt.END).toString() }
    }

    private fun withFade(argb: Int, factor: Float): Int =
        Color.argb((Color.alpha(argb) * factor).toInt().coerceIn(0, 255), Color.red(argb), Color.green(argb), Color.blue(argb))
}
