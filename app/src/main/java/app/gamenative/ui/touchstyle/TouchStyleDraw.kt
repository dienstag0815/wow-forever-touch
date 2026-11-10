package app.gamenative.ui.touchstyle

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.LruCache
import timber.log.Timber
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Zeichnet die Touch-Elemente in den Stilen F und G (Auftrag tastenoptik, Phase 2a). Formeln und Werte: [TouchStyleMath],
 * Vorlage `tools/tastenoptik_vorschau.py`. Nur der Zeichenthread ruft auf (die Hilfsobjekte sind geteilt).
 *
 * Leistung: alles, was Unschärfe oder Verlauf braucht (Schatten, Lichtkante, dunkle Haarlinie, gedrückter Rand), wird je
 * (Stil, Rolle, Form, Größe) einmal als Bitmap gebaut und im LruCache gehalten. Die Zustände (Ruhe, aktiv, gedrückt) und der
 * Kontrast ändern nur die Deckkraft beim Blit; dafür sind die Schichten einfarbig (ALPHA_8) oder tragen den Verlauf in voller
 * Deckkraft. Pro Bild und Element bleiben ein Pfad für die Füllung und ein bis vier Bitmap-Blits, kein Blur.
 */
object TouchStyleDraw {
    const val SHAPE_CHAMFER = 0
    const val SHAPE_OVAL = 1
    private const val SHAPE_SECTOR = 2

    const val ROLE_SLOT = 0
    const val ROLE_TEXT = 1
    const val ROLE_ROUND = 2
    const val ROLE_PANEL = 3
    const val ROLE_BASE = 4
    const val ROLE_THUMB = 5
    const val ROLE_SEGMENT = 6

    const val DIR_UP = 0
    const val DIR_RIGHT = 1
    const val DIR_DOWN = 2
    const val DIR_LEFT = 3

    private const val CACHE_BYTES = 12 * 1024 * 1024
    private const val TAG = "TouchStyle"

    private val rest = TouchRestTimer()
    private val stats = FrameTimeStats()
    private var density: Float = Resources.getSystem().displayMetrics.density
    private var frameLevel = 1f
    private var frameHeld = false
    private var frameStartNs = 0L

    // ---- Stil und Bild -------------------------------------------------------------------------------------------------

    /** Ist ein anderer Stil als das heutige Aussehen gewählt? */
    @JvmStatic
    fun isStyled(): Boolean = TouchStyleSettings.style != TouchStyle.CURRENT

    /** Stick ohne Ring wird als weiche Fläche gezeichnet (nur G); F zeichnet Grundkreis und Daumen. */
    @JvmStatic
    fun softStick(): Boolean = TouchStyleSettings.style == TouchStyle.G

    /** Hat der Daumen im gedrückten Zustand den hellen Rand? F ja, G nicht (dort zeigt die weiche Fläche den Druck). */
    @JvmStatic
    fun thumbHighlight(pressed: Boolean): Boolean = pressed && TouchStyleSettings.style == TouchStyle.F

    /** Neue Spielsitzung: die Elemente beginnen aktiv und gehen nach 4 s in die Ruhe. */
    @JvmStatic
    fun resetSession(nowMs: Long) {
        rest.reset(nowMs)
    }

    /** Ein Element wurde berührt oder losgelassen (vom Berührungsthread). */
    @JvmStatic
    fun touched(nowMs: Long) {
        rest.touch(nowMs)
    }

    /** Vor dem Zeichnen der Elemente: Ruhe-Stufe bestimmen und die Zeit messen. [held]: ein Finger liegt auf einem Element. */
    @JvmStatic
    fun beginFrame(nowMs: Long, held: Boolean, displayDensity: Float) {
        if (displayDensity > 0f) density = displayDensity
        frameHeld = held
        frameLevel = rest.update(nowMs, held)
        frameStartNs = System.nanoTime()
    }

