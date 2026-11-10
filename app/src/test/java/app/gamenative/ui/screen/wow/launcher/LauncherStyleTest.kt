package app.gamenative.ui.screen.wow.launcher

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class LauncherStyleTest {

    @Test
    fun contrastRatioMatchesTheKnownExtremes() {
        assertEquals(21.0, LauncherContrast.ratio(0xFFFFFFFFL, 0xFF000000L), 0.001)
        assertEquals(1.0, LauncherContrast.ratio(0xFF336699L, 0xFF336699L), 0.0001)
        // symmetrisch
        assertEquals(
            LauncherContrast.ratio(LauncherPalette.GOLD, LauncherPalette.SURFACE),
            LauncherContrast.ratio(LauncherPalette.SURFACE, LauncherPalette.GOLD),
            0.0,
        )
    }

    @Test
    fun everyTextSurfacePairReachesAtLeast4Point5() {
        val bad = LauncherContrast.pairs.filter { LauncherContrast.ratio(it.text, it.surface) < 4.5 }
        assertTrue(
            "Kontrast unter 4,5: " + bad.joinToString { "${it.name} = ${"%.2f".format(LauncherContrast.ratio(it.text, it.surface))}" },
            bad.isEmpty(),
        )
        // Die Liste ist nicht leer und deckt Flächen, Hauptknopf und beide Abzeichen ab.
        assertTrue(LauncherContrast.pairs.size >= 40)
        val names = LauncherContrast.pairs.map { it.name }
        assertTrue("Hellgold auf Karmesin" in names)
        assertTrue("Abzeichen Nur hier" in names)
        assertTrue("Abzeichen Auch im Spiel" in names)
    }

    /** Textbalken auf Hero und Kacheln: Schrift gegen den schwächsten Balkenwert (72 % unter dem Text, 80 % unten) über hellstem Bild. */
    @Test
    fun textBarKeepsContrastOverTheBrightestImage() {
        val bad = LauncherContrast.barPairs.filter { LauncherContrast.ratio(it.text, it.surface) < 4.5 }
        assertTrue(
            "Balken-Kontrast unter 4,5: " + bad.joinToString { "${it.name} = ${"%.2f".format(LauncherContrast.ratio(it.text, it.surface))}" },
            bad.isEmpty(),
        )
        // 3 Texte x 2 Balkenwerte x 4 Hintergründe
        assertEquals(24, LauncherContrast.barPairs.size)
        // Der schwächste Fall (Hellgold auf Textzone über Weiß) liegt klar über der Grenze.
        val worst = LauncherContrast.barPairs.minOf { LauncherContrast.ratio(it.text, it.surface) }
        assertTrue("schwächster Balken-Kontrast $worst", worst >= 5.0)
    }

    @Test
    fun barColorsAreTheAgreedDarkBrownAt72And80Percent() {
        assertEquals(0xB8L, (LauncherPalette.BAR_TEXT_ZONE shr 24) and 0xFFL)
        assertEquals(0xCCL, (LauncherPalette.BAR_BOTTOM shr 24) and 0xFFL)
        assertEquals(0L, (LauncherPalette.BAR_CLEAR shr 24) and 0xFFL)
        for (c in listOf(LauncherPalette.BAR_CLEAR, LauncherPalette.BAR_TEXT_ZONE, LauncherPalette.BAR_BOTTOM)) {
            assertEquals(0x0C0906L, c and 0xFFFFFFL)
        }
        assertEquals(0.8, 0xCC / 255.0, 0.001)
        assertEquals(0.72, 0xB8 / 255.0, 0.005)
    }

    @Test
    fun overCompositesAlphaOverAnOpaqueBackground() {
        assertEquals(0xFFFFFFFFL, LauncherContrast.over(0x00000000L, 0xFFFFFFFFL))
        assertEquals(0xFF000000L, LauncherContrast.over(0xFF000000L, 0xFFFFFFFFL))
        // 128/255 Schwarz über Weiß = 127
        assertEquals(0xFF7F7F7FL, LauncherContrast.over(0x80000000L, 0xFFFFFFFFL))
        // volle Deckkraft ersetzt den Hintergrund
        assertEquals(0xFF336699L, LauncherContrast.over(0xFF336699L, 0xFFAABBCCL))
    }

    @Test
    fun paletteKeepsTheAgreedAnchorValues() {
        assertEquals(0xFF14100CL, LauncherPalette.STONE_DARK)
        assertEquals(0xFF1E1812L, LauncherPalette.STONE_LIGHT)
        assertEquals(0xFFE2C995L, LauncherPalette.GOLD_BRIGHT)
        assertEquals(0xFFB59A72L, LauncherPalette.GOLD)
        assertEquals(0xFF85673CL, LauncherPalette.BRONZE)
        assertEquals(0xFF5F2226L, LauncherPalette.CRIMSON_DARK)
        assertEquals(0xFFF0E6D2L, LauncherPalette.TEXT_HEAD)
        assertEquals(0xFFE9DCB8L, LauncherPalette.PARCHMENT)
    }

    private fun saturation(argb: Long): Double {
        val r = ((argb shr 16) and 0xFF) / 255.0
        val g = ((argb shr 8) and 0xFF) / 255.0
        val b = (argb and 0xFF) / 255.0
        val hi = maxOf(r, g, b)
        return if (hi == 0.0) 0.0 else (hi - minOf(r, g, b)) / hi
    }

    /** Milde Metalle: Altgold statt Gelb, Burgund statt Signalrot (fold8.29). */
    @Test
    fun goldAndRedAreMuted() {
        assertTrue("Hellgold zu gesättigt", saturation(LauncherPalette.GOLD_BRIGHT) <= 0.40)
        assertTrue("Gold zu gesättigt", saturation(LauncherPalette.GOLD) <= 0.40)
        assertTrue("Bronze zu gesättigt", saturation(LauncherPalette.BRONZE) <= 0.58)
        assertTrue("Burgund zu gesättigt", saturation(LauncherPalette.CRIMSON) <= 0.66)
        // Rot dunkler als das frühere Signalrot (Rotanteil 0xAD) und der Verlauf geht nach unten dunkler.
        assertTrue(((LauncherPalette.CRIMSON shr 16) and 0xFFL) < 0xADL)
        assertTrue(LauncherContrast.luminance(LauncherPalette.CRIMSON_DARK) < LauncherContrast.luminance(LauncherPalette.CRIMSON))
        // Hellgold liegt unter dem früheren Gelbgold F2D27A.
        assertTrue(LauncherContrast.luminance(LauncherPalette.GOLD_BRIGHT) < LauncherContrast.luminance(0xFFF2D27AL))
    }

    /** Knopf "SPIELEN": Text >= 3:1 (verlangt), hier sogar >= 4,5 auf beiden Verlaufsenden; Rand (oben Gold) nicht heller als der Text. */
    @Test
    fun playButtonKeepsContrastAndRimIsNotBrighterThanText() {
        for (fill in listOf(LauncherPalette.CRIMSON, LauncherPalette.CRIMSON_DARK)) {
            val ratio = LauncherContrast.ratio(LauncherPalette.GOLD_BRIGHT, fill)
            assertTrue("Knopftext %.2f".format(ratio), ratio >= 4.5)
        }
        assertTrue(LauncherContrast.luminance(LauncherPalette.GOLD) <= LauncherContrast.luminance(LauncherPalette.GOLD_BRIGHT))
        // Rand gegen die Knopffläche bleibt als Kante sichtbar (>= 3:1 für Grafik).
        assertTrue(LauncherContrast.ratio(LauncherPalette.GOLD, LauncherPalette.CRIMSON) >= 3.0)
        assertTrue(LauncherContrast.ratio(LauncherPalette.BRONZE, LauncherPalette.CRIMSON_DARK) >= 1.5)
    }

    /** Titel (Gold, >= 18 sp) auf den Flächen >= 3:1, Karten-/Schalterrand (Bronze) auf Fläche >= 3:1, "Neu"-Abzeichen Tinte auf Hellgold >= 4,5. */
    @Test
    fun titlesBordersAndNewBadgeStayReadable() {
        for (surface in listOf(LauncherPalette.STONE_DARK, LauncherPalette.STONE_LIGHT, LauncherPalette.SURFACE, LauncherPalette.SURFACE_RAISED, LauncherPalette.SURFACE_PRESSED)) {
            assertTrue(LauncherContrast.ratio(LauncherPalette.GOLD_BRIGHT, surface) >= 3.0)
            assertTrue(LauncherContrast.ratio(LauncherPalette.GOLD, surface) >= 4.5)
        }
        assertTrue(LauncherContrast.ratio(LauncherPalette.BRONZE, LauncherPalette.SURFACE) >= 3.0)
        assertTrue(LauncherContrast.ratio(LauncherPalette.BRONZE, LauncherPalette.STONE_DARK) >= 3.0)
        assertTrue(LauncherContrast.ratio(LauncherPalette.INK, LauncherPalette.GOLD_BRIGHT) >= 4.5)
        // Schalter an: Daumen (Text hell) auf Bronze-Spur.
        assertTrue(LauncherContrast.ratio(LauncherPalette.TEXT_HEAD, LauncherPalette.BRONZE) >= 3.0)
    }

    @Test
    fun flatSurfacesAreWarmNotBlue() {
        for (argb in listOf(LauncherPalette.STONE_DARK, LauncherPalette.STONE_LIGHT, LauncherPalette.SURFACE, LauncherPalette.SURFACE_RAISED)) {
            val r = (argb shr 16) and 0xFFL
            val b = argb and 0xFFL
            assertTrue("Fläche %06X ist nicht warm".format(argb and 0xFFFFFFL), r > b)
        }
    }

    /** Keine Farbliteral-Streuung: im Paket launcher/ steht `Color(0x...)` nur in LauncherStyle.kt. */
    @Test
    fun noColorLiteralsOutsideTheStyleFile() {
        val dir = listOf(
            "src/main/java/app/gamenative/ui/screen/wow/launcher",
            "app/src/main/java/app/gamenative/ui/screen/wow/launcher",
        ).map { File(it) }.firstOrNull { it.isDirectory }
        assumeTrue("Quellordner nicht gefunden", dir != null)
        val offenders = dir!!.listFiles { f -> f.extension == "kt" && f.name != "LauncherStyle.kt" }.orEmpty()
            .filter { Regex("""Color\(\s*0x""").containsMatchIn(it.readText()) }
            .map { it.name }
        assertTrue("Color(0x...) außerhalb von LauncherStyle.kt: $offenders", offenders.isEmpty())
    }
}
