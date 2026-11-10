package app.gamenative.ui.screen.xserver.editor

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import app.gamenative.ui.touchstyle.RadialArc
import com.winlator.inputcontrols.Binding
import com.winlator.inputcontrols.ControlElement
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.annotation.StringRes
import app.gamenative.R

/** Kind of picture shown in the "+" gallery and in the settings sheet. */
enum class PreviewKind(@StringRes val label: Int) {
    BUTTON(R.string.editor_kind_button),
    WIDE_BUTTON(R.string.editor_kind_wide_button),
    KEY_STRIP(R.string.editor_kind_key_strip),
    D_PAD(R.string.editor_kind_d_pad),
    STICK(R.string.editor_kind_stick),
    STICK_RING(R.string.editor_kind_stick_ring),
    SCROLL_WHEEL(R.string.editor_kind_scroll_wheel),
    RADIAL(R.string.editor_kind_radial),
    RADIAL_BAR(R.string.editor_kind_radial_bar),
    RADIAL_GAME(R.string.editor_kind_radial_game),
    EMOTE(R.string.editor_kind_emote),
    VOICE(R.string.editor_kind_voice),
    MENU(R.string.editor_kind_menu),
    KEYBOARD(R.string.editor_kind_keyboard),
    EDIT(R.string.editor_kind_edit),
    RELOAD(R.string.editor_kind_reload),
    TRACKPAD(R.string.editor_kind_trackpad),
    SHOOTER(R.string.editor_kind_shooter),
    GENERIC(R.string.editor_kind_generic),
}

/** Which picture fits an element of the profile (types that do not exist yet in this build fall back to their name). */
fun previewKindOf(element: ControlElement): PreviewKind = when (element.type.name) {
    "BUTTON" -> when {
        element.isRadialMenuButton -> PreviewKind.RADIAL
        element.wowAction == app.gamenative.wowaction.ReloadGuard.ACTION_ID -> PreviewKind.RELOAD
        element.getBindingComboAt(0).contains(Binding.VOICE_CHAT) -> PreviewKind.VOICE
        element.getBindingComboAt(0).contains(Binding.OPEN_NAVIGATION_MENU) -> PreviewKind.MENU
        element.getBindingComboAt(0).contains(Binding.SHOW_KEYBOARD) -> PreviewKind.KEYBOARD
        element.getBindingComboAt(0).contains(Binding.OPEN_CONTROLS_EDITOR) -> PreviewKind.EDIT
        element.shape == ControlElement.Shape.RECT || element.shape == ControlElement.Shape.ROUND_RECT ||
            element.shape == ControlElement.Shape.PILL -> PreviewKind.WIDE_BUTTON
        else -> PreviewKind.BUTTON
    }
    "D_PAD" -> PreviewKind.D_PAD
    "STICK" -> if (element.ringSegments > 0) PreviewKind.STICK_RING else PreviewKind.STICK
    "SCROLL_WHEEL" -> PreviewKind.SCROLL_WHEEL
    "RANGE_BUTTON" -> PreviewKind.KEY_STRIP
    "TRACKPAD" -> PreviewKind.TRACKPAD
    "SHOOTER_MODE" -> PreviewKind.SHOOTER
    else -> PreviewKind.GENERIC
}

