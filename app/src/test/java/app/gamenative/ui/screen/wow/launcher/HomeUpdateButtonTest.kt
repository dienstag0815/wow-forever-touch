package app.gamenative.ui.screen.wow.launcher

import app.gamenative.R
import app.gamenative.update.CheckPhase
import app.gamenative.update.UpdateInfo
import app.gamenative.update.UpdateState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeUpdateButtonTest {
    private val sha = "a".repeat(64)
    private val info = UpdateInfo(245, "2.3.4-fold8.1", "WoW-Forever-Fold-2.3.4-fold8.1.apk", "", sha, 150_000_000L, "Neu")
    private val current = "2.3.3-fold8.26"

    private fun ui(state: UpdateState, phase: CheckPhase = CheckPhase.Done, canInstall: Boolean = true) =
        homeUpdateButtonState(state, phase, current, canInstall)

    @Test
    fun quietStateOffersTheSearch() {
        for (phase in listOf(CheckPhase.Unknown, CheckPhase.Done, CheckPhase.Failed)) {
            val b = ui(UpdateState.Idle, phase)
            assertEquals(HomeUpdateKind.Search, b.kind)
            assertEquals(UiText.res(R.string.launcher_home_check_updates), b.label)
            assertEquals(HomeUpdateAction.Check, b.action)
            assertTrue(b.enabled)
            assertFalse(b.spinner)
            assertFalse(b.autoReset)
        }
    }

    @Test
    fun anInstalledUpdateGoesBackToTheSearch() {
        assertEquals(HomeUpdateKind.Search, ui(UpdateState.Installed("2.3.4-fold8.1", info)).kind)
    }

    @Test
    fun runningSearchIsLockedAndShowsProgress() {
        val b = ui(UpdateState.Checking)
        assertEquals(HomeUpdateKind.Checking, b.kind)
        assertEquals(HomeUpdateAction.None, b.action)
        assertFalse(b.enabled)
        assertTrue(b.spinner)
    }

    @Test
    fun theAutomaticStartCheckAlsoLocksTheButton() {
        // Läuft die Suche vom Start noch (Zustand Idle, Phase Running), darf keine zweite beginnen.
        val b = ui(UpdateState.Idle, CheckPhase.Running)
        assertEquals(HomeUpdateKind.Checking, b.kind)
        assertFalse(b.enabled)
        assertEquals(HomeUpdateAction.None, b.action)
    }

    @Test
    fun upToDateShowsTheVersionAndResetsItself() {
        val b = ui(UpdateState.UpToDate)
        assertEquals(HomeUpdateKind.UpToDate, b.kind)
        assertEquals(UiText.res(R.string.launcher_home_up_to_date, current), b.label)
        assertTrue(b.autoReset)
        assertEquals(HomeUpdateAction.Check, b.action)
        assertEquals(UiText.res(R.string.launcher_home_up_to_date_desc, current), b.description)
    }

    @Test
    fun anOfferedUpdateGivesDirectInstall() {
        val b = ui(UpdateState.Available(info))
        assertEquals(HomeUpdateKind.Offered, b.kind)
        assertEquals(UiText.res(R.string.launcher_home_install_update, "2.3.4-fold8.1"), b.label)
        assertEquals(HomeUpdateAction.Install, b.action)
        assertTrue(b.enabled)
        assertFalse(b.autoReset)
    }

    @Test
    fun anOfferedUpdateShowsEvenWhileTheStartCheckStillRuns() {
        // Angebot aus dem Zwischenspeicher: der Knopf zeigt es sofort, ohne auf die Suche zu warten.
        val b = ui(UpdateState.Available(info), CheckPhase.Running)
        assertEquals(HomeUpdateKind.Offered, b.kind)
    }

    @Test
    fun installIsLockedWhileTheGameStartsOrFilesLoad() {
        val b = ui(UpdateState.Available(info), canInstall = false)
        assertEquals(HomeUpdateKind.Offered, b.kind)
        assertFalse(b.enabled)
        assertEquals(UiText.res(R.string.launcher_home_update_blocked_desc, "2.3.4-fold8.1"), b.description)
    }

    @Test
    fun downloadAndInstallationAreLockedAndShowProgress() {
        val d = ui(UpdateState.Downloading(info, 0.426f))
        assertEquals(HomeUpdateKind.Working, d.kind)
        assertEquals(UiText.res(R.string.launcher_home_downloading, 42), d.label)
        assertFalse(d.enabled)
        assertTrue(d.spinner)
        assertEquals(HomeUpdateAction.None, d.action)
        assertEquals(UiText.res(R.string.launcher_home_downloading, 100), ui(UpdateState.Downloading(info, 1.7f)).label)
        assertEquals(UiText.res(R.string.launcher_home_downloading, 0), ui(UpdateState.Downloading(info, -0.2f)).label)
        val i = ui(UpdateState.Installing(info, UiText.raw("Warte ..."), 0.7f))
        assertEquals(HomeUpdateKind.Working, i.kind)
        assertEquals(UiText.res(R.string.launcher_home_installing), i.label)
        assertFalse(i.enabled)
        assertEquals(HomeUpdateAction.None, i.action)
    }

    @Test
    fun noNetworkShowsTheConnectionProblemWithRetry() {
        val b = ui(UpdateState.Error(UiText.res(R.string.launcher_update_no_network), null), CheckPhase.Failed)
        assertEquals(HomeUpdateKind.Failed, b.kind)
        assertEquals(UiText.res(R.string.launcher_home_reason_retry, UiText.res(R.string.launcher_home_no_connection)), b.label)
        assertEquals(HomeUpdateAction.Check, b.action)
        assertTrue(b.enabled)
        assertTrue(b.autoReset)
    }

    @Test
    fun otherSearchErrorsAreNamedSeparately() {
        val b = ui(UpdateState.Error(UiText.res(R.string.launcher_update_search_failed, "Server antwortet mit HTTP 500"), null), CheckPhase.Failed)
        assertEquals(HomeUpdateKind.Failed, b.kind)
        assertEquals(UiText.res(R.string.launcher_home_reason_retry, UiText.res(R.string.launcher_home_search_failed)), b.label)
        assertEquals(HomeUpdateAction.Check, b.action)
        val net = ui(UpdateState.Error(UiText.res(R.string.launcher_update_search_failed, "Unable to resolve host x"), null), CheckPhase.Failed)
        assertEquals(UiText.res(R.string.launcher_home_reason_retry, UiText.res(R.string.launcher_home_no_connection)), net.label)
    }

    @Test
    fun aFailedInstallationRetriesTheInstallationAndStaysVisible() {
        val b = ui(UpdateState.Error(UiText.res(R.string.launcher_update_low_storage_install, ""), info))
        assertEquals(HomeUpdateKind.Failed, b.kind)
        assertEquals(HomeUpdateAction.Install, b.action)
        assertFalse(b.autoReset)
        assertTrue(b.enabled)
        assertFalse(ui(UpdateState.Error(UiText.raw("x"), info), canInstall = false).enabled)
    }

    @Test
    fun connectionProblemDetection() {
        assertTrue(isConnectionProblem(UiText.res(R.string.launcher_update_no_network)))
        assertTrue(isConnectionProblem(UiText.res(R.string.launcher_update_search_failed, "timeout")))
        assertTrue(isConnectionProblem(UiText.res(R.string.launcher_update_search_failed, "Failed to connect to /1.2.3.4:443")))
        assertFalse(isConnectionProblem(UiText.res(R.string.launcher_update_feed_unusable, UiText.res(R.string.launcher_update_feed_no_version))))
        assertFalse(isConnectionProblem(UiText.res(R.string.launcher_update_http_error, 404)))
        assertFalse(isConnectionProblem(UiText.raw("Kein Netz.")))
    }

    @Test
    fun onlyTransientResultsResetThemselves() {
        val states = listOf(
            UpdateState.Idle, UpdateState.Checking, UpdateState.UpToDate, UpdateState.Available(info),
            UpdateState.Downloading(info, 0.5f), UpdateState.Installing(info, UiText.raw(""), null),
            UpdateState.Error(UiText.res(R.string.launcher_update_no_network), null), UpdateState.Error(UiText.raw("x"), info), UpdateState.Installed("2.3.4", info),
        )
        val resetting = states.filter { ui(it).autoReset }
        assertEquals(listOf<UpdateState>(UpdateState.UpToDate, UpdateState.Error(UiText.res(R.string.launcher_update_no_network), null)), resetting)
    }

    @Test
    fun everyStateHasLabelAndDescriptionForAccessibility() {
        val states = listOf(
            UpdateState.Idle, UpdateState.Checking, UpdateState.UpToDate, UpdateState.Available(info),
            UpdateState.Downloading(info, 0.5f), UpdateState.Installing(info, UiText.raw(""), null),
            UpdateState.Error(UiText.res(R.string.launcher_update_no_network), null), UpdateState.Error(UiText.raw("x"), info), UpdateState.Installed("2.3.4", info),
        )
        for (s in states) {
            val b = ui(s)
            // Ein Text ist immer eine Ressource (mit Platzhaltern), nie leer und nie ein fertiger Satz.
            assertTrue("Beschreibung fehlt für $s", b.description is UiText.Res)
            assertTrue("Text fehlt für $s", b.label is UiText.Res)
        }
    }

    @Test
    fun theResultStaysAFewSeconds() {
        assertTrue(HOME_UPDATE_RESULT_MS in 3_000L..10_000L)
    }
}
