package app.gamenative.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Startwahl der Startseite (Auftrag startwahl, Punkte 1, 3, 4, 6): jede Zeile der Entscheidungstabelle. */
class LabStartModeTest {
    private val signedIn = LabGuideSelf(signedInMs = 1L)

    /** Eingaben, bei denen die Schritte 1 bis 3 erfüllt sind (Paket, Launcher-Datei, Voreinstellung). */
    private fun inputs(
        startable: Boolean = true,
        self: LabGuideSelf = LabGuideSelf(),
        fex: Boolean = true,
        launcher: Boolean = true,
        setting: Boolean = true,
    ) = LabGuideInputs(
        gameContainerPresent = true,
        fexListed = true,
        fexInstalled = fex && startable,
        installerFilePresent = true,
        launcherExePresent = launcher && startable,
        loginWayApplied = setting && startable,
        self = self,
    )

    private fun facts(chosen: StartMode?, i: LabGuideInputs) = LabStartMode.facts(chosen, LabGuide.evaluate(i))

    /** Teil B eingerichtet: Kopie fertig und eingehängt, Kennungsdatei des Spiels in der Kopie. */
    private fun partB(i: LabGuideInputs) = i.copy(
        useGameCopy = true,
        copy = LabGuideCopy(originalPresent = true, complete = true),
        gameMarkerInCopy = true,
    )

    // --- Entscheidungstabelle Startart (einmalanmelden: Vorgabe immer Direkt) ---

    @Test
    fun noChoiceIsDirectInEveryCase() {
        val cases = listOf(
            inputs(startable = false), // 1 nichts eingerichtet
            inputs(), // 2 Schritte 1-3
            inputs(self = signedIn), // 3 Teil A
            partB(inputs()), // 4 nur Teil B
            partB(inputs(self = signedIn)), // 5 Teil A und B
        )
        for (i in cases) {
            val f = facts(null, i)
            assertEquals(EffectiveStart(StartMode.DIRECT, StartSource.DEFAULT), LabStartMode.effective(f))
            assertEquals(StartRoute.Direct, LabStartMode.route(f))
            assertTrue(LabStartMode.autoStartAllowed(LabStartMode.effective(f)))
        }
    }

    @Test
    fun chosenDirectWinsEvenWhenPartAAndBAreSetUp() {
        val f = facts(StartMode.DIRECT, partB(inputs(self = signedIn)))
        assertEquals(EffectiveStart(StartMode.DIRECT, StartSource.CHOSEN), LabStartMode.effective(f))
        assertEquals(StartRoute.Direct, LabStartMode.route(f))
    }

    @Test
    fun chosenBattleNetWinsEvenWithoutSetUp() {
        val f = facts(StartMode.BATTLENET, inputs(startable = false))
        assertEquals(EffectiveStart(StartMode.BATTLENET, StartSource.CHOSEN), LabStartMode.effective(f))
    }

    @Test
    fun partAIsStepsOneToFourAndSignedInIsStepFourAlone() {
        assertFalse(facts(null, inputs()).setUp)
        assertFalse(facts(null, inputs()).signedIn)
        assertTrue(facts(null, inputs(self = signedIn)).setUp)
        assertTrue(facts(null, inputs(self = signedIn)).signedIn)
        // Angemeldet gemerkt, aber Paket fehlt: Schritt 4 ist erledigt, Teil A nicht.
        val f = facts(null, inputs(self = signedIn, startable = false))
        assertTrue(f.signedIn)
        assertFalse(f.setUp)
    }

    @Test
    fun partBIsTheCopyAndTheGameInTheLauncherAndPlayingDoesNotCount() {
        assertFalse(facts(null, inputs()).partBDone)
        assertEquals(GuideStep.COPY, facts(null, inputs().copy(copy = LabGuideCopy(originalPresent = true))).missingB)
        assertTrue(facts(null, partB(inputs())).partBDone)
        // Kopie fertig, Spiel im Launcher noch nicht installiert: Schritt 6 fehlt.
        val noGame = partB(inputs()).copy(gameMarkerInCopy = false)
        assertFalse(facts(null, noGame).partBDone)
        assertEquals(GuideStep.GAME, facts(null, noGame).missingB)
        // "Ohne Spielkopie" (Selbstauskunft) haelt nicht auf.
        assertTrue(facts(null, inputs(self = LabGuideSelf(noCopyMs = 1L))).partBDone)
    }

    // --- Weiche ---

    @Test
    fun battleNetWithMissingPackageOpensTheGuideAtThePackageStep() {
        val f = facts(StartMode.BATTLENET, inputs(fex = false).copy(fexInstalled = false))
        assertEquals(StartRoute.Guide(GuideStep.PACKAGES), LabStartMode.route(f))
    }

    @Test
    fun battleNetWithMissingLauncherFileOpensTheGuideAtTheLauncherStep() {
        val f = facts(StartMode.BATTLENET, inputs().copy(launcherExePresent = false))
        assertEquals(StartRoute.Guide(GuideStep.LAUNCHER), LabStartMode.route(f))
    }

