package app.gamenative.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Felder der `Connection.log` mit Kontobezug oder Geheimnis verlieren ihren Wert; alles andere und die Anmeldung selbst bleiben lesbar. */
class ConnectionFieldsRedactionTest {
    private val redactor = ReportRedactor(listOf("player@example.com"), listOf("Tr0ub4dor&3xyz"))

    private fun conn(text: String) = redactor.redact("11/14 19:58:12.255  [N][Connection] $text", LogKind.CONNECTION)

    @Test
    fun accountFieldsLoseTheirValueEvenWhenShort() {
        val out = conn("Battle.net account list received accountList=\"1#A\" gameAccountName=\"WoW1\" battleTag=\"Testname#1234\" displayName=\"Testname\" name=\"Testname\" email=\"a\"")
        for (secret in listOf("1#A", "WoW1", "Testname", "1234", "email=\"a\"")) assertFalse(secret, out.contains(secret))
        assertEquals(6, Regex("\\[KONTO]").findAll(out).count())
    }

    @Test
    fun tokenFieldsLoseTheirValueEvenWhenShort() {
        val out = conn("Battle.net realm join requested joinToken=\"abc\" ticket=\"x1\" sessionKey=\"k\"")
        assertEquals("11/14 19:58:12.255  [N][Connection] Battle.net realm join requested joinToken=\"[GEHEIM]\" ticket=\"[GEHEIM]\" sessionKey=\"[GEHEIM]\"", out)
    }

    @Test
    fun theRealmAddressLosesItsIpButAHostnameStaysReadable() {
        assertEquals(
            "11/14 19:58:12.255  [N][Connection] Realm join realmAddress=\"[IP]:8085\" host=\"EU.actual.battle.net\"",
            conn("Realm join realmAddress=\"203.0.113.9:8085\" host=\"EU.actual.battle.net\""),
        )
        assertTrue(conn("Realm join realmAddress=\"[2001:db8::9]:8085\"").contains("realmAddress=\"[IP6]:8085\""))
    }

    @Test
    fun harmlessValuesOfTheSameKeysStayAndTheLoginFlowIsUntouched() {
        val lines = listOf(
            "Battle.net front connected connectionId=\"1\" title_id=\"5730135\" platform=\"WinA\" locale=\"enUS\" usedToken=\"false\"",
            "Battle.net logon completed result=\"ERROR_OK (0)\" numGameAccounts=\"1\"",
            "Battle.net logon form submission response received result=\"Status has no name. (14400)\" authenticatorRequired=\"false\" legalRequired=\"false\"",
            "Glue login started loginType=\"(enum)0\" launcherPortal=\"nullopt\" loginPortal=\"EU.actual.battle.net:1119\"",
            "Battle.net external challenge received usedSSO=\"false\" hasPayload=\"true\"",
        )
        for (l in lines) assertEquals(l, "11/14 19:58:12.255  [N][Connection] $l", conn(l))
    }

    @Test
    fun theRuleOnlyAppliesToTheConnectionLog() {
        val text = "info accountList=\"1#A\" name=\"Testname\""
        assertEquals(text, redactor.redact(text, LogKind.GENERAL))
    }

    @Test
    fun theParserStillReadsTheRedactedLogAndEmailAndPasswordAreStillBlackedOut() {
        val log = listOf(
            "11/14 19:58:00.000  [N][Connection] Client connection initialized",
            "11/14 19:58:10.000  [N][Connection] Glue login started loginType=\"(enum)0\" launcherPortal=\"nullopt\" loginPortal=\"EU.actual.battle.net:1119\"",
            "11/14 19:58:11.800  [N][Connection] Battle.net logon completed result=\"ERROR_OK (0)\" numGameAccounts=\"1\"",
            "11/14 19:58:12.000  [N][Connection] Battle.net account list received accountList=\"player@example.com\" user=\"Tr0ub4dor&3xyz\"",
        ).joinToString("\n")
        val out = redactor.redact(log, LogKind.CONNECTION)
        assertFalse(out.contains("player"))
        assertFalse(out.contains("Tr0ub4dor"))
        assertEquals(AttemptResult.Success, LoginAttempts.parse(out).single().result)
    }
}
