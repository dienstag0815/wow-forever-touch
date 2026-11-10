package app.gamenative.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportRedactorTest {
    private val email = "Spieler.Test@example.org"
    private val password = "Tr0ub4dor&3x!"
    private val redactor = ReportRedactor(listOf(email), listOf(password))

    @Test
    fun savedEmailIsReplacedExactlyAndIgnoringCase() {
        val out = redactor.redact("login Spieler.Test@example.org ok, again spieler.test@example.org done")
        assertEquals("login [EMAIL] ok, again [EMAIL] done", out)
    }

    @Test
    fun savedEmailIsReplacedWhenUrlEncoded() {
        val out = redactor.redact("GET /x?user=Spieler.Test%40example.org&b=1 und spieler.test%40example.org")
        assertFalse(out.contains("example.org"))
        assertFalse(out.contains("%40"))
        assertTrue(out.contains("[EMAIL]"))
    }

    @Test
    fun anyOtherEmailIsReplacedByTheGeneralRule() {
        val out = redactor.redact("support: someone.else@mail.example.com und x%40y.org")
        assertEquals("support: [EMAIL] und [EMAIL]", out)
    }

    @Test
    fun savedPasswordIsReplacedInPlainFormAndIgnoringCase() {
        val out = redactor.redact("typed Tr0ub4dor&3x! and tr0ub4dor&3X!")
        assertEquals("typed [PASSWORT] and [PASSWORT]", out)
    }

    @Test
    fun savedPasswordIsReplacedWhenUrlEncodedOrJsonEscaped() {
        val form = redactor.redact("a=Tr0ub4dor%263x%21&b=2")
        assertEquals("a=[PASSWORT]&b=2", form)
        val spaced = ReportRedactor(passwords = listOf("my pass 123")).redact("p=my+pass+123 q=my%20pass%20123")
        assertEquals("p=[PASSWORT] q=[PASSWORT]", spaced)
        val quoted = ReportRedactor(passwords = listOf("ab\"cd\\ef1")).redact("""{"p":"ab\"cd\\ef1"}""")
        assertFalse(quoted.contains("cd"))
    }

    @Test
    fun noPasswordCharacterSurvivesInAMixedLog() {
        val log = """
            12:00:01 user=$email
            12:00:02 password field got Tr0ub4dor&3x!
            12:00:03 body=email%3D&pw=Tr0ub4dor%263x%21
        """.trimIndent()
        val out = redactor.redact(log)
        assertFalse(out.contains("Tr0ub4dor"))
        assertFalse(out.contains("3x"))
        assertFalse(out.contains("example.org"))
    }

    @Test
    fun veryShortSecretsAreNotSearchedSoLogsStayReadable() {
        val out = ReportRedactor(passwords = listOf("abc")).redact("abc is in the alphabet")
        assertEquals("abc is in the alphabet", out)
    }

    @Test
    fun configWtfAccountLinesLoseTheirValues() {
        val wtf = """
            SET gxApi "D3D11"
            SET accountName "Spieler.Test@example.org"
            SET accountList "!ACCOUNT1|ACCOUNT2"
            SET lastSelectedCharacter "Hero"
            SET lastSelectedRealm "SomeRealm"
            SET RenderScale "1"
        """.trimIndent()
        val out = redactor.redact(wtf, LogKind.CONFIG_WTF)
        assertTrue(out.contains("SET accountName \"[KONTO]\""))
        assertTrue(out.contains("SET accountList \"[KONTO]\""))
        assertTrue(out.contains("SET lastSelectedCharacter \"[KONTO]\""))
        assertTrue(out.contains("SET lastSelectedRealm \"[KONTO]\""))
        assertTrue(out.contains("SET gxApi \"D3D11\""))
        assertTrue(out.contains("SET RenderScale \"1\""))
        assertFalse(out.contains("Hero"))
        assertFalse(out.contains("ACCOUNT1"))
    }

    @Test
    fun configWtfKeepsWindowsLineBreaks() {
        val out = ReportRedactor().redact("SET accountName \"x\"\r\nSET a \"1\"\r\n", LogKind.CONFIG_WTF)
        assertEquals("SET accountName \"[KONTO]\"\r\nSET a \"1\"\r\n", out)
    }

    @Test
    fun longValuesAfterTokenLikeKeysAreHidden() {
        val log = """
            session_token=AbCdEf0123456789AbCdEf0123456789
            "access_token":"eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dBjftJeZ4CVPmB92K27uhbUJU1p1r_wW1gFWFOEjXk"
            Authorization: Bearer 0123456789abcdefABCDEF
            GET /login?ticket=1a2b3c4d5e6f7a8b9c0d&lang=de
            sessionId = "9f8e7d6c5b4a39281706"
        """.trimIndent()
        val out = redactor.redact(log)
        assertFalse(out.contains("AbCdEf0123456789"))
        assertFalse(out.contains("eyJhbGci"))
        assertFalse(out.contains("0123456789abcdefABCDEF"))
        assertFalse(out.contains("1a2b3c4d5e6f7a8b9c0d"))
        assertFalse(out.contains("9f8e7d6c5b4a39281706"))
        assertTrue(out.contains("session_token=[GEHEIM]"))
        assertTrue(out.contains("Authorization: Bearer [GEHEIM]"))
        assertTrue(out.contains("ticket=[GEHEIM]&lang=de"))
    }

    @Test
    fun ordinaryWordsAfterSessionKeysStayReadable() {
        val out = redactor.redact("session=connected_to_realm auth=ok token=none")
        assertEquals("session=connected_to_realm auth=ok token=none", out)
    }

    @Test
    fun loginTicketsAreHiddenEverywhere() {
        val out = redactor.redact("login with US-0123456789abcdef0123456789abcdef-1234567890 sent")
        assertEquals("login with [GEHEIM] sent", out)
    }

    @Test
    fun connectionLogAlsoHidesLongKeylessValues() {
        val hex = "0123456789abcdef0123456789abcdef0123"
        val b64 = "QWxhZGRpbjpvcGVuIHNlc2FtZTEyMzQ1Njc4OTBhYmNkZWZnaGlqa2xtbg=="
        val log = "reply hash $hex blob $b64 ready"
        val conn = redactor.redact(log, LogKind.CONNECTION)
        assertEquals("reply hash [GEHEIM] blob [GEHEIM] ready", conn)
        // Andere Logs behalten solche Werte (Dateihashe in Wine-Logs usw.).
        assertEquals(log, redactor.redact(log, LogKind.GENERAL))
    }

    @Test
    fun machineSecretsAreReplacedLiterally() {
        val guid = "3f0c1d2e-aaaa-bbbb-cccc-0123456789ab"
        val r = ReportRedactor(extraSecrets = listOf(guid, "MY-PC-NAME", "ab"))
        val out = r.redact("MachineGuid=$guid host my-pc-name and ab")
        assertEquals("MachineGuid=[GEHEIM] host [GEHEIM] and ab", out)
    }

    @Test
    fun emptyRedactorStillHidesAnyEmail() {
        assertEquals("[EMAIL]", ReportRedactor().redact("a.b@example.org"))
    }

    @Test
    fun hugeRunsOfWordCharactersAreRedactedFast() {
        val big = "a".repeat(200_000) + " " + "token=" + "9".repeat(200_000)
        val start = System.nanoTime()
        val out = redactor.redact(big, LogKind.CONNECTION)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("zu langsam: $ms ms", ms < 5_000)
        assertFalse(out.contains("9999999999"))
    }

    @Test
    fun accountFolderInWtfPathIsRedacted() {
        val out = ReportRedactor().redact("Pfad WTF/Account/ACC1/SavedVariables/TouchForever.lua und WTF\\Account\\ACC1\\x")
        val konto = ReportRedactor.ACCOUNT
        assertEquals("Pfad WTF/Account/$konto/SavedVariables/TouchForever.lua und WTF\\Account\\$konto\\x", out)
    }

    @Test
    fun ownMachineIdentityIsRedacted() {
        val own = listOf("2f6c1a9e-4b7d-4e2a-9c3f-8d1e5a7b6c40", "DESKTOP-K7Q2XM9", "A1B2C3D4", "00330-81234-56789-AAOEM")
        val out = ReportRedactor(extraSecrets = own).redact(
            "ProductId 00330-81234-56789-AAOEM, Host DESKTOP-K7Q2XM9, Seriennummer A1B2C3D4, Guid 2f6c1a9e-4b7d-4e2a-9c3f-8d1e5a7b6c40 ende",
        )
        assertEquals("ProductId [GEHEIM], Host [GEHEIM], Seriennummer [GEHEIM], Guid [GEHEIM] ende", out)
    }
}
