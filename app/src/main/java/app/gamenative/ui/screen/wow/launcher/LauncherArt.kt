package app.gamenative.ui.screen.wow.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * Die Bilder des Launchers hinter einer kleinen Schnittstelle. Heute zeichnet [DrawnLauncherArt] alles selbst (Compose-Canvas,
 * keine Fremdbilder, keine Blizzard-Grafik). Echte Bilder (Screenshot, WoW-Artwork, mitgelieferte freie Bilder) liefert
 * [BitmapLauncherArt] als weitere Umsetzung dieser Schnittstelle, ohne dass sich eine Seite ändert.
 */
internal interface LauncherArt {
    /** Hintergrund der Hero-Kachel; füllt den übergebenen Platz, beschnitten auf dessen Rand. */
    @Composable
    fun Hero(modifier: Modifier)

    /** Großes, blasses Motiv einer Kachel. [tint] ist die Linienfarbe. */
    @Composable
    fun TileIcon(tile: LauncherTile, tint: Color, modifier: Modifier)

    /** Wappen oben links. */
    @Composable
    fun Crest(modifier: Modifier)

    /** true, wenn für [tile] ein Bild als Hintergrund da ist (dann zeichnet die Kachel [TileBackdrop] statt des Linienmotivs). */
    fun hasTileImage(tile: LauncherTile): Boolean = false

    /** Bild hinter dem Titel einer Kachel, zugeschnitten auf den Platz; ohne Bild leer. Die Abdunkelung legt die Kachel selbst darüber. */
    @Composable
    fun TileBackdrop(tile: LauncherTile, modifier: Modifier) {
    }
}

/** Kartenbilder der Kacheln: 20 % weniger Sättigung ([LauncherStyle.TILE_SATURATION]), damit sie ruhiger wirken. */
private val TileImageFilter: ColorFilter =
    ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(LauncherStyle.TILE_SATURATION) })

/** Platzhalterkunst: Dämmerung in Gold und Violett, Goldlinien-Motive je Kachel auf dunklem Grund, schlichtes Wappen. Alles aus Pfaden, nichts geladen. */
internal object DrawnLauncherArt : LauncherArt {
    @Composable
    override fun Hero(modifier: Modifier) {
        Canvas(modifier.clipToBounds()) { drawHeroLandscape() }
    }

    @Composable
    override fun TileIcon(tile: LauncherTile, tint: Color, modifier: Modifier) {
        Canvas(modifier) { drawTileMotif(tile, tint) }
    }

    @Composable
    override fun Crest(modifier: Modifier) {
        Canvas(modifier) { drawCrest() }
    }
}

/**
 * Kunst mit Bildern: [hero] füllt den Hero (zugeschnitten auf [heroFocus], leicht abgedunkelt), [tiles] liefert je Kachel ein Bild. Was fehlt,
 * zeichnet [fallback] (die gezeichnete Kunst). Wappen und Linienmotive kommen immer aus [fallback].
 */
internal class BitmapLauncherArt(
    private val hero: ImageBitmap?,
    private val tiles: Map<LauncherTile, ImageBitmap> = emptyMap(),
    private val fallback: LauncherArt = DrawnLauncherArt,
    private val heroFocus: ArtFocus = ArtFocus(),
) : LauncherArt {
    @Composable
    override fun Hero(modifier: Modifier) {
        val image = hero
        if (image == null) {
            fallback.Hero(modifier)
        } else {
            Box(modifier.clipToBounds()) {
                // Füllt den Platz (wie Crop), aber der Ausschnitt liegt mit seiner Mitte auf dem Fokus des Bildes, nicht fest in der Bildmitte.
                Canvas(Modifier.matchParentSize()) {
                    val r = focusCropRect(image.width, image.height, size.width, size.height, heroFocus)
                    drawImage(
                        image = image,
                        srcOffset = IntOffset(r.left, r.top),
                        srcSize = IntSize(r.width, r.height),
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                        filterQuality = FilterQuality.Medium,
                    )
                }
                Box(Modifier.matchParentSize().background(LauncherColors.ImageScrim))
            }
        }
    }

    @Composable
    override fun TileIcon(tile: LauncherTile, tint: Color, modifier: Modifier) = fallback.TileIcon(tile, tint, modifier)

    @Composable
    override fun Crest(modifier: Modifier) = fallback.Crest(modifier)

    override fun hasTileImage(tile: LauncherTile): Boolean = tiles.containsKey(tile)

    @Composable
    override fun TileBackdrop(tile: LauncherTile, modifier: Modifier) {
        val image = tiles[tile] ?: return
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            colorFilter = TileImageFilter,
            modifier = modifier,
        )
    }
}

// --- Hero: Dämmerungslandschaft (Zeichenfläche 800 x 700, füllt den Platz wie "slice") ---

private fun polygon(vararg xy: Float): Path = Path().apply {
    moveTo(xy[0], xy[1])
    var k = 2
    while (k + 1 < xy.size) {
        lineTo(xy[k], xy[k + 1])
        k += 2
    }
    close()
}

