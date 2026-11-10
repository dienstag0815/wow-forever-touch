package app.gamenative.ui.screen.wow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstRunFlowTest {

    /** A fresh install on Wi-Fi: nothing granted, nothing installed, nothing asked yet. */
    private val fresh = FirstRunInput(
        busy = false,
        hasStorageAccess = false,
        micGranted = false,
        notificationsOk = false,
        batteryExempt = false,
        isWrongGame = false,
        isLegacyBeta = false,
        needsInstall = true,
        hasSavedLogin = false,
        locationChosen = false,
        unmetered = true,
        autoLaunchPending = false,
        storageRequested = false,
        micRequested = false,
        notificationsRequested = false,
        batteryRequested = false,
        installAttempted = false,
        loginPrompted = false,
    )

    private val withAccess = fresh.copy(hasStorageAccess = true, micGranted = true, notificationsOk = true, batteryExempt = true)

    /** Storage granted, microphone, notifications and battery exemption all granted or asked. */
    private val permsDone = fresh.copy(hasStorageAccess = true, micRequested = true, notificationsRequested = true, batteryRequested = true)

    private fun step(i: FirstRunInput) = nextFirstRunStep(i)

    @Test
    fun freshStart_asksForFileAccessFirst() {
        assertEquals(FirstRunStep.RequestStorage, step(fresh))
    }

    @Test
    fun fileAccessAskedOnce_thenWaitsForTheButton() {
        assertEquals(FirstRunStep.None, step(fresh.copy(storageRequested = true)))
    }

    @Test
    fun afterFileAccess_microphoneIsAskedBeforeInstall() {
        assertEquals(FirstRunStep.RequestMicrophone, step(fresh.copy(hasStorageAccess = true, storageRequested = true)))
    }

    @Test
    fun microphoneRefused_doesNotBlockTheInstallAndIsNotAskedAgain() {
        assertEquals(FirstRunStep.AutoInstall, step(permsDone))
    }

    @Test
    fun afterMicrophone_notificationsAreAskedThenBatteryExemption() {
        val micDone = fresh.copy(hasStorageAccess = true, micGranted = true)
        assertEquals(FirstRunStep.RequestNotifications, step(micDone))
        val notifDone = micDone.copy(notificationsRequested = true)
        assertEquals(FirstRunStep.RequestBatteryExemption, step(notifDone))
        assertEquals(FirstRunStep.AutoInstall, step(notifDone.copy(batteryRequested = true)))
    }

    @Test
    fun notificationsRefused_doesNotBlockBatteryExemptionAndIsNotAskedAgain() {
        val refused = fresh.copy(hasStorageAccess = true, micRequested = true, notificationsRequested = true)
        assertEquals(FirstRunStep.RequestBatteryExemption, step(refused))
    }

    @Test
    fun notificationsGrantedByOldAndroid_areSkipped() {
        val i = fresh.copy(hasStorageAccess = true, micGranted = true, notificationsOk = true)
        assertEquals(FirstRunStep.RequestBatteryExemption, step(i))
    }

    @Test
    fun batteryExemptionRefusedOrAlreadyGranted_isNotAskedAgain() {
        val base = fresh.copy(hasStorageAccess = true, micGranted = true, notificationsOk = true)
        assertEquals(FirstRunStep.AutoInstall, step(base.copy(batteryRequested = true)))
        assertEquals(FirstRunStep.AutoInstall, step(base.copy(batteryExempt = true)))
    }

    @Test
    fun permissionPromptsWaitWhileGameAutoLaunches() {
        val pending = fresh.copy(hasStorageAccess = true, micGranted = true, needsInstall = false, hasSavedLogin = true, autoLaunchPending = true)
        assertEquals(FirstRunStep.None, step(pending))
    }

    @Test
    fun permissionPromptPending_trueUntilEveryPromptIsAnsweredOnce() {
        assertTrue(permissionPromptPending(false, false, false, false, false, false))
        assertTrue(permissionPromptPending(true, false, true, false, false, false))
        assertTrue(permissionPromptPending(true, false, false, false, true, false))
        assertFalse(permissionPromptPending(false, true, false, true, false, true))
        assertFalse(permissionPromptPending(true, false, true, false, true, false))
    }

    @Test
    fun microphoneNotAskedWhileGameAutoLaunches() {
        val pending = withAccess.copy(micGranted = false, needsInstall = false, hasSavedLogin = true, autoLaunchPending = true)
        assertEquals(FirstRunStep.None, step(pending))
    }

    @Test
    fun noInstallAndNoChosenFolderOnWifi_installsAutomatically() {
        assertEquals(FirstRunStep.AutoInstall, step(withAccess))
    }

    @Test
    fun mobileData_keepsTheButton() {
        assertEquals(FirstRunStep.None, step(withAccess.copy(unmetered = false)))
    }

    @Test
    fun chosenFolder_keepsTheButton() {
        assertEquals(FirstRunStep.None, step(withAccess.copy(locationChosen = true)))
    }

    @Test
    fun betaUpgrade_keepsItsDialog() {
        assertEquals(FirstRunStep.None, step(withAccess.copy(isLegacyBeta = true)))
    }

    @Test
    fun wrongGame_doesNothing() {
        assertEquals(FirstRunStep.None, step(withAccess.copy(isWrongGame = true)))
    }

    @Test
    fun cancelledOrFailedInstall_isNotRestarted() {
        assertEquals(FirstRunStep.None, step(withAccess.copy(installAttempted = true)))
    }

    @Test
    fun busy_doesNothing() {
        assertEquals(FirstRunStep.None, step(fresh.copy(busy = true)))
        assertEquals(FirstRunStep.None, step(withAccess.copy(busy = true)))
    }

    @Test
    fun afterInstallWithoutLogin_opensLoginOnce() {
        val installed = withAccess.copy(needsInstall = false, installAttempted = true)
        assertEquals(FirstRunStep.PromptLogin, step(installed))
        assertEquals(FirstRunStep.None, step(installed.copy(loginPrompted = true)))
    }

    @Test
    fun savedLogin_noPrompt() {
        assertEquals(FirstRunStep.None, step(withAccess.copy(needsInstall = false, hasSavedLogin = true)))
    }

    @Test
    fun launchAfterLogin_onlyWhenInstalledAndCurrent() {
        assertTrue(shouldLaunchAfterLogin(installed = true, isWrongGame = false, isOutdated = false))
        assertFalse(shouldLaunchAfterLogin(installed = false, isWrongGame = false, isOutdated = false))
        assertFalse(shouldLaunchAfterLogin(installed = true, isWrongGame = true, isOutdated = false))
        assertFalse(shouldLaunchAfterLogin(installed = true, isWrongGame = false, isOutdated = true))
    }

    @Test
    fun autoDownload_onlyOnUnmeteredNonCellular() {
        assertTrue(allowsAutoDownload(hasInternet = true, notMetered = true, isCellular = false))
        assertFalse(allowsAutoDownload(hasInternet = true, notMetered = false, isCellular = false))
        assertFalse(allowsAutoDownload(hasInternet = true, notMetered = true, isCellular = true))
        assertFalse(allowsAutoDownload(hasInternet = false, notMetered = true, isCellular = false))
    }
}
