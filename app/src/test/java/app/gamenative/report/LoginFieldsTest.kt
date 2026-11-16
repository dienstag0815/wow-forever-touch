package app.gamenative.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Die zusätzlichen Felder, der Fehlerschritt und der Realm-Teil in `anmeldung.txt` (Format aus einem echten, erfolgreichen Lauf). */
class LoginFieldsTest {
    private fun line(time: String, text: String) = "11/14 $time  [N][Connection] $text"

    /** Der Ablauf einer erfolgreichen Anmeldung wie im echten Bericht; [form] ist das Ergebnis der Formular-Antwort, [realm] das der Realm-Anmeldung (null = nicht erreicht). */
    private fun flow(form: String = "ERROR_OK (0)", realm: String? = "ERROR_OK (0)", cut: Boolean = false, extra: String = ""): String {
        val ok = form == "ERROR_OK (0)"
        val lines = mutableListOf(
            line("19:58:00.000", "Client connection initialized"),
            line("19:58:05.000", "Battle.net services startup requested hasFrontInterface=\"false\" hasBackInterface=\"false\""),
            line("19:58:10.000", "Glue login started loginType=\"(enum)0\" launcherPortal=\"nullopt\" loginPortal=\"EU.actual.battle.net:1119\""),
            line("19:58:10.000", "Battle.net logon requested host=\"EU.actual.battle.net\" port=\"1119\""),
            line("19:58:10.500", "Battle.net front connected connectionId=\"1\" title_id=\"5730135\" platform=\"WinA\" locale=\"enUS\" usedToken=\"false\""),
            line("19:58:10.600", "Battle.net logon queue update received position=\"1\" estimatedQueueExitTime=\"1794587259\""),
            line("19:58:10.700", "Battle.net logon response received result=\"ERROR_OK (0)\""),
            line("19:58:11.000", "Battle.net external challenge received usedSSO=\"false\" hasPayload=\"true\"$extra"),
            line("19:58:11.000", "Battle.net logon form requested"),
            line("19:58:11.300", "Battle.net logon form received result=\"ERROR_OK (0)\" hasCaptcha=\"false\""),
            line("19:58:11.300", "Battle.net logon form submission requested"),
            line("19:58:11.400", "Battle.net SRP endpoint response received result=\"ERROR_OK (0)\""),
        )
        if (cut) return lines.joinToString("\n")
        lines += line("19:58:11.600", "Battle.net logon form submission response received result=\"$form\" authenticatorRequired=\"false\" legalRequired=\"false\"")
        if (!ok) {
            lines += line("19:58:11.600", "Fatal login error received result=\"$form\"")
            return lines.joinToString("\n")
        }
        lines += listOf(
            line("19:58:11.600", "Battle.net credentials submission requested"),
            line("19:58:11.700", "Battle.net credentials response received result=\"ERROR_OK (0)\""),
            line("19:58:11.800", "Battle.net logon completed result=\"ERROR_OK (0)\" numGameAccounts=\"1\""),
            line("19:58:12.255", "Realm list received count=\"3\""),
        )
        if (realm != null) {
            lines += line("19:58:13.100", "Authentication response received result=\"0\" hasSuccessInfo=\"true\" hasWaitInfo=\"false\"")
            lines += line("19:58:13.200", "Realm authentication completed result=\"$realm\"")
        }
        lines += line("19:58:40.000", "Battle.net front disconnected connectionId=\"1\" reason=\"ERROR_NETWORK_MODULE_SOCKET_CLOSED (1016)\"")
        return lines.joinToString("\n")
    }

    private fun one(text: String) = LoginAttempts.parse(text).single()