private fun DrawScope.drawHeroLandscape() {
    val s = maxOf(size.width / 800f, size.height / 700f)
    val dx = (size.width - 800f * s) / 2f
    val dy = (size.height - 700f * s) / 2f
    withTransform({
        translate(dx, dy)
        scale(scaleX = s, scaleY = s, pivot = Offset.Zero)
    }) {
        // Himmel und Sonnenschein
        drawRect(
            brush = Brush.verticalGradient(
                0f to LauncherArtColors.SkyTop, 0.38f to LauncherArtColors.SkyUpper, 0.62f to LauncherArtColors.SkyMid,
                0.8f to LauncherArtColors.SkyLow, 1f to LauncherArtColors.SkyHorizon,
                startY = 0f, endY = 700f,
            ),
            size = Size(800f, 700f),
        )
        drawRect(
            brush = Brush.radialGradient(
                0f to LauncherArtColors.SunCore, 0.35f to LauncherArtColors.SunMid, 1f to LauncherArtColors.SunEdge,
                center = Offset(496f, 364f), radius = 400f,
            ),
            size = Size(800f, 700f),
        )
        // Sterne und Wolkenbänder
        val star = LauncherArtColors.Star
        for ((x, y, r) in listOf(
            Triple(90f, 60f, 1.5f), Triple(210f, 110f, 1.2f), Triple(340f, 48f, 1.6f), Triple(470f, 92f, 1.1f),
            Triple(560f, 40f, 1.4f), Triple(700f, 120f, 1.3f), Triple(150f, 170f, 1.0f), Triple(640f, 190f, 1.1f),
        )) {
            drawCircle(star, radius = r, center = Offset(x, y))
        }
        val cloud = LauncherArtColors.Cloud
        drawOval(cloud, topLeft = Offset(40f, 236f), size = Size(300f, 28f))
        drawOval(cloud, topLeft = Offset(370f, 193f), size = Size(380f, 24f))
        drawOval(cloud, topLeft = Offset(290f, 290f), size = Size(260f, 20f))
        // Hintere Bergkette mit Schneekappen
        drawPath(
            polygon(0f, 400f, 70f, 330f, 120f, 372f, 200f, 280f, 262f, 350f, 330f, 300f, 420f, 390f, 520f, 310f, 600f, 368f, 690f, 296f, 800f, 380f, 800f, 700f, 0f, 700f),
            brush = Brush.verticalGradient(0f to LauncherArtColors.FarTop, 1f to LauncherArtColors.FarBottom, startY = 280f, endY = 700f),
        )
        drawPath(polygon(200f, 280f, 222f, 312f, 205f, 306f, 190f, 320f), color = LauncherArtColors.Snow)
        drawPath(polygon(690f, 296f, 712f, 328f, 696f, 322f, 680f, 336f), color = LauncherArtColors.SnowSoft)
        // Turm (eigene Silhouette)
        val tower = LauncherArtColors.Tower
        drawRect(tower, topLeft = Offset(496f, 258f), size = Size(14f, 92f))
        drawPath(polygon(493f, 258f, 503f, 222f, 513f, 258f), color = tower)
        drawRect(tower, topLeft = Offset(470f, 298f), size = Size(9f, 56f))
        drawPath(polygon(468f, 298f, 474.5f, 274f, 481f, 298f), color = tower)
        drawRect(tower, topLeft = Offset(530f, 290f), size = Size(9f, 62f))
        drawPath(polygon(528f, 290f, 534.5f, 268f, 541f, 290f), color = tower)
        drawRect(tower, topLeft = Offset(452f, 326f), size = Size(108f, 30f))
        // ein paar erleuchtete Fenster
        for ((wx, wy) in listOf(498f to 290f, 498f to 312f, 472f to 318f, 532f to 312f)) {
            drawRect(LauncherArtColors.Window, topLeft = Offset(wx, wy), size = Size(4f, 7f))
        }
        // Mittlere und vordere Bergkette
        drawPath(
            polygon(0f, 470f, 90f, 410f, 170f, 450f, 260f, 395f, 360f, 470f, 450f, 420f, 560f, 480f, 650f, 425f, 800f, 470f, 800f, 700f, 0f, 700f),
            brush = Brush.verticalGradient(0f to LauncherArtColors.MidTop, 1f to LauncherArtColors.MidBottom, startY = 395f, endY = 700f),
        )
        drawPath(
            polygon(0f, 560f, 110f, 500f, 210f, 548f, 330f, 490f, 440f, 556f, 560f, 505f, 690f, 560f, 800f, 520f, 800f, 700f, 0f, 700f),
            brush = Brush.verticalGradient(0f to LauncherArtColors.NearTop, 1f to LauncherArtColors.NearBottom, startY = 490f, endY = 700f),
        )
        // Wald an den Rändern
        val forest = LauncherArtColors.Forest
        for (t in listOf(
            floatArrayOf(30f, 66f, 48f, 622f), floatArrayOf(62f, 106f, 84f, 600f), floatArrayOf(104f, 136f, 120f, 636f),
            floatArrayOf(660f, 696f, 678f, 628f), floatArrayOf(692f, 740f, 716f, 592f), floatArrayOf(736f, 768f, 752f, 640f),
        )) {
            drawPath(polygon(t[0], 700f, t[2], t[3], t[1], 700f), color = forest)
        }
        // Dunst am Boden
        drawRect(
            brush = Brush.verticalGradient(0f to LauncherArtColors.HazeClear, 1f to LauncherArtColors.HazeFull, startY = 480f, endY = 700f),
            topLeft = Offset(0f, 480f),
            size = Size(800f, 220f),
        )
    }
}

