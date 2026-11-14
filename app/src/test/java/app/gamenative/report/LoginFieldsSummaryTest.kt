package app.gamenative.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Die neuen Angaben in `kurz.txt` und im Journal (Obergrenze 2500 Zeichen, Datei, Text). */
class LoginFieldsSummaryTest {
    private val redactor = ReportRedactor()

    private fun attempt(i: Int, step: String = LoginStep.REALM_AUTH, failed: Boolean = false, platform: String? = "WinA") = LoginAttempt(
        time = "11/14 19:5$i:10", run = 1, connectionId = i, usedToken = false, authenticatorRequired = false,
        result = if (failed) AttemptResult.Error(14400) else AttemptResult.Success, durationMs = 1800L,
        platform = platform, launcherPortalSet = false, usedSso = false, step = step, stepFailed = failed, stepMs = 3200L,
    )

    private val note = AttemptNote("e14400", false, false, 41, step = LoginStep.FORM_SUBMISSION, stepFailed = true, platform = "WinA", launcherPortalSet = false, usedSso = false)

    private val entry = JournalEntry(
        startMs = 1_760_000_000_000L, appVersion = "2.3.3-fold9.1", level = "fill", scope = "all", loginFileWritten = false,
        fillTapMs = 1_760_000_012_000L, fillResult = "done", worldMs = 1_760_000_083_000L, endKind = "game_exit", endMs = 1_760_003_600_000L,
        network = "wifi", vpn = false, attempts = AttemptSummary(20, 20, List(20) { note }),
    )

    private fun input(lastAttempts: List<String>, header: String = "attempts 8") = SummaryInput(
        device = "Samsung SM-F000", android = "17 (API 37)", chip = "Qualcomm SM8850", appVersion = "2.3.3-fold9.1 (code 233091)", clientBuild = "11.2.7.64978",
        region = "Americas", loginLevel = "fill (scope all)", machineIdSet = "yes", machineIdFingerprint = "ab12cd34", machineIdChanges = "0", systemViewChanges = "1",
        attemptsHeader = header, lastAttempts = lastAttempts, lastJournal = List(3) { LoginJournal.shortLine(entry.copy(startMs = entry.startMs + it * 3_600_000L)) },
        answers = ProblemAnswers().lines(), history = "starts 12 (data 12); ok at 1st/2nd/3rd+ attempt 3/2/1; never ok 4; no attempt 0; first ok 2026-11-02",
        timeline = listOf("App installed 2026-11-01, updated 2026-11-10; source com.android.vending; game folder since 2026-10-20, WTF since 2026-10-22", "Windows ID since 2026-11-01, from game folder: no; app versions in journal 3"),
        otherLaunchers = "other launcher apps installed: Winlator; game folder since 2026-01-01, this install since 2026-11-13, difference 316 days: earlier install of this or another app; other files in the game folder not from this app: 1",
    )

    @Test
    fun theShortLinesWithTheNewPartsStayUnderTheLimitOfTheSummary() {
        val lines = (1..5).map { LoginAttempts.attemptShort(attempt(it, failed = it % 2 == 0, step = if (it % 2 == 0) LoginStep.FORM_SUBMISSION else LoginStep.REALM_AUTH)) }
        val header = LoginAttempts.headerLine(LoginAttempts.stats((1..5).map { attempt(it) }))
        val text = ReportSummary.build(input(lines, header), redactor)
        assertTrue("${text.length} Zeichen", text.length <= ReportSummary.MAX_CHARS)
        // Mit allen Teilen am Anschlag (viele Antworten, Zeitleiste, Fremd-Apps) darf nur das Ende der letzten Journalzeile fehlen.
        assertTrue(text, text.lines().count { it.contains("attempts 20, first ok #20, last: step form_submission failed, WinA, portal none, sso false") } >= 2)
        for (part in listOf("step realm_auth reached", "step form_submission failed", "| WinA | portal none | sso false", "last: step form_submission failed, WinA, portal none, sso false")) {
            assertTrue("fehlt: $part", text.contains(part))
        }
    }