    @Test
    fun battleNetWithoutPresetOpensTheGuideAtTheSettingStep() {
        val f = facts(StartMode.BATTLENET, inputs().copy(loginWayApplied = false))
        assertEquals(StartRoute.Guide(GuideStep.SETTING), LabStartMode.route(f))
    }

    @Test
    fun battleNetStartableWithoutSignInConfirmationStartsTheLauncher() {
        // Der Start ist selbst der Weg zur Anmeldung: „angemeldet“ ist keine Voraussetzung für den Start.
        val f = facts(StartMode.BATTLENET, inputs())
        assertEquals(StartRoute.Launcher, LabStartMode.route(f))
    }

    // --- Wahl "Ueber Battle.net" nur mit Teil B ---

    @Test
    fun directIsAlwaysChoosableAndNeverAsks() {
        for (chosen in listOf(null, StartMode.BATTLENET, StartMode.DIRECT)) {
            for (i in listOf(inputs(startable = false), inputs(self = signedIn), partB(inputs(self = signedIn)))) {
                assertEquals(ChooseResult.Commit(StartMode.DIRECT), LabStartMode.choose(facts(chosen, i), StartMode.DIRECT))
            }
        }
    }

    @Test
    fun battleNetIsChoosableOnlyWhenPartBIsSetUp() {
        assertEquals(ChooseResult.Commit(StartMode.BATTLENET), LabStartMode.choose(facts(null, partB(inputs())), StartMode.BATTLENET))
        assertEquals(ChooseResult.Commit(StartMode.BATTLENET), LabStartMode.choose(facts(StartMode.DIRECT, partB(inputs(self = signedIn))), StartMode.BATTLENET))
    }

    @Test
    fun battleNetWithoutPartBOpensTheGuideAtTheMissingStepAndChoosesNothing() {
        assertEquals(ChooseResult.OpenGuide(GuideStep.COPY), LabStartMode.choose(facts(null, inputs()), StartMode.BATTLENET))
        val noGame = partB(inputs()).copy(gameMarkerInCopy = false)
        assertEquals(ChooseResult.OpenGuide(GuideStep.GAME), LabStartMode.choose(facts(null, noGame), StartMode.BATTLENET))
        // Fehlt schon Schritt 1-3, nennt der Satz diesen Schritt zuerst.
        assertEquals(ChooseResult.OpenGuide(GuideStep.PACKAGES), LabStartMode.choose(facts(null, inputs(fex = false).copy(fexInstalled = false)), StartMode.BATTLENET))
        assertEquals(ChooseResult.OpenGuide(GuideStep.LAUNCHER), LabStartMode.choose(facts(null, inputs().copy(launcherExePresent = false)), StartMode.BATTLENET))
        assertEquals(ChooseResult.OpenGuide(GuideStep.SETTING), LabStartMode.choose(facts(null, inputs().copy(loginWayApplied = false)), StartMode.BATTLENET))
    }

    @Test
    fun anExistingBattleNetChoiceStaysAndStillStarts() {
        // Wahl aus 9.34-9.37: bleibt, und der Tipp auf "Spielen" startet wie bisher (nur das Anwaehlen ist neu gesperrt).
        val f = facts(StartMode.BATTLENET, inputs())
        assertFalse(f.partBDone)
        assertEquals(EffectiveStart(StartMode.BATTLENET, StartSource.CHOSEN), LabStartMode.effective(f))
        assertEquals(StartRoute.Launcher, LabStartMode.route(f))
        assertEquals(ChooseResult.Commit(StartMode.BATTLENET), LabStartMode.choose(f, StartMode.BATTLENET))
    }

    // --- Hinweis auf der Startseite ---

    @Test
    fun theSignInTipShowsUntilStepFourIsNoted() {
        assertTrue(LabStartMode.showSignInTip(facts(null, inputs(startable = false))))
        assertTrue(LabStartMode.showSignInTip(facts(null, inputs())))
        assertFalse(LabStartMode.showSignInTip(facts(null, inputs(self = signedIn))))
        assertFalse(LabStartMode.showSignInTip(facts(StartMode.DIRECT, partB(inputs(self = signedIn)))))
    }

    // --- Durchstich: was die Weiche ruft ---

    private fun calls(f: LabStartFacts): List<String> {
        val log = mutableListOf<String>()
        LabStartMode.dispatch(f, direct = { log += "direct" }, launcher = { log += "launcher" }, guide = { log += "guide:${it.name}" })
        return log
    }

    @Test
    fun directStartCallsOnlyTheUnchangedDirectStartOnce() {
        assertEquals(listOf("direct"), calls(facts(StartMode.DIRECT, inputs(self = signedIn))))
        assertEquals(listOf("direct"), calls(facts(null, partB(inputs(self = signedIn)))))
        assertEquals(listOf("direct"), calls(facts(null, inputs(startable = false))))
        assertEquals(listOf("direct"), calls(LabStartFacts()))
    }