    /**
     * Nach dem Zeichnen der Elemente: Zeichenzeit festhalten (alle 300 Bilder eine Zeile im Log, in jedem Stil, zum Vergleich
     * vorher/nachher) und zurückgeben, wann noch einmal gezeichnet werden muss (0 = gleich, Millisekunden, -1 = nicht nötig).
     */
    @JvmStatic
    fun endFrame(nowMs: Long): Long {
        val summary = stats.add(System.nanoTime() - frameStartNs)
        if (summary != null) {
            Timber.tag(TAG).i(
                "Zeichenzeit Stil %s: %d Bilder, Mittel %.3f ms, groesster %.3f ms",
                TouchStyleSettings.style.id, summary.frames, summary.meanMs, summary.maxMs,
            )
        }
        // Die Ruhe-Stufe gibt es nur bei G; F und das heutige Aussehen haben feste Werte.
        if (TouchStyleSettings.style != TouchStyle.G) return -1L
        return rest.nextRedrawIn(nowMs, frameHeld)
    }

    /** Ruhe-Stufe dieses Bildes: 0 = Ruhe, 1 = aktiv. */
    @JvmStatic
    fun level(): Float = frameLevel

    @JvmStatic
    fun clearCache() {
        cache.evictAll()
        shaders.clear()
    }

    // ---- einfache Werte für ControlElement -----------------------------------------------------------------------------

    @JvmStatic
    fun symbolScale(pressed: Boolean): Float = TouchStyleMath.symbolScale(TouchStyleSettings.style, pressed)

    @JvmStatic
    fun iconAlpha(pressed: Boolean): Int = TouchStyleMath.iconAlpha(TouchStyleSettings.style, pressed, frameLevel)

    @JvmStatic
    fun textColor(pressed: Boolean): Int = TouchStyleMath.textColor(TouchStyleSettings.style, pressed)

    /** Kantenbreite in Pixeln, für Abstände, die sich an der Kante des Elements orientieren. */
    @JvmStatic
    fun edgePx(): Float = max(1f, TouchStyleMath.edgeWidthDp(TouchStyleSettings.style, TouchRole.SLOT) * density)

    // ---- Schichten im Cache --------------------------------------------------------------------------------------------

    private data class Key(
        val style: Int,
        val role: Int,
        val shape: Int,
        val w: Int,
        val h: Int,
        val p1: Int,
        val p2: Int,
        val p3: Int,
        val p4: Int,
        val dens: Int,
    )

    /** Die gebauten Schichten einer Form. [ox]/[oy]: Lage der linken oberen Ecke zur Mitte (nur Ringsegmente). */
    private class Layers(
        val path: Path,
        val pad: Int,
        val w: Int,
        val h: Int,
        val shadow: Bitmap?,
        val rim: Bitmap,
        val hair: Bitmap,
        val ox: Float,
        val oy: Float,
    ) {
        var ring: Bitmap? = null

        fun bytes(): Int = (shadow?.allocationByteCount ?: 0) + rim.allocationByteCount + hair.allocationByteCount +
            (ring?.allocationByteCount ?: 0) + 256
    }

