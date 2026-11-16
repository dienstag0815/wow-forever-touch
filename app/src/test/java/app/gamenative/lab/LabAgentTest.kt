package app.gamenative.lab

import app.gamenative.report.ReportRedactor
import java.io.File
import java.net.ServerSocket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LabAgentTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val agentRel = "ProgramData/Battle.net/Agent"

    private fun file(path: String, bytes: ByteArray = byteArrayOf(1), modified: Long = 0L) =
        File(tmp.root, path).also { it.parentFile.mkdirs(); it.writeBytes(bytes); if (modified > 0) it.setLastModified(modified) }

    /** Kleinste PE-Datei: DOS-Kopf mit `e_lfanew`, dann `PE\0\0` und der Maschinencode. */
    private fun pe(machine: Int, lfanew: Int = 0x80, extra: Int = 100): ByteArray {
        val b = ByteArray(lfanew + 6 + extra)
        b[0] = 'M'.code.toByte(); b[1] = 'Z'.code.toByte()
        b[0x3C] = (lfanew and 0xFF).toByte(); b[0x3D] = ((lfanew shr 8) and 0xFF).toByte()
        b[lfanew] = 'P'.code.toByte(); b[lfanew + 1] = 'E'.code.toByte()
        b[lfanew + 4] = (machine and 0xFF).toByte(); b[lfanew + 5] = ((machine shr 8) and 0xFF).toByte()
        return b
    }

    // ---- PE-Kopf ----

    @Test
    fun machineNames() {
        assertEquals("x86", LabPeHeader.machineName(0x14c))
        assertEquals("x64", LabPeHeader.machineName(0x8664))
        assertEquals("ARM64", LabPeHeader.machineName(0xAA64))
        assertEquals("ARM64EC", LabPeHeader.machineName(0xA641))
        assertEquals("unknown (0x1234)", LabPeHeader.machineName(0x1234))
    }

    @Test
    fun describeReadsMachineAndSize() {
        val x64 = file("c/a.exe", pe(0x8664))
        val arm = file("c/b.exe", pe(0xAA64, lfanew = 0x100))
        val x86 = file("c/c.exe", pe(0x14c, lfanew = 64))
        assertEquals("${x64.length()} bytes, machine x64", LabPeHeader.describe(x64))
        assertEquals("${arm.length()} bytes, machine ARM64", LabPeHeader.describe(arm))
        assertEquals("${x86.length()} bytes, machine x86", LabPeHeader.describe(x86))
    }

    @Test
    fun describeHandlesBrokenFiles() {
        assertEquals("missing", LabPeHeader.describe(null))
        assertEquals("missing", LabPeHeader.describe(File(tmp.root, "nope.exe")))
        assertEquals("5 bytes, not a PE file", LabPeHeader.describe(file("c/short.exe", ByteArray(5))))
        assertEquals("200 bytes, not a PE file", LabPeHeader.describe(file("c/text.exe", ByteArray(200) { 'a'.code.toByte() })))
        // e_lfanew zeigt hinter das Dateiende
        val beyond = pe(0x8664).copyOf(100).also { it[0x3C] = 0x00; it[0x3D] = 0x10 }
        assertEquals("100 bytes, not a PE file", LabPeHeader.describe(file("c/cut.exe", beyond)))
    }

    @Test
    fun peOffsetRejectsHugeAndTinyValues() {
        val dos = pe(0x8664).copyOf(64)
        dos[0x3F] = 0x7F
        assertNull(LabPeHeader.peOffset(dos))
        dos[0x3F] = 0; dos[0x3C] = 10; dos[0x3D] = 0
        assertNull(LabPeHeader.peOffset(dos))
    }

    // ---- Agent.dat ----

    @Test
    fun datWithDigitsGivesThePort() {
        val d = LabAgentDat.parse("1120".toByteArray(), 4)
        assertEquals(1120, d.port)
        assertEquals("Agent.dat: 4 bytes, digits only, port 1120", LabAgentDat.line(d))
        assertEquals(1120, LabAgentDat.parse("1120\r\n".toByteArray(), 6).port)
    }

    @Test
    fun datWithOtherContentGivesOnlyTheLength() {
        val d = LabAgentDat.parse("abc123".toByteArray(), 6)
        assertNull(d.port)
        assertEquals("Agent.dat: 6 bytes, content is not a port number", LabAgentDat.line(d))
        // Ziffern, aber kein Port (zu lang oder 0): der Wert wird nie ausgegeben
        val long = LabAgentDat.parse("123456789012".toByteArray(), 12)
        assertNull(long.port)
        assertTrue(long.digitsOnly)
        assertFalse(LabAgentDat.line(long).contains("1234"))
        assertNull(LabAgentDat.parse("0".toByteArray(), 1).port)
        assertNull(LabAgentDat.parse("70000".toByteArray(), 5).port)
        // größer als der gelesene Kopf: nur die Länge
        assertEquals("Agent.dat: 500 bytes, content is not a port number", LabAgentDat.line(LabAgentDat.parse(ByteArray(64), 500)))
    }

    @Test
    fun readDatFromFolder() {
        file("c/$agentRel/Agent.dat", "1120".toByteArray())
        val d = LabAgentFiles.readDat(LabAgentFiles.agentDir(File(tmp.root, "c")))
        assertEquals(4L, d.length)
        assertEquals(1120, d.port)
        assertNull(LabAgentFiles.readDat(null).length)
        assertEquals("Agent.dat: missing", LabAgentDat.line(LabAgentFiles.readDat(null)))
    }

    // ---- Dateien ----

    @Test
    fun agentDirIsFoundCaseInsensitive() {
        file("c/programdata/battle.net/agent/Agent.dat")
        val dir = LabAgentFiles.agentDir(File(tmp.root, "c"))
        assertNotNull(dir)
        assertNull(LabAgentFiles.agentDir(File(tmp.root, "other")))
    }

    @Test
    fun newestAgentExeTakesTheHighestVersionNumber() {
        file("c/$agentRel/Agent.9873/Agent.exe", pe(0x8664))
        file("c/$agentRel/Agent.10020/Agent.exe", pe(0xAA64))
        file("c/$agentRel/Agent.998/Agent.exe", pe(0x14c))
        file("c/$agentRel/Agent.exe", pe(0x14c))
        val dir = LabAgentFiles.agentDir(File(tmp.root, "c"))!!
        assertEquals("Agent.10020", LabAgentFiles.newestAgentExe(dir)!!.parentFile.name)
        assertEquals(listOf("Agent.10020", "Agent.9873", "Agent.998"), LabAgentFiles.versionDirs(dir).map { it.name })
        assertEquals(dir, LabAgentFiles.stubAgentExe(dir)!!.parentFile)
    }

    @Test
    fun helperIsFoundOutsideTheAgentFolder() {
        file("c/ProgramData/Battle.net_components/battlenet_helpersvc/AgentHelper.exe", pe(0x14c))
        val h = LabAgentFiles.helperExe(File(tmp.root, "c"))
        assertEquals("AgentHelper.exe", h!!.name)
        assertNull(LabAgentFiles.helperExe(File(tmp.root, "empty")))
    }

    @Test
    fun listPutsTheImportantFilesFirstAndNamesMissingOnes() {
        val entries = listOf(
            LabFileEntry("$agentRel/Logs/Agent-1.log", 10, 1_000),
            LabFileEntry("$agentRel/Logs/Agent-2.log", 10, 2_000),
            LabFileEntry("$agentRel/.patch.result", 1, 5),
            LabFileEntry("$agentRel/Agent.exe", 100, 5),
            LabFileEntry("$agentRel/Agent.9873/Agent.exe", 7_000_000, 5),
            LabFileEntry("$agentRel/Agent.dat", 4, 5),
            LabFileEntry("$agentRel/Agent.9873/config.json", 3, 5),
        )
        val lines = LabAgentFiles.listLines(agentRel, entries, helper = null, scanCut = false)
        assertEquals("(missing) $agentRel/product.db", lines[0])
        assertEquals("(missing) AgentHelper.exe", lines[1])
        assertTrue(lines[2].startsWith("$agentRel/Agent.dat | 4 | "))
        assertTrue(lines[3].startsWith("$agentRel/Agent.exe | 100 | "))
        assertTrue(lines[4].startsWith("$agentRel/Agent.9873/Agent.exe | 7000000 | "))
        // übrige Dateien vor den Protokollen, Protokolle jüngste zuerst
        val tail = lines.drop(5).map { it.substringBefore(" | ") }
        assertEquals(listOf("$agentRel/.patch.result", "$agentRel/Agent.9873/config.json", "$agentRel/Logs/Agent-2.log", "$agentRel/Logs/Agent-1.log"), tail)
    }

    @Test
    fun listIsCappedAtEightyLinesWithACounter() {
        val entries = (1..200).map { LabFileEntry("$agentRel/Logs/Agent-$it.log", 1, it.toLong()) } + LabFileEntry("$agentRel/Agent.dat", 4, 5)
        val lines = LabAgentFiles.listLines(agentRel, entries, helper = LabFileEntry("ProgramData/Battle.net_components/x/AgentHelper.exe", 9, 9), scanCut = false)
        assertEquals(80, lines.size)
        assertTrue(lines.last().startsWith("("))
        assertTrue(lines.last().contains("more not listed"))
        assertTrue(lines[0].startsWith("(missing) $agentRel/product.db"))
        assertTrue(lines[1].startsWith("$agentRel/Agent.dat"))
        assertTrue(lines[2].startsWith("ProgramData/Battle.net_components/x/AgentHelper.exe"))
    }

    @Test
    fun factsOfAFullPrefix() {
        val now = 1_700_000_000_000L
        file("c/$agentRel/Agent.dat", "1120".toByteArray(), now)
        file("c/$agentRel/product.db", ByteArray(40), now)
        file("c/$agentRel/Agent.exe", pe(0x14c), now)
        file("c/$agentRel/Agent.9873/Agent.exe", pe(0x8664), now)
        file("c/$agentRel/Agent.9873/Logs/Agent-20261115T130724.log", "x".toByteArray(), now)
        file("c/ProgramData/Battle.net_components/battlenet_helpersvc/AgentHelper.exe", pe(0xAA64), now)
        val facts = LabAgentFiles.facts(File(tmp.root, "c"))
        val head = facts.head.joinToString("\n")
        assertTrue(head, head.contains("Agent folder: $agentRel"))
        assertTrue(head, head.contains("Agent version folders: Agent.9873"))
        assertTrue(head, head.contains("Agent.dat: 4 bytes, digits only, port 1120"))
        assertTrue(head, head.contains("Agent.exe (newest version folder Agent.9873): ") && head.contains("machine x64"))
        assertTrue(head, head.contains("stub in the Agent folder): ") && head.contains("machine x86"))
        assertTrue(head, head.contains("AgentHelper.exe (ProgramData/Battle.net_components/battlenet_helpersvc/AgentHelper.exe): ") && head.contains("machine ARM64"))
        assertTrue(facts.files.first().startsWith("Files under $agentRel (path | size | date), 5 found"))
        assertTrue(facts.files.any { it.contains("$agentRel/Agent.dat | 4 | ") })
        assertTrue(facts.files.any { it.contains("AgentHelper.exe | ") })
    }

    @Test
    fun factsWithoutAgentFolder() {
        file("c/Windows/x.txt")
        val facts = LabAgentFiles.facts(File(tmp.root, "c"))
        assertTrue(facts.head.contains("Agent folder: ProgramData/Battle.net/Agent does not exist"))
        assertTrue(facts.head.contains("Agent.dat: missing"))
        assertEquals(listOf("Files: (no Agent folder)"), facts.files)
        assertTrue(facts.head.any { it.contains("Agent.exe (newest version folder): missing") })
    }

    // ---- Abfrage ----

    @Test
    fun answerParsingKeepsOnlyStatusLengthAndJson() {
        val body = """{"a":1}"""
        val ok = LabAgentProbe.parseAnswer("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n$body".toByteArray(), partial = false)
        assertEquals(200, ok.status)
        assertEquals(body.length, ok.bodyBytes)
        assertTrue(ok.json)
        assertEquals("status=200 length=7 json=yes", LabAgentProbe.answerText(ok))
        val html = LabAgentProbe.parseAnswer("HTTP/1.0 404 Not Found\r\nContent-Type: text/html\r\n\r\n<html></html>".toByteArray(), false)
        assertEquals("status=404 length=13 json=no", LabAgentProbe.answerText(html))
        val bodyJson = LabAgentProbe.parseAnswer("HTTP/1.1 200 OK\r\n\r\n [1,2] ".toByteArray(), false)
        assertTrue(bodyJson.json)
        val garbage = LabAgentProbe.parseAnswer(byteArrayOf(0x16, 0x03, 0x01, 0x02), false)
        assertNull(garbage.status)
        assertEquals("no HTTP answer (4 bytes received)", LabAgentProbe.answerText(garbage))
        assertEquals("no HTTP answer (0 bytes received)", LabAgentProbe.answerText(LabAgentProbe.parseAnswer(ByteArray(0), false)))
    }

    @Test
    fun answerTextNeverContainsTheBody() {
        val text = LabAgentProbe.answerText(LabAgentProbe.parseAnswer("HTTP/1.1 200 OK\r\n\r\n{\"token\":\"SECRETVALUE\"}".toByteArray(), false))
        assertFalse(text.contains("SECRETVALUE"))
        assertFalse(text.contains("token"))
    }

    @Test
    fun requestIsAPlainGet() {
        val req = String(LabAgentProbe.requestBytes(1120), Charsets.US_ASCII)
        assertTrue(req.startsWith("GET /agent HTTP/1.1\r\nHost: 127.0.0.1:1120\r\n"))
        assertTrue(req.endsWith("\r\n\r\n"))
    }

    @Test
    fun probeLineFormat() {
        assertEquals("  +20s  port=1120  status=200 length=7 json=yes", LabAgentProbe.line("+20s", 1120, "status=200 length=7 json=yes"))
        assertEquals("  end  skipped: Agent.dat missing", LabAgentProbe.line("end", null, "skipped: Agent.dat missing"))
    }

    @Test
    fun runAgainstALocalServer() {
        ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1")).use { server ->
            val t = Thread {
                server.accept().use { c ->
                    val buf = ByteArray(512)
                    c.getInputStream().read(buf)
                    c.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"ok\":true}".toByteArray())
                }
            }.apply { isDaemon = true; start() }
            val result = LabAgentProbe.run(server.localPort)
            t.join(2_000)
            assertEquals("status=200 length=11 json=yes", result)
        }
    }

    @Test
    fun runAgainstAClosedPortReportsTheKindOfError() {
        val port = ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1")).use { it.localPort }
        val result = LabAgentProbe.run(port)
        assertTrue(result, result.startsWith("error="))
        assertFalse(result.contains("127.0.0.1"))
    }

    @Test
    fun errorKinds() {
        assertEquals("timeout", LabAgentProbe.errorKind(java.net.SocketTimeoutException("x")))
        assertEquals("connection refused", LabAgentProbe.errorKind(java.net.ConnectException("Connection refused")))
        assertEquals("connect failed", LabAgentProbe.errorKind(java.net.ConnectException("other")))
        assertEquals("IllegalStateException", LabAgentProbe.errorKind(IllegalStateException("with a secret message")))
    }

    // ---- Agent-Prozesse ----

    private fun create(stamp: String, creator: String, path: String, pid: String, tid: String) =
        "$stamp:$creator:trace:process:NtCreateUserProcess L\"\\\\??\\\\$path\" pid $pid tid $tid handles 0x14/0x18"

    private fun exit(stamp: String, tid: String, code: Int = 0, handle: String = "0xffffffffffffffff", exiting: Int = 1) =
        "$stamp:$tid:trace:process:NtTerminateProcess handle $handle, exit_code $code, process_exiting $exiting."

    @Test
    fun scannerRecordsStartAndEndOfAgentProcessesOnly() {
        val s = LabAgentProcessScanner()
        s.add(create("182748.722", "00dc", "C:\\\\ProgramData\\\\Battle.net\\\\Agent\\\\Agent.exe", "0148", "014c"), 5_000)
        s.add(create("182749.230", "014c", "C:\\\\ProgramData\\\\Battle.net\\\\Agent\\\\Agent.9873\\\\Agent.exe", "0150", "0154"), 5_500)
        s.add(create("182749.300", "00dc", "C:\\\\windows\\\\system32\\\\winedevice.exe", "0160", "0164"), 5_600)
        s.add(exit("182749.242", "014c", 0, handle = "0x0", exiting = 0), 6_000)
        s.add(exit("182749.243", "014c", 0), 6_001)
        s.add(exit("182749.244", "014c", 0), 6_002)
        s.add(exit("182749.500", "0164", 3), 6_100)
        val lines = s.lines()
        assertEquals(4, lines.size)
        assertEquals("+5.0s  wine=182748.722  start  pid=0148  program=Agent.exe", lines[0])
        assertEquals("+5.5s  wine=182749.230  start  pid=0150  program=Agent.exe", lines[1])
        assertEquals("+6.0s  wine=182749.243  end    pid=0148  program=Agent.exe  exit code 0", lines[2])
        assertEquals("Started: 2, ended with a message: 1, without an end message: 1 (running at the end of the log or killed)", lines[3])
        assertEquals(2, s.startCount)
    }

    @Test
    fun scannerSeesAgentHelperAndNonZeroExitCodes() {
        val s = LabAgentProcessScanner()
        s.add(create("181216.700", "00dc", "C:\\\\ProgramData\\\\Battle.net_components\\\\battlenet_helpersvc\\\\AgentHelper.exe", "0200", "0204"), 12_000)
        s.add(exit("181217.100", "0204", -1073741819), 12_400)
        val lines = s.lines()
        assertTrue(lines[0].endsWith("program=AgentHelper.exe"))
        assertTrue(lines[1].endsWith("exit code -1073741819"))
    }

    @Test
    fun scannerWithoutAgentSaysSo() {
        val s = LabAgentProcessScanner()
        s.add("183502.785:0034:trace:process:CreateProcessInternalW app L\"C:\\\\windows\\\\system32\\\\services.exe\"", 100)
        s.add("not a wine line", 100)
        assertEquals("(no start of a process named Agent* in the Wine output)", s.lines()[0])
        assertEquals(2, s.lines().size)
    }

    @Test
    fun scannerKeepsAtMostFortyLines() {
        val s = LabAgentProcessScanner()
        for (i in 0 until 100) {
            val tid = Integer.toHexString(0x1000 + i * 8)
            s.add(create("100.$i", "0010", "C:\\\\Agent\\\\Agent.exe", Integer.toHexString(0x2000 + i * 8), tid), i * 1000L)
            s.add(exit("100.$i", tid), i * 1000L + 10)
        }
        val lines = s.lines()
        assertEquals(LabAgentProcessScanner.MAX_LINES, lines.size)
        assertTrue(lines.any { it.contains("events in the middle left out") })
        assertTrue(lines.last().startsWith("Started: 100, ended with a message: 100, without an end message: 0"))
        assertTrue(lines.first().contains("start"))
    }

    // ---- Abschnitt ----

    @Test
    fun sectionIsInsertedBeforeTheEnd() {
        val text = "head\n== Battle.net files (name, size, date) ==\na | 1 | t\n\n== End ==\nEnded: x\n"
        val facts = LabAgentFacts(listOf("Agent folder: x"), listOf("Files: (no Agent folder)"))
        val lines = LabAgentReport.lines(facts, listOf("  +20s  port=1  status=200 length=1 json=no"), listOf("+1.0s  start"))
        val out = LabAgentReport.insertBeforeEnd(text, lines)!!
        assertTrue(out.startsWith("head\n== Battle.net files"))
        val agent = out.indexOf("\n== Agent ==\n")
        val end = out.indexOf("\n== End ==\n")
        assertTrue(agent in 1 until end)
        assertEquals(1, Regex("== Agent ==").findAll(out).count())
        assertTrue(out.endsWith("== End ==\nEnded: x\n"))
        assertTrue(out.contains("  +20s  port=1  status=200 length=1 json=no"))
        assertTrue(out.indexOf("Files: (no Agent folder)") > out.indexOf("Agent processes in the Wine output"))
        assertNull(LabAgentReport.insertBeforeEnd("no marker here", lines))
    }

    @Test
    fun sectionWithoutRequestsSaysSo() {
        val lines = LabAgentReport.lines(LabAgentFacts(emptyList(), emptyList()), emptyList(), emptyList())
        assertTrue(lines.contains("  (no request was made)"))
    }

    // ---- Fehlerfenster ----

    private fun ev(t: Long, kind: LabWinEventKind, id: Int, title: String) = LabWinEvent(1_000L + t, kind, id, "battle.net.exe", title, 400, 200)

    @Test
    fun errorWindowLineForTitlesWithFehlerOrError() {
        val events = listOf(
            ev(500, LabWinEventKind.OPEN, 1, "Battle.net"),
            ev(305_123, LabWinEventKind.OPEN, 2, "Battle.net-Fehler"),
            ev(310_000, LabWinEventKind.CLOSE, 2, "Battle.net-Fehler"),
            ev(400_000, LabWinEventKind.TITLE, 1, "Battle.net error 3"),
            ev(401_000, LabWinEventKind.RAISE, 1, "Battle.net error 3"),
        )
        val lines = LabWindowsReport.errorLines(events, 1_000L)
        assertEquals(2, lines.size)
        assertEquals("error window  +305.123s  open  0x2  class=battle.net.exe  title=\"Battle.net-Fehler\"  400x200", lines[0])
        assertTrue(lines[1].startsWith("error window  +400.000s  title  0x1"))
    }

    @Test
    fun errorWindowTitleIsRedacted() {
        val e = listOf(ev(10, LabWinEventKind.OPEN, 3, "Fehler fuer max@example.org"))
        val line = LabWindowsReport.errorLines(e, 1_000L, ReportRedactor(emails = listOf("max@example.org"))).single()
        assertFalse(line, line.contains("max@example.org"))
        assertTrue(line.startsWith("error window  "))
    }

    @Test
    fun sameWindowAndTitleOnlyOnce() {
        val events = listOf(ev(10, LabWinEventKind.SEEN, 7, "Error"), ev(20, LabWinEventKind.TITLE, 7, "Error"), ev(30, LabWinEventKind.TITLE, 7, "Error 2"))
        assertEquals(2, LabWindowsReport.errorLines(events, 1_000L).size)
    }

    @Test
    fun errorWindowLinesAreInTheWindowsSectionAndSurviveCutting() {
        val many = (1..600).map { ev(it * 10L, LabWinEventKind.OPEN, it, "Window $it") }
        val events = many + ev(7_000, LabWinEventKind.OPEN, 9999, "Battle.net-Fehler")
        val lines = LabWindowsReport.lines(events, 0, 1_000L, LabWinSnapshot.EMPTY)
        assertTrue(lines.size <= LabWindowsReport.MAX_LINES)
        assertTrue(lines.any { it.contains("events in the middle left out") })
        assertEquals(2, lines.count { it.contains("Battle.net-Fehler") })
        assertEquals(1, lines.count { it.startsWith("error window  ") })
    }

    @Test
    fun noErrorWindowNoLine() {
        val lines = LabWindowsReport.lines(listOf(ev(10, LabWinEventKind.OPEN, 1, "Battle.net")), 0, 1_000L, LabWinSnapshot.EMPTY)
        assertTrue(lines.none { it.startsWith("error window") })
    }

    // ---- Auswahl der Protokolle ----

    @Test
    fun agentLogsComeInAdditionToTheTwentyBattleNetLogs() {
        val c = File(tmp.root, "c")
        for (i in 1..25) file("c/ProgramData/Battle.net/Logs/battle.net-$i.log", modified = 1_000_000L + i * 1000L)
        for (i in 1..35) file("c/ProgramData/Battle.net/Agent/Agent.9873/Logs/Agent-$i.log", modified = 2_000_000L + i * 1000L)
        for (i in 1..3) file("c/ProgramData/Battle.net/Agent/Agent.9873/Logs/AgentErrors-$i.log", modified = 500_000L + i * 1000L)
        assertEquals(LabReportSelect.MAX_BNET_LOGS, LabReportSelect.battleNetLogs(c).size)
        assertTrue(LabReportSelect.battleNetLogs(c).none { LabReportSelect.isAgentLog(it) })
        assertEquals("battle.net-25.log", LabReportSelect.battleNetLogs(c).first().name)
        val agents = LabReportSelect.agentLogs(c)
        assertEquals(LabReportSelect.MAX_AGENT_LOGS, agents.size)
        assertEquals("Agent-35.log", agents.first().name)
        val sources = LabReportSelect.sources(File(tmp.root, "labs"), c)
        assertEquals(50, sources.size)
        assertTrue(sources.all { it.entryName.startsWith("battlenet/ProgramData/Battle.net/") })
        // die Agent-Protokolle kommen im Budget vor den übrigen Battle.net-Protokollen
        assertTrue(sources.filter { it.entryName.contains("/Agent-") }.all { it.priority < 10 })
        assertTrue(sources.filter { it.entryName.contains("battle.net-") }.all { it.priority >= 10 })
    }

    @Test
    fun agentErrorsLogIsAnAgentLog() {
        assertTrue(LabReportSelect.isAgentLog(File("AgentErrors-20261115T110608.log")))
        assertTrue(LabReportSelect.isAgentLog(File("agent-20261115.log")))
        assertFalse(LabReportSelect.isAgentLog(File("battle.net-20261115.log")))
    }
}
