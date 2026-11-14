package app.gamenative.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportSummaryTest {
    private val guid = listOf("5f0d9a42", "77c1", "4c1e", "9b0a", "123456789abc").joinToString("-")
    private val email = "player@example.com"
    private val phrase = "Tr0ub4dor&3xyz"

    private fun input(
        model: String = "Samsung SM-F000",
        attempts: List<String> = (1..8).map { "11/12 10:0$it:00 | run 1 | conn $it | usedToken false | authenticatorRequired false | error 14400 (BLZ51914400) | 1.2 s" },
        journal: List<String> = (1..5).map { "2026-11-14 10:0$it:00 | app 2.3.3 | level fill" },
    ) = SummaryInput(
        device = model,
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
        attemptsHeader = "attempts 8, ok 0, errors: 14400 x8, open 0; first login in a run ok, later rejected: no; ok then rejected (any run): no",
        lastAttempts = attempts,
        lastJournal = journal,
    )

    private val redactor = ReportRedactor(listOf(email), listOf(phrase), listOf(guid))

    @Test
    fun containsTheRequiredPartsAndLimitsTheLines() {
        val text = ReportSummary.build(input(), redactor)
        for (needle in listOf(
            "Device: Samsung", "Android: 17", "Chip: Qualcomm", "App: 2.3.3-fold9.1", "Client build: 11.2.7.64978", "Region: Americas",
            "Login level: fill", "fingerprint ab12cd34", "changes 0", "System view changes: 1", "Login attempts: attempts 8",
        )) {
            assertTrue("fehlt: $needle", text.contains(needle))
        }
        assertEquals(5, text.lines().count { it.contains("| usedToken") })
        assertTrue(text.contains("conn 8 |"))
        assertFalse(text.contains("conn 3 |"))
        assertEquals(3, text.lines().count { it.startsWith("2026-11-14") })
        assertTrue(text.contains("10:05:00"))
        assertFalse(text.contains("10:02:00"))
    }

    @Test
    fun neverLongerThanTheLimitEvenWithHugeInput() {
        val huge = "x".repeat(900)
        val text = ReportSummary.build(input(attempts = List(5) { huge }, journal = List(3) { huge }), redactor)
        assertTrue(text.length <= ReportSummary.MAX_CHARS)
        assertTrue(text.endsWith("..."))
        assertEquals("short", ReportSummary.fit("short", 10))
    }

    @Test
    fun aNormalSummaryFitsWithoutCutting() {
        val text = ReportSummary.build(input(), redactor)
        assertTrue("Länge ${text.length}", text.length <= ReportSummary.MAX_CHARS)
        assertFalse(text.endsWith("..."))
    }

    @Test
    fun noEmailPasswordAddressOrFullIdentityGetsThrough() {
        val dirty = input(
            model = "Phone $email",
            attempts = listOf(
                "11/12 10:00:00 | pw=$phrase | host 10.20.30.40",
                "11/12 10:00:01 | guid $guid | mail ${email.uppercase()}",
            ),
            journal = listOf("2026-11-14 | wlan 10.0.0.12 and 192.168.1.20:1119 | $phrase"),
        )
        val text = ReportSummary.build(dirty, redactor)
        assertFalse(text.contains("example.com"))
        assertFalse(text.lowercase().contains("player@"))
        assertFalse(text.contains(phrase))
        assertFalse(text.contains(guid))
        assertFalse(Regex("""\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\b""").containsMatchIn(text))
        assertTrue(text.contains(ReportRedactor.EMAIL))
        assertTrue(text.contains(ReportRedactor.PASSWORD))
        assertTrue(text.contains(ReportSummary.ADDRESS))
        assertTrue(text.contains(ReportRedactor.SECRET))
    }

    @Test
    fun versionNumbersAreNotTakenForAddresses() {
        val text = ReportSummary.build(input(), redactor)
        assertTrue(text.contains("11.2.7.64978"))
        assertFalse(text.contains(ReportSummary.ADDRESS))
    }

    @Test
    fun lineBreaksInValuesCannotAddLines() {
        val text = ReportSummary.build(input(model = "Evil\nApp: fake"), redactor)
        assertEquals(1, text.lines().count { it.startsWith("App:") })
    }
}