// --- Kachelmotive (Zeichenfläche 24 x 24, Linien) ---

private fun DrawScope.drawTileMotif(tile: LauncherTile, tint: Color) {
    val k = size.minDimension / 24f
    val line = Stroke(width = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    withTransform({ scale(scaleX = k, scaleY = k, pivot = Offset.Zero) }) {
        when (tile) {
            LauncherTile.Touch -> {
                // Steuerung: Griffbrett mit Steuerkreuz und zwei Knöpfen
                drawRoundRect(tint, topLeft = Offset(2.5f, 6.5f), size = Size(19f, 11f), cornerRadius = CornerRadius(5f, 5f), style = line)
                drawLine(tint, Offset(7.5f, 10f), Offset(7.5f, 14f), strokeWidth = 1.7f, cap = StrokeCap.Round)
                drawLine(tint, Offset(5.5f, 12f), Offset(9.5f, 12f), strokeWidth = 1.7f, cap = StrokeCap.Round)
                drawCircle(tint, radius = 1.1f, center = Offset(15.5f, 10.8f), style = line)
                drawCircle(tint, radius = 1.1f, center = Offset(18.2f, 13.2f), style = line)
            }
            LauncherTile.Addons -> {
                // Bausteine: drei Quadrate und ein Kreis
                drawRoundRect(tint, topLeft = Offset(3f, 3f), size = Size(7.5f, 7.5f), cornerRadius = CornerRadius(1.5f, 1.5f), style = line)
                drawRoundRect(tint, topLeft = Offset(13.5f, 3f), size = Size(7.5f, 7.5f), cornerRadius = CornerRadius(1.5f, 1.5f), style = line)
                drawRoundRect(tint, topLeft = Offset(3f, 13.5f), size = Size(7.5f, 7.5f), cornerRadius = CornerRadius(1.5f, 1.5f), style = line)
                drawCircle(tint, radius = 4f, center = Offset(17.25f, 17.25f), style = line)
            }
            LauncherTile.Settings -> {
                // Drei Schieberegler
                for ((y, knob) in listOf(6f to 8f, 12f to 16f, 18f to 10f)) {
                    drawLine(tint, Offset(3f, y), Offset(knob - 2.7f, y), strokeWidth = 1.7f, cap = StrokeCap.Round)
                    drawLine(tint, Offset(knob + 2.7f, y), Offset(21f, y), strokeWidth = 1.7f, cap = StrokeCap.Round)
                    drawCircle(tint, radius = 2.2f, center = Offset(knob, y), style = line)
                }
            }
            LauncherTile.Files -> {
                // Ordner
                drawPath(polygon(3f, 6f, 10f, 6f, 12f, 8.2f, 21f, 8.2f, 21f, 19f, 3f, 19f), color = tint, style = line)
            }
            LauncherTile.News -> {
                // Hinweiszeichen im Ring
                drawCircle(tint, radius = 8.6f, center = Offset(12f, 12f), style = line)
                drawLine(tint, Offset(12f, 7.6f), Offset(12f, 12.8f), strokeWidth = 1.9f, cap = StrokeCap.Round)
                drawCircle(tint, radius = 1f, center = Offset(12f, 16.2f))
            }
            LauncherTile.Help -> {
                // Schild mit Haken
                drawPath(polygon(12f, 3f, 20f, 6f, 20f, 12f, 17f, 18f, 12f, 21f, 7f, 18f, 4f, 12f, 4f, 6f), color = tint, style = line)
                val tick = Path().apply {
                    moveTo(8.5f, 12.5f)
                    lineTo(11f, 15f)
                    lineTo(15.5f, 10f)
                }
                drawPath(tick, color = tint, style = line)
            }
        }
    }
}

// --- Wappen (Zeichenfläche 40 x 40) ---

private fun DrawScope.drawCrest() {
    val k = size.minDimension / 40f
    withTransform({ scale(scaleX = k, scaleY = k, pivot = Offset.Zero) }) {
        val gold = LauncherColors.Gold
        val bronze = LauncherColors.Bronze
        val fill = LauncherColors.StoneDark
        drawCircle(fill, radius = 17.5f, center = Offset(20f, 20f))
        drawCircle(gold, radius = 17.5f, center = Offset(20f, 20f), style = Stroke(width = 2f))
        drawCircle(bronze, radius = 12.5f, center = Offset(20f, 20f), style = Stroke(width = 1f))
        drawPath(polygon(20f, 8.5f, 26.5f, 20f, 20f, 31.5f, 13.5f, 20f), color = gold)
        drawPath(polygon(20f, 14f, 23f, 20f, 20f, 26f, 17f, 20f), color = fill)
    }
}
