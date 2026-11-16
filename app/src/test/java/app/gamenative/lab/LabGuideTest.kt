package app.gamenative.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Zustandslogik des Assistenten (Auftrag labassistent, Punkt 1): je Schritt die Zustände, Reihenfolge, nächster Schritt, Quelle jeder Angabe. */
class LabGuideTest {
    private fun run(
        t: Long = 1_000L,
        main: Boolean = false,
        game: Boolean = false,
        target: String = "battlenet",
        watchEnd: String = "",
        noWindow: Boolean = false,
        restart: Boolean = false,
    ) = LabRunEntry(
        t, target, 100, 4, "FEX", "FEXCore 2508", 0,
        watchEnd = watchEnd, noWindowSeen = noWindow, mainWindowSeen = main, gameWindowSeen = game, restartRequested = restart,
    )

    private val freshCopy = LabGuideCopy(originalPresent = true, fits = true, neededBytes = 54L shl 30, freeBytes = 100L shl 30)
    private val doneCopy = freshCopy.copy(complete = true, finishedMs = 5_000L)

    /** Alles bis einschließlich Spielkopie erledigt. */
    private val upToCopy = LabGuideInputs(
        fexInstalled = true,
        installerFilePresent = true,
        launcherExePresent = true,
        loginWayApplied = true,
        useGameCopy = true,
        self = LabGuideSelf(signedInMs = 2_000L),
        copy = doneCopy,
    )

    private fun states(i: LabGuideInputs) = LabGuide.evaluate(i).steps.map { it.state }

    // --- Schritt 1: Pakete ---

    @Test fun step1PackageMissingOffersLoad() {
        val s = LabGuide.evaluate(LabGuideInputs()).status(GuideStep.PACKAGES)
        assertEquals(GuideState.OPEN, s.state)
        assertEquals(GuideFact.PKG_FEX_MISSING, s.fact)
        assertEquals(GuideAction.LOAD_FEX, s.action)
        assertEquals(GuideSource.INSTALLED_PACKAGES, s.source)
    }

    @Test fun step1PackageInstalledIsDone() {
        val s = LabGuide.evaluate(LabGuideInputs(fexInstalled = true)).status(GuideStep.PACKAGES)
        assertEquals(GuideState.DONE, s.state)
        assertEquals(GuideAction.NONE, s.action)
    }

    @Test fun step1ListWithoutFexIsAnErrorWithoutButton() {
        val s = LabGuide.evaluate(LabGuideInputs(fexListed = false)).status(GuideStep.PACKAGES)
        assertEquals(GuideState.ERROR, s.state)
        assertEquals(GuideFact.PKG_FEX_NOT_LISTED, s.fact)
        assertEquals(GuideSource.PACKAGE_LIST, s.source)
        assertEquals(GuideAction.NONE, s.action)
    }

    // --- Schritt 2: Launcher installiert ---

    @Test fun step2LauncherFilePresentIsDone() {
        val s = LabGuide.evaluate(LabGuideInputs(launcherExePresent = true)).status(GuideStep.LAUNCHER)
        assertEquals(GuideState.DONE, s.state)
        assertEquals(GuideFact.LAUNCHER_FILE_PRESENT, s.fact)
        assertEquals(GuideSource.FILE_CHECK, s.source)
    }

    @Test fun step2WithoutInstallerOffersToLoadIt() {
        val s = LabGuide.evaluate(LabGuideInputs()).status(GuideStep.LAUNCHER)
        assertEquals(GuideState.OPEN, s.state)
        assertEquals(GuideAction.LOAD_INSTALLER, s.action)
    }

    @Test fun step2WithInstallerOffersToStartIt() {
        val s = LabGuide.evaluate(LabGuideInputs(installerFilePresent = true)).status(GuideStep.LAUNCHER)
        assertEquals(GuideState.OPEN, s.state)
        assertEquals(GuideAction.START_INSTALLER, s.action)
    }