/** Simple line drawing of an element kind; no image files needed. */
@Composable
fun PreviewIcon(kind: PreviewKind, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val s = size.minDimension
        val c = Offset(size.width / 2f, size.height / 2f)
        val line = Stroke(width = s * 0.055f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (kind) {
            PreviewKind.BUTTON -> {
                drawCircle(color = color, radius = s * 0.42f, center = c, style = line)
                drawCircle(color = color.copy(alpha = 0.35f), radius = s * 0.2f, center = c)
            }
            PreviewKind.WIDE_BUTTON -> {
                val w = s * 0.92f
                val h = s * 0.46f
                drawRoundRect(
                    color = color,
                    topLeft = Offset(c.x - w / 2f, c.y - h / 2f),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(h / 2f, h / 2f),
                    style = line,
                )
            }
            PreviewKind.KEY_STRIP -> {
                // Three keys side by side in one frame (a row of cells that scrolls in the game).
                val w = s * 0.94f
                val h = s * 0.4f
                val left = c.x - w / 2f
                val top = c.y - h / 2f
                drawRoundRect(
                    color = color,
                    topLeft = Offset(left, top),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(h * 0.3f, h * 0.3f),
                    style = line,
                )
                for (k in 1..2) {
                    val x = left + w * k / 3f
                    drawLine(color, Offset(x, top + h * 0.12f), Offset(x, top + h * 0.88f), strokeWidth = s * 0.045f, cap = StrokeCap.Round)
                }
            }
            PreviewKind.D_PAD -> drawDPad(color, c, s, line)
            PreviewKind.STICK -> {
                drawCircle(color = color, radius = s * 0.44f, center = c, style = line)
                drawCircle(color = color.copy(alpha = 0.55f), radius = s * 0.2f, center = c)
            }
            PreviewKind.STICK_RING -> {
                drawCircle(color = color, radius = s * 0.46f, center = c, style = line)
                for (k in 0 until 6) {
                    val angle = (k * 60.0 - 90.0) * PI / 180.0
                    val p = Offset(c.x + (cos(angle) * s * 0.33).toFloat(), c.y + (sin(angle) * s * 0.33).toFloat())
                    drawCircle(color = color, radius = s * 0.075f, center = p, style = line)
                }
                drawCircle(color = color.copy(alpha = 0.55f), radius = s * 0.13f, center = c)
            }
            PreviewKind.SCROLL_WHEEL -> {
                val w = s * 0.42f
                val h = s * 0.9f
                drawRoundRect(
                    color = color,
                    topLeft = Offset(c.x - w / 2f, c.y - h / 2f),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(w / 2f, w / 2f),
                    style = line,
                )
                for (dy in listOf(-0.2f, 0f, 0.2f)) {
                    drawLine(
                        color = color,
                        start = Offset(c.x - s * 0.1f, c.y + s * dy),
                        end = Offset(c.x + s * 0.1f, c.y + s * dy),
                        strokeWidth = s * 0.045f,
                        cap = StrokeCap.Round,
                    )
                }
            }
            PreviewKind.RADIAL -> {
                // Like in the game: ring segments on an arc of 270 degrees, the free wedge below, a round middle plate.
                drawArcSegments(color, c, s, count = 6, inner = 0.25f, outer = 0.47f)
                drawCircle(color = color, radius = s * 0.14f, center = c, style = line)
            }
            PreviewKind.RADIAL_BAR -> {
                // Eight segments like the slots of an action bar, each with a dot (the slot), a plate in the middle.
                drawArcSegments(color, c, s, count = 8, inner = 0.25f, outer = 0.47f)
                for (p in arcSegmentMids(8)) {
                    val mid = s * 0.36f
                    drawCircle(color = color, radius = s * 0.035f, center = Offset(c.x + cos(p) * mid, c.y + sin(p) * mid))
                }
                drawCircle(color = color, radius = s * 0.14f, center = c, style = line)
            }
            PreviewKind.RADIAL_GAME -> {
                // Six segments with a small round symbol each and the folded map as the plate in the middle.
                drawArcSegments(color, c, s, count = 6, inner = 0.3f, outer = 0.48f)
                for (p in arcSegmentMids(6)) {
                    val mid = s * 0.39f
                    drawCircle(color = color, radius = s * 0.04f, center = Offset(c.x + cos(p) * mid, c.y + sin(p) * mid))
                }
                val w = s * 0.15f
                val h = s * 0.13f
                val fold = Path().apply {
                    moveTo(c.x - w, c.y - h * 0.7f)
                    lineTo(c.x - w / 3f, c.y - h)
                    lineTo(c.x + w / 3f, c.y - h * 0.7f)
                    lineTo(c.x + w, c.y - h)
                    lineTo(c.x + w, c.y + h * 0.7f)
                    lineTo(c.x + w / 3f, c.y + h)
                    lineTo(c.x - w / 3f, c.y + h * 0.7f)
                    lineTo(c.x - w, c.y + h)
                    close()
                }
                drawPath(fold, color = color, style = Stroke(width = s * 0.045f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            PreviewKind.EMOTE -> {
                drawCircle(color = color, radius = s * 0.44f, center = c, style = line)
                drawCircle(color = color, radius = s * 0.05f, center = Offset(c.x - s * 0.15f, c.y - s * 0.1f))
                drawCircle(color = color, radius = s * 0.05f, center = Offset(c.x + s * 0.15f, c.y - s * 0.1f))
                drawArc(
                    color = color,
                    startAngle = 20f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(c.x - s * 0.24f, c.y - s * 0.2f),
                    size = Size(s * 0.48f, s * 0.44f),
                    style = line,
                )
            }
            PreviewKind.VOICE -> {
                val w = s * 0.26f
                val h = s * 0.46f
                drawRoundRect(
                    color = color,
                    topLeft = Offset(c.x - w / 2f, c.y - s * 0.4f),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(w / 2f, w / 2f),
                    style = line,
                )
                drawArc(
                    color = color,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(c.x - s * 0.24f, c.y - s * 0.26f),
                    size = Size(s * 0.48f, s * 0.44f),
                    style = line,
                )
                drawLine(color, Offset(c.x, c.y + s * 0.18f), Offset(c.x, c.y + s * 0.36f), strokeWidth = s * 0.055f, cap = StrokeCap.Round)
                drawLine(color, Offset(c.x - s * 0.12f, c.y + s * 0.36f), Offset(c.x + s * 0.12f, c.y + s * 0.36f), strokeWidth = s * 0.055f, cap = StrokeCap.Round)
            }
            PreviewKind.MENU -> {
                val w = s * 0.8f
                drawRoundRect(
                    color = color,
                    topLeft = Offset(c.x - w / 2f, c.y - w / 2f),
                    size = Size(w, w),
                    cornerRadius = CornerRadius(s * 0.14f, s * 0.14f),
                    style = line,
                )
                for (dy in listOf(-0.17f, 0f, 0.17f)) {
                    drawLine(color, Offset(c.x - s * 0.2f, c.y + s * dy), Offset(c.x + s * 0.2f, c.y + s * dy), strokeWidth = s * 0.05f, cap = StrokeCap.Round)
                }
            }
            PreviewKind.EDIT -> {
                // Pencil: slanted body with a line below it.
                val sw = s * 0.07f
                drawLine(color, Offset(c.x - s * 0.3f, c.y + s * 0.3f), Offset(c.x + s * 0.22f, c.y - s * 0.22f), strokeWidth = s * 0.16f, cap = StrokeCap.Round)
                drawLine(color, Offset(c.x - s * 0.36f, c.y + s * 0.4f), Offset(c.x + s * 0.34f, c.y + s * 0.4f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            PreviewKind.KEYBOARD -> {
                val w = s * 0.94f
                val h = s * 0.6f
                drawRoundRect(
                    color = color,
                    topLeft = Offset(c.x - w / 2f, c.y - h / 2f),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(s * 0.08f, s * 0.08f),
                    style = line,
                )
                val keyW = s * 0.12f
                val keyH = s * 0.09f
                for (row in 0 until 2) {
                    for (col in 0 until 4) {
                        val x = c.x - s * 0.34f + col * s * 0.2f
                        val y = c.y - s * 0.17f + row * s * 0.15f
                        drawRoundRect(
                            color = color.copy(alpha = 0.6f),
                            topLeft = Offset(x, y),
                            size = Size(keyW, keyH),
                            cornerRadius = CornerRadius(s * 0.02f, s * 0.02f),
                        )
                    }
                }
                drawRoundRect(
                    color = color.copy(alpha = 0.6f),
                    topLeft = Offset(c.x - s * 0.24f, c.y + s * 0.12f),
                    size = Size(s * 0.48f, keyH),
                    cornerRadius = CornerRadius(s * 0.02f, s * 0.02f),
                )
            }
            PreviewKind.TRACKPAD -> {
                val w = s * 0.8f
                drawRoundRect(
                    color = color,
                    topLeft = Offset(c.x - w / 2f, c.y - w / 2f),
                    size = Size(w, w),
                    cornerRadius = CornerRadius(s * 0.1f, s * 0.1f),
                    style = line,
                )
                drawLine(color, Offset(c.x - s * 0.26f, c.y), Offset(c.x + s * 0.26f, c.y), strokeWidth = s * 0.04f, cap = StrokeCap.Round)
                drawLine(color, Offset(c.x, c.y - s * 0.26f), Offset(c.x, c.y + s * 0.26f), strokeWidth = s * 0.04f, cap = StrokeCap.Round)
            }
            PreviewKind.SHOOTER -> {
                drawCircle(color = color, radius = s * 0.3f, center = c, style = line)
                for ((dx, dy) in listOf(0f to -1f, 0f to 1f, -1f to 0f, 1f to 0f)) {
                    drawLine(
                        color,
                        Offset(c.x + dx * s * 0.34f, c.y + dy * s * 0.34f),
                        Offset(c.x + dx * s * 0.46f, c.y + dy * s * 0.46f),
                        strokeWidth = s * 0.05f,
                        cap = StrokeCap.Round,
                    )
                }
            }
            PreviewKind.RELOAD -> {
                // Circular arrow: arc of 300 degrees, arrow head at its end.
                val r = s * 0.3f
                drawArc(color, startAngle = -60f, sweepAngle = 300f, useCenter = false, topLeft = Offset(c.x - r, c.y - r), size = Size(2 * r, 2 * r), style = line)
                val a = -PI.toFloat() / 3f
                val ex = c.x + r * cos(a)
                val ey = c.y + r * sin(a)
                drawLine(color, Offset(ex, ey), Offset(ex + s * 0.17f, ey - s * 0.01f), strokeWidth = s * 0.055f, cap = StrokeCap.Round)
                drawLine(color, Offset(ex, ey), Offset(ex + s * 0.01f, ey + s * 0.18f), strokeWidth = s * 0.055f, cap = StrokeCap.Round)
            }
            PreviewKind.GENERIC -> {
                val w = s * 0.8f
                drawRoundRect(
                    color = color,
                    topLeft = Offset(c.x - w / 2f, c.y - w / 2f),
                    size = Size(w, w),
                    cornerRadius = CornerRadius(s * 0.14f, s * 0.14f),
                    style = line,
                )
            }
        }
    }
}

/** Gap between two segments of the preview, in degrees. */
private const val PREVIEW_GAP_DEG = 6f

/** Middle angles (radians) of the [count] segments of a radial menu: on the arc of the game, field 1 at the left end, then clockwise. */
private fun arcSegmentMids(count: Int): List<Float> {
    val pitch = RadialArc.ARC_DEG / count
    val first = RadialArc.CENTER_DEG - RadialArc.ARC_DEG / 2f + pitch / 2f
    return List(count) { Math.toRadians((first + it * pitch).toDouble()).toFloat() }
}

/** The outline of the [count] ring segments of a radial menu (as in the game, see RadialArc): bands between [inner] and [outer] of the picture size. */
private fun DrawScope.drawArcSegments(color: Color, c: Offset, s: Float, count: Int, inner: Float, outer: Float) {
    val pitch = RadialArc.ARC_DEG / count
    val sweep = pitch - PREVIEW_GAP_DEG
    val first = RadialArc.CENTER_DEG - RadialArc.ARC_DEG / 2f + PREVIEW_GAP_DEG / 2f
    val stroke = Stroke(width = s * 0.04f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val rIn = s * inner
    val rOut = s * outer
    for (k in 0 until count) {
        val start = first + k * pitch
        val path = Path().apply {
            arcTo(Rect(c.x - rOut, c.y - rOut, c.x + rOut, c.y + rOut), start, sweep, true)
            arcTo(Rect(c.x - rIn, c.y - rIn, c.x + rIn, c.y + rIn), start + sweep, -sweep, false)
            close()
        }
        drawPath(path, color = color, style = stroke)
    }
}

private fun DrawScope.drawDPad(color: Color, c: Offset, s: Float, line: Stroke) {
    val a = s * 0.15f
    val l = s * 0.43f
    val path = Path().apply {
        moveTo(c.x - a, c.y - l)
        lineTo(c.x + a, c.y - l)
        lineTo(c.x + a, c.y - a)
        lineTo(c.x + l, c.y - a)
        lineTo(c.x + l, c.y + a)
        lineTo(c.x + a, c.y + a)
        lineTo(c.x + a, c.y + l)
        lineTo(c.x - a, c.y + l)
        lineTo(c.x - a, c.y + a)
        lineTo(c.x - l, c.y + a)
        lineTo(c.x - l, c.y - a)
        lineTo(c.x - a, c.y - a)
        close()
    }
    drawPath(path = path, color = color, style = line)
}
