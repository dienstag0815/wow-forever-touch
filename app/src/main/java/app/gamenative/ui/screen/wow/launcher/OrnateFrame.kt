package app.gamenative.ui.screen.wow.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/*
 * Gezeichnete Bausteine des WoW-Stils: Hintergrund aus Stein mit Körnung und Vignette, doppelter Goldrahmen mit Filigran in den
 * Ecken, Trenner (Linie - Raute - Linie), Titel in Gold und der Edelstein der Statuspille. Alles aus Compose-Zeichenbefehlen, die
 * Farben stammen aus LauncherStyle.kt.
 */

/** Form aller Rahmen des Launchers. */
internal val FrameShape = RoundedCornerShape(LauncherStyle.FrameRadius)

/** Form von Hero und Kacheln der Startseite (siehe [softFrame]). */
internal val CardShape = RoundedCornerShape(LauncherStyle.CardRadius)

// --- Hintergrund ---

/**
 * Die Körnung entsteht einmal (fester Zufallssamen) als kleine Bitmap und wird gekachelt; sie wird nie pro Frame neu gewürfelt.
 * Der Brush ist unveränderlich und wird von allen Seiten geteilt.
 */
private val grainBrush: ShaderBrush by lazy {
    val n = LauncherStyle.GRAIN_TILE_PX
    val random = java.util.Random(LauncherStyle.GRAIN_SEED)
    val light = LauncherColors.GrainLight.toArgb()
    val dark = LauncherColors.GrainDark.toArgb()
    val pixels = IntArray(n * n)
    for (i in pixels.indices) {
        val v = random.nextInt(100)
        pixels[i] = when {
            v < 7 -> light
            v < 17 -> dark
            else -> 0
        }
    }
    val bitmap = android.graphics.Bitmap.createBitmap(pixels, n, n, android.graphics.Bitmap.Config.ARGB_8888)
    ShaderBrush(ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}

/** Warmer Stein von dunkel nach hell und wieder dunkel, darüber feine Körnung und eine Vignette zu den Rändern. */
internal fun Modifier.launcherBackground(): Modifier = this
    .background(Brush.verticalGradient(listOf(LauncherColors.StoneDark, LauncherColors.StoneLight, LauncherColors.StoneDark)))
    .drawBehind {
        drawRect(grainBrush)
        drawRect(
            Brush.radialGradient(
                0f to Color.Transparent, 0.55f to Color.Transparent, 1f to LauncherColors.VignetteEdge,
                center = center,
                radius = size.maxDimension * 0.75f,
            ),
        )
    }

// --- Rahmen ---

/**
 * Rahmen im Stil eines Fantasy-Launchers: [fill] als Grund, außen eine Goldlinie ([outer]), innen eine Bronzelinie ([inner]) mit
 * dunkler Innenkante und in den vier Ecken ein Filigran aus Bogen und Raute. Die Linien liegen über dem Inhalt (auch über Bildern).
 * Kleine Flächen bekommen keine Eckverzierung; mit [corners] = false auch große nicht.
 */
internal fun Modifier.ornateFrame(
    fill: Color = LauncherColors.Surface,
    outer: Color = LauncherColors.Gold,
    inner: Color = LauncherColors.Bronze,
    corners: Boolean = true,
    radius: Dp = LauncherStyle.FrameRadius,
): Modifier = this
    .clip(RoundedCornerShape(radius))
    .background(fill)
    .drawWithContent {
        drawContent()
        drawOrnateFrame(outer, inner, radius.toPx(), corners)
    }

/**
 * Ruhiger Rahmen für Hero und Kacheln der Startseite: [fill] als Grund, abgerundete Ecken ([radius], Standard 14 dp) und eine dünne, gedämpfte
 * Linie ([line], 1 dp) über dem Inhalt. Kein Doppelrahmen, keine Eckverzierung; die Verzierung steht nur noch im Kopf der Unterseiten.
 */
internal fun Modifier.softFrame(
    fill: Color,
    line: Color = LauncherColors.GoldMuted,
    radius: Dp = LauncherStyle.CardRadius,
    lineWidth: Dp = LauncherStyle.CARD_LINE_DP.dp,
): Modifier = this
    .clip(RoundedCornerShape(radius))
    .background(fill)
    .drawWithContent {
        drawContent()
        val w = lineWidth.toPx()
        val r = maxOf(radius.toPx() - w / 2f, 0f)
        if (size.width > w * 2f && size.height > w * 2f) {
            drawRoundRect(
                color = line,
                topLeft = Offset(w / 2f, w / 2f),
                size = Size(size.width - w, size.height - w),
                cornerRadius = CornerRadius(r, r),
                style = Stroke(width = w),
            )
        }
    }

private fun DrawScope.drawOrnateFrame(outer: Color, inner: Color, radiusPx: Float, corners: Boolean) {
    val dp1 = 1.dp.toPx()
    val w = size.width
    val h = size.height

    fun ring(insetPx: Float, color: Color, widthPx: Float) {
        val o = insetPx + widthPx / 2f
        if (w - 2f * o <= 0f || h - 2f * o <= 0f) return
        val r = maxOf(radiusPx - insetPx, 0f)
        drawRoundRect(
            color = color,
            topLeft = Offset(o, o),
            size = Size(w - 2f * o, h - 2f * o),
            cornerRadius = CornerRadius(r, r),
            style = Stroke(width = widthPx),
        )
    }

    val innerInset = LauncherStyle.INNER_INSET_DP * dp1
    ring(0f, outer, LauncherStyle.OUTER_LINE_DP * dp1)
    ring(innerInset, inner, LauncherStyle.INNER_LINE_DP * dp1)
    ring(innerInset + (LauncherStyle.INNER_LINE_DP + 0.5f) * dp1, LauncherColors.Shadow.copy(alpha = 0.35f), LauncherStyle.EDGE_LINE_DP * dp1)

    if (corners && minOf(w, h) >= 56.dp.toPx()) {
        for ((x, sx) in listOf(0f to 1f, w to -1f)) {
            for ((y, sy) in listOf(0f to 1f, h to -1f)) {
                withTransform({
                    translate(x, y)
                    scale(scaleX = sx * dp1, scaleY = sy * dp1, pivot = Offset.Zero)
                }) {
                    drawCornerOrnament(outer)
                }
            }
        }
    }
}

/** Filigran einer Ecke in dp-Einheiten, Ursprung an der Ecke, Achsen nach innen: Raute in der Ecke, ein Bogen darum. */
private fun DrawScope.drawCornerOrnament(color: Color) {
    val arc = Path().apply {
        moveTo(4f, 14f)
        cubicTo(4f, 9f, 9f, 4f, 14f, 4f)
    }
    drawPath(arc, color, style = Stroke(width = 1.2f, cap = StrokeCap.Round))
    val gem = Path().apply {
        moveTo(4f, 1.4f)
        lineTo(6.6f, 4f)
        lineTo(4f, 6.6f)
        lineTo(1.4f, 4f)
        close()
    }
    drawPath(gem, color)
    drawCircle(color, radius = 1.1f, center = Offset(4f, 14f))
    drawCircle(color, radius = 1.1f, center = Offset(14f, 4f))
}

// --- Trenner ---

/** Linie - Raute - Linie, die Linien laufen zu den Enden hin aus. Unter Seitentiteln. */
@Composable
internal fun OrnamentDivider(modifier: Modifier = Modifier.fillMaxWidth()) {
    Canvas(modifier.height(14.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val gap = 10.dp.toPx()
        val stroke = 1.dp.toPx()
        val d = 4.dp.toPx()
        if (cx - gap > 0f) {
            drawLine(
                brush = Brush.horizontalGradient(listOf(Color.Transparent, LauncherColors.Bronze, LauncherColors.Gold), startX = 0f, endX = cx - gap),
                start = Offset(0f, cy), end = Offset(cx - gap, cy), strokeWidth = stroke,
            )
            drawLine(
                brush = Brush.horizontalGradient(listOf(LauncherColors.Gold, LauncherColors.Bronze, Color.Transparent), startX = cx + gap, endX = size.width),
                start = Offset(cx + gap, cy), end = Offset(size.width, cy), strokeWidth = stroke,
            )
        }
        val gem = Path().apply {
            moveTo(cx, cy - d)
            lineTo(cx + d, cy)
            lineTo(cx, cy + d)
            lineTo(cx - d, cy)
            close()
        }
        drawPath(gem, LauncherColors.GoldBright)
        drawPath(gem, LauncherColors.Bronze, style = Stroke(width = stroke))
    }
}

// --- Schrift ---

private val goldFill: Brush get() = Brush.verticalGradient(listOf(LauncherColors.GoldBright, LauncherColors.Gold))

/**
 * Titel und Wortmarke: Cinzel in Versalien, Laufweite 0,12 em, Verlauf von Hellgold nach Gold, dunkler Schatten.
 * [maxLines] 2 lässt lange Titel umbrechen statt abzuschneiden.
 */
@Composable
internal fun LauncherTitle(
    text: String,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    lineHeight: TextUnit = TextUnit.Unspecified,
    maxLines: Int = 2,
    tracking: Float = LauncherStyle.TITLE_TRACKING_EM,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        fontSize = fontSize,
        lineHeight = lineHeight,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            brush = goldFill,
            fontFamily = LauncherFonts.Cinzel,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = tracking.em,
            shadow = Shadow(color = LauncherColors.TextShadowSoft, offset = Offset(0f, 2f), blurRadius = 4f),
        ),
    )
}