    @Test fun step2WithoutGameContainerIsBlocked() {
        val s = LabGuide.evaluate(LabGuideInputs(gameContainerPresent = false, installerFilePresent = true)).status(GuideStep.LAUNCHER)
        assertEquals(GuideState.BLOCKED, s.state)
        assertEquals(GuideFact.NO_GAME_CONTAINER, s.fact)
        assertEquals(GuideAction.NONE, s.action)
    }

    // --- Schritt 3: Einstellung ---

    @Test fun step3AppliedIsDone() {
        val s = LabGuide.evaluate(LabGuideInputs(fexInstalled = true, loginWayApplied = true)).status(GuideStep.SETTING)
        assertEquals(GuideState.DONE, s.state)
        assertEquals(GuideSource.SETTINGS, s.source)
    }

    @Test fun step3NotAppliedWithPackageOffersToSet() {
        val s = LabGuide.evaluate(LabGuideInputs(fexInstalled = true)).status(GuideStep.SETTING)
        assertEquals(GuideState.OPEN, s.state)
        assertEquals(GuideAction.SET_LOGIN_WAY, s.action)
    }

    @Test fun step3NotAppliedWithoutPackageHasNoButton() {
        val s = LabGuide.evaluate(LabGuideInputs()).status(GuideStep.SETTING)
        assertEquals(GuideState.OPEN, s.state)
        assertEquals(GuideFact.SETTING_NEEDS_PACKAGE, s.fact)
        assertEquals(GuideAction.NONE, s.action)
    }

    // --- Schritt 4: Anmelden (Selbstauskunft, Indiz daneben) ---

    @Test fun step4SelfReportIsDoneWithDate() {
        val s = LabGuide.evaluate(upToCopy).status(GuideStep.SIGN_IN)
        assertEquals(GuideState.DONE, s.state)
        assertEquals(GuideFact.SIGNED_IN_CONFIRMED, s.fact)
        assertEquals(GuideSource.SELF_REPORT, s.source)
        assertEquals(2_000L, s.atMs)
    }

    @Test fun step4WithoutSelfReportStaysOpenEvenWhenMainWindowWasSeen() {
        val s = LabGuide.evaluate(upToCopy.copy(self = LabGuideSelf(), lastRun = run(main = true))).status(GuideStep.SIGN_IN)
        assertEquals(GuideState.OPEN, s.state)
        assertEquals(GuideFact.SIGNED_IN_NOT_CONFIRMED, s.fact)
        assertEquals(GuideAction.START_LAUNCHER, s.action)
        assertEquals(GuideFact.MAIN_WINDOW_SEEN, s.evidence?.fact)
        assertEquals(GuideSource.LAST_RUN, s.evidence?.fact?.source)
        assertEquals(1_000L, s.evidence?.atMs)
    }

    @Test fun step4EvidenceSaysNotSeenOrNoRun() {
        val noMain = LabGuide.evaluate(upToCopy.copy(self = LabGuideSelf(), lastRun = run(main = false))).status(GuideStep.SIGN_IN)
        assertEquals(GuideFact.MAIN_WINDOW_NOT_SEEN, noMain.evidence?.fact)
        val none = LabGuide.evaluate(upToCopy.copy(self = LabGuideSelf())).status(GuideStep.SIGN_IN)
        assertEquals(GuideFact.NO_RUN_YET, none.evidence?.fact)
        assertNull(none.evidence?.atMs)
    }

    @Test fun step4NeedsLauncherFirst() {
        val s = LabGuide.evaluate(LabGuideInputs()).status(GuideStep.SIGN_IN)
        assertEquals(GuideState.OPEN, s.state)
        assertEquals(GuideFact.SIGN_IN_NEEDS_LAUNCHER, s.fact)
        assertEquals(GuideAction.NONE, s.action)
    }

    @Test fun step4ResetSelfReportReopensTheStep() {
        val reset = upToCopy.copy(self = upToCopy.self.copy(signedInMs = null))
        assertEquals(GuideState.OPEN, LabGuide.evaluate(reset).status(GuideStep.SIGN_IN).state)
        assertEquals(GuideStep.SIGN_IN, LabGuide.evaluate(reset).next)
    }

    // --- Schritt 5: Spielkopie ---

