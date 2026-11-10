package app.gamenative.ui.screen.wow.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeMetricsTest {
    private val eps = 0.01f

    /** Die fünf Zielgrößen aus dem Auftrag: kein Scrollen, alle Höhen summieren sich zur Schirmhöhe. */
    @Test
    fun theFiveTargetSizesFitWithoutScrolling() {
        val sizes = listOf(840f to 760f, 760f to 840f, 410f to 900f, 360f to 640f, 1000f to 560f)
        for ((w, h) in sizes) {
            val m = homeMetricsFor(w, h)
            assertFalse("scrollt bei $w x $h", m.scroll)
            assertTrue("Kachel zu niedrig bei $w x $h: ${m.tileHeightDp}", m.tileHeightDp >= HOME_TILE_MIN_DP - eps)
            assertTrue("Hero zu niedrig bei $w x $h: ${m.heroHeightDp}", m.heroHeightDp >= heroMinHeightFor(m.density) - eps)
            val chrome = HOME_HEADER_DP + HOME_FOOTER_DP
            val sum = if (m.layout == HomeLayout.Narrow) {
                chrome + 5f * m.sectionGapDp + m.heroHeightDp + 3f * m.tileHeightDp
            } else {
                chrome + 2f * m.sectionGapDp + m.heroHeightDp
            }
            assertEquals("Höhen ergeben nicht die Schirmhöhe bei $w x $h", h, sum, eps)
        }
    }

    @Test
    fun layoutFollowsTheWidth() {
        assertEquals(HomeLayout.Wide, homeMetricsFor(840f, 760f).layout)
        assertEquals(HomeLayout.Wide, homeMetricsFor(760f, 840f).layout)
        assertEquals(HomeLayout.Wide, homeMetricsFor(1000f, 560f).layout)
        assertEquals(HomeLayout.Narrow, homeMetricsFor(410f, 900f).layout)
        assertEquals(HomeLayout.Narrow, homeMetricsFor(360f, 640f).layout)
    }

    @Test
    fun densityGrowsTighterWithLessHeight() {
        assertEquals(HomeDensity.Roomy, homeMetricsFor(840f, 760f).density)
        assertEquals(HomeDensity.Roomy, homeMetricsFor(410f, 900f).density)
        assertEquals(HomeDensity.Normal, homeMetricsFor(360f, 640f).density)
        assertEquals(HomeDensity.Tight, homeMetricsFor(1000f, 560f).density)
        // Abstände schrumpfen mit der Dichte
        assertTrue(homeMetricsFor(840f, 800f).sectionGapDp > homeMetricsFor(840f, 700f).sectionGapDp)
        assertTrue(homeMetricsFor(840f, 700f).sectionGapDp > homeMetricsFor(840f, 560f).sectionGapDp)
    }

    @Test
    fun scrollingStartsOnlyBelowTheMinimumHeight() {
        // aufgeklappt: Grenze 520 dp
        assertFalse(homeMetricsFor(840f, 520f).scroll)
        assertTrue(homeMetricsFor(840f, 519f).scroll)
        assertTrue(homeMetricsFor(1000f, 400f).scroll)
        // zugeklappt: Grenze 600 dp
        assertFalse(homeMetricsFor(410f, 600f).scroll)
        assertTrue(homeMetricsFor(410f, 599f).scroll)
        // beim Scrollen hat der Mittelteil eine feste Höhe, die zu den Mindestmaßen passt
        val narrow = homeMetricsFor(360f, 560f)
        assertEquals(HOME_TILE_MIN_DP, narrow.tileHeightDp, eps)
        assertEquals(narrow.heroHeightDp + 3f * narrow.tileHeightDp + 3f * narrow.tileGapDp, narrow.middleMinDp, eps)
        val wide = homeMetricsFor(840f, 480f)
        assertTrue(wide.middleMinDp >= 300f)
    }

    @Test
    fun noHeightAboveTheMinimumSqueezesHeroOrTiles() {
        for (h in 600..1400) {
            val m = homeMetricsFor(400f, h.toFloat())
            assertFalse(m.scroll)
            assertTrue("Hero bei Höhe $h: ${m.heroHeightDp}", m.heroHeightDp >= heroMinHeightFor(m.density) - eps)
            assertTrue("Kachel bei Höhe $h: ${m.tileHeightDp}", m.tileHeightDp in HOME_TILE_MIN_DP - eps..HOME_TILE_MAX_DP + eps)
        }
        for (h in 520..1400) {
            val m = homeMetricsFor(900f, h.toFloat())
            assertFalse(m.scroll)
            assertTrue("Kachel bei Höhe $h: ${m.tileHeightDp}", m.tileHeightDp >= HOME_TILE_MIN_DP - eps)
            assertTrue("Raster bei Höhe $h: ${m.heroHeightDp}", m.heroHeightDp >= heroMinHeightFor(m.density) - eps)
        }
    }

    @Test
    fun smallTilesShowOnlyTitleAndOneLine() {
        // 360 x 640: Kachel 73,6 dp -> kurz; 410 x 900: 103,5 dp -> voll
        assertTrue(homeMetricsFor(360f, 640f).tileShort)
        assertFalse(homeMetricsFor(410f, 900f).tileShort)
        assertFalse(homeMetricsFor(840f, 760f).tileShort)
    }

    @Test
    fun theWidthOnlyLayoutFunctionStaysAsItWas() {
        assertEquals(HomeLayout.Narrow, homeLayoutFor(639.9f))
        assertEquals(HomeLayout.Wide, homeLayoutFor(640f))
    }

    // --- Update-Knopf (kein Platzbedarf: Kopf oder Fußzeile tragen ihn) ---

    @Test
    fun updateButtonFitsTheHeaderAndFooterRowsOnAllFiveSizes() {
        val sizes = listOf(840f to 760f, 760f to 840f, 410f to 900f, 360f to 640f, 1000f to 560f)
        for ((w, h) in sizes) {
            val m = homeMetricsFor(w, h)
            val slotDp = if (m.updateSlot == UpdateButtonSlot.Header) HOME_HEADER_DP else HOME_FOOTER_DP
            assertTrue("Knopf höher als seine Zeile bei $w x $h", HOME_UPDATE_BUTTON_DP <= slotDp + eps)
            // die Höhenrechnung ist dieselbe wie ohne Knopf: kein Scrollen, Hero und Kacheln unverändert
            assertFalse("scrollt bei $w x $h", m.scroll)
            assertTrue(m.heroHeightDp >= heroMinHeightFor(m.density) - eps)
            assertTrue(m.tileHeightDp >= HOME_TILE_MIN_DP - eps)
        }
    }

    @Test
    fun updateButtonSitsInTheHeaderWhenWideAndInTheFooterWhenNarrow() {
        assertEquals(UpdateButtonSlot.Header, homeMetricsFor(840f, 760f).updateSlot)
        assertEquals(UpdateButtonSlot.Header, homeMetricsFor(760f, 840f).updateSlot)
        assertEquals(UpdateButtonSlot.Header, homeMetricsFor(1000f, 560f).updateSlot)
        assertEquals(UpdateButtonSlot.Footer, homeMetricsFor(410f, 900f).updateSlot)
        assertEquals(UpdateButtonSlot.Footer, homeMetricsFor(360f, 640f).updateSlot)
        assertEquals(UpdateButtonSlot.Header, updateButtonSlotFor(HomeLayout.Wide))
        assertEquals(UpdateButtonSlot.Footer, updateButtonSlotFor(HomeLayout.Narrow))
    }

    @Test
    fun updateButtonShowsTextOnlyWhenWideAndRoomy() {
        assertTrue(homeMetricsFor(840f, 760f).updateButtonText)
        assertTrue(homeMetricsFor(1000f, 560f).updateButtonText)
        assertFalse(homeMetricsFor(760f, 840f).updateButtonText)
        assertFalse(homeMetricsFor(410f, 900f).updateButtonText)
        assertFalse(homeMetricsFor(360f, 640f).updateButtonText)
        assertTrue(updateButtonShowsText(HomeLayout.Wide, HOME_UPDATE_TEXT_MIN_WIDTH_DP))
        assertFalse(updateButtonShowsText(HomeLayout.Wide, HOME_UPDATE_TEXT_MIN_WIDTH_DP - 1f))
        // zugeklappt nie Text, auch nicht bei großer Breite (die Fußzeile ist dort zu voll)
        assertFalse(updateButtonShowsText(HomeLayout.Narrow, 2000f))
    }
}