    @Test
    fun theLimitHoldsEvenWithHugeValuesInEveryPart() {
        val long = attempt(1, platform = "Mac1")
        val lines = List(5) { LoginAttempts.attemptShort(long) + " | " + "x".repeat(400) }
        val text = ReportSummary.build(input(lines, "attempts 999 " + "y".repeat(800)), redactor)
        assertTrue("${text.length} Zeichen", text.length <= ReportSummary.MAX_CHARS)
        assertTrue(text.endsWith("..."))
    }

    @Test
    fun theJournalKeepsTheNewNoteFieldsThroughTheFile() {
        val summary = AttemptSummary(2, null, listOf(note, note.copy(result = "ok", step = LoginStep.REALM_AUTH, stepFailed = false, launcherPortalSet = true, usedSso = null)))
        val back = LoginJournal.parse(LoginJournal.serialize(listOf(entry.copy(attempts = summary)))).single().attempts
        assertEquals(summary.copy(firstOk = 2), back)
        assertEquals(LoginStep.FORM_SUBMISSION, back!!.notes[0].step)
        assertEquals(true, back.notes[1].launcherPortalSet)
        assertNull(back.notes[1].usedSso)
    }

    @Test
    fun theNotesWithoutTheNewFieldsStillReadAndStayShort() {
        val old = "start=1700000000000 v=2.3.2 level=fill scope=all loginfile=0 signal=1 net=wifi att=e14400:ff:0,ok:ff:41 attN=2 attOk=2"
        val a = LoginJournal.parse(old).single().attempts!!
        assertEquals(listOf(AttemptNote("e14400", false, false, 0), AttemptNote("ok", false, false, 41)), a.notes)
        assertTrue(LoginHistory.attemptsText(a).contains("#1 error 14400 (usedToken false, authenticatorRequired false)"))
        assertEquals("2, first ok #2", LoginHistory.attemptsShort(a))
        val serialized = LoginJournal.serialize(listOf(LoginJournal.parse(old).single()))
        assertTrue(serialized, serialized.contains("att=e14400:ff:0,ok:ff:41 "))
    }

    @Test
    fun forbiddenValuesInTheNoteFieldsDoNotSurviveTheFile() {
        val evil = "start=1700000000000 v=2.3.3 level=fill scope=all loginfile=0 signal=1 net=wifi att=ok:ff:0:evil_step:Win_A:ttt attN=1"
        val parsed = LoginJournal.parse(evil).single().attempts!!.notes.single()
        assertNull(parsed.step)
        assertNull(parsed.platform)
        // Ein Eintrag mit falscher Teilezahl ist ungültig (die Zeile entfällt).
        assertTrue(LoginJournal.parse("start=1700000000000 v=2.3.3 level=fill scope=all loginfile=0 signal=1 net=wifi att=ok:ff:0:a:b attN=1").single().attempts == null)
        val out = LoginJournal.serialize(listOf(entry.copy(attempts = AttemptSummary(1, null, listOf(note.copy(step = "x y", platform = "Wi n"))))))
        assertFalse(out.contains("x y"))
        assertFalse(out.contains("Wi n"))
    }

    @Test
    fun theReadableLineAndTheShortLineNameStepPlatformPortalAndSso() {
        val text = LoginJournal.readableLine(entry)
        assertTrue(text, text.contains("#1 +41s error 14400 (usedToken false, authenticatorRequired false, step form_submission failed, platform WinA, launcherPortal none, usedSSO false)"))
        val short = LoginJournal.shortLine(entry)
        assertTrue(short, short.contains("attempts 20, first ok #20, last: step form_submission failed, WinA, portal none, sso false"))
    }

    @Test
    fun theSummaryNeverGetsLongerThanTheLimitWithTheFullJournalLines() {
        val full = ReportSummary.build(input(List(5) { LoginAttempts.attemptShort(attempt(it + 1)) }), redactor)
        assertTrue(full.length <= ReportSummary.MAX_CHARS)
        assertEquals(3, full.lines().count { it.startsWith("20") && it.contains("attempts 20") })
    }
}
