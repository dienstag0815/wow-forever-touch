package app.gamenative.ui.screen.wow.launcher

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** fold8.25: Hauptbild zentriert (Fokus und Zuschnitt), Schriftgrößen und Abstände der Startseite. */
class LauncherReadabilityTest {
    private val eps = 0.01f

    // --- Zuschnitt auf den Fokus ---

    @Test
    fun centerFocusGivesTheCenteredCrop() {
        // 1280 x 720 in einen Platz 400 x 400: Ausschnitt 720 x 720 aus der Mitte
        assertEquals(SourceRect(280, 0, 720, 720), focusCropRect(1280, 720, 400f, 400f, ArtFocus()))
        // 1280 x 720 in einen Platz 640 x 360 (gleiches Seitenverhältnis): ganzes Bild
        assertEquals(SourceRect(0, 0, 1280, 720), focusCropRect(1280, 720, 640f, 360f, ArtFocus()))
        // hoher Platz 300 x 600: Ausschnitt 360 x 720, mittig
        assertEquals(SourceRect(460, 0, 360, 720), focusCropRect(1280, 720, 300f, 600f, ArtFocus()))
    }

    @Test
    fun theCropIsCenteredOnTheFocusWhereTheImageAllows() {
        // Fokus bei 40 % der Breite: Mitte des Ausschnitts bei 512 Pixeln
        val r = focusCropRect(1280, 720, 300f, 600f, ArtFocus(0.4f, 0.5f))
        assertEquals(360, r.width)
        assertEquals(720, r.height)
        assertEquals(512, r.left + r.width / 2)
        assertEquals(0, r.top)
        // Fokus in der Höhe: breiter, flacher Platz 800 x 200 aus 1280 x 720: Ausschnitt 1280 x 320, Fokus 0,25
        val flat = focusCropRect(1280, 720, 800f, 200f, ArtFocus(0.5f, 0.25f))
        assertEquals(1280, flat.width)
        assertEquals(320, flat.height)
        assertEquals(180, flat.top + flat.height / 2)
    }

    @Test
    fun theCropNeverLeavesTheImage() {
        val sizes = listOf(
            1280 to 720, 1280 to 545, 1280 to 865, 1920 to 1080, 640 to 360, 100 to 100,
        )
        val boxes = listOf(340f to 640f, 410f to 300f, 1000f to 560f, 300f to 300f, 123.5f to 777.7f, 2000f to 100f)
        val foci = listOf(ArtFocus(0f, 0f), ArtFocus(1f, 1f), ArtFocus(0.2f, 0.9f), ArtFocus(-3f, 7f), ArtFocus(Float.NaN, 0.5f), ArtFocus())
        for ((w, h) in sizes) for ((bw, bh) in boxes) for (f in foci) {
            val r = focusCropRect(w, h, bw, bh, f)
            val msg = "Bild ${w}x$h Platz ${bw}x$bh Fokus $f -> $r"
            assertTrue(msg, r.left >= 0 && r.top >= 0 && r.width >= 1 && r.height >= 1)
            assertTrue(msg, r.left + r.width <= w && r.top + r.height <= h)
            // Seitenverhältnis des Ausschnitts = das des Platzes (bis auf die Rundung auf ganze Pixel)
            val ratioBox = bw / bh
            val ratioCrop = r.width.toFloat() / r.height
            assertEquals(msg, ratioBox.toDouble(), ratioCrop.toDouble(), ratioBox * 0.02 + 0.02)
            // Ein Rand des Bildes ist ganz gefüllt
            assertTrue(msg, r.width == w || r.height == h)
        }
    }

    @Test
    fun invalidSizesGiveTheWholeImage() {
        assertEquals(SourceRect(0, 0, 1280, 720), focusCropRect(1280, 720, 0f, 100f, ArtFocus()))
        assertEquals(SourceRect(0, 0, 1280, 720), focusCropRect(1280, 720, 100f, -1f, ArtFocus()))
        assertEquals(SourceRect(0, 0, 0, 0), focusCropRect(0, 0, 100f, 100f, ArtFocus()))
    }

