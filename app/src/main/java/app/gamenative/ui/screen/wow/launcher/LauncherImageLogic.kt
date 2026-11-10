package app.gamenative.ui.screen.wow.launcher

import java.io.File

/*
 * Reine Logik der Launcher-Bilder (ohne Android, per JUnit getestet): welche Bildquelle zuerst gilt, wie die eigenen Screenshots im
 * Spielordner gefunden werden, wie die Verkleinerung beim Laden gerechnet wird und wie die mitgelieferten Bilder heißen.
 * Das Laden und Zeichnen selbst steht in LauncherImages.kt und LauncherArt.kt.
 */

/** Woher ein Bild kommt. Die Reihenfolge der Auswahl steht in [heroSourceOrder] und [tileSourceOrder]. */
internal enum class ArtSource { Screenshot, WowArt, Bundled, Drawn }

/**
 * Hero: eigene Screenshots, dann WoW-Artwork aus dem Netz (oder dem Bildspeicher), dann mitgelieferte freie Bilder, zuletzt die
 * gezeichnete Kunst. Eine Quelle steht nur drin, wenn ihr Schalter an ist und es etwas zu zeigen geben kann; [ArtSource.Drawn]
 * steht immer am Ende.
 */
internal fun heroSourceOrder(
    screenshotsOn: Boolean,
    hasScreenshot: Boolean,
    wowArtOn: Boolean,
    hasBundled: Boolean,
): List<ArtSource> = buildList {
    if (screenshotsOn && hasScreenshot) add(ArtSource.Screenshot)
    if (wowArtOn) add(ArtSource.WowArt)
    if (hasBundled) add(ArtSource.Bundled)
    add(ArtSource.Drawn)
}

/** Kachel: WoW-Karte, dann freies Bild, zuletzt das gezeichnete Motiv. */
internal fun tileSourceOrder(wowArtOn: Boolean, hasBundled: Boolean): List<ArtSource> = buildList {
    if (wowArtOn) add(ArtSource.WowArt)
    if (hasBundled) add(ArtSource.Bundled)
    add(ArtSource.Drawn)
}

/** [list] so gedreht, dass der Eintrag Nummer `seed mod Größe` vorn steht (Wechsel je Start); leer bleibt leer. */
internal fun <T> rotatedBy(list: List<T>, seed: Long): List<T> {
    if (list.isEmpty()) return list
    val i = Math.floorMod(seed, list.size.toLong()).toInt()
    return list.drop(i) + list.take(i)
}

// --- Fokuspunkt und Zuschnitt des Hero ---

/**
 * Stelle im Hero-Bild, die beim Zuschneiden möglichst in der Mitte des Platzes bleiben soll (Anteile 0..1 von Breite und Höhe des
 * sichtbaren Bildes; Standard Mitte). So wird eine Figur nicht abgeschnitten, wenn der Platz viel schmaler oder flacher ist als das Bild.
 */
internal data class ArtFocus(val x: Float = 0.5f, val y: Float = 0.5f) {
    /** Beide Werte auf 0..1 begrenzt; ungültige Zahlen (NaN) werden zur Mitte. */
    fun clamped(): ArtFocus = ArtFocus(
        if (x.isNaN()) 0.5f else x.coerceIn(0f, 1f),
        if (y.isNaN()) 0.5f else y.coerceIn(0f, 1f),
    )
}

/** Ausschnitt eines Bildes in Pixeln: erste Spalte, erste Zeile, Breite, Höhe. */
internal data class SourceRect(val left: Int, val top: Int, val width: Int, val height: Int)

/**
 * Welcher Teil eines Bildes ([imgW] x [imgH] Pixel) einen Platz von [boxW] x [boxH] füllt (wie `ContentScale.Crop`, nur nicht fest in der Mitte):
 * der Ausschnitt hat das Seitenverhältnis des Platzes, ist so groß wie möglich und liegt mit seiner Mitte auf [focus], soweit das Bild reicht
 * (am Rand wird verschoben, nie leer gelassen). Bei `focus` = Mitte ist das genau der mittige Zuschnitt. Ungültige Maße ergeben das ganze Bild.
 */
internal fun focusCropRect(imgW: Int, imgH: Int, boxW: Float, boxH: Float, focus: ArtFocus): SourceRect {
    if (imgW <= 0 || imgH <= 0 || boxW <= 0f || boxH <= 0f) return SourceRect(0, 0, maxOf(imgW, 0), maxOf(imgH, 0))
    val f = focus.clamped()
    val scale = maxOf(boxW / imgW, boxH / imgH)
    val viewW = minOf(imgW.toDouble(), boxW / scale.toDouble())
    val viewH = minOf(imgH.toDouble(), boxH / scale.toDouble())
    val w = Math.round(viewW).toInt().coerceIn(1, imgW)
    val h = Math.round(viewH).toInt().coerceIn(1, imgH)
    val left = Math.round(f.x * imgW - w / 2.0).toInt().coerceIn(0, imgW - w)
    val top = Math.round(f.y * imgH - h / 2.0).toInt().coerceIn(0, imgH - h)
    return SourceRect(left, top, w, h)
}

