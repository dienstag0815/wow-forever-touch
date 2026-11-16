package app.gamenative.lab

import app.gamenative.report.ReportRedactor
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LabReportSelectTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun file(path: String, text: String = "x", modified: Long = 0L) =
        File(tmp.root, path).also { it.parentFile.mkdirs(); it.writeText(text); if (modified > 0) it.setLastModified(modified) }

    private fun entries(bytes: ByteArray): Map<String, String> {
        val out = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                out[e.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        return out
    }

    @Test
    fun onlyTheNewestTwentyBattleNetLogsAreTaken() {
        val c = File(tmp.root, "c")
        for (i in 1..25) file("c/ProgramData/Battle.net/Logs/l$i.log", "line $i", modified = 1_000_000L + i * 1000L)
        file("c/ProgramData/Battle.net/Logs/readme.txt")
        file("c/ProgramData/Battle.net/Logs/data.bin")
        val logs = LabReportSelect.battleNetLogs(c)
        assertEquals(LabReportSelect.MAX_BNET_LOGS, logs.size)
        assertEquals("l25.log", logs.first().name)
        assertEquals("l6.log", logs.last().name)
        assertTrue(logs.all { it.extension == "log" })
    }

    @Test
    fun logsAreFoundInProgramDataAndInUserAppData() {
        val c = File(tmp.root, "c")
        file("c/ProgramData/Battle.net/Agent/a.log")
        file("c/users/steamuser/AppData/Local/Battle.net/Logs/b.log")
        file("c/users/steamuser/AppData/Roaming/Battle.net/Logs/c.log")
        file("c/users/steamuser/AppData/Local/Other/d.log")
        file("c/windows/e.log")
        assertEquals(setOf("a.log", "b.log", "c.log"), LabReportSelect.battleNetLogs(c).map { it.name }.toSet())
    }

    @Test
    fun aLargeLogIsCutToTheLimit() {
        val c = File(tmp.root, "c")
        file("c/ProgramData/Battle.net/Logs/big.log", ("A".repeat(99) + System.lineSeparator()).repeat(31_000))
        val sources = LabReportSelect.sources(File(tmp.root, "none"), c)
        assertEquals(LabReportSelect.MAX_BNET_LOG_BYTES, sources.single().maxBytes)
        val (bytes, _) = LabReportSelect.zipBytes(sources, "device", ReportRedactor())
        val big = entries(bytes).entries.single { it.key.endsWith("big.log") }.value
        assertTrue("size ${big.length}", big.length <= LabReportSelect.MAX_BNET_LOG_BYTES + 512)
        assertTrue(big.length > 1024 * 1024)
    }

    @Test
    fun labProtocolsComeFirstAndAreLimited() {
        val dir = File(tmp.root, "p")
        for (i in 1..8) file("p/lab-$i.log", "p$i", modified = 1_000_000L + i * 1000L)
        file("p/pakete.log", "pkg")
        file("p/other.txt")
        val logs = LabReportSelect.labLogs(dir)
        assertEquals(LabReportSelect.MAX_LAB_LOGS, logs.size)
        assertEquals("lab-8.log", logs.first().name)
        val names = LabReportSelect.sources(dir, File(tmp.root, "c")).map { it.entryName }
        assertEquals("labor/lab-8.log", names.first())
        assertTrue(names.contains("labor/pakete.log"))
    }

    @Test
    fun anEmailInABattleNetLogIsNotInTheZip() {
        val c = File(tmp.root, "c")
        val secretMail = "someone.private@example.invalid"
        file("c/ProgramData/Battle.net/Logs/login.log", "user typed $secretMail then ok\nsecond line with Other.Name@example.invalid")
        file("p/lab-1.log", "protocol mentions $secretMail too")
        val sources = LabReportSelect.sources(File(tmp.root, "p"), c)
        assertTrue(sources.any { it.entryName.startsWith("battlenet/") })
        val (bytes, names) = LabReportSelect.zipBytes(sources, "device $secretMail", ReportRedactor(emails = listOf(secretMail)))
        val all = entries(bytes)
        assertTrue(names.any { it.contains("login.log") })
        val joined = all.values.joinToString("\n")
        assertFalse(joined, joined.contains("example.invalid"))
        assertFalse(joined.contains("someone.private"))
        assertTrue(joined.contains("user typed"))
    }
}
