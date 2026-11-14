package app.gamenative.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginAttemptsTest {
    private fun line(time: String, text: String) = "$time  [N][Connection] $text"

    private fun init(time: String) = line(time, "Client connection initialized")

    private fun resultOf(end: String) = if (end == "ok" || end == "open") "ERROR_OK (0)" else "Status has no name. ($end)"

    /** Ein Versuch im belegten Format (Kommentare in den Issues #57/#28), Ende: "ok", "open" oder ein Fehlercode. */
    private fun attempt(day: String, start: String, form: String, end: String, conn: Int = 1, token: String = "false", auth: String = "false"): String =
        listOf(
            line("$day $start", "Glue login started loginType=\"(enum)0\" launcherPortal=\"nullopt\" loginPortal=\"EU.actual.battle.net:1119\""),
            line("$day $start", "Battle.net logon requested host=\"EU.actual.battle.net\" port=\"1119\""),
            line("$day $start", "Battle.net front connected connectionId=\"$conn\" title_id=\"5730135\" platform=\"WinA\" locale=\"enUS\" usedToken=\"$token\""),
            line("$day $start", "Battle.net logon response received result=\"ERROR_OK (0)\""),
            line("$day $start", "Battle.net logon form submission requested"),
            line("$day $form", "Battle.net logon form submission response received result=\"${resultOf(end)}\" authenticatorRequired=\"$auth\" legalRequired=\"false\""),
            when (end) {
                "ok" -> line("$day $form", "Battle.net logon completed result=\"ERROR_OK (0)\" numGameAccounts=\"1\"")
                "open" -> ""
                else -> line("$day $form", "Fatal login error received result=\"Status has no name. ($end)\"")
            },
        ).filter { it.isNotEmpty() }.joinToString("\n")

    @Test
    fun successfulAttemptFromRealFormat() {
        val log = init("11/13 20:15:51.495") + "\n" + attempt("11/13", "20:16:05.401", "20:16:08.953", "ok", conn = 1)
        val a = LoginAttempts.parse(log).single()
        assertEquals("11/13 20:16:05", a.time)
        assertEquals(1, a.run)
        assertEquals(1, a.connectionId)
        assertEquals(false, a.usedToken)
        assertEquals(false, a.authenticatorRequired)
        assertEquals(AttemptResult.Success, a.result)
        assertEquals(3552L, a.durationMs)
    }

    /** Auszüge aus den Kommentaren der Issues #57 und #28 (Zeilen unverändert; ein fehlgeschlagener und ein erfolgreicher Lauf). */
    private val realFailed = """
11/13 19:12:54.600  [N][Connection] Client connection initialized
11/13 19:13:33.123  [N][Connection] Battle.net services startup requested hasFrontInterface="false" hasBackInterface="false"
11/13 19:27:41.055  [N][Connection] Login error state cleared from glue script
11/13 19:27:41.055  [N][Connection] Glue login started loginType="(enum)0" launcherPortal="nullopt" loginPortal="EU.actual.battle.net:1119"
11/13 19:27:41.055  [N][Connection] Glue login state reset requested
11/13 19:27:41.055  [N][Connection] Battle.net login session initialization requested
11/13 19:27:41.055  [N][Connection] Battle.net logon requested host="EU.actual.battle.net" port="1119"
11/13 19:27:41.171  [N][Connection] Battle.net login waiting for server response
11/13 19:27:41.656  [N][Connection] Battle.net front connected connectionId="1" title_id="5730135" platform="WinA" locale="enUS" usedToken="false"
11/13 19:27:41.656  [N][Connection] Battle.net login waiting for server response
11/13 19:27:41.734  [N][Connection] Battle.net logon queue update received position="1" estimatedQueueExitTime="1794587259"
11/13 19:27:41.734  [N][Connection] Battle.net logon response received result="ERROR_OK (0)"
11/13 19:27:42.018  [N][Connection] Battle.net external challenge received usedSSO="false" hasPayload="true"
11/13 19:27:42.018  [N][Connection] Battle.net logon form requested
11/13 19:27:42.018  [N][Connection] Battle.net login waiting for server response
11/13 19:27:42.452  [N][Connection] Battle.net logon form received result="ERROR_OK (0)" hasCaptcha="false"
11/13 19:27:42.452  [N][Connection] Battle.net logon form submission requested
11/13 19:27:42.460  [N][Connection] Battle.net login waiting for server response
11/13 19:27:42.568  [N][Connection] Battle.net SRP endpoint response received result="ERROR_OK (0)"
11/13 19:27:42.568  [N][Connection] Battle.net login waiting for server response
11/13 19:27:42.627  [N][Connection] Battle.net login waiting for server response
11/13 19:27:42.801  [N][Connection] Battle.net logon form submission response received result="Status has no name. (14400)" authenticatorRequired="false" legalRequired="false"
11/13 19:27:42.801  [N][Connection] Fatal login error received result="Status has no name. (14400)"
""".trim()

    private val realOk = """
11/13 20:15:51.495  [N][Connection] Client connection initialized
11/13 20:15:57.029  [N][Connection] Battle.net services startup requested hasFrontInterface="false" hasBackInterface="false"
11/13 20:16:05.401  [N][Connection] Glue login started loginType="(enum)0" launcherPortal="nullopt" loginPortal="EU.actual.battle.net:1119"
11/13 20:16:05.401  [N][Connection] Glue login state reset requested
11/13 20:16:05.401  [N][Connection] Battle.net login session initialization requested
11/13 20:16:05.401  [N][Connection] Battle.net logon requested host="EU.actual.battle.net" port="1119"
11/13 20:16:06.613  [N][Connection] Battle.net front connected connectionId="1" title_id="5730135" platform="WinA" locale="enUS" usedToken="false"
11/13 20:16:06.842  [N][Connection] Battle.net logon queue update received position="2" estimatedQueueExitTime="1794590164"
11/13 20:16:06.842  [N][Connection] Battle.net logon queue updated
11/13 20:16:06.842  [N][Connection] Battle.net logon response received result="ERROR_OK (0)"
11/13 20:16:07.842  [N][Connection] Battle.net external challenge received usedSSO="false" hasPayload="true"
11/13 20:16:07.842  [N][Connection] Battle.net logon form requested
11/13 20:16:08.340  [N][Connection] Battle.net logon form received result="ERROR_OK (0)" hasCaptcha="false"
11/13 20:16:08.340  [N][Connection] Battle.net logon form submission requested
11/13 20:16:08.496  [N][Connection] Battle.net SRP endpoint response received result="ERROR_OK (0)"
11/13 20:16:08.953  [N][Connection] Battle.net logon form submission response received result="ERROR_OK (0)" authenticatorRequired="false" legalRequired="false"
11/13 20:16:08.953  [N][Connection] Battle.net credentials submission requested
11/13 20:16:09.070  [N][Connection] Battle.net credentials response received result="ERROR_OK (0)"
11/13 20:16:09.586  [N][Connection] Battle.net logon completed result="ERROR_OK (0)" numGameAccounts="1"
11/13 20:16:09.720  [N][Connection] Battle.net account service response received responseType="BNetInfo" responseTypeValue="1" code="ERROR_OK (0)"
11/13 20:16:09.803  [N][Connection] Battle.net complete login requested hasGameAccount="true"
""".trim()

    @Test
    fun realExcerptsFromTheIssuesGiveTheExpectedLines() {
        val failed = LoginAttempts.parse(realFailed).single()
        assertEquals(
            "11/13 19:27:41 | run 1 | conn 1 | usedToken false | authenticatorRequired false | error 14400 (BLZ51914400) | 1.7 s | step form_submission failed +1.7 s | realm_auth not reached | platform WinA | locale enUS | loginType (enum)0 | launcherPortal none | region EU | usedSSO false | hasPayload true | hasCaptcha false | legalRequired false | gameAccounts ? | queue 1",
            LoginAttempts.attemptLine(failed),
        )
        val ok = LoginAttempts.parse(realOk).single()
        assertEquals(
            "11/13 20:16:05 | run 1 | conn 1 | usedToken false | authenticatorRequired false | ok | 4.2 s | step logon_completed reached +4.2 s | realm_auth not reached | platform WinA | locale enUS | loginType (enum)0 | launcherPortal none | region EU | usedSSO false | hasPayload true | hasCaptcha false | legalRequired false | gameAccounts 1 | queue 2",
            LoginAttempts.attemptLine(ok),
        )
        // Beide Läufe in einer Datei: zwei Läufe, ein Fehler, ein Erfolg, der Fehler kam zuerst.
        val both = LoginAttempts.stats(LoginAttempts.parse(realFailed + "\n" + realOk))
        assertEquals(2, both.total)
        assertEquals(1, both.successes)
        assertEquals(mapOf(14400 to 1), both.errorsByCode)
        assertFalse(both.successThenError)
    }

    @Test
    fun error14400() {
        val log = init("11/11 21:13:11.807") + "\n" + attempt("11/11", "21:13:36.758", "21:13:40.251", "14400")
        val a = LoginAttempts.parse(log).single()
        assertEquals(AttemptResult.Error(14400), a.result)
        assertEquals(3493L, a.durationMs)
        assertEquals("error 14400 (BLZ51914400)", LoginAttempts.resultText(a.result))
    }

    @Test
    fun error14500IsReadLikeTheOthers() {
        val log = init("11/12 09:13:53.498") + "\n" + attempt("11/12", "09:15:13.802", "09:15:16.371", "14500")
        val a = LoginAttempts.parse(log).single()
        assertEquals(AttemptResult.Error(14500), a.result)
        assertEquals("error 14500 (BLZ51914500)", LoginAttempts.resultText(a.result))
    }

    @Test
    fun severalAttemptsInOneRunAndSeveralRuns() {
        val log = listOf(
            init("11/12 10:00:00.000"),
            attempt("11/12", "10:00:10.000", "10:00:12.000", "ok", conn = 1),
            attempt("11/12", "10:05:10.000", "10:05:11.500", "14400", conn = 2),
            attempt("11/12", "10:06:10.000", "10:06:11.000", "14400", conn = 3),
            init("11/12 11:00:00.000"),
            attempt("11/12", "11:00:10.000", "11:00:12.000", "14400", conn = 1),
        ).joinToString("\n")
        val attempts = LoginAttempts.parse(log)
        assertEquals(4, attempts.size)
        assertEquals(listOf(1, 1, 1, 2), attempts.map { it.run })
        assertEquals(listOf(1, 2, 3, 1), attempts.map { it.connectionId })
        val stats = LoginAttempts.stats(attempts)
        assertEquals(4, stats.total)
        assertEquals(1, stats.successes)
        assertEquals(mapOf(14400 to 3), stats.errorsByCode)
        assertTrue(stats.firstOkThenRejectedInRun)
        assertTrue(stats.successThenError)
    }

    @Test
    fun firstOkFlagIsNoWhenTheRejectedOnesComeFirstOrInOtherRuns() {
        val log = listOf(
            init("11/12 10:00:00.000"),
            attempt("11/12", "10:00:10.000", "10:00:12.000", "14400"),
            init("11/12 11:00:00.000"),
            attempt("11/12", "11:00:10.000", "11:00:12.000", "ok"),
        ).joinToString("\n")
        val stats = LoginAttempts.stats(LoginAttempts.parse(log))
        assertFalse(stats.firstOkThenRejectedInRun)
        assertFalse(stats.successThenError)
        // Erfolg in Lauf 1, Ablehnung erst im nächsten Lauf: der Lauf-Marker bleibt nein, der Marker über Läufe ja.
        val across = listOf(
            init("11/12 10:00:00.000"),
            attempt("11/12", "10:00:10.000", "10:00:12.000", "ok"),
            init("11/12 11:00:00.000"),
            attempt("11/12", "11:00:10.000", "11:00:12.000", "14400"),
        ).joinToString("\n")
        val s2 = LoginAttempts.stats(LoginAttempts.parse(across))
        assertFalse(s2.firstOkThenRejectedInRun)
        assertTrue(s2.successThenError)
    }

    @Test
    fun truncatedFileEndsWithOpenAttempt() {
        val full = init("11/12 10:00:00.000") + "\n" + attempt("11/12", "10:00:10.000", "10:00:12.000", "open", auth = "true")
        val a = LoginAttempts.parse(full + "\n11/12 10:00:13.0").single()
        assertEquals(AttemptResult.Open, a.result)
        assertEquals(true, a.authenticatorRequired)
        assertNull(a.durationMs)
        assertEquals(1, LoginAttempts.stats(listOf(a)).open)
    }

    @Test
    fun fileCutInTheMiddleOfAnAttemptStillGivesTheLaterLines() {
        val log = "[earlier lines omitted]\n" +
            line("11/12 10:00:10.100", "Battle.net front connected connectionId=\"2\" title_id=\"5730135\" platform=\"WinA\" locale=\"enUS\" usedToken=\"false\"") + "\n" +
            line("11/12 10:00:12.000", "Fatal login error received result=\"Status has no name. (14400)\"")
        val a = LoginAttempts.parse(log).single()
        assertEquals(0, a.run)
        assertEquals(2, a.connectionId)
        assertEquals(AttemptResult.Error(14400), a.result)
        assertTrue(LoginAttempts.attemptLine(a).contains("run ?"))
    }

    @Test
    fun emptyAndUnknownText() {
        assertTrue(LoginAttempts.parse("").isEmpty())
        assertTrue(LoginAttempts.parse("\n\n").isEmpty())
        val unknown = "hello\n11/12 10:00:00.000  [N][Connection] Something else entirely foo=\"bar\"\nrandom [N][Connection] text"
        assertTrue(LoginAttempts.parse(unknown).isEmpty())
        val report = LoginAttempts.report("")
        assertTrue(report.contains("attempts 0"))
        assertTrue(report.contains("no login attempts found"))
        assertTrue(LoginAttempts.report(null).contains("attempts 0"))
    }

    @Test
    fun timeAcrossMidnightGivesAPositiveDuration() {
        val log = attempt("11/12", "23:59:59.000", "23:59:59.500", "open") + "\n" +
            line("11/13 00:00:01.000", "Fatal login error received result=\"Status has no name. (14400)\"")
        val a = LoginAttempts.parse(log).single()
        assertEquals(2000L, a.durationMs)
    }

    @Test
    fun fieldsWithOtherContentAreDropped() {
        val log = listOf(
            line("11/12 10:00:00.000", "Glue login started loginType=\"(enum)0\""),
            line("11/12 10:00:00.100", "Battle.net front connected connectionId=\"1\" platform=\"WinA\" usedToken=\"player@example.com\""),
            line("11/12 10:00:01.000", "Battle.net logon form submission response received result=\"ERROR_OK (0)\" authenticatorRequired=\"tok_9f8e7d6c5b\""),
        ).joinToString("\n")
        val a = LoginAttempts.parse(log).single()
        assertNull(a.usedToken)
        assertNull(a.authenticatorRequired)
        assertTrue(LoginAttempts.attemptLine(a).contains("usedToken ?"))
    }

    @Test
    fun onlyTheAllowedFieldsAppearInTheReport() {
        val secretLine = line(
            "11/12 10:00:00.200",
            "Battle.net logon requested host=\"EU.actual.battle.net\" port=\"1119\" accountName=\"someone@example.com\" token=\"abcdef0123456789abcdef\" ip=\"10.20.30.40\"",
        )
        val log = init("11/12 09:59:00.000") + "\n" +
            line("11/12 10:00:00.000", "Glue login started loginType=\"(enum)0\" launcherPortal=\"nullopt\" loginPortal=\"EU.actual.battle.net:1119\"") + "\n" +
            secretLine + "\n" +
            line("11/12 10:00:00.300", "Battle.net front connected connectionId=\"1\" title_id=\"5730135\" platform=\"WinA\" locale=\"enUS\" usedToken=\"false\" session=\"S3cr3tS3cr3tS3cr3t\"") + "\n" +
            line("11/12 10:00:01.300", "Fatal login error received result=\"Status has no name. (14400)\" email=\"someone@example.com\" addr=\"10.20.30.40\"")
        val report = LoginAttempts.report(log)
        for (forbidden in listOf("someone", "example.com", "token", "abcdef0123", "10.20.30.40", "S3cr3t", "battle.net", "EU.actual")) {
            assertFalse("$forbidden steht im Bericht", report.contains(forbidden))
        }
        assertTrue(report, report.contains("11/12 10:00:00 | run 1 | conn 1 | usedToken false | authenticatorRequired ? | error 14400 (BLZ51914400) | 1.3 s"))
    }

    @Test
    fun onlyTheLastTwentyAreShownButAllCounted() {
        val log = buildString {
            appendLine(init("11/12 08:00:00.000"))
            for (i in 0 until 25) {
                val minute = "%02d".format(i)
                appendLine(attempt("11/12", "09:$minute:00.000", "09:$minute:01.000", "14400", conn = i + 1))
            }
        }
        val report = LoginAttempts.report(log)
        assertTrue(report.contains("last 20 of 25 found"))
        assertTrue(report.contains("attempts 25"))
        assertEquals(20, report.lines().count { it.startsWith("11/12 ") })
        assertFalse(report.contains("11/12 09:00:00"))
        assertTrue(report.contains("11/12 09:24:00"))
    }

    @Test
    fun headerLineNamesTheErrorsPerCode() {
        val log = listOf(
            attempt("11/12", "10:00:00.000", "10:00:01.000", "14400"),
            attempt("11/12", "10:10:00.000", "10:10:01.000", "14500"),
            attempt("11/12", "10:20:00.000", "10:20:01.000", "14400"),
        ).joinToString("\n")
        val header = LoginAttempts.headerLine(LoginAttempts.stats(LoginAttempts.parse(log)))
        assertTrue(header, header.contains("attempts 3, ok 0, errors: 14400 x2, 14500 x1"))
        assertTrue(header.contains("first login in a run ok, later rejected: no"))
    }

    @Test
    fun blzNameOnlyForTheKnownRange() {
        assertEquals("BLZ51914400", LoginAttempts.blzName(14400))
        assertNull(LoginAttempts.blzName(5))
        assertNull(LoginAttempts.blzName(123456))
    }
}
