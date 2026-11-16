package app.gamenative.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Zustandssatz, Hauptknopf und Haken der Ansicht „Einfach“ je Zustand des Assistenten: frisch, mitten, fertig, Hänger, Neustart-Vorschlag. */
class LabEasyStateTest {
    private val doneCopy = LabGuideCopy(originalPresent = true, fits = true, complete = true, finishedMs = 5_000L)

    private fun run(game: Boolean = false, watchEnd: String = "", noWindow: Boolean = false, restart: Boolean = false) =
        LabRunEntry(
            1_000L, "battlenet", 100, 4, "FEX", "FEXCore 2508", 0,
            watchEnd = watchEnd, noWindowSeen = noWindow, gameWindowSeen = game, restartRequested = restart,
        )

    private fun easy(i: LabGuideInputs, restartPending: Boolean = false): LabEasyState =
        LabEasyState.of(LabGuide.evaluate(i), i, restartPending)

    private val fresh = LabGuideInputs()
    private val allDone = LabGuideInputs(
        fexInstalled = true,
        installerFilePresent = true,
        launcherExePresent = true,
        loginWayApplied = true,
        useGameCopy = true,
        self = LabGuideSelf(signedInMs = 2_000L),
        copy = doneCopy,
        gameMarkerInCopy = true,
        lastRun = run(game = true),
    )

    @Test
    fun freshStartPointsAtStepOneWithItsButton() {
        val s = easy(fresh)
        assertEquals(EasyHeadline.NEXT, s.headline)
        assertEquals(GuideStep.PACKAGES, s.step)
        assertEquals(EasyMain.CONTINUE, s.main)
        assertEquals(GuideAction.LOAD_FEX, s.action)
        assertEquals(EasyMark.OPEN, s.marks.getValue(GuideStep.PACKAGES))
    }

    @Test
    fun everyStepHasAMark() {
        assertEquals(GuideStep.entries.toSet(), easy(fresh).marks.keys)
    }

    @Test
    fun midwayNamesTheFirstOpenStepAndMarksTheDoneOnes() {
        val i = fresh.copy(fexInstalled = true, installerFilePresent = true, gameContainerPresent = true)
        val s = easy(i)
        assertEquals(EasyHeadline.NEXT, s.headline)
        assertEquals(GuideStep.LAUNCHER, s.step)
        assertEquals(GuideAction.START_INSTALLER, s.action)
        assertEquals(EasyMark.DONE, s.marks.getValue(GuideStep.PACKAGES))
        assertEquals(EasyMark.OPEN, s.marks.getValue(GuideStep.LAUNCHER))
    }

    @Test
    fun allDoneAfterPartAOffersTheDirectStart() {
        val s = easy(allDone)
        assertEquals(EasyHeadline.PART_A_DONE, s.headline)
        assertNull(s.step)
        assertEquals(EasyMain.DIRECT_START, s.main)
        // Der Hauptknopf tut in der App nichts am Launcher: keine Aktion des Assistenten.
        assertEquals(GuideAction.NONE, s.action)
        assertTrue(s.mainUsable)
        assertTrue(s.marks.values.all { it == EasyMark.DONE })
    }

    @Test
    fun partAIsEnoughPartBStaysOpenAndDoesNotHoldBackTheDirectStart() {
        // Schritte 1 bis 4 erledigt, keine Kopie, kein Spiel im Launcher: Teil B ist offen, der Satz sagt trotzdem „direkt starten“.
        val i = allDone.copy(useGameCopy = false, copy = LabGuideCopy(originalPresent = true), gameMarkerInCopy = false, lastRun = null)
        val state = LabGuide.evaluate(i)
        assertEquals(GuideStep.COPY, state.next)
        val s = easy(i)
        assertEquals(EasyHeadline.PART_A_DONE, s.headline)
        assertEquals(EasyMain.DIRECT_START, s.main)
        assertEquals(EasyMark.OPEN, s.marks.getValue(GuideStep.COPY))
    }

