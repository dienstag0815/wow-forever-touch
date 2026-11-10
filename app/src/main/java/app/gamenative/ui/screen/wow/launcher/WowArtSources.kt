package app.gamenative.ui.screen.wow.launcher

/*
 * Offizielles WoW-Artwork für den Launcher: nur die Adressen stehen im Code. Die Bilder werden zur Laufzeit einmal in den
 * App-Speicher geladen (WowArtLoader.kt) und liegen nie im Repository oder im APK. Quelle der Adressen: Recherche 10.11.2026
 * (interne Quellenliste). Die Pfade der Blizzard-Bilder enthalten Hashes und können sich ändern; ein Fehler
 * (404, kein Netz) wird still übergangen und die nächste Quelle versucht.
 */
internal object WowArtSources {
    /**
     * Name und Pfad ab [HERO_BASE] eines Hero-Bildes, dazu die Ränder, die beim Anzeigen abgeschnitten werden (Anteil der Bildhöhe):
     * die Bilder sind für die Webseite gemacht und haben oben einen weißen, gezackten Rand und/oder unten eine weiße Blende.
     * Gemessen am 11.11.2026 an den 1280 Pixel breiten Bildern, mit etwas Reserve (siehe [heroCropRows]).
     */
    data class HeroArt(
        val name: String,
        val path: String,
        val cropTop: Float = 0f,
        val cropBottom: Float = 0f,
        /** Fokus im zugeschnittenen Bild (0..1): die Figur, die beim Anzeigen in der Mitte bleiben soll (siehe [focusCropRect]). */
        val focusX: Float = 0.5f,
        val focusY: Float = 0.5f,
    ) {
        fun focus(): ArtFocus = ArtFocus(focusX, focusY).clamped()
    }

    const val HERO_BASE = "https://blz-contentstack-images.akamaized.net/v3/assets/blt9c12f249ac15c7ec/"

    /** Immer verkleinert und als JPEG anfordern: etwa 72 KB statt 1,7 MB. */
    const val HERO_QUERY = "?width=1280&format=jpg"

    /** Die fünf Hero-Bilder; das erste ist das beste (Onyxia und Magier). */
    val HERO: List<HeroArt> = listOf(
        // Fokus (focusX/focusY) gemessen am 11.11.2026 im zugeschnittenen Bild: masthead = Onyxia (Magier ganz links bleibt am Rand), ragnaros = Gestalt
    // links, hardcore = Zwerg, sod = Bestie, bcc = beide Kämpfer. Bei engem Hochformat zeigt der Hero nur etwa ein Drittel der Breite.
    // masthead (620 hoch): kein Rand oben, unten Blende zu Weiß ab etwa Zeile 550 -> unten 12 % weg.
        HeroArt("masthead", "bltdb388a4abd197586/6a86a1886cec0d30e9b13fe8/classic-masthead-lg.png", cropTop = 0f, cropBottom = 0.12f, focusX = 0.34f, focusY = 0.48f),
        // Die vier anderen (957 hoch): Zackenrand oben bis etwa Zeile 45, Blende unten ab etwa Zeile 915.
        HeroArt("ragnaros", "bltd7f3db63b41b3f73/6a86c0659b942ed84ef740cf/classic-bg.png", cropTop = 0.05f, cropBottom = 0.045f, focusX = 0.20f, focusY = 0.48f),
        HeroArt("hardcore", "bltfd5ba4f497ee0326/6a86b17c32e602ffd4c0d989/classic-hardcore-bg.png", cropTop = 0.05f, cropBottom = 0.045f, focusX = 0.25f, focusY = 0.50f),
        HeroArt("sod", "blte598e7adea0ac141/6a86c0669b942ebd63f740d3/classic-sod-bg.png", cropTop = 0.05f, cropBottom = 0.045f, focusX = 0.31f, focusY = 0.58f),
        HeroArt("bcc", "blt9637c72b4c27eb66/6a86c058c751e17dc40c4de7/classic-bcc-bg.png", cropTop = 0.055f, cropBottom = 0.05f, focusX = 0.26f, focusY = 0.55f),
    )

    /** Größter Anteil, den ein Rand abschneiden darf; ein Fehlwert im Code kann so nie das ganze Bild wegnehmen. */
    const val MAX_CROP = 0.2f

    /**
     * Erste Zeile und Zeilenanzahl des sichtbaren Teils eines Bildes der Höhe [height] (Pixel) nach dem Abschneiden von [cropTop]
     * oben und [cropBottom] unten (Anteile der Höhe, auf 0 bis [MAX_CROP] begrenzt). Gerundet wird zugunsten der Reserve
     * (aufrunden), es bleibt immer mindestens eine Zeile. Gilt für Bilder in jeder Auflösung (auch verkleinert dekodiert).
     */
    fun heroCropRows(height: Int, cropTop: Float, cropBottom: Float): Pair<Int, Int> {
        if (height <= 1) return 0 to maxOf(height, 0)
        val top = Math.ceil(height * cropTop.coerceIn(0f, MAX_CROP).toDouble() - 1e-4).toInt()
        val bottom = Math.ceil(height * cropBottom.coerceIn(0f, MAX_CROP).toDouble() - 1e-4).toInt()
        val rows = (height - top - bottom).coerceAtLeast(1)
        return top.coerceAtMost(height - 1) to rows
    }

    fun heroUrl(art: HeroArt): String = HERO_BASE + art.path + HERO_QUERY

    /** Schlüssel im Bildspeicher, unabhängig von Pfad und Endung. */
    fun heroKey(art: HeroArt): String = "hero_${art.name}"

    /** Zonenkarten (Classic) bei Wowhead: das verkleinerte Format (772 x 515). */
    const val MAP_BASE = "https://wow.zamimg.com/images/wow/classic/maps/enus/zoom/"

    /** Zonenkarte je Kachel: Durotar, Teldrassil, Eisenschmiede, Sturmwind, Elwynn, Orgrimmar. */
    fun mapId(tile: LauncherTile): Int = when (tile) {
        LauncherTile.Touch -> 14
        LauncherTile.Addons -> 141
        LauncherTile.Settings -> 1537
        LauncherTile.Files -> 1519
        LauncherTile.News -> 12
        LauncherTile.Help -> 1637
    }

    fun tileUrl(tile: LauncherTile): String = "$MAP_BASE${mapId(tile)}.jpg"

    fun tileKey(tile: LauncherTile): String = "tile_${mapId(tile)}"
}