    @Test
    fun theSuccessfulFlowGivesEveryFieldTheLastStepAndTheRealmPart() {
        val a = one(flow())
        assertEquals(AttemptResult.Success, a.result)
        assertEquals("WinA", a.platform)
        assertEquals("enUS", a.locale)
        assertEquals("(enum)0", a.loginType)
        assertEquals(false, a.launcherPortalSet)
        assertEquals("EU", a.region)
        assertEquals(false, a.usedSso)
        assertEquals(true, a.hasPayload)
        assertEquals(false, a.hasCaptcha)
        assertEquals(false, a.legalRequired)
        assertEquals(1, a.numGameAccounts)
        assertEquals(1, a.queuePosition)
        assertEquals(LoginStep.REALM_AUTH, a.step)
        assertFalse(a.stepFailed)
        assertEquals(3200L, a.stepMs)
        assertEquals(RealmResult.Ok, a.realm)
        assertEquals(1800L, a.durationMs)
        assertEquals(
            "11/14 19:58:10 | run 1 | conn 1 | usedToken false | authenticatorRequired false | ok | 1.8 s | step realm_auth reached +3.2 s | realm_auth ok | " +
                "platform WinA | locale enUS | loginType (enum)0 | launcherPortal none | region EU | usedSSO false | hasPayload true | hasCaptcha false | " +
                "legalRequired false | gameAccounts 1 | queue 1",
            LoginAttempts.attemptLine(a),
        )
        assertEquals(
            "11/14 19:58:10 | run 1 | conn 1 | usedToken false | authenticatorRequired false | ok | 1.8 s | step realm_auth reached +3.2 s | WinA | portal none | sso false",
            LoginAttempts.attemptShort(a),
        )
    }

    @Test
    fun withoutTheRealmLinesTheLastStepIsLogonCompleted() {
        val a = one(flow(realm = null))
        assertEquals(LoginStep.LOGON_COMPLETED, a.step)
        assertEquals(1800L, a.stepMs)
        assertEquals(RealmResult.NotReached, a.realm)
        assertTrue(LoginAttempts.attemptLine(a).contains("realm_auth not reached"))
    }

    @Test
    fun anErrorInTheFormAnswerIsTheStepFormSubmission() {
        val a = one(flow(form = "Status has no name. (14400)"))
        assertEquals(AttemptResult.Error(14400), a.result)
        assertEquals(LoginStep.FORM_SUBMISSION, a.step)
        assertTrue(a.stepFailed)
        assertEquals(1600L, a.stepMs)
        assertEquals(RealmResult.NotReached, a.realm)
        assertTrue(LoginAttempts.attemptLine(a).contains("error 14400 (BLZ51914400) | 1.6 s | step form_submission failed +1.6 s | realm_auth not reached"))
    }

    @Test
    fun anErrorOnlyInTheRealmPartKeepsTheLoginOkAndNamesTheStepRealmAuth() {
        val a = one(flow(realm = "ERROR_BAD (4)"))
        assertEquals(AttemptResult.Success, a.result)
        assertEquals(LoginStep.REALM_AUTH, a.step)
        assertTrue(a.stepFailed)
        assertEquals(RealmResult.Error(4), a.realm)
        val line = LoginAttempts.attemptLine(a)
        assertTrue(line, line.contains("| ok | 1.8 s | step realm_auth failed +3.2 s | realm_auth error 4 |"))
        val stats = LoginAttempts.stats(listOf(a))
        assertEquals(mapOf(LoginStep.REALM_AUTH to 1), stats.errorSteps)
        assertEquals(1, stats.realmError)
        assertEquals(1, stats.successes)
    }

    @Test
    fun aCutAttemptHasNoStepAndNoResult() {
        val a = one(flow(cut = true))
        assertEquals(AttemptResult.Open, a.result)
        assertEquals(LoginStep.UNKNOWN, a.step)
        assertFalse(a.stepFailed)
        assertNull(a.stepMs)
        assertEquals(RealmResult.NotReached, a.realm)
        assertTrue(LoginAttempts.attemptLine(a).contains("no result in log | - | step unknown | realm_auth not reached"))
        // Die Felder aus den Zeilen davor sind trotzdem da.
        assertEquals("WinA", a.platform)
        assertEquals(true, a.hasPayload)
    }

    private fun attemptWithStepLine(stepLine: String): LoginAttempt = one(
        listOf(
            line("10:00:00.000", "Client connection initialized"),
            line("10:00:01.000", "Glue login started loginType=\"(enum)0\" launcherPortal=\"nullopt\" loginPortal=\"US.actual.battle.net:1119\""),
            line("10:00:02.500", stepLine),
        ).joinToString("\n"),
    )

