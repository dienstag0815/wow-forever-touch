package app.gamenative.wowaction

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.winlator.inputcontrols.BindingCombo
import com.winlator.inputcontrols.WowActionBindings
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/**
 * Own vector symbols (drawn with canvas primitives, no Blizzard graphics) for the wheel and the buttons.
 * Every symbol is drawn inside the square [-1, 1] x [-1, 1] scaled to the given half size.
 */
object WowSymbols {
    /** Names of all symbols [draw] knows (unknown names fall back to a ring). */
    val KNOWN: Set<String> = setOf(
        "map", "talents", "hammer", "book", "bag", "quest", "person", "group", "gem", "star", "banner", "journal",
        "gear", "eye", "camera", "target", "cursor", "slot", "chat", "arrow_up", "arrow_down", "arrow_left",
        "arrow_right", "run", "sit", "sword", "ring", "reload",
        "emote_wave", "emote_bye", "emote_dance", "emote_cheer", "emote_laugh", "emote_bow", "emote_heart",
        "emote_clap", "emote_cry", "emote_roar", "emote_wink", "emote_kiss", "emote_point", "emote_salute",
        "emote_sleep", "emote_yes", "emote_no", "emote_dice",
    )

    private val paint by lazy { Paint(Paint.ANTI_ALIAS_FLAG) }
    private val path by lazy { Path() }
    private val rect by lazy { RectF() }

    /** Symbol name for a WoW action id, null if the id is not a catalog action (called from Java). */
    @JvmStatic
    fun symbolFor(actionId: String?): String? = WowActionCatalog.symbolOf(actionId)

