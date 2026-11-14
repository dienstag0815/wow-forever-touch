package app.gamenative.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Die neuen Teile von `kurz.txt`: Antworten, Übersicht, Zeitleiste, andere Start-Apps. */
class ReportSummaryHistoryTest {
    private val guid = listOf("5f0d9a42", "77c1", "4c1e", "9b0a", "123456789abc").joinToString("-")
    private val email = "player@example.com"
    private val phrase = "Tr0ub4dor&3xyz"
    private val redactor = ReportRedactor(listOf(email), listOf(phrase), listOf(guid))

    private val journalEntry = JournalEntry(
        startMs = 1_760_000_000_000L,
        appVersion = "2.3.3-fold9.1",
        level = "fill",
        scope = "all",
        loginFileWritten = false,
        fillTapMs = 1_760_000_012_000L,
        fillResult = "done",
        worldMs = 1_760_000_083_000L,
        endKind = "game_exit",
        endMs = 1_760_003_600_000L,
        network = "wifi",
        vpn = false,
        attempts = AttemptSummary(3, 3, List(3) { AttemptNote(if (it == 2) "ok" else "e14400", false, false, 41L * it) }),
    )

    private fun input(answers: List<String>? = ProblemAnswers().lines(), journal: List<JournalEntry> = List(5) { journalEntry.copy(startMs = journalEntry.startMs + it * 3_600_000L) }) = SummaryInput(
        device = "Samsung SM-F000",
        android = "17 (API 37)",
        chip = "Qualcomm SM8850",
        appVersion = "2.3.3-fold9.1 (code 233091)",
        clientBuild = "11.2.7.64978",
        region = "Americas",
        loginLevel = "fill (scope all)",
        machineIdSet = "yes",
        machineIdFingerprint = "ab12cd34",
        machineIdChanges = "0",
        systemViewChanges = "1",
        attemptsHeader = "attempts 8, ok 1, errors: 14400 x7, open 0; first login in a run ok, later rejected: no; ok then rejected (any run): no",
        lastAttempts = (1..5).map { "11/12 10:0$it:00 | run 1 | conn $it | usedToken false | authenticatorRequired false | error 14400 (BLZ51914400) | 1.2 s" },
        lastJournal = journal.takeLast(3).map { LoginJournal.shortLine(it) },
        answers = answers,
        history = LoginHistory.overviewCompact(LoginHistory.overview(journal)),
        timeline = listOf(
            "App installed 2026-11-01, updated 2026-11-10; source com.android.vending; game folder since 2026-10-20, WTF since 2026-10-22",
            "Windows ID since 2026-11-01, from game folder: no; app versions in journal 3",
        ),
        otherLaunchers = "other launcher apps installed: Winlator; game folder since 2026-01-01, this install since 2026-11-13, difference 316 days: earlier install of this or another app; other files in the game folder not from this app: 1",
    )

    @Test
    fun aFullSummaryWithEveryPartFitsWithoutCutting() {
        val text = ReportSummary.build(input(), redactor)
        assertTrue("${text.length} Zeichen", text.length <= ReportSummary.MAX_CHARS)
        assertFalse(text.endsWith("..."))
        for (needle in listOf("Answers from the player:", "History over game starts:", "Other apps: other launcher apps installed: Winlator", "Timeline:", "Last 3 game starts:")) {
            assertTrue("fehlt: $needle", text.contains(needle))
        }
    }

    @Test
    fun theAnswersComeBeforeTheAttemptsAndStayEvenWhenNotAnswered() {
        val text = ReportSummary.build(input(), redactor)
        assertEquals(4, text.lines().count { it.endsWith("not answered") })
        assertTrue(text.indexOf("Answers from the player:") < text.indexOf("Login attempts:"))
        val answered = ReportSummary.build(input(ProblemAnswers(WorkedBefore.YES, OtherAppBefore.NO, true, setOf(LoggedInElsewhere.PC_APP)).lines()), redactor)
        assertTrue(answered.contains("worked before: yes"))
        assertTrue(answered.contains("on a PC in the Battle.net app"))
        assertFalse(answered.contains("not answered"))
    }

    @Test
    fun withoutQuestionsThereAreNoAnswerLines() {
        val text = ReportSummary.build(input(answers = null), redactor)
        assertFalse(text.contains("Answers from the player"))
        assertFalse(text.contains("not answered"))
    }

    @Test
    fun theNewPartsGoThroughTheRedactorAndTheAddressFilter() {
        val dirty = input().copy(
            history = "starts 1 $email and $phrase",
            timeline = listOf("installer $guid", "address 10.1.2.3 and 192.168.0.7"),
            otherLaunchers = "other $email",
            answers = listOf("worked before: $phrase"),
        )
        val text = ReportSummary.build(dirty, redactor)
        for (secret in listOf(email, phrase, guid, "10.1.2.3", "192.168.0.7")) assertFalse("durchgelassen: $secret", text.contains(secret))
        assertTrue(text.contains(ReportSummary.ADDRESS))
    }

    @Test
    fun aLongJournalLosesItsTailBeforeTheAnswersAndTheHistory() {
        val huge = input().copy(lastJournal = List(3) { "x".repeat(900) })
        val text = ReportSummary.build(huge, redactor)
        assertTrue(text.length <= ReportSummary.MAX_CHARS)
        assertTrue(text.contains("Answers from the player:"))
        assertTrue(text.contains("History over game starts:"))
        assertTrue(text.contains("Timeline:"))
    }
}