    @Test
    fun everyStepLineWithAnErrorGivesItsFixedAbbreviation() {
        val bad = "result=\"Status has no name. (14500)\""
        val cases = listOf(
            "Battle.net logon response received $bad" to LoginStep.LOGON_RESPONSE,
            "Battle.net logon form received $bad hasCaptcha=\"false\"" to LoginStep.LOGON_FORM,
            "Battle.net SRP endpoint response received $bad" to LoginStep.SRP,
            "Battle.net logon form submission response received $bad authenticatorRequired=\"false\" legalRequired=\"false\"" to LoginStep.FORM_SUBMISSION,
            "Battle.net credentials response received $bad" to LoginStep.CREDENTIALS,
            "Battle.net logon completed $bad numGameAccounts=\"0\"" to LoginStep.LOGON_COMPLETED,
            "Realm authentication completed $bad" to LoginStep.REALM_AUTH,
            "Fatal login error received $bad" to LoginStep.FATAL,
        )
        for ((text, step) in cases) {
            val a = attemptWithStepLine(text)
            assertEquals(text, step, a.step)
            assertTrue(text, a.stepFailed)
            assertEquals(text, 1500L, a.stepMs)
            if (step != LoginStep.REALM_AUTH) assertEquals(text, AttemptResult.Error(14500), a.result)
        }
        // authenticator kommt nicht aus einer Schrittzeile, sondern aus der Antwort nach der Code-Abgabe (eigener Test in LoginAttemptsTest).
        assertEquals(LoginStep.ALL.toSet(), cases.map { it.second }.toSet() + LoginStep.UNKNOWN + LoginStep.AUTHENTICATOR)
    }

    @Test
    fun aFatalErrorAfterTheFormErrorDoesNotChangeTheStep() {
        val a = one(flow(form = "Status has no name. (14400)"))
        assertEquals(LoginStep.FORM_SUBMISSION, a.step)
        // Nur die Fatal-Zeile (kein Schritt davor): der Schritt ist fatal.
        val onlyFatal = attemptWithStepLine("Fatal login error received result=\"Status has no name. (14400)\"")
        assertEquals(LoginStep.FATAL, onlyFatal.step)
    }

    @Test
    fun aStepLineWithoutAResultCodeIsIgnoredAndNeverInvented() {
        val a = attemptWithStepLine("Battle.net logon response received result=\"strange\"")
        assertEquals(LoginStep.UNKNOWN, a.step)
        assertEquals(AttemptResult.Open, a.result)
    }

    @Test
    fun theHeaderCountsTheErrorsPerStepAndTheRealmPart() {
        val log = listOf(
            flow(form = "Status has no name. (14400)"),
            flow(),
            flow(realm = "ERROR_BAD (4)"),
            flow(realm = null),
            flow(cut = true),
        ).joinToString("\n")
        val header = LoginAttempts.headerLine(LoginAttempts.stats(LoginAttempts.parse(log)))
        assertTrue(header, header.contains("attempts 5, ok 3, errors: 14400 x1, open 1; error steps: form_submission x1, realm_auth x1; realm ok 1, error 1, not reached 3;"))
        val none = LoginAttempts.headerLine(LoginAttempts.stats(emptyList()))
        assertTrue(none, none.contains("error steps: none; realm ok 0, error 0, not reached 0;"))
    }

    // --- Unerlaubte Werte werden zu "?" ---

    @Test
    fun everyNewFieldWithAForbiddenValueBecomesAQuestionMark() {
        val text = listOf(
            line("11:00:00.000", "Client connection initialized"),
            line("11:00:01.000", "Glue login started loginType=\"(enum)x\" launcherPortal=\"\" loginPortal=\"player@example.com:1119\""),
            line("11:00:01.100", "Battle.net front connected connectionId=\"1\" platform=\"Win A!\" locale=\"en-US\" usedToken=\"false\""),
            line("11:00:01.200", "Battle.net logon queue update received position=\"12345678\""),
            line("11:00:01.300", "Battle.net external challenge received usedSSO=\"maybe\" hasPayload=\"1\""),
            line("11:00:01.400", "Battle.net logon form received result=\"ERROR_OK (0)\" hasCaptcha=\"yes\""),
            line("11:00:01.500", "Battle.net logon form submission response received result=\"ERROR_OK (0)\" authenticatorRequired=\"false\" legalRequired=\"\""),
            line("11:00:01.600", "Battle.net logon completed result=\"ERROR_OK (0)\" numGameAccounts=\"player\""),
        ).joinToString("\n")
        val a = one(text)
        assertNull(a.platform)
        assertNull(a.locale)
        assertNull(a.loginType)
        assertEquals("other", a.region)
        assertNull(a.usedSso)
        assertNull(a.hasPayload)
        assertNull(a.hasCaptcha)
        assertNull(a.legalRequired)
        assertNull(a.numGameAccounts)
        assertNull(a.queuePosition)
        val out = LoginAttempts.attemptLine(a)
        assertTrue(out, out.contains("platform ? | locale ? | loginType ? | launcherPortal none | region other | usedSSO ? | hasPayload ? | hasCaptcha ? | legalRequired ? | gameAccounts ? | queue -"))
        assertFalse(out.contains("player"))
        assertFalse(out.contains("example"))
    }