    private val cache = object : LruCache<Key, Layers>(CACHE_BYTES) {
        override fun sizeOf(key: Key, value: Layers): Int = max(1, value.bytes())
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val alphaPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { color = Color.WHITE }
    private val roles = TouchRole.entries

    private fun blurRadius(sigmaPx: Float): Float = max(1f, sigmaPx * 1.7f)

    private fun chamferPath(w: Float, h: Float, c: Float): Path {
        val p = Path()
        val k = max(0f, min(c, min(w, h) / 2f))
        p.moveTo(k, 0f)
        p.lineTo(w - k, 0f)
        p.lineTo(w, k)
        p.lineTo(w, h - k)
        p.lineTo(w - k, h)
        p.lineTo(k, h)
        p.lineTo(0f, h - k)
        p.lineTo(0f, k)
        p.close()
        return p
    }

    private fun ovalPath(w: Float, h: Float): Path {
        val p = Path()
        p.addOval(RectF(0f, 0f, w, h), Path.Direction.CW)
        return p
    }

    /** Ringsegment um den Nullpunkt (Winkel in Grad, 0 = 3 Uhr, im Uhrzeigersinn), die Ecken leicht gerundet. */
    private fun sectorPath(inner: Float, outer: Float, startDeg: Float, sweepDeg: Float): Path {
        val poly = Path()
        val steps = max(4, ceil(sweepDeg / 3f).toInt())
        for (i in 0..steps) {
            val a = Math.toRadians((startDeg + sweepDeg * i / steps).toDouble())
            val x = (cos(a) * outer).toFloat()
            val y = (sin(a) * outer).toFloat()
            if (i == 0) poly.moveTo(x, y) else poly.lineTo(x, y)
        }
        for (i in steps downTo 0) {
            val a = Math.toRadians((startDeg + sweepDeg * i / steps).toDouble())
            poly.lineTo((cos(a) * inner).toFloat(), (sin(a) * inner).toFloat())
        }
        poly.close()
        val rounded = Path()
        val p = Paint()
        p.pathEffect = CornerPathEffect(max(1f, 4f * density))
        p.style = Paint.Style.FILL
        return if (p.getFillPath(poly, rounded) && !rounded.isEmpty) rounded else poly
    }

    private fun invertedFill(path: Path): Path {
        val inv = Path(path)
        inv.fillType = Path.FillType.INVERSE_WINDING
        return inv
    }

    private fun clearPaint(style: Paint.Style, width: Float): Paint {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = style
        p.strokeWidth = width
        p.strokeJoin = Paint.Join.MITER
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        return p
    }

    private fun opaque(rgb: Int): Int = TouchStyleMath.withAlpha(rgb, 255)

    private fun rimShader(style: TouchStyle, w: Float, h: Float): Shader =
        if (style == TouchStyle.G) {
            LinearGradient(
                0f, 0f, 0f, h,
                intArrayOf(opaque(TouchStyleMath.G_LIGHT), opaque(TouchStyleMath.G_BRONZE), opaque(TouchStyleMath.G_BRONZE)),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP,
            )
        } else {
            val e = (w + h) / 2f
            LinearGradient(
                0f, 0f, e, e,
                opaque(TouchStyleMath.F_EDGE_LIGHT), opaque(TouchStyleMath.F_EDGE_DARK),
                Shader.TileMode.CLAMP,
            )
        }

    /** Baut Schatten, Lichtkante und Haarlinie einer Form (Pfad in Koordinaten 0..w, 0..h). */
    private fun build(style: TouchStyle, role: TouchRole, path: Path, w: Int, h: Int, ox: Float, oy: Float): Layers {
        val d = density
        val ew = max(1f, TouchStyleMath.edgeWidthDp(style, role) * d)
        val hair = max(0.5f, TouchStyleMath.hairlineWidthDp(style) * d)
        val sh = TouchStyleMath.shadow(style, role)
        val pad = if (sh != null) ceil(sh.dyDp * d + blurRadius(sh.blurDp * d) + 2f).toInt() else 3
        val bw = w + 2 * pad
        val bh = h + 2 * pad

        var shadowBmp: Bitmap? = null
        if (sh != null) {
            val bm = Bitmap.createBitmap(bw, bh, Bitmap.Config.ALPHA_8)
            val c = Canvas(bm)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = Color.BLACK
            p.maskFilter = BlurMaskFilter(blurRadius(sh.blurDp * d), BlurMaskFilter.Blur.NORMAL)
            c.save()
            c.translate(pad.toFloat(), pad + sh.dyDp * d)
            c.drawPath(path, p)
            c.restore()
            if (TouchStyleMath.shadowOutsideOnly(style)) {
                c.save()
                c.translate(pad.toFloat(), pad.toFloat())
                c.drawPath(path, clearPaint(Paint.Style.FILL, 0f))
                c.restore()
            }
            shadowBmp = bm
        }

        val inv = invertedFill(path)

        val rim = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        run {
            val c = Canvas(rim)
            c.translate(pad.toFloat(), pad.toFloat())
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 2f * ew
            p.strokeJoin = Paint.Join.MITER
            p.shader = rimShader(style, w.toFloat(), h.toFloat())
            c.drawPath(path, p)
            c.drawPath(inv, clearPaint(Paint.Style.FILL, 0f))
        }

        val hairBmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ALPHA_8)
        run {
            val c = Canvas(hairBmp)
            c.translate(pad.toFloat(), pad.toFloat())
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 2f * (ew + hair)
            p.strokeJoin = Paint.Join.MITER
            p.color = Color.BLACK
            c.drawPath(path, p)
            c.drawPath(inv, clearPaint(Paint.Style.FILL, 0f))
            c.drawPath(path, clearPaint(Paint.Style.STROKE, 2f * ew))
        }

        return Layers(path, pad, w, h, shadowBmp, rim, hairBmp, ox, oy)
    }

