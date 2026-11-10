package app.gamenative.update

import app.gamenative.R
import app.gamenative.ui.screen.wow.launcher.UiText
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateManagerStateTest {

    private val sha = "a".repeat(64)
    private val info = UpdateInfo(245, "2.3.4-fold8.1", "WoW-Forever-Fold-2.3.4-fold8.1.apk", "", sha, 150_000_000L, "Neu")

    // ---- Ende der Installation ------------------------------------------------------------------------------------

    @Test
    fun successEndsOnTheInstalledStateWithTheOfferedVersion() {
        assertEquals(UpdateState.Installed("2.3.4-fold8.1", info), UpdateManager.installedState(info))
    }

    @Test
    fun successWithoutKnownOfferGoesToIdle() {
        assertEquals(UpdateState.Idle, UpdateManager.installedState(null))
    }

    @Test
    fun installStaysRunningWhileTheOfferedVersionIsNotInstalledYet() {
        val running = UpdateState.Installing(info, UiText.res(R.string.launcher_update_waiting_android), 0.6f)
        assertEquals(running, UpdateManager.settleInstalled(running, 244, "2.3.3-fold8.1"))
    }

    @Test
    fun installEndsWhenTheLauncherIsBackAndTheNewVersionCodeIsInstalled() {
        val running = UpdateState.Installing(info, UiText.res(R.string.launcher_update_waiting_android), 1f)
        assertEquals(
            UpdateState.Installed("2.3.4-fold8.1", info),
            UpdateManager.settleInstalled(running, 245, "2.3.4-fold8.1"),
        )
    }

    @Test
    fun installWithoutVersionCodeEndsWhenTheVersionNameIsInstalled() {
        val noCode = UpdateInfo(0, "2.3.4-fold8.1", "a.apk", "", sha, 10L, "")
        val running = UpdateState.Installing(noCode, UiText.res(R.string.launcher_update_waiting_android), null)
        assertEquals(
            UpdateState.Installed("2.3.4-fold8.1", noCode),
            UpdateManager.settleInstalled(running, 244, "2.3.4-fold8.1"),
        )
    }

    @Test
    fun otherStatesAreNotChangedBySettle() {
        val offer = UpdateState.Available(info)
        val downloading = UpdateState.Downloading(info, 0.5f)
        assertEquals(offer, UpdateManager.settleInstalled(offer, 245, "2.3.4-fold8.1"))
        assertEquals(downloading, UpdateManager.settleInstalled(downloading, 245, "2.3.4-fold8.1"))
        assertEquals(UpdateState.Idle, UpdateManager.settleInstalled(UpdateState.Idle, 245, "2.3.4-fold8.1"))
    }

    // ---- Balken ---------------------------------------------------------------------------------------------------

    @Test
    fun installBarIsDeterminateFromCopyToAndroidsSession() {
        val total = 100L
        assertEquals(0f, UpdateLogic.installFraction(0L, total, null), 0.001f)
        assertEquals(0.25f, UpdateLogic.installFraction(50L, total, null), 0.001f)
        assertEquals(0.5f, UpdateLogic.installFraction(100L, total, null), 0.001f)
        assertEquals(0.5f, UpdateLogic.installFraction(100L, total, 0f), 0.001f)
        assertEquals(1f, UpdateLogic.installFraction(100L, total, 1f), 0.001f)
    }

    @Test
    fun downloadInstallAndFinishFollowEachOther() {
        // Zustandsfolge wie in UpdateManager.install() und onInstallStatus(): Download, Kopie, Android, Fertig.
        val steps = listOf(
            UpdateState.Downloading(info, 1f),
            UpdateState.Installing(info, UiText.res(R.string.launcher_update_preparing), 0f),
            UpdateState.Installing(info, UiText.res(R.string.launcher_update_handing_over), UpdateLogic.installFraction(50L, 100L, null)),
            UpdateState.Installing(info, UiText.res(R.string.launcher_update_waiting_android), 0.5f),
            UpdateManager.installedState(info),
        )
        assertEquals(UpdateState.Installed("2.3.4-fold8.1", info), steps.last())
        assertEquals(0.25f, (steps[2] as UpdateState.Installing).fraction!!, 0.001f)
    }
}
