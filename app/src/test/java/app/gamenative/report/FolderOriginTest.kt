package app.gamenative.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderOriginTest {
    private val day = 86_400_000L
    private val jan1 = 1_767_225_600_000L // 2026-01-01 UTC

    @Test
    fun aReinstallOfThisAppWithoutProofIsNeutralAndNamesBothDatesAndTheDifference() {
        val line = FolderOrigin.line(jan1, jan1 + 316 * day, null)
        assertEquals("game folder since 2026-01-01, this install since 2026-11-13, difference 316 days: earlier install of this or another app", line)
        assertFalse(line.contains("traces"))
        assertFalse(line.contains("confirmed"))
    }

    @Test
    fun anOwnHistoryFileOlderThanTheInstallProvesAnEarlierInstallOfThisApp() {
        val line = FolderOrigin.line(jan1, jan1 + 40 * day, jan1 + 3 * day)
        assertTrue(line, line.contains("difference 40 days: earlier install of this app confirmed"))
        assertTrue(line, line.contains("since 2026-01-04"))
        assertFalse(line.contains("or another app"))
    }

    @Test
    fun anOwnHistoryFileYoungerThanTheInstallProvesNothing() {
        val line = FolderOrigin.line(jan1, jan1 + 40 * day, jan1 + 41 * day)
        assertTrue(line, line.endsWith("earlier install of this or another app"))
    }

    @Test
    fun aFreshInstallShowsNoEarlierInstall() {
        assertTrue(FolderOrigin.line(jan1 + 10 * day, jan1 + 10 * day, null).endsWith("no earlier install indicated"))
        // Unterschied unter einem Tag und ein Spielordner, der erst nach der Installation entstand.
        assertTrue(FolderOrigin.line(jan1 + 10 * day, jan1 + 10 * day + day / 2, null).endsWith("no earlier install indicated"))
        assertTrue(FolderOrigin.line(jan1 + 12 * day, jan1 + 10 * day, null).endsWith("no earlier install indicated"))
        // Die eigene Verlaufsdatei entsteht beim ersten Start, also nach der Installation: kein Beleg.
        assertTrue(FolderOrigin.line(jan1 + 10 * day, jan1 + 10 * day, jan1 + 10 * day + 1000).endsWith("no earlier install indicated"))
    }

    @Test
    fun missingDatesGiveAnUnknownDifferenceNotAFinding() {
        assertEquals("game folder since unknown, this install since 2026-01-01, difference unknown", FolderOrigin.line(null, jan1, null))
        assertTrue(FolderOrigin.line(jan1, null, jan1).endsWith("difference unknown"))
    }
}