    /** Der gedrückte Rand (innen an der Kante, einfarbig; die Farbe kommt beim Blit). */
    private fun ringOf(layers: Layers, style: TouchStyle): Bitmap {
        layers.ring?.let { return it }
        val pad = layers.pad
        val bm = Bitmap.createBitmap(layers.w + 2 * pad, layers.h + 2 * pad, Bitmap.Config.ALPHA_8)
        val c = Canvas(bm)
        c.translate(pad.toFloat(), pad.toFloat())
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f * max(1f, TouchStyleMath.pressedRingWidthDp(style) * density)
        p.strokeJoin = Paint.Join.MITER
        p.color = Color.BLACK
        c.drawPath(layers.path, p)
        c.drawPath(invertedFill(layers.path), clearPaint(Paint.Style.FILL, 0f))
        layers.ring = bm
        return bm
    }

    private fun layersFor(style: TouchStyle, role: TouchRole, shape: Int, w: Int, h: Int, c: Float): Layers {
        val key = Key(style.ordinal, role.ordinal, shape, w, h, (c * 10f).roundToInt(), 0, 0, 0, (density * 100f).roundToInt())
        cache.get(key)?.let { return it }
        val path = if (shape == SHAPE_OVAL) ovalPath(w.toFloat(), h.toFloat()) else chamferPath(w.toFloat(), h.toFloat(), c)
        val layers = build(style, role, path, w, h, 0f, 0f)
        cache.put(key, layers)
        return layers
    }

    private fun drawLayers(
        canvas: Canvas,
        layers: Layers,
        left: Float,
        top: Float,
        style: TouchStyle,
        role: TouchRole,
        pressed: Boolean,
        highlight: Boolean,
        ringColor: Int,
    ) {
        val contrast = TouchStyleSettings.contrast
        val level = frameLevel
        val pad = layers.pad.toFloat()
        canvas.save()
        canvas.translate(left, top)

        val sh = TouchStyleMath.shadow(style, role)
        val shadowBmp = layers.shadow
        if (sh != null && shadowBmp != null) {
            tintPaint.color = TouchStyleMath.withAlpha(0x000000, TouchStyleMath.scaleAlpha(sh.alpha, contrast))
            canvas.drawBitmap(shadowBmp, -pad, -pad, tintPaint)
        }

        fillPaint.color = TouchStyleMath.withAlpha(
            TouchStyleMath.fillRgb(style, role, pressed),
            TouchStyleMath.fillAlpha(style, role, pressed, level, contrast),
        )
        canvas.drawPath(layers.path, fillPaint)

        if (highlight) {
            val rgb = if (ringColor != 0) ringColor and 0xFFFFFF else TouchStyleMath.pressedRingRgb(style)
            tintPaint.color = TouchStyleMath.withAlpha(rgb, TouchStyleMath.pressedRingAlpha(style, contrast))
            canvas.drawBitmap(ringOf(layers, style), -pad, -pad, tintPaint)
        } else {
            alphaPaint.alpha = TouchStyleMath.edgeAlpha(style, role, level, contrast)
            canvas.drawBitmap(layers.rim, -pad, -pad, alphaPaint)
        }

        tintPaint.color = TouchStyleMath.withAlpha(0x000000, TouchStyleMath.hairlineAlpha(style, contrast))
        canvas.drawBitmap(layers.hair, -pad, -pad, tintPaint)
        canvas.restore()
    }

