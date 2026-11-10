package app.gamenative.ui.screen.wow

import org.junit.Assert.assertEquals
import org.junit.Test

class FitScreenSizeTest {

    @Test
    fun sixteenByNineWindow_keepsBaseSize() {
        assertEquals("1920x1080", fitScreenSize("1920x1080", 2400, 1350))
    }

    @Test
    fun foldInnerScreen_keepsPixelBudget() {
        assertEquals("1104x834", fitScreenSize("1280x720", 2448, 1848))
    }

    @Test
    fun portraitWindow_usesLandscapeAspect() {
        assertEquals("1104x834", fitScreenSize("1280x720", 1848, 2448))
    }

    @Test
    fun adreno740Base_onFoldInnerScreen() {
        assertEquals("1658x1252", fitScreenSize("1920x1080", 2448, 1848))
    }

    @Test
    fun widePhone_widensAndShortens() {
        assertEquals("2146x966", fitScreenSize("1920x1080", 2400, 1080))
    }

    @Test
    fun unknownWindow_keepsBaseSize() {
        assertEquals("1280x720", fitScreenSize("1280x720", 0, 0))
    }

    @Test
    fun galaxyFoldInner_720p900p1080p() {
        assertEquals("1012x912", fitScreenSize("1280x720", 2184, 1968))
        assertEquals("1264x1140", fitScreenSize("1600x900", 2184, 1968))
        assertEquals("1516x1366", fitScreenSize("1920x1080", 2184, 1968))
    }

    @Test
    fun galaxyFoldOuter_720p900p1080p() {
        assertEquals("1466x628", fitScreenSize("1280x720", 2520, 1080))
        assertEquals("1834x786", fitScreenSize("1600x900", 2520, 1080))
        assertEquals("2200x942", fitScreenSize("1920x1080", 2520, 1080))
    }

    @Test
    fun foldInner_portraitAndLandscapeGiveSameSize() {
        assertEquals(fitScreenSize("1600x900", 2184, 1968), fitScreenSize("1600x900", 1968, 2184))
    }

    @Test
    fun resolutionSteps_areOrderedByPixelBudget() {
        assertEquals(listOf("720p", "900p", "1080p"), RenderResolution.entries.map { it.label })
        assertEquals(listOf("1280x720", "1600x900", "1920x1080"), RenderResolution.entries.map { it.size })
    }
}