/** Überschrift einer Gruppe: Cinzel, Gold, leichte Laufweite, kein Versalien-Titel (bleibt gut lesbar). */
@Composable
internal fun LauncherSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = LauncherFonts.Cinzel,
        letterSpacing = 0.03.em,
        color = LauncherColors.Gold,
        style = TextStyle(shadow = Shadow(color = LauncherColors.TextShadowSoft, offset = Offset(0f, 1f), blurRadius = 3f)),
    )
}

// --- Edelstein der Statuspille ---

/** Kreis mit Goldring und einem Stein in der Farbe des Zustands; ein heller Punkt oben links gibt den Glanz. */
@Composable
internal fun StatusGem(color: Color, diameter: Dp = 18.dp) {
    Canvas(Modifier.size(diameter)) {
        val r = this.size.minDimension / 2f
        val ring = 1.5.dp.toPx()
        drawCircle(LauncherColors.Gold, radius = r)
        drawCircle(LauncherColors.StoneDark, radius = r - ring)
        val stone = r - ring - 1.dp.toPx()
        if (stone > 0f) {
            drawCircle(
                brush = Brush.radialGradient(
                    0f to lerp(color, Color.White, 0.55f), 1f to color,
                    center = Offset(center.x - stone * 0.3f, center.y - stone * 0.35f),
                    radius = stone * 1.5f,
                ),
                radius = stone,
            )
            drawCircle(Color.White.copy(alpha = 0.55f), radius = stone * 0.18f, center = Offset(center.x - stone * 0.35f, center.y - stone * 0.4f))
        }
    }
}