    @Test
    fun withoutTheSignInNoteStepFourIsNextEvenWhenPartBIsDone() {
        val i = allDone.copy(self = LabGuideSelf())
        val s = easy(i)
        assertEquals(EasyHeadline.NEXT, s.headline)
        assertEquals(GuideStep.SIGN_IN, s.step)
        assertEquals(EasyMain.CONTINUE, s.main)
    }

    @Test
    fun allDoneWithoutLauncherHasNoMainButton() {
        val s = easy(allDone.copy(launcherExePresent = false))
        // Schritt 2 ist dann offen; der Hauptknopf gehört ihm, nicht dem Start.
        assertEquals(EasyHeadline.NEXT, s.headline)
        assertEquals(GuideStep.LAUNCHER, s.step)
    }

    @Test
    fun stepWithoutButtonWaits() {
        // Kein Spiel-Container: der Installer-Schritt ist blockiert und hat keinen Knopf.
        val s = easy(fresh.copy(fexInstalled = true, gameContainerPresent = false))
        assertEquals(EasyHeadline.WAITING, s.headline)
        assertEquals(GuideStep.LAUNCHER, s.step)
        assertEquals(EasyMain.NONE, s.main)
        assertFalse(s.mainUsable)
        assertEquals(EasyMark.PROBLEM, s.marks.getValue(GuideStep.LAUNCHER))
    }

    @Test
    fun hangReplacesTheNextStepSentenceAndTurnsTheMainButtonIntoRestart() {
        val stuck = allDone.copy(lastRun = run(watchEnd = LabWatchState.NO_WINDOW.text, noWindow = true))
        val s = easy(stuck)
        assertEquals(EasyHeadline.STUCK, s.headline)
        assertEquals(EasyMain.RESTART, s.main)
        assertTrue(s.mainUsable)
    }

    @Test
    fun errorWindowIsAHangToo() {
        val s = easy(allDone.copy(lastRun = run(watchEnd = LabWatchState.ERROR_WINDOW.text)))
        assertEquals(EasyHeadline.STUCK, s.headline)
    }

    @Test
    fun hangRestartNeedsLauncherFileAndContainer() {
        val s = easy(allDone.copy(lastRun = run(noWindow = true), launcherExePresent = false))
        assertEquals(EasyHeadline.STUCK, s.headline)
        assertFalse(s.mainUsable)
    }

    @Test
    fun gameWindowSeenCancelsTheHint() {
        val s = easy(allDone.copy(lastRun = run(game = true, noWindow = true)))
        assertEquals(EasyHeadline.PART_A_DONE, s.headline)
    }

    @Test
    fun alreadyRestartedIsNoHangHint() {
        val s = easy(allDone.copy(lastRun = run(noWindow = true, restart = true)))
        assertFalse(s.headline == EasyHeadline.STUCK)
    }

    @Test
    fun restartOfferFromTheKeyBarComesFirst() {
        val s = easy(fresh, restartPending = true)
        assertEquals(EasyHeadline.RESTART_PENDING, s.headline)
        assertEquals(EasyMain.RESTART_PENDING, s.main)
        assertTrue(s.mainUsable)
    }

    @Test
    fun skippedAndUnknownShowAsDash() {
        assertEquals(EasyMark.SKIPPED, LabEasyState.mark(GuideState.SKIPPED))
        assertEquals(EasyMark.SKIPPED, LabEasyState.mark(GuideState.UNKNOWN))
        assertEquals(EasyMark.PROBLEM, LabEasyState.mark(GuideState.ERROR))
        assertEquals(EasyMark.PROBLEM, LabEasyState.mark(GuideState.BLOCKED))
        assertEquals(EasyMark.OPEN, LabEasyState.mark(GuideState.RUNNING))
        assertEquals(EasyMark.DONE, LabEasyState.mark(GuideState.DONE))
    }
}
