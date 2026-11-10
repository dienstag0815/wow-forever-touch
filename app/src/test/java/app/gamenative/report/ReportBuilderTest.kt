package app.gamenative.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

class ReportBuilderTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun file(path: String, text: String, modified: Long = 1_000L): File {
        val f = File(tmp.root, path)
        f.parentFile?.mkdirs()
        f.writeText(text)
        f.setLastModified(modified)
        return f
    }

    private fun bigFile(path: String, bytes: Int, modified: Long = 1_000L): File {
        val f = File(tmp.root, path)
        f.parentFile?.mkdirs()
        f.writeBytes(ByteArray(bytes) { 'x'.code.toByte() })
        f.setLastModified(modified)
        return f
    }

    private fun src(name: String, f: File, priority: Int, kind: LogKind = LogKind.GENERAL, max: Long = ReportBuilder.PER_FILE_MAX_BYTES) =
        ReportSource(name, f, kind, priority, max)

    private fun unzip(bytes: ByteArray): Map<String, String> {
        val out = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zin ->
            while (true) {
                val e = zin.nextEntry ?: break
                out[e.name] = zin.readBytes().toString(Charsets.UTF_8)
            }
        }
        return out
    }

    // --- Dateiauswahl ---

    @Test
    fun loginFileAndBinaryDumpsAreNeverAllowed() {
        assertFalse(ReportBuilder.isAllowed(File("x/_forever_/login.txt")))
        assertFalse(ReportBuilder.isAllowed(File("x/LOGIN.TXT")))
        assertFalse(ReportBuilder.isAllowed(File("x/Errors/crash.dmp")))
        assertFalse(ReportBuilder.isAllowed(File("x/Wow-ARM64.exe")))
        assertTrue(ReportBuilder.isAllowed(File("x/Logs/Connection.log")))
        assertTrue(ReportBuilder.isAllowed(File("x/WTF/Config.wtf")))
        assertTrue(ReportBuilder.isAllowed(File("x/Errors/2026-11-08 Crash.txt")))
    }

    @Test
    fun newestTakesTheYoungestAllowedFilesFirst() {
        file("d/a.log", "a", 100)
        file("d/b.log", "b", 300)
        file("d/c.txt", "c", 200)
        file("d/login.txt", "mail\npass\n", 900)
        file("d/old.dmp", "dump", 800)
        val names = ReportBuilder.newest(File(tmp.root, "d"), 2).map { it.name }
        assertEquals(listOf("b.log", "c.txt"), names)
        assertTrue(ReportBuilder.newest(File(tmp.root, "missing"), 3).isEmpty())
    }

    @Test
    fun planFollowsPriorityAndRespectsTheTotalBudget() {
        val a = bigFile("a.log", 2000)
        val b = bigFile("b.log", 2000)
        val c = bigFile("c.log", 2000)
        val planned = ReportBuilder.plan(listOf(src("c", c, 3), src("a", a, 1), src("b", b, 2)), budget = 5500)
        assertEquals(listOf("a", "b", "c"), planned.map { it.source.entryName })
        assertEquals(listOf(2000L, 2000L, 1500L), planned.map { it.bytes })
    }

    @Test
    fun planCapsEachFileAndSkipsTinyLeftoversEmptyMissingAndForbiddenFiles() {
        val huge = bigFile("huge.log", 5000)
        val rest = bigFile("rest.log", 5000)
        val empty = file("empty.log", "")
        val login = file("login.txt", "a@example.org\nsecret\n")
        val missing = File(tmp.root, "none.log")
        val planned = ReportBuilder.plan(
            listOf(src("h", huge, 1, max = 3000), src("r", rest, 2), src("e", empty, 3), src("l", login, 0), src("m", missing, 4)),
            budget = 3500,
        )
        // Der Rest des Budgets (500 Bytes) ist kleiner als 1024: die halbe Datei "rest" lohnt nicht.
        assertEquals(listOf("h"), planned.map { it.source.entryName })
        assertEquals(listOf(3000L), planned.map { it.bytes })
        val smallRest = ReportBuilder.plan(listOf(src("h", huge, 1, max = 3000), src("r", rest, 2)), budget = 4500)
        assertEquals(listOf(3000L, 1500L), smallRest.map { it.bytes })
    }

    @Test
    fun totalPlannedBytesNeverExceedTheDefaultBudget() {
        val files = (1..6).map { src("f$it", bigFile("f$it.log", 3 * 1024 * 1024), it) }
        val total = ReportBuilder.plan(files).sumOf { it.bytes }
        assertEquals(ReportBuilder.TOTAL_FILE_BUDGET_BYTES, total)
        assertTrue(ReportBuilder.plan(files).all { it.bytes <= ReportBuilder.PER_FILE_MAX_BYTES })
    }

    @Test
    fun readTailKeepsTheEndAndDropsAHalfLine() {
        val f = file("t.log", "line one\nline two\nline three\n")
        assertEquals("line one\nline two\nline three\n", ReportBuilder.readTail(f, 1000))
        val cut = ReportBuilder.readTail(f, 20)
        assertTrue(cut.startsWith("[earlier lines omitted]\n"))
        assertTrue(cut.endsWith("line three\n"))
        assertFalse(cut.contains("one"))
    }

    @Test
    fun entryNamesCannotEscapeTheZip() {
        assertEquals("a/b.log", ReportBuilder.safeEntryName("../../a/./b.log"))
        assertEquals("a/b.log", ReportBuilder.safeEntryName("\\a\\b.log"))
        assertEquals("datei", ReportBuilder.safeEntryName("../.."))
    }

    // --- Zip ---

    @Test
    fun zipHoldsDeviceFileLogcatAndSourcesAllRedactedAndNeverLoginTxt() {
        val email = "spieler@example.org"
        val password = "Tr0ub4dor&3x!"
        val redactor = ReportRedactor(listOf(email), listOf(password))
        val conn = file("g/_forever_/Logs/Connection.log", "login $email\nsession=ab12cd34ef56ab12cd34\nok\n")
        val cfg = file("g/_forever_/WTF/Config.wtf", "SET accountName \"$email\"\nSET gxApi \"D3D11\"\n")
        val err = file("g/_forever_/Errors/crash.txt", "boom with $password inside\n")
        val login = file("g/_forever_/login.txt", "$email\n$password\n")
        val out = ByteArrayOutputStream()
        val names = ReportBuilder.write(
            out,
            deviceText = "Modell: Test\nAnmeldung $email\n",
            logcat = "I/Wine: typed $password for $email\n",
            sources = listOf(
                src("spiel/Config.wtf", cfg, 0, LogKind.CONFIG_WTF),
                src("spiel/Logs/Connection.log", conn, 1, LogKind.CONNECTION),
                src("spiel/Errors/crash.txt", err, 2),
                src("spiel/login.txt", login, 0),
            ),
            redactor = redactor,
        )
        assertEquals(listOf("geraet.txt", "logcat.txt", "spiel/Config.wtf", "spiel/Logs/Connection.log", "spiel/Errors/crash.txt"), names)
        val entries = unzip(out.toByteArray())
        assertEquals(names, entries.keys.toList())
        assertTrue(entries.keys.none { it.contains("login", ignoreCase = true) })
        for ((name, text) in entries) {
            assertFalse("$name enthält das Passwort", text.contains("Tr0ub4dor"))
            assertFalse("$name enthält die E-Mail", text.contains("example.de"))
        }
        assertTrue(entries.getValue("spiel/Config.wtf").contains("SET accountName \"[KONTO]\""))
        assertTrue(entries.getValue("spiel/Config.wtf").contains("SET gxApi \"D3D11\""))
        assertTrue(entries.getValue("spiel/Logs/Connection.log").contains("session=[GEHEIM]"))
        assertEquals("Modell: Test\nAnmeldung [EMAIL]\n", entries.getValue("geraet.txt"))
    }

    @Test
    fun logcatIsLimitedToItsTailAndEntryNamesStayUnique() {
        val long = "a ".repeat(ReportBuilder.LOGCAT_MAX_CHARS) + "ENDE"
        val f = file("x.log", "inhalt")
        val out = ByteArrayOutputStream()
        val names = ReportBuilder.write(out, "g", long, listOf(src("geraet.txt", f, 1), src("dup.log", f, 2), src("dup.log", f, 3)), ReportRedactor())
        assertEquals(listOf("geraet.txt", "logcat.txt", "geraet.txt.2", "dup.log", "dup.log.2"), names)
        val logcat = unzip(out.toByteArray()).getValue("logcat.txt")
        assertEquals(ReportBuilder.LOGCAT_MAX_CHARS, logcat.length)
        assertTrue(logcat.endsWith("ENDE"))
    }

    @Test
    fun onlyTheNewestThreeReportsStay() {
        for (i in 1..5) file("reports/${ReportBuilder.REPORT_PREFIX}2026110$i-1200.zip", "z", i * 1000L)
        file("reports/andere.zip", "z", 1)
        ReportBuilder.pruneOldReports(File(tmp.root, "reports"), 3)
        val left = File(tmp.root, "reports").list().orEmpty().sorted()
        assertEquals(4, left.size)
        assertTrue(left.contains("andere.zip"))
        assertTrue(left.contains("${ReportBuilder.REPORT_PREFIX}20261105-1200.zip"))
        assertTrue(left.contains("${ReportBuilder.REPORT_PREFIX}20261104-1200.zip"))
        assertTrue(left.contains("${ReportBuilder.REPORT_PREFIX}20261103-1200.zip"))
    }

    // --- Registry ---

    @Test
    fun registryScanFindsMachineGuidAndComputerNames() {
        val reg = listOf(
            "[Software\\\\Microsoft\\\\Cryptography] 1700000000",
            "\"MachineGuid\"=\"3f0c1d2e-aaaa-bbbb-cccc-0123456789ab\"",
            "[System\\\\CurrentControlSet\\\\Control\\\\ComputerName\\\\ComputerName] 1700000000",
            "\"ComputerName\"=\"SALIS-FOLD\"",
            "\"ActiveComputerName\"=\"SALIS-FOLD\"",
            "\"Other\"=\"value\"",
            "\"MachineGuid\"=\"\"",
        )
        assertEquals(setOf("3f0c1d2e-aaaa-bbbb-cccc-0123456789ab", "SALIS-FOLD"), RegistryValues.secretsFrom(reg.asSequence()))
        assertTrue(RegistryValues.hasMachineGuid(reg.asSequence()))
        assertFalse(RegistryValues.hasMachineGuid(sequenceOf("\"Other\"=\"value\"")))
        assertTrue(RegistryValues.secretsFromFile(File(tmp.root, "nofile.reg")).isEmpty())
    }
}