    @Test
    fun focusIsClampedToTheImage() {
        assertEquals(ArtFocus(0f, 1f), ArtFocus(-2f, 5f).clamped())
        assertEquals(ArtFocus(0.5f, 0.5f), ArtFocus(Float.NaN, Float.NaN).clamped())
        // Fokus ganz links: Ausschnitt beginnt bei 0, nicht davor
        assertEquals(0, focusCropRect(1280, 720, 300f, 600f, ArtFocus(0f, 0.5f)).left)
        // ganz rechts: Ausschnitt endet am Bildrand
        val right = focusCropRect(1280, 720, 300f, 600f, ArtFocus(1f, 0.5f))
        assertEquals(1280, right.left + right.width)
    }

    // --- Fokus der Bilder ---

    @Test
    fun everyBlizzardHeroHasAFocusInsideTheImage() {
        assertEquals(5, WowArtSources.HERO.size)
        for (art in WowArtSources.HERO) {
            assertTrue("${art.name} Fokus x ${art.focusX}", art.focusX in 0.1f..0.9f)
            assertTrue("${art.name} Fokus y ${art.focusY}", art.focusY in 0.2f..0.8f)
            assertEquals(ArtFocus(art.focusX, art.focusY), art.focus())
        }
        // Gesetzt, nicht Standard: mindestens vier der fünf Bilder haben einen Fokus links der Mitte (Figuren stehen links)
        assertTrue(WowArtSources.HERO.count { it.focusX < 0.45f } >= 4)
        // Der Standard ist die Mitte
        assertEquals(ArtFocus(0.5f, 0.5f), WowArtSources.HeroArt("x", "y").focus())
    }

    @Test
    fun bundledHeroFocusComesFromTheTableAndDefaultsToTheCenter() {
        assertEquals(ArtFocus(0.58f, 0.45f), bundledHeroFocus("hero_eilean_donan.jpg"))
        assertEquals(ArtFocus(0.58f, 0.45f), bundledHeroFocus("HERO_EILEAN_DONAN.JPG"))
        assertEquals(ArtFocus(), bundledHeroFocus("hero_unbekannt.jpg"))
        assertEquals(ArtFocus(), bundledHeroFocus("hero_ohne_endung"))
        for ((name, f) in BUNDLED_HERO_FOCUS) {
            assertTrue(name, isBundledHeroName("$name.jpg"))
            assertEquals(name, f, f.clamped())
        }
    }

    @Test
    fun everyBundledHeroImageInTheAssetsHasAFocusEntry() {
        val dir = listOf("src/main/assets/launcher_art", "app/src/main/assets/launcher_art").map { File(it) }.firstOrNull { it.isDirectory }
        assumeTrue("Assets nicht gefunden", dir != null)
        val heroes = dir!!.listFiles { f -> isBundledHeroName(f.name) }.orEmpty().map { it.name.substringBeforeLast('.').lowercase() }
        assertTrue(heroes.isNotEmpty())
        val missing = heroes.filter { it !in BUNDLED_HERO_FOCUS }
        assertTrue("Hero ohne Fokus-Eintrag: $missing", missing.isEmpty())
    }

    // --- Schrift ---

    @Test
    fun heroTitleIs40To48SpWideAndNeverBelow34Compact() {
        for (d in HomeDensity.entries) {
            val wide = heroTitleSp(compact = false, density = d)
            assertTrue("aufgeklappt $d = $wide", wide in 40f..48f)
            val compact = heroTitleSp(compact = true, density = d)
            assertTrue("zugeklappt $d = $compact", compact in 34f..48f)
            assertTrue(compact <= wide)
        }
        assertEquals(48f, heroTitleSp(false, HomeDensity.Roomy), eps)
        assertEquals(40f, heroTitleSp(false, HomeDensity.Tight), eps)
    }