    @JvmStatic
    fun draw(canvas: Canvas, symbol: String, cx: Float, cy: Float, halfSize: Float, color: Int) {
        if (halfSize <= 1f) return
        val p = paint
        val r = halfSize
        p.reset()
        p.isAntiAlias = true
        p.color = color
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.style = Paint.Style.STROKE
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(r, r)
        // Stroke width is given in symbol units after the scale.
        p.strokeWidth = 0.15f
        when (symbol) {
            "map" -> {
                rect.set(-0.85f, -0.7f, 0.85f, 0.7f)
                canvas.drawRect(rect, p)
                canvas.drawLine(-0.28f, -0.7f, -0.28f, 0.7f, p)
                canvas.drawLine(0.28f, -0.7f, 0.28f, 0.7f, p)
            }
            "talents" -> {
                canvas.drawLine(0f, -0.55f, -0.6f, 0.5f, p)
                canvas.drawLine(0f, -0.55f, 0.6f, 0.5f, p)
                canvas.drawLine(-0.6f, 0.5f, 0.6f, 0.5f, p)
                fill(p)
                canvas.drawCircle(0f, -0.55f, 0.25f, p)
                canvas.drawCircle(-0.6f, 0.5f, 0.25f, p)
                canvas.drawCircle(0.6f, 0.5f, 0.25f, p)
            }
            "hammer" -> {
                canvas.drawLine(-0.7f, 0.8f, 0.2f, -0.1f, p)
                p.strokeWidth = 0.4f
                canvas.drawLine(-0.05f, -0.7f, 0.75f, 0.1f, p)
            }
            "book" -> {
                rect.set(-0.7f, -0.85f, 0.7f, 0.85f)
                canvas.drawRoundRect(rect, 0.12f, 0.12f, p)
                canvas.drawLine(-0.35f, -0.85f, -0.35f, 0.85f, p)
                canvas.drawLine(-0.05f, -0.35f, 0.4f, -0.35f, p)
                canvas.drawLine(-0.05f, 0.05f, 0.4f, 0.05f, p)
            }
            "journal" -> {
                rect.set(-0.6f, -0.85f, 0.6f, 0.85f)
                canvas.drawRoundRect(rect, 0.12f, 0.12f, p)
                canvas.drawLine(-0.3f, -0.45f, 0.3f, -0.45f, p)
                canvas.drawLine(-0.3f, -0.05f, 0.3f, -0.05f, p)
                canvas.drawLine(-0.3f, 0.35f, 0.1f, 0.35f, p)
            }
            "bag" -> {
                // Drawstring pouch: round belly, tied neck and a flared rim. (The old rectangle with an arc on top
                // looked like a padlock.)
                path.rewind()
                path.moveTo(-0.24f, -0.4f)
                path.cubicTo(-1.0f, 0.0f, -0.9f, 0.92f, 0f, 0.92f)
                path.cubicTo(0.9f, 0.92f, 1.0f, 0.0f, 0.24f, -0.4f)
                canvas.drawPath(path, p)
                canvas.drawLine(-0.3f, -0.4f, 0.3f, -0.4f, p)
                path.rewind()
                path.moveTo(-0.24f, -0.4f)
                path.lineTo(-0.5f, -0.88f)
                path.quadTo(0f, -0.62f, 0.5f, -0.88f)
                path.lineTo(0.24f, -0.4f)
                canvas.drawPath(path, p)
            }
            "quest" -> {
                p.strokeWidth = 0.26f
                canvas.drawLine(0f, -0.8f, 0f, 0.25f, p)
                fill(p)
                canvas.drawCircle(0f, 0.7f, 0.2f, p)
            }
            "person" -> {
                canvas.drawCircle(0f, -0.45f, 0.3f, p)
                rect.set(-0.65f, 0.1f, 0.65f, 1.2f)
                canvas.drawArc(rect, 180f, 180f, false, p)
            }
            "group" -> {
                canvas.drawCircle(-0.4f, -0.45f, 0.25f, p)
                canvas.drawCircle(0.4f, -0.45f, 0.25f, p)
                rect.set(-0.85f, 0.05f, 0.05f, 0.95f)
                canvas.drawArc(rect, 180f, 180f, false, p)
                rect.set(-0.05f, 0.05f, 0.85f, 0.95f)
                canvas.drawArc(rect, 180f, 180f, false, p)
            }
            "gem" -> {
                path.rewind()
                path.moveTo(0f, -0.8f)
                path.lineTo(0.7f, -0.2f)
                path.lineTo(0f, 0.8f)
                path.lineTo(-0.7f, -0.2f)
                path.close()
                canvas.drawPath(path, p)
                canvas.drawLine(-0.7f, -0.2f, 0.7f, -0.2f, p)
            }
            "star" -> {
                path.rewind()
                for (i in 0 until 10) {
                    val radius = if (i % 2 == 0) 0.9f else 0.4f
                    val angle = Math.toRadians(-90.0 + i * 36.0)
                    val x = (cos(angle) * radius).toFloat()
                    val y = (sin(angle) * radius).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                canvas.drawPath(path, p)
            }
            "banner" -> {
                canvas.drawLine(-0.6f, -0.85f, -0.6f, 0.85f, p)
                path.rewind()
                path.moveTo(-0.6f, -0.8f)
                path.lineTo(0.7f, -0.8f)
                path.lineTo(0.4f, -0.35f)
                path.lineTo(0.7f, 0.1f)
                path.lineTo(-0.6f, 0.1f)
                canvas.drawPath(path, p)
            }
            "gear" -> {
                canvas.drawCircle(0f, 0f, 0.35f, p)
                for (i in 0 until 8) {
                    val angle = Math.toRadians(i * 45.0)
                    val c = cos(angle).toFloat()
                    val s = sin(angle).toFloat()
                    canvas.drawLine(c * 0.58f, s * 0.58f, c * 0.85f, s * 0.85f, p)
                }
            }
            "eye" -> {
                rect.set(-0.9f, -0.5f, 0.9f, 0.5f)
                canvas.drawOval(rect, p)
                fill(p)
                canvas.drawCircle(0f, 0f, 0.22f, p)
            }
            "camera" -> {
                rect.set(-0.85f, -0.45f, 0.85f, 0.7f)
                canvas.drawRoundRect(rect, 0.15f, 0.15f, p)
                canvas.drawCircle(0f, 0.12f, 0.3f, p)
                canvas.drawLine(-0.3f, -0.45f, -0.15f, -0.75f, p)
                canvas.drawLine(-0.15f, -0.75f, 0.15f, -0.75f, p)
                canvas.drawLine(0.15f, -0.75f, 0.3f, -0.45f, p)
            }
            "target" -> {
                canvas.drawCircle(0f, 0f, 0.55f, p)
                canvas.drawLine(0f, -0.95f, 0f, -0.35f, p)
                canvas.drawLine(0f, 0.35f, 0f, 0.95f, p)
                canvas.drawLine(-0.95f, 0f, -0.35f, 0f, p)
                canvas.drawLine(0.35f, 0f, 0.95f, 0f, p)
            }
            "cursor" -> {
                path.rewind()
                path.moveTo(-0.5f, -0.85f)
                path.lineTo(-0.5f, 0.6f)
                path.lineTo(-0.15f, 0.3f)
                path.lineTo(0.15f, 0.9f)
                path.lineTo(0.4f, 0.78f)
                path.lineTo(0.1f, 0.2f)
                path.lineTo(0.6f, 0.2f)
                path.close()
                canvas.drawPath(path, p)
            }
            "reload" -> {
                // Circular arrow: an arc of 300 degrees with an arrow head at its end.
                rect.set(-0.62f, -0.62f, 0.62f, 0.62f)
                canvas.drawArc(rect, -60f, 300f, false, p)
                // end of the arc at -60 degrees (upper right); head pointing along the direction of travel
                val a = Math.toRadians(-60.0)
                val ex = (cos(a) * 0.62).toFloat()
                val ey = (sin(a) * 0.62).toFloat()
                canvas.drawLine(ex, ey, ex + 0.42f, ey - 0.02f, p)
                canvas.drawLine(ex, ey, ex + 0.02f, ey + 0.44f, p)
            }
            "slot" -> {
                rect.set(-0.8f, -0.8f, 0.8f, 0.8f)
                canvas.drawRoundRect(rect, 0.25f, 0.25f, p)
            }
            "chat" -> {
                rect.set(-0.85f, -0.7f, 0.85f, 0.3f)
                canvas.drawRoundRect(rect, 0.25f, 0.25f, p)
                path.rewind()
                path.moveTo(-0.4f, 0.3f)
                path.lineTo(-0.55f, 0.85f)
                path.lineTo(0.1f, 0.3f)
                canvas.drawPath(path, p)
            }
            "arrow_up" -> arrow(canvas, p, 0f, false)
            "arrow_right" -> arrow(canvas, p, 90f, false)
            "arrow_down" -> arrow(canvas, p, 180f, false)
            "arrow_left" -> arrow(canvas, p, 270f, false)
            "run" -> arrow(canvas, p, 0f, true)
            "sit" -> {
                path.rewind()
                path.moveTo(-0.5f, -0.85f)
                path.lineTo(-0.5f, 0.15f)
                path.lineTo(0.6f, 0.15f)
                path.lineTo(0.6f, 0.85f)
                canvas.drawPath(path, p)
                canvas.drawLine(-0.5f, 0.85f, -0.5f, 0.15f, p)
            }
            "sword" -> {
                canvas.drawLine(-0.7f, 0.7f, 0.7f, -0.7f, p)
                canvas.drawLine(-0.45f, 0.05f, 0.05f, 0.55f, p)
            }
            "emote_wave" -> {
                rect.set(-0.55f, -0.05f, 0.2f, 0.8f)
                canvas.drawRoundRect(rect, 0.2f, 0.2f, p)
                canvas.drawLine(-0.4f, -0.05f, -0.4f, -0.65f, p)
                canvas.drawLine(-0.17f, -0.05f, -0.17f, -0.8f, p)
                canvas.drawLine(0.06f, -0.05f, 0.06f, -0.65f, p)
                rect.set(0.3f, -0.45f, 0.75f, 0.25f)
                canvas.drawArc(rect, -60f, 120f, false, p)
                rect.set(0.45f, -0.75f, 1.0f, 0.55f)
                canvas.drawArc(rect, -60f, 120f, false, p)
            }
            "emote_bye" -> {
                canvas.drawLine(-0.7f, -0.8f, -0.7f, 0.8f, p)
                canvas.drawLine(-0.3f, 0f, 0.8f, 0f, p)
                canvas.drawLine(0.4f, -0.4f, 0.8f, 0f, p)
                canvas.drawLine(0.4f, 0.4f, 0.8f, 0f, p)
            }
            "emote_dance" -> {
                canvas.drawLine(0.25f, -0.8f, 0.25f, 0.45f, p)
                canvas.drawLine(0.25f, -0.8f, 0.75f, -0.45f, p)
                fill(p)
                rect.set(-0.4f, 0.25f, 0.25f, 0.8f)
                canvas.drawOval(rect, p)
            }
            "emote_cheer" -> {
                canvas.drawCircle(0f, -0.62f, 0.22f, p)
                canvas.drawLine(0f, -0.3f, 0f, 0.35f, p)
                canvas.drawLine(0f, -0.1f, -0.65f, -0.75f, p)
                canvas.drawLine(0f, -0.1f, 0.65f, -0.75f, p)
                canvas.drawLine(0f, 0.35f, -0.4f, 0.9f, p)
                canvas.drawLine(0f, 0.35f, 0.4f, 0.9f, p)
            }
            "emote_laugh" -> {
                canvas.drawCircle(0f, 0f, 0.85f, p)
                fill(p)
                canvas.drawCircle(-0.3f, -0.28f, 0.1f, p)
                canvas.drawCircle(0.3f, -0.28f, 0.1f, p)
                rect.set(-0.5f, -0.15f, 0.5f, 0.55f)
                canvas.drawArc(rect, 0f, 180f, true, p)
            }
            "emote_bow" -> {
                canvas.drawLine(-0.3f, 0.2f, -0.3f, 0.9f, p)
                canvas.drawLine(-0.3f, 0.2f, 0.4f, -0.2f, p)
                canvas.drawCircle(0.65f, -0.38f, 0.22f, p)
            }
            "emote_heart" -> {
                path.rewind()
                path.moveTo(0f, 0.8f)
                path.cubicTo(-1.0f, 0.0f, -0.7f, -0.8f, 0f, -0.3f)
                path.cubicTo(0.7f, -0.8f, 1.0f, 0.0f, 0f, 0.8f)
                path.close()
                canvas.drawPath(path, p)
            }
            "emote_clap" -> {
                p.strokeWidth = 0.3f
                canvas.drawLine(-0.8f, 0.7f, -0.1f, 0f, p)
                canvas.drawLine(0.8f, 0.7f, 0.1f, 0f, p)
                p.strokeWidth = 0.12f
                canvas.drawLine(0f, -0.3f, 0f, -0.85f, p)
                canvas.drawLine(-0.35f, -0.25f, -0.6f, -0.65f, p)
                canvas.drawLine(0.35f, -0.25f, 0.6f, -0.65f, p)
            }
            "emote_cry" -> {
                canvas.drawCircle(0f, 0f, 0.85f, p)
                fill(p)
                canvas.drawCircle(-0.3f, -0.25f, 0.1f, p)
                canvas.drawCircle(0.3f, -0.25f, 0.1f, p)
                canvas.drawCircle(-0.32f, 0.05f, 0.12f, p)
                p.style = Paint.Style.STROKE
                rect.set(-0.45f, 0.2f, 0.45f, 0.8f)
                canvas.drawArc(rect, 210f, 120f, false, p)
            }
            "emote_roar" -> {
                canvas.drawCircle(0f, 0f, 0.85f, p)
                canvas.drawLine(-0.55f, -0.5f, -0.15f, -0.25f, p)
                canvas.drawLine(0.55f, -0.5f, 0.15f, -0.25f, p)
                path.rewind()
                path.moveTo(-0.5f, 0.2f)
                path.lineTo(-0.3f, 0.55f)
                path.lineTo(-0.1f, 0.2f)
                path.lineTo(0.1f, 0.55f)
                path.lineTo(0.3f, 0.2f)
                path.lineTo(0.5f, 0.55f)
                canvas.drawPath(path, p)
            }
            "emote_wink" -> {
                canvas.drawCircle(0f, 0f, 0.85f, p)
                canvas.drawLine(0.15f, -0.25f, 0.45f, -0.25f, p)
                rect.set(-0.5f, -0.2f, 0.5f, 0.5f)
                canvas.drawArc(rect, 20f, 140f, false, p)
                fill(p)
                canvas.drawCircle(-0.3f, -0.25f, 0.1f, p)
            }
            "emote_kiss" -> {
                path.rewind()
                path.moveTo(-0.8f, 0f)
                path.quadTo(-0.4f, -0.7f, 0f, -0.15f)
                path.quadTo(0.4f, -0.7f, 0.8f, 0f)
                path.quadTo(0f, 0.95f, -0.8f, 0f)
                path.close()
                canvas.drawPath(path, p)
                canvas.drawLine(-0.8f, 0f, 0.8f, 0f, p)
            }
            "emote_point" -> {
                rect.set(-0.85f, -0.35f, -0.15f, 0.4f)
                canvas.drawRoundRect(rect, 0.2f, 0.2f, p)
                p.strokeWidth = 0.25f
                canvas.drawLine(-0.15f, -0.05f, 0.85f, -0.05f, p)
            }
            "emote_salute" -> {
                canvas.drawCircle(0f, -0.35f, 0.3f, p)
                rect.set(-0.65f, 0.15f, 0.65f, 1.25f)
                canvas.drawArc(rect, 180f, 180f, false, p)
                path.rewind()
                path.moveTo(0.45f, 0.5f)
                path.lineTo(0.8f, -0.1f)
                path.lineTo(0.25f, -0.6f)
                canvas.drawPath(path, p)
            }
            "emote_sleep" -> {
                path.rewind()
                path.moveTo(-0.7f, -0.6f)
                path.lineTo(0f, -0.6f)
                path.lineTo(-0.7f, 0.1f)
                path.lineTo(0f, 0.1f)
                canvas.drawPath(path, p)
                path.rewind()
                path.moveTo(0.2f, 0.2f)
                path.lineTo(0.7f, 0.2f)
                path.lineTo(0.2f, 0.75f)
                path.lineTo(0.7f, 0.75f)
                canvas.drawPath(path, p)
            }
            "emote_yes" -> {
                p.strokeWidth = 0.22f
                path.rewind()
                path.moveTo(-0.75f, 0.05f)
                path.lineTo(-0.25f, 0.6f)
                path.lineTo(0.8f, -0.6f)
                canvas.drawPath(path, p)
            }
            "emote_no" -> {
                p.strokeWidth = 0.22f
                canvas.drawLine(-0.65f, -0.65f, 0.65f, 0.65f, p)
                canvas.drawLine(-0.65f, 0.65f, 0.65f, -0.65f, p)
            }
            "emote_dice" -> {
                rect.set(-0.8f, -0.8f, 0.8f, 0.8f)
                canvas.drawRoundRect(rect, 0.2f, 0.2f, p)
                fill(p)
                canvas.drawCircle(-0.4f, -0.4f, 0.1f, p)
                canvas.drawCircle(0.4f, -0.4f, 0.1f, p)
                canvas.drawCircle(0f, 0f, 0.1f, p)
                canvas.drawCircle(-0.4f, 0.4f, 0.1f, p)
                canvas.drawCircle(0.4f, 0.4f, 0.1f, p)
            }
            else -> canvas.drawCircle(0f, 0f, 0.6f, p)
        }
        canvas.restore()
        p.reset()
    }

    private fun fill(p: Paint) {
        p.style = Paint.Style.FILL
    }

    private fun arrow(canvas: Canvas, p: Paint, rotation: Float, twice: Boolean) {
        canvas.save()
        canvas.rotate(rotation)
        canvas.drawLine(0f, 0.8f, 0f, -0.75f, p)
        canvas.drawLine(-0.5f, -0.25f, 0f, -0.8f, p)
        canvas.drawLine(0.5f, -0.25f, 0f, -0.8f, p)
        if (twice) {
            canvas.drawLine(-0.5f, 0.25f, 0f, -0.3f, p)
            canvas.drawLine(0.5f, 0.25f, 0f, -0.3f, p)
        }
        canvas.restore()
    }
}

/**
 * Loads the player's WoW key bindings from the game folder and hands the resolved keys to the profile code
 * ([WowActionBindings]) so wheel fields and buttons that store a WoW action use the current key.
 */
object WowActionSource {
    /** Reads the files below [gameRoot], resolves all catalog actions and publishes them. Never throws. */
    fun refresh(gameRoot: File): Map<String, ResolvedKey> {
        val resolved = resolveFor(gameRoot)
        publish(resolved)
        return resolved
    }

    /** Resolved keys for display; does not publish. Never throws (missing/unreadable folder = standard keys). */
    fun resolveFor(gameRoot: File): Map<String, ResolvedKey> {
        val bindings = try {
            WowBindingsLoader.load(gameRoot)
        } catch (e: Exception) {
            null
        }
        return WowActionResolver.resolveAll(bindings)
    }

    fun publish(resolved: Map<String, ResolvedKey>) {
        WowActionBindings.setResolved(resolved.mapValues { it.value.combo })
    }

    /** Key combinations by action id (as needed for new radial fields). */
    fun combos(resolved: Map<String, ResolvedKey>): Map<String, BindingCombo> = resolved.mapValues { it.value.combo }
}