    @Test
    fun theLauncherPortalValueIsNeverPrintedOnlySetOrNone() {
        val set = one(flow().replace("launcherPortal=\"nullopt\"", "launcherPortal=\"https://portal.example/path?token=abc\""))
        assertEquals(true, set.launcherPortalSet)
        val out = LoginAttempts.attemptLine(set)
        assertTrue(out.contains("launcherPortal set"))
        assertFalse(out.contains("portal.example"))
        assertFalse(out.contains("token"))
        // Ohne das Feld: unbekannt.
        assertNull(one(flow().replace(" launcherPortal=\"nullopt\"", "")).launcherPortalSet)
    }

    @Test
    fun theRegionIsOnlyOneOfTheKnownAbbreviationsOtherwiseOther() {
        for ((portal, expected) in listOf(
            "US.actual.battle.net:1119" to "US", "eu.actual.battle.net:1119" to "EU", "KR.actual.battle.net:1119" to "KR",
            "TW.actual.battle.net:1119" to "TW", "CN.actual.battle.net.cn:1119" to "CN", "XX.actual.battle.net:1119" to "other", "10.0.0.1:1119" to "other", "localhost" to "other",
        )) {
            val a = one(flow().replace("EU.actual.battle.net:1119", portal))
            assertEquals(portal, expected, a.region)
        }
        // Ohne loginPortal gilt der Host der Zeile "logon requested".
        val byHost = one(flow().replace(" loginPortal=\"EU.actual.battle.net:1119\"", "").replace("host=\"EU.actual", "host=\"KR.actual"))
        assertEquals("KR", byHost.region)
    }

    @Test
    fun theLengthLimitsOfTheNewFields() {
        fun withValues(platform: String, loginType: String, accounts: String, position: String, locale: String = "enUS"): LoginAttempt = one(
            flow()
                .replace("platform=\"WinA\"", "platform=\"$platform\"").replace("loginType=\"(enum)0\"", "loginType=\"$loginType\"")
                .replace("numGameAccounts=\"1\"", "numGameAccounts=\"$accounts\"").replace("position=\"1\"", "position=\"$position\"")
                .replace("locale=\"enUS\"", "locale=\"$locale\""),
        )
        val max = withValues("Mac1", "(enum)99", "99", "999999")
        assertEquals("Mac1", max.platform)
        assertEquals("(enum)99", max.loginType)
        assertEquals(99, max.numGameAccounts)
        assertEquals(999999, max.queuePosition)
        val over = withValues("Mac12", "(enum)100", "100", "1000000", locale = "enUSA")
        assertNull(over.platform)
        assertNull(over.loginType)
        assertNull(over.numGameAccounts)
        assertNull(over.queuePosition)
        assertNull(over.locale)
        assertEquals(0, withValues("WinA", "(enum)0", "0", "0").numGameAccounts)
        assertNull(withValues("Win", "(enum)0", "1", "1").platform)
    }

    @Test
    fun theShortLineIsShorterThanTheFullLineAndHasTheFourNewParts() {
        val a = one(flow())
        val short = LoginAttempts.attemptShort(a)
        val full = LoginAttempts.attemptLine(a)
        assertTrue(short.length < full.length)
        for (part in listOf("step realm_auth reached", "| WinA |", "portal none", "sso false")) assertTrue(part, short.contains(part))
    }

    @Test
    fun theFileListsEveryColumnAndNeverARealmAddressOrAnyOtherFreeText() {
        val log = flow(extra = " realmAddress=\"203.0.113.9:8085\" accountList=\"player@example.com\"")
        val report = LoginAttempts.report(log)
        assertTrue(report.contains("realm_auth | platform | locale | loginType | launcherPortal (set/none, value never shown) | region"))
        for (secret in listOf("203.0.113.9", "8085", "player", "example", "realmAddress", "accountList", "estimatedQueueExitTime", "1794587259")) assertFalse(secret, report.contains(secret))
    }
}