    @Test
    fun tileTitlesAndLinesAreAsLargeAsAgreed() {
        assertEquals(23f, tileTitleSp(compact = false, short = false), eps)
        assertEquals(20f, tileTitleSp(compact = true, short = false), eps)
        assertEquals(17f, tileTitleSp(compact = true, short = true), eps)
        assertEquals(15f, tileLineSp(short = false), eps)
        assertEquals(13f, tileLineSp(short = true), eps)
        for (compact in listOf(false, true)) for (d in HomeDensity.entries) {
            assertTrue(heroLineSp(compact, d) in 15f..16f)
        }
        assertTrue(HOME_MIN_SP >= 13f)
        assertTrue(tileLineSp(true) >= HOME_MIN_SP)
    }

    /** Kacheltitel in Cinzel (Versalien) wie der Hero: lange Titel schrumpfen, bis das längste Wort passt, nie unter 15 sp. */
    @Test
    fun longTileTitlesFitTheTileWidthWithoutBreakingWords() {
        // Textbreiten aus dem Bericht: 192 dp aufgeklappt (Start 23 sp), 173 dp zugeklappt (Start 20 sp)
        for (title in listOf("Einstellungen", "Spieldateien", "Hilfe und Diagnose")) {
            for ((width, start) in listOf(192f to 23f, 173f to 20f)) {
                val caps = title.uppercase()
                val sp = fitTitleSp(caps, width, start, TILE_TITLE_MIN_SP)
                assertTrue("$caps bei $width dp: $sp sp", sp >= TILE_TITLE_MIN_SP - eps)
                val longest = caps.split(' ').maxOf { it.length }
                assertTrue("$caps bei $width dp: Wort bricht", longest * CAPS_EM_PER_CHAR * sp <= width + 0.5f)
            }
        }
        // EINSTELLUNGEN (13 Zeichen): 192 / (13 * 0,88) = 16,8 sp; 173 / (13 * 0,88) = 15,1 sp
        assertEquals(192f / (13 * CAPS_EM_PER_CHAR), fitTitleSp("EINSTELLUNGEN", 192f, 23f, TILE_TITLE_MIN_SP), eps)
        assertEquals(173f / (13 * CAPS_EM_PER_CHAR), fitTitleSp("EINSTELLUNGEN", 173f, 20f, TILE_TITLE_MIN_SP), eps)
        // SPIELDATEIEN (12 Zeichen): 18,2 sp und 16,4 sp
        assertEquals(192f / (12 * CAPS_EM_PER_CHAR), fitTitleSp("SPIELDATEIEN", 192f, 23f, TILE_TITLE_MIN_SP), eps)
        assertEquals(173f / (12 * CAPS_EM_PER_CHAR), fitTitleSp("SPIELDATEIEN", 173f, 20f, TILE_TITLE_MIN_SP), eps)
        // HILFE UND DIAGNOSE: das längste Wort (DIAGNOSE, 8 Zeichen) passt schon bei der Höchstgröße, der Titel darf zweizeilig sein
        assertEquals(23f, fitTitleSp("HILFE UND DIAGNOSE", 192f, 23f, TILE_TITLE_MIN_SP), eps)
        assertEquals(20f, fitTitleSp("HILFE UND DIAGNOSE", 173f, 20f, TILE_TITLE_MIN_SP), eps)
    }