/**
 * Fokus der mitgelieferten Hero-Bilder (Dateiname ohne Endung, kleingeschrieben), festgelegt am Bild: Drache und Feuer, Burg, Glutwolke,
 * Wasserfall, Bergspitze, Sonnenuntergang. Ein neues Bild ohne Eintrag hat die Mitte.
 */
internal val BUNDLED_HERO_FOCUS: Map<String, ArtFocus> = mapOf(
    "hero_confront_the_dragon" to ArtFocus(0.42f, 0.55f),
    "hero_eilean_donan" to ArtFocus(0.58f, 0.45f),
    "hero_great_day_of_his_wrath" to ArtFocus(0.47f, 0.45f),
    "hero_landers_peak" to ArtFocus(0.52f, 0.45f),
    "hero_stars_over_mountains" to ArtFocus(0.48f, 0.55f),
    "hero_twilight_in_the_wilderness" to ArtFocus(0.47f, 0.55f),
)

internal fun bundledHeroFocus(fileName: String): ArtFocus =
    BUNDLED_HERO_FOCUS[fileName.substringBeforeLast('.').lowercase()] ?: ArtFocus()

// --- Dateinamen ---

private val SCREENSHOT_EXTENSIONS = setOf("jpg", "jpeg", "png")
private val ASSET_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

/** Ordner für die mitgelieferten freien Bilder in den Assets. */
internal const val BUNDLED_ART_DIR = "launcher_art"

/** Nur Bilder, die BitmapFactory sicher liest (WoW-Screenshots als .tga sind ausgenommen). */
internal fun isScreenshotName(name: String): Boolean =
    !name.startsWith(".") && name.substringAfterLast('.', "").lowercase() in SCREENSHOT_EXTENSIONS

/** Mitgeliefertes Hero-Bild: Dateiname beginnt mit "hero" (Groß-/Kleinschreibung egal) und endet auf jpg, jpeg, png oder webp. */
internal fun isBundledHeroName(name: String): Boolean =
    name.lowercase().startsWith("hero") && name.substringAfterLast('.', "").lowercase() in ASSET_EXTENSIONS

/** Name des mitgelieferten Kachelbildes (ohne Endung), z. B. "tile_touch". */
internal fun bundledTileStem(tile: LauncherTile): String = "tile_${tile.name.lowercase()}"

/** Die Datei aus [names] (Inhalt von launcher_art), die zu [tile] gehört; null, wenn keine da ist. */
internal fun bundledTileName(tile: LauncherTile, names: List<String>): String? {
    val stem = bundledTileStem(tile)
    return names.sorted().firstOrNull { it.lowercase().substringBeforeLast('.') == stem && it.substringAfterLast('.', "").lowercase() in ASSET_EXTENSIONS }
}

/** Wählt aus den Hero-Bildern der Assets eines aus; mit gleichem [seed] immer dasselbe, sortiert nach Namen. */
internal fun pickBundledHero(names: List<String>, seed: Long): String? =
    rotatedBy(names.filter(::isBundledHeroName).sorted(), seed).firstOrNull()

// --- Screenshots des Spiels ---

/** Unterordner der Spielinstallation, in denen WoW Screenshots ablegt: die Vollversion, die frühere Beta und der Spielordner selbst. */
internal fun screenshotBaseDirs(gamePath: String, flavorDirs: List<String>): List<File> {
    val root = File(gamePath)
    return flavorDirs.map { File(root, it) } + root
}

/** Das Unterverzeichnis "Screenshots" von [parent], Groß-/Kleinschreibung egal; null, wenn es keins gibt. */
internal fun screenshotsDirIn(parent: File): File? =
    parent.listFiles { f -> f.isDirectory && f.name.equals("Screenshots", ignoreCase = true) }?.minByOrNull { it.name }

/**
 * Alle Screenshots (jpg, jpeg, png; nicht leer, nicht versteckt) aus den Ordnern `Screenshots` unter [gamePath], neueste zuerst
 * (nach Änderungszeit, bei Gleichstand nach Name absteigend). Nur die Ordner selbst, keine Unterordner.
 */
internal fun findScreenshots(gamePath: String, flavorDirs: List<String>): List<File> =
    screenshotBaseDirs(gamePath, flavorDirs)
        .mapNotNull { screenshotsDirIn(it) }
        .flatMap { dir -> dir.listFiles { f -> f.isFile && isScreenshotName(f.name) && f.length() > 0L }?.toList().orEmpty() }
        .sortedWith(compareByDescending<File> { it.lastModified() }.thenByDescending { it.name })

internal fun newestScreenshot(gamePath: String, flavorDirs: List<String>): File? = findScreenshots(gamePath, flavorDirs).firstOrNull()

// --- Verkleinerung beim Laden ---

/** Größte Zweierpotenz `inSampleSize`, bei der das Bild noch mindestens [reqWidth] x [reqHeight] hat (BitmapFactory-Regel). */
internal fun sampleSizeFor(srcWidth: Int, srcHeight: Int, reqWidth: Int, reqHeight: Int): Int {
    if (srcWidth <= 0 || srcHeight <= 0 || reqWidth <= 0 || reqHeight <= 0) return 1
    var sample = 1
    while (srcWidth / (sample * 2) >= reqWidth && srcHeight / (sample * 2) >= reqHeight) sample *= 2
    return sample
}