    @Test fun step5Variants() {
        fun s(copy: LabGuideCopy, use: Boolean = false, self: LabGuideSelf = LabGuideSelf()) =
            LabGuide.evaluate(upToCopy.copy(copy = copy, useGameCopy = use, self = self.copy(signedInMs = 2_000L))).status(GuideStep.COPY)
        assertEquals(GuideFact.COPY_NONE to GuideState.OPEN, s(freshCopy).let { it.fact to it.state })
        assertEquals(GuideAction.ASK_COPY, s(freshCopy).action)
        assertEquals(GuideFact.COPY_PARTIAL to GuideState.OPEN, s(freshCopy.copy(partial = true)).let { it.fact to it.state })
        assertEquals(GuideFact.COPY_RUNNING to GuideState.RUNNING, s(freshCopy.copy(running = true)).let { it.fact to it.state })
        assertEquals(GuideAction.NONE, s(freshCopy.copy(running = true)).action)
        assertEquals(GuideFact.COPY_LAYOUT_FAILED to GuideState.ERROR, s(freshCopy.copy(layoutFailed = true)).let { it.fact to it.state })
        assertEquals(GuideFact.COPY_NO_ORIGINAL to GuideState.BLOCKED, s(freshCopy.copy(originalPresent = false)).let { it.fact to it.state })
        assertEquals(GuideFact.COPY_NO_SPACE to GuideState.BLOCKED, s(freshCopy.copy(fits = false)).let { it.fact to it.state })
        assertEquals(GuideSource.DISK_SPACE, s(freshCopy.copy(fits = false)).source)
        // Fertig, aber Schalter aus: offen mit dem Knopf „einbinden“; Schalter an: erledigt (Quelle: Marke der Kopie, mit Datum).
        assertEquals(GuideFact.COPY_NOT_MOUNTED to GuideState.OPEN, s(doneCopy, use = false).let { it.fact to it.state })
        assertEquals(GuideAction.ENABLE_COPY, s(doneCopy, use = false).action)
        val ready = s(doneCopy, use = true)
        assertEquals(GuideState.DONE, ready.state)
        assertEquals(GuideSource.COPY_MARK, ready.source)
        assertEquals(5_000L, ready.atMs)
    }

    @Test fun step5SkippedBySelfReportSkipsSteps6And7() {
        val r = LabGuide.evaluate(upToCopy.copy(copy = freshCopy, useGameCopy = false, self = LabGuideSelf(signedInMs = 2_000L, noCopyMs = 3_000L)))
        assertEquals(GuideState.SKIPPED, r.status(GuideStep.COPY).state)
        assertEquals(3_000L, r.status(GuideStep.COPY).atMs)
        assertEquals(GuideSource.SELF_REPORT, r.status(GuideStep.COPY).source)
        assertEquals(GuideState.SKIPPED, r.status(GuideStep.GAME).state)
        assertEquals(GuideState.SKIPPED, r.status(GuideStep.PLAY).state)
        assertNull(r.next)
    }

    // --- Schritt 6: Spiel im Launcher installiert (Indiz) ---

    @Test fun step6MarkerInCopyIsDoneAsIndicator() {
        val s = LabGuide.evaluate(upToCopy.copy(gameMarkerInCopy = true)).status(GuideStep.GAME)
        assertEquals(GuideState.DONE, s.state)
        assertEquals(GuideFact.GAME_MARKER_PRESENT, s.fact)
        assertEquals(GuideSource.FILE_CHECK, s.source)
    }

    @Test fun step6WithoutMarkerOffersToStartTheLauncher() {
        val s = LabGuide.evaluate(upToCopy).status(GuideStep.GAME)
        assertEquals(GuideState.OPEN, s.state)
        assertEquals(GuideFact.GAME_NOT_INSTALLED, s.fact)
        assertEquals(GuideAction.START_LAUNCHER, s.action)
    }