    @Test
    fun longHeroTitlesShrinkUntilTheirLongestWordFits() {
        // kurzes Wort: volle Größe
        assertEquals(44f, fitTitleSp("SPIELEN", 400f, 44f, 28f), eps)
        // "INSTALLIERT" (11 Zeichen) in 284 dp: 284 / (11 * 0,88) = 29,3
        val s = fitTitleSp("WIRD INSTALLIERT", 284f, 44f, 28f)
        assertEquals(284f / (11 * CAPS_EM_PER_CHAR), s, eps)
        assertTrue(s in 28f..44f)
        // nie kleiner als das Minimum, nie größer als das Maximum
        assertEquals(28f, fitTitleSp("WIRD INSTALLIERT", 100f, 44f, 28f), eps)
        assertEquals(44f, fitTitleSp("WIRD INSTALLIERT", 5000f, 44f, 28f), eps)
        // ungültige Breite oder leerer Text: Maximum
        assertEquals(44f, fitTitleSp("X", 0f, 44f, 28f), eps)
        assertEquals(44f, fitTitleSp("", 300f, 44f, 28f), eps)
        // Minimum über Maximum: Maximum gewinnt
        assertEquals(20f, fitTitleSp("LANGESWORTLANGESWORT", 50f, 20f, 28f), eps)
    }

    /** Die Texte der Startseite brauchen einen Platz, in dem das längste Hero-Wort in der kleinsten Größe noch in eine Zeile passt. */
    @Test
    fun theLongestHeroWordFitsTheSmallestHeroOnBothDisplays() {
        val words = listOf("Zugriff nötig", "Upgrade", "Installieren", "Update", "Wird installiert", "Startet ...", "Prüfe ...", "Spielen", "Falsches Spiel")
        // schmalster Hero: zugeklappt 360 dp breit minus Ränder 32 und Hero-Abstand 2 x 14; aufgeklappt 760 dp: 1,45/3,45 der Breite
        val narrow = 360f - 32f - 28f
        val wide = (760f - 32f - 10f) * 1.45f / 3.45f - 2f * 16f
        for (w in words) for (width in listOf(narrow, wide)) {
            val sp = fitTitleSp(w.uppercase(), width, 48f, HERO_TITLE_MIN_SP)
            val longest = w.uppercase().split(' ').maxOf { it.length }
            assertTrue("$w passt nicht in $width dp bei $sp sp", longest * CAPS_EM_PER_CHAR * sp <= width + 0.5f || sp <= HERO_TITLE_MIN_SP + eps)
        }
        // Kein Titel fällt unter 26 sp, und "INSTALLIERT" passt in beiden Plätzen ohne Mindestgröße-Notlösung
        assertTrue(fitTitleSp("INSTALLIERT", narrow, 48f, HERO_TITLE_MIN_SP) >= HERO_TITLE_MIN_SP)
        assertTrue(11 * CAPS_EM_PER_CHAR * HERO_TITLE_MIN_SP <= wide)
    }

    // --- Abstände und Höhen ---

    @Test
    fun tileGapsAre10To12DpAndStayOrdered() {
        for (h in listOf(560f, 640f, 700f, 760f, 900f)) {
            for (w in listOf(410f, 840f)) {
                val g = homeMetricsFor(w, h).tileGapDp
                assertTrue("Abstand $g bei ${w}x$h", g in 10f..12f)
            }
        }
        assertTrue(homeMetricsFor(840f, 800f).sectionGapDp > homeMetricsFor(840f, 700f).sectionGapDp)
        assertTrue(homeMetricsFor(840f, 700f).sectionGapDp > homeMetricsFor(840f, 560f).sectionGapDp)
    }

    @Test
    fun theFiveTargetSizesStillFitWithoutScrolling() {
        for ((w, h) in listOf(840f to 760f, 760f to 840f, 410f to 900f, 360f to 640f, 1000f to 560f)) {
            val m = homeMetricsFor(w, h)
            assertFalse("scrollt bei $w x $h", m.scroll)
            assertTrue("Hero ${m.heroHeightDp} bei $w x $h", m.heroHeightDp >= heroMinHeightFor(m.density) - eps)
            assertTrue("Kachel ${m.tileHeightDp} bei $w x $h", m.tileHeightDp >= HOME_TILE_MIN_DP - eps)
        }
    }

    // --- Quelltext: keine kleine Schrift, keine Doppelrahmen auf Kacheln ---

