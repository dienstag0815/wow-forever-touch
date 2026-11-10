package app.gamenative.ui.screen.wow

import org.junit.Rule
import app.gamenative.TestTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AutoStartTest {
    @get:Rule
    val texts = TestTexts.rule("de")


    private fun input(
        enabled: Boolean = true,
        updateOffered: Boolean = false,
        gameOutdated: Boolean = false,
        permissionsPending: Boolean = false,
        busy: Boolean = false,
        userStopped: Boolean = false,
        checkRunning: Boolean = false,
    ) = AutoStartInput(enabled, updateOffered, gameOutdated, permissionsPending, busy, userStopped, checkRunning)

    /** Runs the countdown loop of the screen with a fixed input per second; returns the shown seconds and the end step. */
    private fun run(seconds: Int, inputAt: (secondsShown: List<Int>) -> AutoStartInput): Pair<List<Int>, AutoStartStep> {
        val shown = mutableListOf<Int>()
        var left = seconds
        var holdMs = 0
        while (true) {
            when (val step = AutoStartLogic.step(left, inputAt(shown), holdMs >= AutoStartLogic.CHECK_HOLD_MAX_MS)) {
                is AutoStartStep.Wait -> { shown += step.secondsLeft; left-- }
                AutoStartStep.WaitForCheck -> holdMs += 250
                else -> return shown to step
            }
        }
    }

    @Test
    fun withoutUpdateTheCountdownRunsDownAndThenStarts() {
        val (shown, end) = run(5) { input() }
        assertEquals(listOf(5, 4, 3, 2, 1), shown)
        assertEquals(AutoStartStep.Start, end)
    }

    @Test
    fun launcherUpdateMeansNoAutoStart() {
        assertEquals(AutoStartBlock.UpdateAvailable, AutoStartLogic.blockReason(input(updateOffered = true)))
        assertEquals(AutoStartStep.Stop(AutoStartBlock.UpdateAvailable), AutoStartLogic.step(5, input(updateOffered = true)))
    }

    @Test
    fun switchedOffMeansNoAutoStart() {
        assertEquals(AutoStartStep.Stop(AutoStartBlock.Disabled), AutoStartLogic.step(5, input(enabled = false)))
        // also when everything else would allow it, and ahead of other reasons
        assertEquals(AutoStartBlock.Disabled, AutoStartLogic.blockReason(input(enabled = false, updateOffered = true)))
    }

    @Test
    fun noNetworkStillCountsDown() {
        // No network: the update check ended as "failed", no update is offered and nothing is running.
        val (shown, end) = run(3) { input(updateOffered = false, checkRunning = false) }
        assertEquals(listOf(3, 2, 1), shown)
        assertEquals(AutoStartStep.Start, end)
    }

    @Test
    fun updateAnswerDuringTheCountdownStopsIt() {
        val (shown, end) = run(5) { seen -> input(updateOffered = seen.size >= 2) }
        assertEquals(listOf(5, 4), shown)
        assertEquals(AutoStartStep.Stop(AutoStartBlock.UpdateAvailable), end)
    }

    @Test
    fun runningUpdateCheckHoldsTheStartUntilItAnswers() {
        assertEquals(AutoStartStep.WaitForCheck, AutoStartLogic.step(0, input(checkRunning = true)))
        assertEquals(AutoStartStep.Start, AutoStartLogic.step(0, input(checkRunning = false)))
        // it answered with an update while holding
        assertEquals(AutoStartStep.Stop(AutoStartBlock.UpdateAvailable), AutoStartLogic.step(0, input(checkRunning = false, updateOffered = true)))
    }

    @Test
    fun aCheckThatNeverAnswersDoesNotHoldTheGameForever() {
        val (shown, end) = run(3) { input(checkRunning = true) }
        assertEquals(listOf(3, 2, 1), shown)
        assertEquals(AutoStartStep.Start, end)
        assertEquals(AutoStartStep.Start, AutoStartLogic.step(0, input(checkRunning = true), checkHoldExpired = true))
    }

    @Test
    fun otherReasonsStopTheCountdown() {
        assertEquals(AutoStartBlock.GameOutdated, AutoStartLogic.blockReason(input(gameOutdated = true)))
        assertEquals(AutoStartBlock.PermissionsPending, AutoStartLogic.blockReason(input(permissionsPending = true)))
        assertEquals(AutoStartBlock.Busy, AutoStartLogic.blockReason(input(busy = true)))
        assertEquals(AutoStartBlock.UserStopped, AutoStartLogic.blockReason(input(userStopped = true)))
        assertNull(AutoStartLogic.blockReason(input()))
    }

    @Test
    fun cancelDuringTheCountdownStopsIt() {
        val (shown, end) = run(10) { seen -> input(userStopped = seen.size >= 3) }
        assertEquals(listOf(10, 9, 8), shown)
        assertEquals(AutoStartStep.Stop(AutoStartBlock.UserStopped), end)
    }

    @Test
    fun countdownLengthCyclesThroughThePresets() {
        assertEquals(5, AutoStartLogic.DEFAULT_SECONDS)
        assertEquals(listOf(3, 5, 10), AutoStartLogic.CHOICES)
        assertEquals(5, AutoStartLogic.nextSeconds(3))
        assertEquals(10, AutoStartLogic.nextSeconds(5))
        assertEquals(3, AutoStartLogic.nextSeconds(10))
        // unknown stored values fall back to the default
        assertEquals(5, AutoStartLogic.normalizeSeconds(7))
        assertEquals(10, AutoStartLogic.nextSeconds(7))
    }

    @Test
    fun textsNameTheCountdownAndTheReasons() {
        assertEquals("Spiel startet in 5 s", AutoStartLogic.countdownText(5, waitingForCheck = false))
        assertEquals("Spiel startet gleich ...", AutoStartLogic.countdownText(0, waitingForCheck = true))
        assertNotNull(AutoStartLogic.blockText(AutoStartBlock.UpdateAvailable))
        assertNotNull(AutoStartLogic.blockText(AutoStartBlock.GameOutdated))
        assertNull(AutoStartLogic.blockText(AutoStartBlock.Disabled))
        assertNull(AutoStartLogic.blockText(AutoStartBlock.PlayedNow))
    }
}