    @Test fun step6MarkerAlsoInOriginalSaysNothing() {
        val r = LabGuide.evaluate(upToCopy.copy(gameMarkerInCopy = true, gameMarkerInOriginal = true))
        assertEquals(GuideState.UNKNOWN, r.status(GuideStep.GAME).state)
        assertEquals(GuideFact.GAME_MARKER_UNRELIABLE, r.status(GuideStep.GAME).fact)
        // Ein Schritt ohne Aussage hält die Empfehlung nicht auf: weiter mit Schritt 7.
        assertEquals(GuideStep.PLAY, r.next)
    }

    @Test fun step6NeedsTheCopyFirst() {
        val s = LabGuide.evaluate(upToCopy.copy(copy = freshCopy, useGameCopy = false, gameMarkerInCopy = true)).status(GuideStep.GAME)
        assertEquals(GuideState.OPEN, s.state)
        assertEquals(GuideFact.GAME_NEEDS_COPY, s.fact)
        assertEquals(GuideAction.NONE, s.action)
    }

    // --- Schritt 7: Spielen ---

    @Test fun step7GameWindowInLastRunIsDoneWithDate() {
        val s = LabGuide.evaluate(upToCopy.copy(gameMarkerInCopy = true, lastRun = run(t = 9_000L, main = true, game = true))).status(GuideStep.PLAY)
        assertEquals(GuideState.DONE, s.state)
        assertEquals(GuideFact.GAME_WINDOW_SEEN, s.fact)
        assertEquals(GuideSource.LAST_RUN, s.source)
        assertEquals(9_000L, s.atMs)
    }

    @Test fun step7NotSeenOffersTheLauncher() {
        val s = LabGuide.evaluate(upToCopy.copy(gameMarkerInCopy = true, lastRun = run(main = true))).status(GuideStep.PLAY)
        assertEquals(GuideState.OPEN, s.state)
        assertEquals(GuideFact.GAME_WINDOW_NOT_SEEN, s.fact)
        assertEquals(GuideAction.START_LAUNCHER, s.action)
    }

    // --- Reihenfolge, nächster Schritt ---

    @Test fun stepsComeInTheSpecifiedOrder() {
        val r = LabGuide.evaluate(LabGuideInputs())
        assertEquals(GuideStep.entries.toList(), r.steps.map { it.step })
        assertEquals((1..7).toList(), r.steps.map { it.step.number })
    }

    @Test fun freshDeviceRecommendsPackagesFirst() {
        val r = LabGuide.evaluate(LabGuideInputs())
        assertEquals(GuideStep.PACKAGES, r.next)
        assertEquals(GuideAction.LOAD_FEX, r.continueAction)
    }

    @Test fun nextStepWalksThroughTheChain() {
        var i = LabGuideInputs()
        assertEquals(GuideStep.PACKAGES, LabGuide.evaluate(i).next)
        i = i.copy(fexInstalled = true)
        assertEquals(GuideStep.LAUNCHER, LabGuide.evaluate(i).next)
        assertEquals(GuideAction.LOAD_INSTALLER, LabGuide.evaluate(i).continueAction)
        i = i.copy(installerFilePresent = true)
        assertEquals(GuideAction.START_INSTALLER, LabGuide.evaluate(i).continueAction)
        i = i.copy(launcherExePresent = true)
        assertEquals(GuideStep.SETTING, LabGuide.evaluate(i).next)
        assertEquals(GuideAction.SET_LOGIN_WAY, LabGuide.evaluate(i).continueAction)
        i = i.copy(loginWayApplied = true)
        assertEquals(GuideStep.SIGN_IN, LabGuide.evaluate(i).next)
        assertEquals(GuideAction.START_LAUNCHER, LabGuide.evaluate(i).continueAction)
        i = i.copy(self = LabGuideSelf(signedInMs = 2_000L), copy = freshCopy)
        assertEquals(GuideStep.COPY, LabGuide.evaluate(i).next)
        assertEquals(GuideAction.ASK_COPY, LabGuide.evaluate(i).continueAction)
        i = i.copy(copy = doneCopy)
        assertEquals(GuideStep.COPY, LabGuide.evaluate(i).next)
        assertEquals(GuideAction.ENABLE_COPY, LabGuide.evaluate(i).continueAction)
        i = i.copy(useGameCopy = true)
        assertEquals(GuideStep.GAME, LabGuide.evaluate(i).next)
        assertEquals(GuideAction.START_LAUNCHER, LabGuide.evaluate(i).continueAction)
        i = i.copy(gameMarkerInCopy = true)
        assertEquals(GuideStep.PLAY, LabGuide.evaluate(i).next)
        i = i.copy(lastRun = run(game = true))
        assertNull(LabGuide.evaluate(i).next)
    }

