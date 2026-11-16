package app.gamenative.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Die Anmeldeversuche im Journal (Übernahme aus der Connection.log, Datei, Text). */
class LoginJournalAttemptsTest {
    private fun entry(attempts: AttemptSummary?) = JournalEntry(
        startMs = 1_700_000_000_000L,
        appVersion = "2.3.3-fold9.1",
        level = "fill",
        scope = "all",
        loginFileWritten = false,
        worldMs = 1_700_000_083_000L,
        endKind = "game_exit",
        endMs = 1_700_003_600_000L,
        network = "wifi",
        vpn = true,
        attempts = attempts,
    )

    private val two = AttemptSummary(
        total = 2,
        firstOk = 2,
        notes = listOf(AttemptNote("e14400", false, false, 0), AttemptNote("ok", true, null, 41)),
    )

    @Test
    fun attemptsSurviveTheFile() {
        for (a in listOf(two, AttemptSummary(0, null, emptyList()), AttemptSummary(25, 23, List(20) { AttemptNote("e14400", false, false, 20) }))) {
            val back = LoginJournal.parse(LoginJournal.serialize(listOf(entry(a)))).single()
            assertEquals(a, back.attempts)
        }
    }

    @Test
    fun anEntryWithoutCaptureStaysWithoutAndOldLinesStillRead() {
        val text = LoginJournal.serialize(listOf(entry(null)))
        assertFalse(text.contains("att="))
        assertNull(LoginJournal.parse(text).single().attempts)
        val old = "start=1700000000000 v=2.3.2 level=off scope=all loginfile=0 signal=1 net=none"
        val e = LoginJournal.parse(old).single()
        assertEquals("2.3.2", e.appVersion)
        assertNull(e.attempts)
    }

    @Test
    fun theFileLineHoldsOnlyResultFlagsAndSeconds() {
        val line = LoginJournal.serialize(listOf(entry(two))).trim()
        assertTrue(line, line.contains("att=e14400:ff:0,ok:tu:41 attN=2 attOk=2"))
        assertEquals(1, line.lines().size)
    }

    @Test
    fun aBrokenAttemptListDropsOnlyThatFieldNotTheEntry() {
        val base = "start=1700000000000 v=2.3.3 level=fill scope=all loginfile=0"
        for (bad in listOf("att=player@example.com", "att=ok:ff", "att=zzz:ff:0", "att=ok:f:0", "att=ok:ff:abc", "att=e14400:ff:0,,ok:ff:1")) {
            val e = LoginJournal.parse("$base $bad").single()
            assertNull(bad, e.attempts)
            assertEquals("2.3.3", e.appVersion)
        }
    }

    @Test
    fun valuesInTheListCannotInjectFieldsOrAddresses() {
        val evil = entry(AttemptSummary(1, null, listOf(AttemptNote("e1 2", false, false, 0))))
        // Ein ungültiges Ergebnis wird beim Schreiben nicht zu einem weiteren Feld.
        val line = LoginJournal.serialize(listOf(evil)).trim()
        assertEquals(1, line.lines().size)
        assertFalse(line.contains("e1 2"))
        assertNull(LoginJournal.parse(line).single().attempts)
    }

    @Test
    fun theLimitOfSixtyEntriesAlsoHoldsWithAttempts() {
        val many = (1..70).map { entry(two).copy(startMs = 1_700_000_000_000L + it) }
        assertEquals(60, LoginJournal.parse(LoginJournal.serialize(many)).size)
    }

    @Test
    fun readableLineAndReportCarryTheAttemptsAndTheOverview() {
        val line = LoginJournal.readableLine(entry(two))
        assertTrue(line, line.contains("login attempts 2: #1 error 14400 (usedToken false, authenticatorRequired false); #2 +41s ok"))
        assertTrue(LoginJournal.readableLine(entry(null)).endsWith("login attempts not captured"))
        val report = LoginJournal.report(listOf(entry(two), entry(null)))
        assertTrue(report.contains("overview of 2 game starts"))
        assertTrue(report.contains("verdict: works"))
        assertTrue(report.contains("login ok on attempt 1: 0, attempt 2: 1"))
    }

    @Test
    fun shortLineIsShortAndHasTheAttempts() {
        val short = LoginJournal.shortLine(entry(two))
        assertTrue(short, short.length < 150)
        assertTrue(short.endsWith("attempts 2, first ok #2"))
        assertTrue(short.contains("wifi+vpn"))
        assertTrue(short.contains("end game_exit"))
        assertNotNull(LoginJournal.shortLine(entry(null)))
        assertTrue(LoginJournal.shortLine(entry(null)).endsWith("attempts ?"))
    }

    @Test
    fun oldEntryWithFormSubmissionStillReadsAndTheAuthenticatorStepRoundTrips() {
        // Eintrag wie von Version 9.19 geschrieben (Schritt form_submission, ohne die neuen Felder), muss weiter lesbar bleiben.
        val old = AttemptSummary(1, null, listOf(AttemptNote("e14400", false, true, 0, step = LoginStep.FORM_SUBMISSION, stepFailed = true, platform = "WinA", launcherPortalSet = false, usedSso = false)))
        val oldLine = LoginJournal.serialize(listOf(entry(old))).trim()
        assertTrue(oldLine, oldLine.contains(":form_submission:WinA:"))
        for (a in listOf(old, AttemptSummary(1, null, listOf(AttemptNote("e14400", false, true, 0, step = LoginStep.AUTHENTICATOR, stepFailed = true, platform = "WinA", launcherPortalSet = false, usedSso = false))))) {
            val back = LoginJournal.parse(LoginJournal.serialize(listOf(entry(a)))).single()
            assertEquals(a, back.attempts)
        }
    }
}
