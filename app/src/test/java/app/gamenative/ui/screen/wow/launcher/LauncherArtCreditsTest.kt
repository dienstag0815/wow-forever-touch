package app.gamenative.ui.screen.wow.launcher

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherArtCreditsTest {
    /** Ordner mit den mitgelieferten Bildern; Gradle startet Modultests im Ordner "app". */
    private fun artDir(): File? =
        listOf(File("src/main/assets/$BUNDLED_ART_DIR"), File("app/src/main/assets/$BUNDLED_ART_DIR")).firstOrNull { it.isDirectory }

    @Test
    fun everyBundledImageHasExactlyOneCredit() {
        val dir = artDir() ?: return
        val images = dir.list().orEmpty().filter { isBundledHeroName(it) || it.startsWith("tile_") }.sorted()
        assertEquals(images, ART_CREDITS.map { it.file }.sorted())
    }

    @Test
    fun creditsCoverSixHeroesAndAllTiles() {
        assertEquals(6, ART_CREDITS.count { isBundledHeroName(it.file) })
        for (tile in LauncherTile.entries) {
            val found = bundledTileName(tile, ART_CREDITS.map { it.file })
            assertTrue("Kachel $tile ohne Bild", found != null)
        }
    }

    @Test
    fun creditsHaveTitleAuthorAndNoLineBreaks() {
        for (c in ART_CREDITS) {
            assertTrue(c.file, c.title.isNotBlank() && c.author.isNotBlank())
            assertFalse(c.file, artCreditLine(c).contains('\n'))
        }
        assertEquals("Confront the dragon – David Revoy – CC BY 4.0", artCreditLine(ART_CREDITS.first()))
    }

    @Test
    fun licenseLinksListEachUsedLicenseOnce() {
        val used = artLicensesWithLink(ART_CREDITS)
        assertEquals(listOf(ArtLicense.CcBy4, ArtLicense.CcBySa4, ArtLicense.CcBy2, ArtLicense.Unsplash), used)
        assertTrue(artLicensesWithLink(emptyList()).isEmpty())
    }

    @Test
    fun severalHeroesRotateWithTheSeed() {
        val names = ART_CREDITS.map { it.file }
        val picked = (0L until 6L).mapNotNull { pickBundledHero(names, it) }.toSet()
        assertEquals(6, picked.size)
    }
}
