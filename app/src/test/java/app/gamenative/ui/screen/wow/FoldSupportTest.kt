package app.gamenative.ui.screen.wow

import app.gamenative.TestTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoldSupportTest {

    @Test
    fun foldOuterScreen_isFoldedShut() {
        assertTrue(isFoldedShut(2520, 1080, 2184, 1968))
    }

    @Test
    fun foldInnerScreen_isNotFolded() {
        assertFalse(isFoldedShut(2184, 1968, 2184, 1968))
        assertFalse(isFoldedShut(1968, 2184, 2184, 1968))
    }

    @Test
    fun ordinaryPhone_neverFolded() {
        assertFalse(isFoldedShut(2400, 1080, 2400, 1080))
        // Split-screen on a phone is a smaller window, but the display is not near-square.
        assertFalse(isFoldedShut(1200, 1080, 2400, 1080))
    }

    @Test
    fun unknownSizes_notFolded() {
        assertFalse(isFoldedShut(0, 0, 2184, 1968))
        assertFalse(isFoldedShut(2184, 1968, 0, 0))
    }

    @Test
    fun largeSquareInnerDisplay_defaultsTo900p() {
        assertEquals(RenderResolution.HD_PLUS, defaultResolution("1280x720", 2184, 1968))
        assertEquals(RenderResolution.HD_PLUS, defaultResolution("1920x1080", 2184, 1968))
    }

    @Test
    fun otherDisplays_keepTheGpuDefault() {
        assertEquals(RenderResolution.HD, defaultResolution("1280x720", 2400, 1080))
        assertEquals(RenderResolution.FULL_HD, defaultResolution("1920x1080", 2400, 1080))
        // Near-square but the short side is below 1600 px.
        assertEquals(RenderResolution.HD, defaultResolution("1280x720", 1500, 1400))
        // Large but not square (16:10 tablet).
        assertEquals(RenderResolution.HD, defaultResolution("1280x720", 2560, 1600))
    }

    // --- Fold/unfold during play (Aufklappen-Auftrag) ---

    @Test
    fun startedFolded_pictureStillFitsTheInnerDisplay() {
        assertEquals(2184 to 1968, fitBasis(true, 2520, 1080, 2184, 1968))
    }

    @Test
    fun startedUnfolded_picturePicksTheFullDisplay() {
        assertEquals(2184 to 1968, fitBasis(false, 2184, 1968, 2184, 1968))
        // Not folded, window smaller than the display (split screen): still the full display.
        assertEquals(2184 to 1968, fitBasis(false, 1000, 1968, 2184, 1968))
    }

    @Test
    fun startedFolded_unknownWindowFallsBackToTheFullDisplay() {
        assertEquals(2184 to 1968, fitBasis(true, 0, 0, 2184, 1968))
    }

    @Test
    fun unknownFullDisplay_usesTheCurrentWindow() {
        assertEquals(2520 to 1080, fitBasis(true, 2520, 1080, 0, 0))
    }

    @Test
    fun hint_onlyWhenTheFoldStateDiffersFromStart() {
        assertEquals(FoldHint.NONE, foldHintFor(startedFolded = false, foldedNow = false))
        assertEquals(FoldHint.NONE, foldHintFor(startedFolded = true, foldedNow = true))
        assertEquals(FoldHint.FOLDED_AFTER_UNFOLDED_START, foldHintFor(startedFolded = false, foldedNow = true))
        assertEquals(FoldHint.UNFOLDED_AFTER_FOLDED_START, foldHintFor(startedFolded = true, foldedNow = false))
    }

    @Test
    fun hint_textExistsForBothDirectionsAndNotForNone() {
        assertEquals(null, foldHintText(FoldHint.NONE))
        assertTrue(TestTexts.de(foldHintText(FoldHint.FOLDED_AFTER_UNFOLDED_START)!!).startsWith("Zugeklappt"))
        assertTrue(TestTexts.de(foldHintText(FoldHint.UNFOLDED_AFTER_FOLDED_START)!!).startsWith("Aufgeklappt"))
    }

    @Test
    fun quickFoldUnfoldFold_endsWithTheHintForTheLastState() {
        // Started unfolded; fold, unfold, fold again quickly: the last state decides, nothing accumulates.
        val states = listOf(true, false, true)
        val last = states.map { foldHintFor(startedFolded = false, foldedNow = it) }.last()
        assertEquals(FoldHint.FOLDED_AFTER_UNFOLDED_START, last)
    }

    @Test
    fun foldSession_remembersTheStartStateAcrossActivityRecreation() {
        FoldSession.startedFolded = true
        assertEquals(true, FoldSession.startedFolded)
        FoldSession.startedFolded = null
    }
}
