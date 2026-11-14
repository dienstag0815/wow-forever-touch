package app.gamenative.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportDialogStateTest {
    private fun card(time: Long) = ReportCard("report-$time.zip", time, "/cache/report-$time.zip", true, "https://example.org/issues/new")

    @Test
    fun aFinishedReportMakesTheDialogDueForThatCard() {
        val state = ReportDialogState()
        assertNull(state.due.value)
        assertFalse(ReportDialogState.isDue(state.due.value, card(10)))
        state.reportReady(card(10), appVisible = true)
        assertTrue(ReportDialogState.isDue(state.due.value, card(10)))
    }

    @Test
    fun aClosedDialogDoesNotComeBackForTheSameReport() {
        val state = ReportDialogState()
        state.reportReady(card(10), appVisible = true)
        state.dismiss()
        assertFalse(ReportDialogState.isDue(state.due.value, card(10)))
        // Auch das erneute Anlegen derselben Karte (zum Beispiel nach einem Neuaufbau der Zip) macht ihn nicht fällig.
        assertFalse(ReportDialogState.isDue(state.due.value, card(10).copy(zipPath = "/cache/new.zip")))
    }

    @Test
    fun aReportThatIsFinishedWhileTheAppIsInTheBackgroundLeavesOnlyTheCard() {
        val state = ReportDialogState()
        state.reportReady(card(10), appVisible = false)
        assertFalse(ReportDialogState.isDue(state.due.value, card(10)))
    }

    @Test
    fun aBackgroundReportTakesBackTheDialogOfAnOlderReport() {
        val state = ReportDialogState()
        state.reportReady(card(10), appVisible = true)
        state.reportReady(card(20), appVisible = false)
        assertFalse(ReportDialogState.isDue(state.due.value, card(10)))
        assertFalse(ReportDialogState.isDue(state.due.value, card(20)))
    }

    @Test
    fun aNewReportIsDueAgainAndTheOldOneIsNot() {
        val state = ReportDialogState()
        state.reportReady(card(10), appVisible = true)
        state.dismiss()
        state.reportReady(card(20), appVisible = true)
        assertTrue(ReportDialogState.isDue(state.due.value, card(20)))
        assertFalse(ReportDialogState.isDue(state.due.value, card(10)))
    }

    @Test
    fun withoutACardNoDialogIsDue() {
        val state = ReportDialogState()
        state.reportReady(card(10), appVisible = true)
        assertFalse(ReportDialogState.isDue(state.due.value, null))
    }

    @Test
    fun afterAnAppRestartThereIsOnlyTheSavedCardAndNoDialog() {
        val saved = ReportCardFile.parse(ReportCardFile.serialize(card(10)))
        assertEquals(card(10), saved)
        // Ein neuer Prozess beginnt mit einem leeren Zustand.
        val restarted = ReportDialogState()
        assertFalse(ReportDialogState.isDue(restarted.due.value, saved))
    }
}