    @Test fun everythingDoneLetsContinueStartTheLauncherToPlay() {
        val r = LabGuide.evaluate(upToCopy.copy(gameMarkerInCopy = true, lastRun = run(main = true, game = true)))
        assertNull(r.next)
        assertEquals(GuideAction.START_LAUNCHER, r.continueAction)
        assertTrue(r.steps.all { it.state == GuideState.DONE })
    }

    @Test fun runningCopyHoldsTheRecommendationWithoutButton() {
        val r = LabGuide.evaluate(upToCopy.copy(copy = freshCopy.copy(running = true), useGameCopy = false))
        assertEquals(GuideStep.COPY, r.next)
        assertEquals(GuideAction.NONE, r.continueAction)
    }

    @Test fun applyingLoginWayDoesNotUndoTheCopySwitchStep() {
        // Die Voreinstellung zählt auch bei eingeschalteter Kopie als gesetzt (Schritt 3), sonst würde Schritt 5 Schritt 3 wieder öffnen.
        val settings = LabSettings(wine = "", translator = LabTranslator.FEX, fexVersion = "lab-2508", stability = LabStability.FEX, useGameCopy = true)
        assertTrue(LabLoginWay.isAppliedCore(settings))
        assertFalse(LabLoginWay.isApplied(settings))
    }

    // --- Keine Angabe ohne Quelle ---

    @Test fun everyFactHasASource() {
        // Der Typ erzwingt es; der Test hält fest, dass keine Angabe auf eine leere Quelle verweist.
        GuideFact.entries.forEach { assertNotNull(it.source) }
    }

    @Test fun everyStatusOfEveryInputCombinationNamesItsSource() {
        val bools = listOf(false, true)
        var count = 0
        for (container in bools) for (fex in bools) for (installer in bools) for (exe in bools) for (applied in bools) for (use in bools)
            for (self in listOf(LabGuideSelf(), LabGuideSelf(signedInMs = 5L), LabGuideSelf(noCopyMs = 6L), LabGuideSelf(5L, 6L)))
                for (copy in listOf(freshCopy, doneCopy, freshCopy.copy(running = true), freshCopy.copy(partial = true), freshCopy.copy(fits = false), freshCopy.copy(originalPresent = false), freshCopy.copy(layoutFailed = true)))
                    for (lr in listOf(null, run(main = true), run(game = true)))
                        for (marker in bools) for (markerOrig in bools) {
                            val r = LabGuide.evaluate(
                                LabGuideInputs(container, true, fex, installer, exe, applied, use, self, copy, lr, marker, markerOrig),
                            )
                            assertEquals(7, r.steps.size)
                            r.steps.forEach { st ->
                                assertNotNull("source of ${st.step}", st.source)
                                assertEquals(st.fact.source, st.source)
                                st.evidence?.let { assertNotNull(it.fact.source) }
                                // Eine Angabe mit Datum nennt das Datum nur bei Quellen, die eines haben.
                                if (st.atMs != null) assertTrue(st.source in setOf(GuideSource.SELF_REPORT, GuideSource.LAST_RUN, GuideSource.COPY_MARK, GuideSource.SETTINGS))
                            }
                            // Der Hauptknopf ist immer die Aktion des empfohlenen Schritts (oder „Launcher starten“, wenn alles erledigt ist).
                            val next = r.nextStatus
                            if (next != null) assertEquals(next.action, r.continueAction)
                            count++
                        }
        assertTrue(count > 10_000)
    }

    // --- Selbstauskünfte ---

