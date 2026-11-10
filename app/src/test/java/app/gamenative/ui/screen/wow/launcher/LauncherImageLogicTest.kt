package app.gamenative.ui.screen.wow.launcher

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LauncherImageLogicTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val flavors = listOf("_forever_", "_classic_beta_")

    // --- Reihenfolge der Quellen ---

    @Test
    fun heroOrderIsScreenshotThenWowArtThenBundledThenDrawn() {
        assertEquals(
            listOf(ArtSource.Screenshot, ArtSource.WowArt, ArtSource.Bundled, ArtSource.Drawn),
            heroSourceOrder(screenshotsOn = true, hasScreenshot = true, wowArtOn = true, hasBundled = true),
        )
    }

    @Test
    fun heroOrderSkipsWhatCannotBeShown() {
        // Schalter aus oder kein Screenshot: Screenshot fehlt
        assertEquals(
            listOf(ArtSource.WowArt, ArtSource.Bundled, ArtSource.Drawn),
            heroSourceOrder(screenshotsOn = true, hasScreenshot = false, wowArtOn = true, hasBundled = true),
        )
        assertEquals(
            listOf(ArtSource.WowArt, ArtSource.Drawn),
            heroSourceOrder(screenshotsOn = false, hasScreenshot = true, wowArtOn = true, hasBundled = false),
        )
        // WoW-Artwork aus
        assertEquals(
            listOf(ArtSource.Screenshot, ArtSource.Bundled, ArtSource.Drawn),
            heroSourceOrder(screenshotsOn = true, hasScreenshot = true, wowArtOn = false, hasBundled = true),
        )
        // alles aus oder leer: nur gezeichnet
        assertEquals(
            listOf(ArtSource.Drawn),
            heroSourceOrder(screenshotsOn = false, hasScreenshot = false, wowArtOn = false, hasBundled = false),
        )
    }

    @Test
    fun heroOrderAlwaysEndsWithDrawn() {
        for (a in listOf(true, false)) for (b in listOf(true, false)) for (c in listOf(true, false)) for (d in listOf(true, false)) {
            assertEquals(ArtSource.Drawn, heroSourceOrder(a, b, c, d).last())
        }
    }

    @Test
    fun tileOrderIsWowMapThenBundledThenDrawn() {
        assertEquals(listOf(ArtSource.WowArt, ArtSource.Bundled, ArtSource.Drawn), tileSourceOrder(wowArtOn = true, hasBundled = true))
        assertEquals(listOf(ArtSource.Bundled, ArtSource.Drawn), tileSourceOrder(wowArtOn = false, hasBundled = true))
        assertEquals(listOf(ArtSource.WowArt, ArtSource.Drawn), tileSourceOrder(wowArtOn = true, hasBundled = false))
        assertEquals(listOf(ArtSource.Drawn), tileSourceOrder(wowArtOn = false, hasBundled = false))
    }

    @Test
    fun rotationPutsTheChosenEntryFirstAndKeepsAll() {
        val list = listOf("a", "b", "c", "d", "e")
        assertEquals(list, rotatedBy(list, 0))
        assertEquals(listOf("c", "d", "e", "a", "b"), rotatedBy(list, 2))
        assertEquals(listOf("c", "d", "e", "a", "b"), rotatedBy(list, 7))
        assertEquals(listOf("e", "a", "b", "c", "d"), rotatedBy(list, -1))
        assertEquals(emptyList<String>(), rotatedBy(emptyList<String>(), 3))
        // jeder Startwert liefert alle fünf, jeder Eintrag kommt bei irgendeinem Startwert vorn
        assertEquals(list.toSet(), (0L..4L).map { rotatedBy(list, it).first() }.toSet())
    }

    // --- mitgelieferte Bilder ---

    @Test
    fun bundledHeroNamesNeedPrefixAndImageExtension() {
        assertTrue(isBundledHeroName("hero_dawn.jpg"))
        assertTrue(isBundledHeroName("Hero1.PNG"))
        assertTrue(isBundledHeroName("hero.webp"))
        assertFalse(isBundledHeroName("QUELLEN.md"))
        assertFalse(isBundledHeroName("tile_help.jpg"))
        assertFalse(isBundledHeroName("hero_notes.txt"))
    }

    @Test
    fun pickingABundledHeroIsStableAndSorted() {
        val names = listOf("tile_help.jpg", "hero_b.jpg", "QUELLEN.md", "hero_a.png", "hero_c.webp")
        assertEquals("hero_a.png", pickBundledHero(names, 0))
        assertEquals("hero_b.jpg", pickBundledHero(names, 1))
        assertEquals("hero_c.webp", pickBundledHero(names, 2))
        assertEquals("hero_a.png", pickBundledHero(names, 3))
        assertNull(pickBundledHero(listOf("QUELLEN.md", "tile_help.jpg"), 5))
        assertNull(pickBundledHero(emptyList(), 5))
    }

    @Test
    fun bundledTileNamesMatchTheTile() {
        val names = listOf("QUELLEN.md", "tile_help.jpg", "tile_touch.PNG", "hero_a.jpg", "tile_news.txt")
        assertEquals("tile_help", bundledTileStem(LauncherTile.Help))
        assertEquals("tile_help.jpg", bundledTileName(LauncherTile.Help, names))
        assertEquals("tile_touch.PNG", bundledTileName(LauncherTile.Touch, names))
        assertNull(bundledTileName(LauncherTile.News, names))
        assertNull(bundledTileName(LauncherTile.Files, names))
    }

    // --- Screenshots ---

    private fun file(dir: File, name: String, bytes: Int = 10, modified: Long? = null): File {
        dir.mkdirs()
        val f = File(dir, name)
        f.writeBytes(ByteArray(bytes) { 1 })
        if (modified != null) f.setLastModified(modified)
        return f
    }

    @Test
    fun screenshotFileFilterTakesJpgAndPngOnly() {
        assertTrue(isScreenshotName("WoWScrnShot_110926_201512.jpg"))
        assertTrue(isScreenshotName("a.JPEG"))
        assertTrue(isScreenshotName("a.png"))
        assertFalse(isScreenshotName("a.tga"))
        assertFalse(isScreenshotName("a.txt"))
        assertFalse(isScreenshotName("jpg"))
        assertFalse(isScreenshotName(".hidden.jpg"))
    }

    @Test
    fun newestScreenshotIsTheLatestModifiedImage() {
        val game = tmp.newFolder("WoW Forever")
        val shots = File(File(game, "_forever_"), "Screenshots")
        file(shots, "old.jpg", modified = 1_000_000L)
        val newest = file(shots, "new.png", modified = 3_000_000L)
        file(shots, "middle.jpg", modified = 2_000_000L)
        file(shots, "newer-but-tga.tga", modified = 9_000_000L)
        file(shots, "notes.txt", modified = 9_000_000L)
        file(shots, "empty.jpg", bytes = 0, modified = 9_000_000L)
        assertEquals(newest.name, newestScreenshot(game.path, flavors)?.name)
        assertEquals(listOf("new.png", "middle.jpg", "old.jpg"), findScreenshots(game.path, flavors).map { it.name })
    }

    @Test
    fun screenshotFolderIsFoundRegardlessOfCase() {
        val game = tmp.newFolder("g1")
        val shots = File(File(game, "_forever_"), "screenshots")
        file(shots, "a.jpg", modified = 1_000_000L)
        assertEquals("a.jpg", newestScreenshot(game.path, flavors)?.name)
    }

    @Test
    fun screenshotsAlsoComeFromTheGameFolderAndTheOldBetaFolder() {
        val game = tmp.newFolder("g2")
        file(File(game, "Screenshots"), "root.jpg", modified = 1_000_000L)
        file(File(File(game, "_classic_beta_"), "Screenshots"), "beta.jpg", modified = 2_000_000L)
        file(File(File(game, "_forever_"), "Screenshots"), "forever.png", modified = 1_500_000L)
        assertEquals(listOf("beta.jpg", "forever.png", "root.jpg"), findScreenshots(game.path, flavors).map { it.name })
    }

    @Test
    fun equalTimesFallBackToTheNameDescending() {
        val game = tmp.newFolder("g3")
        val shots = File(File(game, "_forever_"), "Screenshots")
        file(shots, "a.jpg", modified = 5_000_000L)
        file(shots, "b.jpg", modified = 5_000_000L)
        assertEquals("b.jpg", newestScreenshot(game.path, flavors)?.name)
    }

    @Test
    fun missingFoldersGiveNothing() {
        val game = tmp.newFolder("g4")
        assertNull(newestScreenshot(game.path, flavors))
        assertTrue(findScreenshots(File(game, "does-not-exist").path, flavors).isEmpty())
        // Ordner ohne Bilder
        File(File(game, "_forever_"), "Screenshots").mkdirs()
        assertNull(newestScreenshot(game.path, flavors))
        // Unterordner zählen nicht
        file(File(File(File(game, "_forever_"), "Screenshots"), "sub"), "x.jpg")
        assertNull(newestScreenshot(game.path, flavors))
    }

    // --- Verkleinerung ---

    @Test
    fun sampleSizeIsTheBiggestPowerOfTwoThatKeepsTheRequestedSize() {
        assertEquals(1, sampleSizeFor(1280, 720, 1280, 720))
        assertEquals(1, sampleSizeFor(1920, 1080, 1280, 720))
        assertEquals(2, sampleSizeFor(2560, 1440, 1280, 720))
        assertEquals(2, sampleSizeFor(3840, 2160, 1280, 720))
        assertEquals(4, sampleSizeFor(5120, 2880, 1280, 720))
        assertEquals(1, sampleSizeFor(500, 400, 1280, 720))
        // zu schmale Höhe begrenzt ebenfalls
        assertEquals(1, sampleSizeFor(4000, 800, 1280, 720))
        assertEquals(1, sampleSizeFor(0, 0, 1280, 720))
        assertEquals(1, sampleSizeFor(1000, 1000, 0, 0))
    }
}