    /**
     * Rahmen mit Füllung, Schatten und Kante um das Rechteck ([left], [top], [right], [bottom]). [shape] ist [SHAPE_CHAMFER]
     * (abgeschrägt) oder [SHAPE_OVAL]; [role] bestimmt Farben und Abschrägung. [pressed]: gedrückter Zustand (Füllung);
     * [highlight]: gedrückter Rand (warmes Gold, bei Auswahl im Editor mit [ringColor], sonst 0).
     */
    @JvmStatic
    fun drawFrame(
        canvas: Canvas,
        shape: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        role: Int,
        pressed: Boolean,
        highlight: Boolean,
        ringColor: Int,
    ) {
        val style = TouchStyleSettings.style
        if (style == TouchStyle.CURRENT) return
        val w = (right - left).roundToInt()
        val h = (bottom - top).roundToInt()
        if (w < 2 || h < 2) return
        val r = roles[role.coerceIn(0, roles.size - 1)]
        val c = TouchStyleMath.chamferFraction(style, r) * min(w, h)
        val layers = layersFor(style, r, shape, w, h, c)
        drawLayers(canvas, layers, left, top, style, r, pressed, highlight, ringColor)
    }

    /** Ringsegment (Tastenring, Bogenknopf) um ([cx], [cy]) von [innerRadius] bis [outerRadius], Winkel in Grad. */
    @JvmStatic
    fun drawSegment(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        innerRadius: Float,
        outerRadius: Float,
        startDeg: Float,
        sweepDeg: Float,
        pressed: Boolean,
        highlight: Boolean,
        ringColor: Int,
    ) {
        val style = TouchStyleSettings.style
        if (style == TouchStyle.CURRENT || outerRadius <= innerRadius || sweepDeg <= 0f) return
        val role = TouchRole.SEGMENT
        val key = Key(
            style.ordinal, role.ordinal, SHAPE_SECTOR, 0, 0,
            (startDeg * 10f).roundToInt(), (sweepDeg * 10f).roundToInt(), innerRadius.roundToInt(), outerRadius.roundToInt(),
            (density * 100f).roundToInt(),
        )
        var layers = cache.get(key)
        if (layers == null) {
            val path = sectorPath(innerRadius, outerRadius, startDeg, sweepDeg)
            val bounds = RectF()
            path.computeBounds(bounds, true)
            path.offset(-bounds.left, -bounds.top)
            layers = build(
                style, role, path,
                max(2, ceil(bounds.width()).toInt()), max(2, ceil(bounds.height()).toInt()),
                bounds.left, bounds.top,
            )
            cache.put(key, layers)
        }
        drawLayers(canvas, layers, cx + layers.ox, cy + layers.oy, style, role, pressed, highlight, ringColor)
    }

    // ---- Schrift, Pfeile, Rillen, Symbol-Tönung ------------------------------------------------------------------------