    @Test
    fun battleNetCallsOnlyTheLauncherStartOrTheGuide() {
        assertEquals(listOf("launcher"), calls(facts(StartMode.BATTLENET, inputs())))
        assertEquals(listOf("guide:LAUNCHER"), calls(facts(StartMode.BATTLENET, inputs().copy(launcherExePresent = false))))
        // Ohne Fakten (nicht ermittelbar) und mit gespeicherter Wahl Battle.net startet nichts, die Anleitung öffnet.
        assertEquals(listOf("guide:PACKAGES"), calls(LabStartFacts(chosen = StartMode.BATTLENET)))
    }

    // --- Auto-Start ---

    @Test
    fun autoStartRunsOnlyForDirectStart() {
        assertTrue(LabStartMode.autoStartAllowed(EffectiveStart(StartMode.DIRECT, StartSource.DEFAULT)))
        assertTrue(LabStartMode.autoStartAllowed(EffectiveStart(StartMode.DIRECT, StartSource.CHOSEN)))
        assertFalse(LabStartMode.autoStartAllowed(EffectiveStart(StartMode.BATTLENET, StartSource.DEFAULT)))
        assertFalse(LabStartMode.autoStartAllowed(EffectiveStart(StartMode.BATTLENET, StartSource.CHOSEN)))
    }

    @Test
    fun theCountdownNeverReachesStartWhenBattleNetIsTheStartMode() {
        for (chosen in listOf(StartMode.BATTLENET)) {
            val f = facts(chosen, inputs(self = signedIn))
            assertEquals(StartMode.BATTLENET, LabStartMode.effective(f).mode)
            val allowed = LabStartMode.autoStartAllowed(LabStartMode.effective(f))
            for (left in 5 downTo 0) {
                val input = app.gamenative.ui.screen.wow.AutoStartInput(
                    enabled = true && allowed, launcherUpdateOffered = false, gameOutdated = false, permissionsPending = false, busy = false,
                )
                val step = app.gamenative.ui.screen.wow.AutoStartLogic.step(left, input, checkHoldExpired = true)
                assertTrue("left=$left: $step", step is app.gamenative.ui.screen.wow.AutoStartStep.Stop)
            }
        }
    }

    @Test
    fun theCountdownStillStartsTheDirectStartLikeBefore() {
        val f = facts(null, inputs(startable = false))
        val allowed = LabStartMode.autoStartAllowed(LabStartMode.effective(f))
        val input = app.gamenative.ui.screen.wow.AutoStartInput(
            enabled = true && allowed, launcherUpdateOffered = false, gameOutdated = false, permissionsPending = false, busy = false,
        )
        assertEquals(app.gamenative.ui.screen.wow.AutoStartStep.Start, app.gamenative.ui.screen.wow.AutoStartLogic.step(0, input, checkHoldExpired = true))
    }

    // --- Schlüssel und Protokoll ---

    @Test
    fun keysRoundTripAndUnknownMeansNeverChosen() {
        for (m in StartMode.entries) assertEquals(m, StartMode.fromKey(m.key))
        assertNull(StartMode.fromKey(null))
        assertNull(StartMode.fromKey(""))
        assertNull(StartMode.fromKey("launcher"))
    }

    @Test
    fun protocolLines() {
        assertEquals("Start mode: battlenet (chosen)", LabStartMode.modeLine(EffectiveStart(StartMode.BATTLENET, StartSource.CHOSEN)))
        assertEquals("Start mode: direct (default)", LabStartMode.modeLine(EffectiveStart(StartMode.DIRECT, StartSource.DEFAULT)))
        assertEquals("Start mode: unknown", LabStartMode.modeLine(null))
        assertEquals("Started from: home", LabStartMode.originLine(LabStartOrigin.HOME))
        assertEquals("Started from: test area", LabStartMode.originLine(LabStartOrigin.TEST_AREA))
    }

    @Test
    fun protocolGuideLinesComeFromTheAppsOwnState() {
        assertEquals(listOf("Guide part A done: no", "Guide part B done: no"), LabStartMode.guideLines(facts(null, inputs())))
        assertEquals(listOf("Guide part A done: yes", "Guide part B done: no"), LabStartMode.guideLines(facts(null, inputs(self = signedIn))))
        assertEquals(listOf("Guide part A done: yes", "Guide part B done: yes"), LabStartMode.guideLines(facts(null, partB(inputs(self = signedIn)))))
        assertEquals(emptyList<String>(), LabStartMode.guideLines(null))
    }

    @Test
    fun theCountdownStartsDirectlyEvenWhenPartAAndBAreSetUp() {
        val f = facts(null, partB(inputs(self = signedIn)))
        val allowed = LabStartMode.autoStartAllowed(LabStartMode.effective(f))
        val input = app.gamenative.ui.screen.wow.AutoStartInput(
            enabled = true && allowed, launcherUpdateOffered = false, gameOutdated = false, permissionsPending = false, busy = false,
        )
        assertEquals(app.gamenative.ui.screen.wow.AutoStartStep.Start, app.gamenative.ui.screen.wow.AutoStartLogic.step(0, input, checkHoldExpired = true))
    }
}