    @Test fun selfReportRoundTripAndReset() {
        val s = LabGuideSelf(signedInMs = 1234L, noCopyMs = 5678L)
        assertEquals(s, LabGuideSelf.fromJson(s.toJson()))
        assertEquals(LabGuideSelf(), LabGuideSelf.fromJson(LabGuideSelf().toJson()))
        assertEquals(LabGuideSelf(noCopyMs = 5678L), LabGuideSelf.fromJson(s.copy(signedInMs = null).toJson()))
    }

    @Test fun brokenSelfReportMeansNothingConfirmed() {
        assertEquals(LabGuideSelf(), LabGuideSelf.fromJson(null))
        assertEquals(LabGuideSelf(), LabGuideSelf.fromJson(""))
        assertEquals(LabGuideSelf(), LabGuideSelf.fromJson("{kaputt"))
        assertEquals(LabGuideSelf(), LabGuideSelf.fromJson("{\"signedIn\":0,\"noCopy\":-3}"))
    }

    // --- Letzter Lauf und Vorschlag „Launcher neu starten“ ---

    @Test fun lastLauncherRunIgnoresOtherTargets() {
        val runs = listOf(run(t = 9_000L, target = "installer"), run(t = 5_000L), run(t = 3_000L, target = "steptest"))
        assertEquals(5_000L, LabGuide.lastLauncherRun(runs)?.startedMs)
        assertNull(LabGuide.lastLauncherRun(listOf(run(target = "installer"))))
        assertNull(LabGuide.lastLauncherRun(emptyList()))
    }

    @Test fun restartIsSuggestedAfterHangOrErrorWindow() {
        assertTrue(LabGuide.suggestsRestart(run(watchEnd = "no_window")))
        assertTrue(LabGuide.suggestsRestart(run(watchEnd = "error_window")))
        assertTrue(LabGuide.suggestsRestart(run(watchEnd = "window_ok", noWindow = true)))
    }

    @Test fun restartIsNotSuggestedForHealthyOrAlreadyRestartedRuns() {
        assertFalse(LabGuide.suggestsRestart(null))
        assertFalse(LabGuide.suggestsRestart(run(watchEnd = "window_ok", main = true)))
        assertFalse(LabGuide.suggestsRestart(run()))
        assertFalse(LabGuide.suggestsRestart(run(watchEnd = "no_window", restart = true)))
        // Ein gesehenes Spielfenster: der Lauf war gesund, auch wenn die Erkennung zwischendurch „kein Fenster“ sagte.
        assertFalse(LabGuide.suggestsRestart(run(watchEnd = "window_ok", noWindow = true, game = true)))
    }

    // --- Fenster eines Laufs (Hauptfenster, Spielfenster) ---

    private fun ev(cls: String, w: Int, kind: LabWinEventKind = LabWinEventKind.OPEN) = LabWinEvent(1_000L, kind, 1, cls, "t", w, 600)

    @Test fun mainWindowNeedsTheWidthOfTheMainWindow() {
        // Anmeldefenster 362×693 und 640×480 sind kein Hauptfenster; 1440×641 ist eines.
        assertFalse(LabRunWindows.summarize(listOf(ev("Battle.net.exe", 362), ev("Battle.net.exe", 640))).mainWindow)
        assertTrue(LabRunWindows.summarize(listOf(ev("Battle.net.exe", 362), ev("Battle.net.exe", 1440))).mainWindow)
        assertFalse(LabRunWindows.summarize(listOf(ev("explorer.exe", 1440))).mainWindow)
    }

    @Test fun gameWindowIsRecognizedByClass() {
        val seen = LabRunWindows.summarize(listOf(ev("wow-arm64.exe", 1440)))
        assertTrue(seen.gameWindow)
        assertFalse(seen.mainWindow)
        assertFalse(LabRunWindows.summarize(emptyList()).gameWindow)
    }

    @Test fun closeAndRaiseEventsDoNotCount() {
        assertFalse(LabRunWindows.summarize(listOf(ev("Battle.net.exe", 1440, LabWinEventKind.CLOSE), ev("Battle.net.exe", 1440, LabWinEventKind.RAISE))).mainWindow)
        assertTrue(LabRunWindows.summarize(listOf(ev("Battle.net.exe", 1440, LabWinEventKind.SEEN))).mainWindow)
    }
}
