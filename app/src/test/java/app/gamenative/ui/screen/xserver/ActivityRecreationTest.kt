package app.gamenative.ui.screen.xserver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rules for a recreated Activity (fold/unfold) while the game keeps running. */
class ActivityRecreationTest {

    @Test
    fun runningGame_keepsItsWinHandler() {
        assertTrue(shouldReuseWinHandler(hasRunningXServer = true, handlerRunning = true))
    }

    @Test
    fun freshStart_getsANewWinHandler() {
        assertFalse(shouldReuseWinHandler(hasRunningXServer = false, handlerRunning = false))
        assertFalse(shouldReuseWinHandler(hasRunningXServer = false, handlerRunning = true))
    }

    @Test
    fun stoppedHandler_isReplaced() {
        assertFalse(shouldReuseWinHandler(hasRunningXServer = true, handlerRunning = false))
    }

    @Test
    fun portraitWidth_isTheNarrowSideOfTheCurrentDisplay() {
        assertEquals(1080, portraitScreenWidth(2520, 1080))
        assertEquals(1080, portraitScreenWidth(1080, 2520))
        // After unfolding the narrow side is larger.
        assertEquals(1968, portraitScreenWidth(2184, 1968))
    }
}
