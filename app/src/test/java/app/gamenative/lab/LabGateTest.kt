package app.gamenative.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LabGateTest {
    @Test
    fun sevenQuickTapsUnlock() {
        var state = LabTapState()
        var t = 1_000L
        for (i in 1..6) {
            val step = LabEntryLogic.tap(state, t)
            assertFalse("tap $i", step.unlocked)
            assertEquals(i, step.state.count)
            state = step.state
            t += 300
        }
        val last = LabEntryLogic.tap(state, t)
        assertTrue(last.unlocked)
        assertEquals(LabTapState(), last.state)
    }

    @Test
    fun aLongPauseStartsOver() {
        var state = LabTapState()
        repeat(5) { state = LabEntryLogic.tap(state, 1_000L + it * 200L).state }
        assertEquals(5, state.count)
        val after = LabEntryLogic.tap(state, 1_000L + 4 * 200L + LabEntryLogic.MAX_GAP_MS + 1)
        assertEquals(1, after.state.count)
        assertFalse(after.unlocked)
    }

    @Test
    fun fewerThanSevenTapsNeverUnlock() {
        var state = LabTapState()
        var unlocked = false
        for (i in 0 until 6) {
            val step = LabEntryLogic.tap(state, i * 100L)
            state = step.state
            unlocked = unlocked || step.unlocked
        }
        assertFalse(unlocked)
    }

    @Test
    fun theLabDoesNotStartWhileTheGameRunsAndTheOtherWayRound() {
        assertEquals(LabGuard.Block.NONE, LabGuard.forLabStart(keepAlive = false, labActive = false))
        assertEquals(LabGuard.Block.GAME_RUNNING, LabGuard.forLabStart(keepAlive = true, labActive = false))
        assertEquals(LabGuard.Block.LAB_RUNNING, LabGuard.forLabStart(keepAlive = true, labActive = true))
        assertEquals(LabGuard.Block.LAB_RUNNING, LabGuard.forLabStart(keepAlive = false, labActive = true))
        assertTrue(LabGuard.gameMayStart(labActive = false))
        assertFalse(LabGuard.gameMayStart(labActive = true))
    }
}
