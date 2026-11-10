package app.gamenative.update

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoInstallDecisionTest {

    private fun decide(
        enabled: Boolean = true,
        offered: Boolean = true,
        downloaded: Boolean = true,
        visible: Boolean = true,
        gameBusy: Boolean = false,
        countdown: Boolean = false,
        allowed: Boolean = true,
    ) = UpdateLogic.autoInstallDecision(enabled, offered, downloaded, visible, gameBusy, countdown, allowed)

    private val bools = listOf(false, true)

    @Test
    fun switchOffNeverDoesAnything() {
        for (downloaded in bools) for (visible in bools) for (busy in bools) for (count in bools) {
            assertEquals(
                AutoInstallAction.None,
                decide(enabled = false, downloaded = downloaded, visible = visible, gameBusy = busy, countdown = count),
            )
        }
    }

    @Test
    fun withoutOfferOrInstallPermissionNothingHappens() {
        assertEquals(AutoInstallAction.None, decide(offered = false, downloaded = false))
        assertEquals(AutoInstallAction.None, decide(offered = false))
        assertEquals(AutoInstallAction.None, decide(allowed = false, downloaded = false))
        assertEquals(AutoInstallAction.None, decide(allowed = false))
    }

    @Test
    fun notYetDownloadedLoadsAtOnceWhateverTheLauncherIsDoing() {
        for (visible in bools) for (busy in bools) for (count in bools) {
            assertEquals(
                AutoInstallAction.Download,
                decide(downloaded = false, visible = visible, gameBusy = busy, countdown = count),
            )
        }
    }

    @Test
    fun downloadedAndNothingInTheWayInstalls() {
        assertEquals(AutoInstallAction.Install, decide())
    }

    @Test
    fun downloadedWaitsWhenTheLauncherIsNotVisible() {
        assertEquals(AutoInstallAction.Wait, decide(visible = false))
    }

    @Test
    fun downloadedWaitsWhileAGameStartsRunsOrLoadsFiles() {
        assertEquals(AutoInstallAction.Wait, decide(gameBusy = true))
    }

    @Test
    fun downloadedWaitsWhileTheStartCountdownRuns() {
        assertEquals(AutoInstallAction.Wait, decide(countdown = true))
    }

    @Test
    fun downloadedInstallsOnlyWhenAllThreeConditionsHold() {
        for (visible in bools) for (busy in bools) for (count in bools) {
            val expected = if (visible && !busy && !count) AutoInstallAction.Install else AutoInstallAction.Wait
            assertEquals(expected, decide(visible = visible, gameBusy = busy, countdown = count))
        }
    }

    @Test
    fun noticeLastsFiveSeconds() {
        assertEquals(5, UpdateLogic.AUTO_INSTALL_NOTICE_SECONDS)
    }
}