    private fun sourceDirs(): List<File> {
        val roots = listOf("src/main/java/app/gamenative/ui/screen/wow", "app/src/main/java/app/gamenative/ui/screen/wow").map { File(it) }
        val root = roots.firstOrNull { it.isDirectory } ?: return emptyList()
        return listOf(File(root, "launcher"), root)
    }

    @Test
    fun noFontBelow12SpOnTheLauncherPages() {
        val dirs = sourceDirs()
        assumeTrue("Quellordner nicht gefunden", dirs.isNotEmpty())
        val names = setOf("LauncherHome.kt", "LauncherSettingsRows.kt", "LauncherSettingsPage.kt", "AddonsPage.kt")
        val files = dirs[0].listFiles { f -> f.extension == "kt" }.orEmpty().toList() + dirs[1].listFiles { f -> f.name in names }.orEmpty().toList()
        assertTrue(files.size >= 15)
        val small = Regex("""fontSize\s*=\s*(\d+(?:\.\d+)?)\.sp""")
        val offenders = files.flatMap { f ->
            small.findAll(f.readText()).filter { it.groupValues[1].toFloat() < 12f }.map { "${f.name}: ${it.value}" }.toList()
        }
        assertTrue("Schrift unter 12 sp: $offenders", offenders.isEmpty())
    }

    @Test
    fun homeTilesAndHeroUseTheSoftFrameNotTheOrnateOne() {
        val dirs = sourceDirs()
        assumeTrue("Quellordner nicht gefunden", dirs.isNotEmpty())
        val parts = File(dirs[0], "LauncherParts.kt")
        assumeTrue("LauncherParts.kt nicht gefunden", parts.isFile)
        val text = parts.readText()
        val tile = text.substringAfter("// --- Kachel ---").substringBefore("// --- Hero ---")
        val hero = text.substringAfter("// --- Hero ---").substringBefore("// --- Banner und Fuß ---")
        assertTrue(tile.contains(".softFrame("))
        assertTrue(hero.contains(".softFrame("))
        assertFalse(tile.contains(".ornateFrame("))
        assertFalse(hero.contains(".ornateFrame("))
        // Text steht auf dem Balken
        assertTrue(tile.contains("ImageTextBar("))
        assertTrue(hero.contains("ImageTextBar("))
        // Eine Linie von 1 dp, Ecken 14 dp
        assertEquals(1f, LauncherStyle.CARD_LINE_DP, eps)
        assertEquals(14f, LauncherStyle.CardRadius.value, eps)
    }

    @Test
    fun tileAndNewsTitlesUseCinzelAndTheTileTitleFitsItsWidth() {
        val dirs = sourceDirs()
        assumeTrue("Quellordner nicht gefunden", dirs.isNotEmpty())
        val parts = File(dirs[0], "LauncherParts.kt")
        assumeTrue("LauncherParts.kt nicht gefunden", parts.isFile)
        val tile = parts.readText().substringAfter("// --- Kachel ---").substringBefore("// --- Hero ---")
        assertTrue(tile.contains("BoxWithConstraints("))
        assertTrue(tile.contains("val titleText = tile.title.asString().uppercase()"))
        assertTrue(tile.contains("fitTitleSp(titleText, innerWidthDp, titleStartSp, TILE_TITLE_MIN_SP)"))
        assertTrue(tile.contains("fontFamily = LauncherFonts.Cinzel"))
        assertTrue(tile.contains("maxLines = 2"))
        assertFalse(tile.contains("titleSp.sp"))
        // Abschnittsüberschrift im Update-Hinweis der Nachrichtenseite: gleiche Familie
        val news = File(dirs[0], "NewsPage.kt")
        assumeTrue("NewsPage.kt nicht gefunden", news.isFile)
        val newsText = news.readText()
        val heading = newsText.indexOf("R.string.launcher_news_changes_since")
        assertTrue(heading >= 0)
        assertTrue(newsText.substring(heading, minOf(newsText.length, heading + 300)).contains("fontFamily = LauncherFonts.Cinzel"))
    }
}
