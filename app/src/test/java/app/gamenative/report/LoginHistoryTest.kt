package app.gamenative.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class LoginHistoryTest {
    private val zone = ZoneId.systemDefault()
    private val stampFormat = DateTimeFormatter.ofPattern("MM/dd HH:mm:ss.SSS")
    private val start = LocalDateTime.of(2026, 11, 14, 10, 0, 0).atZone(zone).toInstant().toEpochMilli()

    private fun at(ms: Long): String = stampFormat.format(Instant.ofEpochMilli(ms).atZone(zone))

    private fun line(ms: Long, text: String) = "${at(ms)}  [N][Connection] $text"

    private fun init(ms: Long) = line(ms, "Client connection initialized")

    /** Ein Versuch im belegten Format; [end]: "ok", "open" oder ein Fehlercode. */
    private fun attempt(ms: Long, end: String, token: String = "false", auth: String = "false"): String {
        val result = if (end == "ok") "ERROR_OK (0)" else "Status has no name. ($end)"
        return listOfNotNull(
            line(ms, "Glue login started loginType=\"(enum)0\" launcherPortal=\"nullopt\" loginPortal=\"EU.actual.battle.net:1119\""),
            line(ms, "Battle.net front connected connectionId=\"1\" title_id=\"5730135\" platform=\"WinA\" locale=\"enUS\" usedToken=\"$token\""),
            if (end == "open") null else line(ms + 2_000, "Battle.net logon form submission response received result=\"$result\" authenticatorRequired=\"$auth\" legalRequired=\"false\""),
            when (end) {
                "ok" -> line(ms + 2_100, "Battle.net logon completed result=\"ERROR_OK (0)\" numGameAccounts=\"1\"")
                "open" -> null
                else -> line(ms + 2_100, "Fatal login error received result=\"Status has no name. ($end)\"")
            },
        ).joinToString("\n")
    }

    private fun entry(n: Int, attempts: AttemptSummary?) = JournalEntry(
        startMs = start + n * 86_400_000L,
        appVersion = "2.3.3",
        level = "fill",
        scope = "all",
        loginFileWritten = false,
        attempts = attempts,
    )

    private fun summary(vararg results: String) = AttemptSummary(
        total = results.size,
        firstOk = results.indexOf("ok").takeIf { it >= 0 }?.plus(1),
        notes = results.map { AttemptNote(it, false, false, 30) },
    )

    // --- Übernahme aus der Connection.log ---

    @Test
    fun captureTakesTheAttemptsOfTheRunThatBelongsToTheStart() {
        val log = init(start + 5_000) + "\n" + attempt(start + 20_000, "14400") + "\n" + attempt(start + 70_000, "ok")
        val c = LoginHistory.capture(start, log)!!
        assertEquals(2, c.total)
        assertEquals(2, c.firstOk)
        assertEquals(listOf("e14400", "ok"), c.notes.map { it.result })
        assertEquals(listOf(0L, 50L), c.notes.map { it.gapSec })
        assertEquals(listOf(false, false), c.notes.map { it.usedToken })
        assertEquals(listOf(false, false), c.notes.map { it.authenticatorRequired })
    }

    @Test
    fun aFileThatGrowsIsReadForItsLastRunOnly() {
        val older = init(start - 3_600_000) + "\n" + attempt(start - 3_500_000, "14400") + "\n" + attempt(start - 3_400_000, "14400")
        val newer = init(start + 1_000) + "\n" + attempt(start + 10_000, "ok")
        val c = LoginHistory.capture(start, older + "\n" + newer)!!
        assertEquals(1, c.total)
        assertEquals(1, c.firstOk)
    }

    @Test
    fun aRunThatBeganBeforeTheStartIsNotThisStartsRun() {
        val log = init(start - 600_000) + "\n" + attempt(start - 590_000, "14400")
        assertNull(LoginHistory.capture(start, log))
    }

    @Test
    fun missingEmptyOrRunlessTextGivesNothing() {
        assertNull(LoginHistory.capture(start, null))
        assertNull(LoginHistory.capture(start, ""))
        assertNull(LoginHistory.capture(start, "[earlier lines omitted]\n" + attempt(start + 5_000, "ok")))
        assertNull(LoginHistory.capture(start, "garbage\nmore garbage"))
    }

    @Test
    fun aRunWithoutAnyAttemptIsCapturedAsEmpty() {
        val c = LoginHistory.capture(start, init(start + 3_000))!!
        assertEquals(0, c.total)
        assertNull(c.firstOk)
        assertEquals("none in the log", LoginHistory.attemptsText(c))
    }

    @Test
    fun aFileCutInTheMiddleOfAnAttemptKeepsTheOpenAttempt() {
        val cut = init(start + 1_000) + "\n" + attempt(start + 10_000, "ok") + "\n" + attempt(start + 90_000, "open")
        val c = LoginHistory.capture(start, cut)!!
        assertEquals(listOf("ok", "open"), c.notes.map { it.result })
        assertEquals(1, c.firstOk)
    }

    @Test
    fun withCaptureKeepsTheEntryWhenTheFileIsNotFromThisStart() {
        val e = entry(0, summary("ok"))
        assertEquals(e, LoginHistory.withCapture(e, init(start - 1_000_000)))
        assertEquals(e, LoginHistory.withCapture(e, null))
        val log = init(start + 1_000) + "\n" + attempt(start + 5_000, "14400")
        assertEquals(listOf("e14400"), LoginHistory.withCapture(e, log).attempts!!.notes.map { it.result })
    }

    @Test
    fun withLatestCaptureOnlyChangesTheNewestEntry() {
        val entries = listOf(entry(0, summary("ok")), entry(1, null))
        val log = init(start + 86_400_000L + 1_000) + "\n" + attempt(start + 86_400_000L + 9_000, "14400")
        val out = LoginHistory.withLatestCapture(entries, log)
        assertEquals(entries[0], out[0])
        assertEquals("e14400", out[1].attempts!!.notes.single().result)
        assertEquals(emptyList<JournalEntry>(), LoginHistory.withLatestCapture(emptyList(), log))
    }

    @Test
    fun onlyTheLastTwentyAttemptsAreKeptButTheCountAndTheFirstSuccessRefToAll() {
        var log = init(start + 1_000)
        for (i in 0 until 25) log += "\n" + attempt(start + 10_000 + i * 20_000L, if (i == 22) "ok" else "14400")
        val c = LoginHistory.capture(start, log)!!
        assertEquals(25, c.total)
        assertEquals(23, c.firstOk)
        assertEquals(20, c.notes.size)
        val text = LoginHistory.attemptsText(c)
        assertTrue(text.startsWith("25: #6 "))
        assertTrue(text.contains("#23 +20s ok"))
        assertTrue(text.endsWith("first ok #23"))
    }

    // --- Zeit ohne Jahr ---

    @Test
    fun stampsGetTheYearNextToTheReference() {
        val dec = LocalDateTime.of(2026, 12, 31, 23, 59, 30).atZone(zone).toInstant().toEpochMilli()
        val janNext = LocalDateTime.of(2027, 1, 1, 0, 0, 10).atZone(zone).toInstant().toEpochMilli()
        assertEquals(janNext, LoginHistory.toMillis("01/01 00:00:10", dec, zone))
        assertEquals(dec, LoginHistory.toMillis("12/31 23:59:30", janNext, zone))
        assertNull(LoginHistory.toMillis("13/40 99:00:00", dec, zone))
        assertNull(LoginHistory.toMillis("nonsense", dec, zone))
    }

    // --- Übersicht ---

    @Test
    fun overviewCountsSuccessOnFirstSecondAndLaterAttemptAndTheStartsWithoutSuccess() {
        val entries = listOf(
            entry(0, summary("ok")),
            entry(1, summary("e14400", "ok")),
            entry(2, summary("e14400", "e14400", "ok")),
            entry(3, summary("e14400", "e14400", "open")),
            entry(4, summary()),
            entry(5, null),
        )
        val o = LoginHistory.overview(entries)
        assertEquals(6, o.starts)
        assertEquals(5, o.withData)
        assertEquals(1, o.okFirst)
        assertEquals(1, o.okSecond)
        assertEquals(1, o.okLater)
        assertEquals(1, o.noSuccess)
        assertEquals(1, o.noAttempts)
        assertEquals(o.withData, o.okFirst + o.okSecond + o.okLater + o.noSuccess + o.noAttempts)
        assertEquals(entries[0].startMs, o.firstSuccessMs)
        assertEquals(entries[2].startMs, o.lastSuccessMs)
        assertEquals(entries[1].startMs, o.firstRejectedMs)
    }

    @Test
    fun verdictNeverWorked() {
        val entries = listOf(entry(0, summary("e14400")), entry(1, summary("e14400", "e14400")))
        assertEquals(Verdict.NEVER, LoginHistory.verdict(entries))
        assertTrue(LoginHistory.overviewLines(LoginHistory.overview(entries)).last().contains("never worked"))
    }

    @Test
    fun verdictWorkedBeforeAndFailingNow() {
        val entries = listOf(entry(0, summary("ok")), entry(1, summary("ok")), entry(2, summary("e14400")), entry(3, null))
        assertEquals(Verdict.WORKED_BEFORE, LoginHistory.verdict(entries))
        assertTrue(LoginHistory.overviewLines(LoginHistory.overview(entries)).last().contains("worked before, failing now"))
    }

    @Test
    fun verdictFailedBeforeAndWorksNow() {
        assertEquals(Verdict.FAILED_BEFORE, LoginHistory.verdict(listOf(entry(0, summary("e14400")), entry(1, summary("ok")))))
    }

    @Test
    fun verdictWorksAndNotEnoughData() {
        assertEquals(Verdict.WORKS, LoginHistory.verdict(listOf(entry(0, summary("ok")), entry(1, summary("e14400", "ok")))))
        assertEquals(Verdict.NO_DATA, LoginHistory.verdict(emptyList()))
        assertEquals(Verdict.NO_DATA, LoginHistory.verdict(listOf(entry(0, null), entry(1, summary()))))
    }

    @Test
    fun overviewTextsNameTheNumbersAndStayShortInCompactForm() {
        val o = LoginHistory.overview(listOf(entry(0, summary("e14400", "ok")), entry(1, summary("e14400"))))
        val lines = LoginHistory.overviewLines(o)
        assertEquals(4, lines.size)
        assertTrue(lines[1].contains("attempt 2: 1"))
        assertTrue(lines[2].contains("first success") && lines[2].contains("first rejected attempt"))
        val compact = LoginHistory.overviewCompact(o)
        assertFalse(compact.contains("\n"))
        assertTrue(compact.length < 330)
        assertTrue(compact.contains("ok at 1st/2nd/3rd+ attempt 0/1/0"))
    }

    @Test
    fun attemptsTextShowsEveryFieldOfTheListAndNothingElse() {
        val text = LoginHistory.attemptsText(
            AttemptSummary(2, 2, listOf(AttemptNote("e14400", false, false, 0), AttemptNote("ok", true, null, 41))),
        )
        assertEquals(
            "2: #1 error 14400 (usedToken false, authenticatorRequired false); #2 +41s ok (usedToken true, authenticatorRequired ?); first ok #2",
            text,
        )
        assertEquals("not captured", LoginHistory.attemptsText(null))
        assertEquals("?", LoginHistory.attemptsShort(null))
        assertEquals("3, first ok none", LoginHistory.attemptsShort(summary("e1", "e2", "e3")))
        assertNotNull(text)
    }
}