    private val serif: Typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = serif }

    /** Serifenschrift der Classic-Stile (für Beschriftungen, die ControlElement selbst setzt). */
    @JvmStatic
    fun typeface(): Typeface = serif

    /** Halbe Breite der dunklen Kontur um Beschriftung in Pixel (auch für die Symbole von [KeyGlyphDraw]). */
    @JvmStatic
    fun labelOutlinePx(): Float = (if (TouchStyleSettings.style == TouchStyle.G) 0.77f else 0.7f) * density

    /** Versatz der Beschriftung nach unten rechts beim Drücken (nur Stil F), in Pixel. */
    @JvmStatic
    fun pressedLabelOffsetPx(pressed: Boolean): Float = if (TouchStyleSettings.style == TouchStyle.F && pressed) 1.15f * density else 0f

    /** Beschriftung mittig um ([cx], [cy]) in Größe [size]; die Verkleinerung auf 94 % und beim Drücken macht diese Methode. */
    @JvmStatic
    fun drawLabel(canvas: Canvas, text: String, cx: Float, cy: Float, size: Float, pressed: Boolean) {
        val style = TouchStyleSettings.style
        if (style == TouchStyle.CURRENT || text.isEmpty()) return
        val p = textPaint
        p.textSize = max(4f, size * TouchStyleMath.textScale(style, pressed))
        val off = pressedLabelOffsetPx(pressed)
        val baseline = cy + off - (p.descent() + p.ascent()) * 0.5f
        val outline = labelOutlinePx()

        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f * outline
        p.strokeJoin = Paint.Join.ROUND
        p.color = TouchStyleMath.argb(235, 0, 0, 0)
        p.clearShadowLayer()
        canvas.drawText(text, cx + off, baseline, p)

        p.style = Paint.Style.FILL
        p.color = TouchStyleMath.textColor(style, pressed)
        if (style == TouchStyle.G) {
            p.setShadowLayer(2.3f * density, 0f, 1.15f * density, TouchStyleMath.argb(210, 0, 0, 0))
        } else {
            p.setShadowLayer(0.5f, 0f, 0.77f * density, TouchStyleMath.argb(120, 0, 0, 0))
        }
        canvas.drawText(text, cx + off, baseline, p)
        p.clearShadowLayer()
    }

    private val arrowPath = Path()

    /** Pfeilspitze des Mausrads (Richtung [DIR_UP] bis [DIR_LEFT]) mit kleinem dunklem Schatten. */
    @JvmStatic
    fun drawArrow(canvas: Canvas, cx: Float, cy: Float, direction: Int, size: Float) {
        val style = TouchStyleSettings.style
        if (style == TouchStyle.CURRENT) return
        val s = max(2f, size)
        val p = arrowPath
        p.reset()
        // drei Eckpunkte des Dreiecks
        val pts: FloatArray = when (direction) {
            DIR_UP -> floatArrayOf(cx - s, cy + s * 0.6f, cx + s, cy + s * 0.6f, cx, cy - s * 0.7f)
            DIR_DOWN -> floatArrayOf(cx - s, cy - s * 0.6f, cx + s, cy - s * 0.6f, cx, cy + s * 0.7f)
            DIR_LEFT -> floatArrayOf(cx + s * 0.6f, cy - s, cx + s * 0.6f, cy + s, cx - s * 0.7f, cy)
            else -> floatArrayOf(cx - s * 0.6f, cy - s, cx - s * 0.6f, cy + s, cx + s * 0.7f, cy)
        }
        p.moveTo(pts[0], pts[1])
        p.lineTo(pts[2], pts[3])
        p.lineTo(pts[4], pts[5])
        p.close()
        fillPaint.color = TouchStyleMath.argb(120, 0, 0, 0)
        canvas.save()
        canvas.translate(0f, density * 0.77f)
        canvas.drawPath(p, fillPaint)
        canvas.restore()
        val rgb = if (style == TouchStyle.G) TouchStyleMath.G_TEXT else TouchStyleMath.F_TEXT
        fillPaint.color = TouchStyleMath.withAlpha(rgb, 215)
        canvas.drawPath(p, fillPaint)
    }

    /** Farbe der Rillen des Mausrads (ARGB). */
    @JvmStatic
    fun ribColor(): Int =
        if (TouchStyleSettings.style == TouchStyle.G) {
            TouchStyleMath.withAlpha(TouchStyleMath.G_TEXT, 38)
        } else {
            TouchStyleMath.withAlpha(TouchStyleMath.F_RIB, 36)
        }

    private val tintRect = RectF()

    /** Dämpfende Tönung über dem Symbol (Farben ruhiger). [kind]: 0 Rechteck, 1 abgerundet, 2 Oval (wie bei WowIconDraw). */
    @JvmStatic
    fun tintIcon(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, kind: Int, radius: Float, pressed: Boolean) {
        val style = TouchStyleSettings.style
        if (style == TouchStyle.CURRENT) return
        fillPaint.color = TouchStyleMath.iconTint(style, pressed)
        tintRect.set(left, top, right, bottom)
        when (kind) {
            2 -> canvas.drawOval(tintRect, fillPaint)
            1 -> canvas.drawRoundRect(tintRect, radius, radius, fillPaint)
            else -> canvas.drawRect(tintRect, fillPaint)
        }
    }

    // ---- Joystick als weiche Fläche (G) --------------------------------------------------------------------------------

    private val shaders = HashMap<Long, Shader>()
    private val softPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val wedgeRect = RectF()
    private val placeholder = Path()

    private fun profileShader(kind: Int, radius: Float, rgb: Int): Shader {
        val key = (kind.toLong() shl 56) or (radius.roundToInt().toLong() shl 28) or (rgb.toLong() and 0xFFFFFF)
        shaders[key]?.let { return it }
        if (shaders.size > 48) shaders.clear()
        val n = 9
        val colors = IntArray(n)
        val pos = FloatArray(n)
        for (i in 0 until n) {
            val t = i / (n - 1).toFloat()
            pos[i] = t
            val a = when (kind) {
                KIND_UNDERLAY -> TouchStyleMath.softUnderlayAlpha(t) / TouchStyleMath.UNDERLAY_PEAK
                KIND_GLOW -> TouchStyleMath.softGlowAlpha(t) / TouchStyleMath.GLOW_PEAK
                else -> 1f - TouchStyleMath.smoothstep(0.45f, 1f, t) // Kern unter dem Daumen
            }
            colors[i] = TouchStyleMath.withAlpha(rgb, (a.coerceIn(0f, 1f) * 255f).roundToInt())
        }
        val s = RadialGradient(0f, 0f, max(1f, radius), colors, pos, Shader.TileMode.CLAMP)
        shaders[key] = s
        return s
    }

    private fun wedgeShader(radius: Float): Shader {
        val key = (KIND_WEDGE.toLong() shl 56) or (radius.roundToInt().toLong() shl 28)
        shaders[key]?.let { return it }
        if (shaders.size > 48) shaders.clear()
        // Helles Band am Rand: von 0,62 bis 0,95 des Joystickradius auf voll, bis 1,3 wieder aus (Radius des Shaders = 1,3).
        val light = 0x00FFE6A0
        val colors = intArrayOf(light, light, light or (255 shl 24), light or (255 shl 24), light)
        val pos = floatArrayOf(0f, 0.62f / 1.3f, 0.95f / 1.3f, 1.0f / 1.3f, 1f)
        val s = RadialGradient(0f, 0f, max(1f, radius), colors, pos, Shader.TileMode.CLAMP)
        shaders[key] = s
        return s
    }

    private const val KIND_UNDERLAY = 1
    private const val KIND_GLOW = 2
    private const val KIND_CORE = 3
    private const val KIND_WEDGE = 4

    private fun drawProfile(canvas: Canvas, cx: Float, cy: Float, radius: Float, shader: Shader, alpha: Int) {
        if (alpha <= 0) return
        softPaint.shader = shader
        softPaint.alpha = alpha
        canvas.save()
        canvas.translate(cx, cy)
        canvas.drawCircle(0f, 0f, radius, softPaint)
        canvas.restore()
        softPaint.shader = null
    }

    /**
     * Joystick als weiche Fläche ohne Ring und feste Scheibe (G). [radius]: Weg des Daumens (Schein bis dorthin, dunkle Unterlage
     * etwas weiter); ([thumbX], [thumbY]) die Lage des Daumens; [touched]: der Finger liegt auf dem Stick. In Ruhe nur ein
     * Platzhalter (20 %, mit dunkler Kontur, damit er auf hellem Grund auffindbar bleibt). Berührt: dunkle Unterlage 33 %,
     * warmer Schein, Kern unter dem Daumen mit dunklem Rand, bei Auslenkung ein helles Segment am Rand.
     */
    @JvmStatic
    fun drawSoftStick(canvas: Canvas, cx: Float, cy: Float, radius: Float, thumbX: Float, thumbY: Float, touched: Boolean) {
        val contrast = TouchStyleSettings.contrast
        val d = density
        if (!touched) {
            drawPlaceholder(canvas, cx, cy, min(radius * 0.5f, 34f * d), contrast)
            return
        }
        val rf = max(8f, radius)
        val ru = rf * 1.45f
        val underlay = (255f * TouchStyleMath.UNDERLAY_PEAK * TouchStyleMath.clampContrast(contrast)).roundToInt().coerceIn(0, 255)
        drawProfile(canvas, cx, cy, ru, profileShader(KIND_UNDERLAY, ru, 0x000000), underlay)
        drawProfile(canvas, cx, cy, rf, profileShader(KIND_GLOW, rf, TouchStyleMath.G_WARM), (255f * TouchStyleMath.GLOW_PEAK).roundToInt())

        val dx = thumbX - cx
        val dy = thumbY - cy
        val defl = (hypot(dx, dy) / rf).coerceIn(0f, 1f)
        if (defl > 0.12f) {
            val angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
            val strength = 0.35f + 0.5f * defl
            val steps = 5
            softPaint.shader = wedgeShader(rf * 1.3f)
            softPaint.alpha = (255f * strength / steps * 1.6f).roundToInt().coerceIn(0, 255)
            canvas.save()
            canvas.translate(cx, cy)
            wedgeRect.set(-rf * 1.3f, -rf * 1.3f, rf * 1.3f, rf * 1.3f)
            for (k in 1..steps) {
                val half = 25f * k / steps
                canvas.drawArc(wedgeRect, angle - half, 2f * half, true, softPaint)
            }
            canvas.restore()
            softPaint.shader = null
        }

        val core = max(12f * d, rf * 0.25f)
        drawProfile(canvas, thumbX, thumbY, core, profileShader(KIND_CORE, core, 0xFFEEC8), (255f * 0.7f).roundToInt())
        // dunkler Rand um den Kern, damit er auf hellem Grund nicht verschwimmt
        softPaint.style = Paint.Style.STROKE
        softPaint.strokeWidth = 1.4f * d
        softPaint.color = TouchStyleMath.argb(TouchStyleMath.scaleAlpha(140, contrast), 0, 0, 0)
        canvas.drawCircle(thumbX, thumbY, core * 0.72f, softPaint)
        softPaint.style = Paint.Style.FILL
    }

    private fun drawPlaceholder(canvas: Canvas, cx: Float, cy: Float, extent: Float, contrast: Float) {
        // Maße der Vorschau (Tafel-Pixel): Mitte 5, Spitze 44, Fuß 28, halbe Breite 11; hier auf [extent] skaliert.
        val u = extent / 44f
        val p = placeholder
        p.reset()
        p.addCircle(cx, cy, 5f * u, Path.Direction.CW)
        for (deg in intArrayOf(0, 90, 180, 270)) {
            val a = Math.toRadians(deg.toDouble())
            val ux = cos(a).toFloat()
            val uy = sin(a).toFloat()
            val px = -uy
            val py = ux
            p.moveTo(cx + ux * 44f * u, cy + uy * 44f * u)
            p.lineTo(cx + ux * 28f * u + px * 11f * u, cy + uy * 28f * u + py * 11f * u)
            p.lineTo(cx + ux * 28f * u - px * 11f * u, cy + uy * 28f * u - py * 11f * u)
            p.close()
        }
        // erst die dunkle Kontur (liegt um die helle Fläche), dann die helle Fläche
        softPaint.style = Paint.Style.STROKE
        softPaint.strokeWidth = 2.2f * density
        softPaint.strokeJoin = Paint.Join.ROUND
        softPaint.color = TouchStyleMath.argb(TouchStyleMath.scaleAlpha(110, contrast), 0, 0, 0)
        canvas.drawPath(p, softPaint)
        softPaint.style = Paint.Style.FILL
        val faint = TouchStyleMath.scaleAlpha(TouchStyleMath.PLACEHOLDER_ALPHA, contrast)
        softPaint.color = TouchStyleMath.withAlpha(TouchStyleMath.G_TEXT, faint)
        canvas.drawPath(p, softPaint)
    }
}
