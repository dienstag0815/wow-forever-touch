package app.gamenative.lab

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LabProtocolTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun plan(settings: LabSettings = LabSettings(), host: String? = "DESKTOP-ABC1234") = LabCommand.build(
        settings,
        LabTarget.BATTLENET,
        LabCommandContext(baseEnv = "MESA_GLTHREAD=true", battleNetExe = "C:\\Program Files (x86)\\Battle.net\\Battle.net.exe", identityHostname = host),
    )

    @Test
    fun headerHasTimeSettingsCommandAndEnvironment() {
        val settings = LabSettings(extraEnv = "MY_PASSWORD=hunter2\nVISIBLE=yes")
        val text = LabProtocol.header(
            LabProtocolInfo(1_700_000_000_000L, "1.0 (code 1)", "Maker Model", "14 (API 34)", settings, plan(settings), 5L * 1024 * 1024 * 1024),
        )
        assertTrue(text.startsWith("WoW Forever lab protocol (format 1)"))
        assertTrue(text.contains("Started: 2023-11-14 22:13:20 UTC"))
        assertTrue(text.contains("== Settings =="))
        assertTrue(text.contains("== Command =="))
        assertTrue(text.contains("Program: C:\\Program Files (x86)\\Battle.net\\Battle.net.exe"))
        assertTrue(text.contains("Arguments: --disable-gpu --disable-gpu-compositing --enable-features=NetworkServiceInProcess2"))
        assertTrue(text.contains("Same Windows identity as the game: on"))
        assertTrue(text.contains("VISIBLE=yes"))
        assertTrue(text.contains("MY_PASSWORD=[GEHEIM]"))
        assertFalse(text.contains("hunter2"))
        assertFalse("the host name of the identity never appears", text.contains("DESKTOP-ABC1234"))
        assertTrue(text.trimEnd().endsWith("== Wine output =="))
    }

    @Test
    fun headerCarriesTheStartLinesOnlyWhenGiven() {
        val settings = LabSettings()
        val without = LabProtocol.header(LabProtocolInfo(1_700_000_000_000L, "1.0", "M", "14", settings, plan(settings), 0L))
        assertFalse(without.contains("Start mode:"))
        assertFalse(without.contains("Started from:"))
        val lines = listOf(
            LabStartMode.modeLine(EffectiveStart(StartMode.BATTLENET, StartSource.DEFAULT)),
            LabStartMode.originLine(LabStartOrigin.HOME),
        )
        val withLines = LabProtocol.header(LabProtocolInfo(1_700_000_000_000L, "1.0", "M", "14", settings, plan(settings), 0L, startLines = lines))
        assertTrue(withLines.contains("\nStart mode: battlenet (default)\nStarted from: home\n"))
        assertTrue("both lines sit in the head, before the settings", withLines.indexOf("Start mode:") < withLines.indexOf("== Settings =="))
    }

    @Test
    fun headerSaysWhetherTheProgramIsAnAbsoluteWindowsPath() {
        val settings = LabSettings()
        val absolute = LabProtocol.header(LabProtocolInfo(1_700_000_000_000L, "1.0", "M", "14", settings, plan(settings), 0L))
        assertTrue(absolute.contains("Absolute Windows path: yes"))
        val relative = plan(settings).copy(program = "notepad.exe")
        val text = LabProtocol.header(LabProtocolInfo(1_700_000_000_000L, "1.0", "M", "14", settings, relative, 0L))
        assertTrue(text.contains("Program: notepad.exe\nAbsolute Windows path: no\n"))
    }

    @Test
    fun fileNameAndFooter() {
        assertEquals("lab-20231114-221320-battlenet.log", LabProtocol.fileName(1_700_000_000_000L, LabTarget.BATTLENET))
        val footer = LabProtocol.footer(14400, 0L, 95_000L, "Wine process ended")
        assertTrue(footer.contains("Runtime: 95 s"))
        assertTrue(footer.contains("Exit code of the Wine process: 14400"))
        assertTrue(LabProtocol.footer(null, 0, 0, "x").contains("unknown"))
    }

    @Test
    fun processAndFileBlocks() {
        assertEquals("12 wine explorer /desktop=shell", LabProtocol.processLine("12", "wine\u0000explorer\u0000/desktop=shell\u0000"))
        assertTrue(LabProtocol.processBlock("end", 4_000, emptyList()).contains("(none)"))
        assertTrue(LabProtocol.fileBlock(listOf(LabFileEntry("a/b.exe", 10, 0)), true).contains("a/b.exe | 10 |"))
        assertTrue(LabProtocol.fileBlock(emptyList(), false).contains("(none found)"))
    }

    private fun file(path: String, bytes: Int = 3) = File(tmp.root, path).also { it.parentFile.mkdirs(); it.writeBytes(ByteArray(bytes)) }

    @Test
    fun scanListsOnlyBattleNetFolders() {
        file("Program Files (x86)/Battle.net/Battle.net.exe", 7)
        file("Program Files (x86)/Battle.net/Battle.net.12345/x.dll", 5)
        file("Program Files (x86)/Other/y.exe")
        file("ProgramData/Battle.net/Agent/a.db", 4)
        file("windows/system32/z.dll")
        val (entries, truncated) = LabProtocol.scanBattleNetFiles(tmp.root)
        assertFalse(truncated)
        assertEquals(
            setOf("Program Files (x86)/Battle.net/Battle.net.exe", "Program Files (x86)/Battle.net/Battle.net.12345/x.dll", "ProgramData/Battle.net/Agent/a.db"),
            entries.map { it.path }.toSet(),
        )
        assertEquals(7L, entries.single { it.path.endsWith("Battle.net.exe") }.size)
        assertTrue(LabProtocol.scanBattleNetFiles(tmp.root, maxEntries = 1).second)
    }

    @Test
    fun findBattleNetExe() {
        assertNull(LabProtocol.findBattleNetExe(tmp.root))
        file("Program Files (x86)/Battle.net/Battle.net.12000/Battle.net.exe")
        assertEquals("C:\\Program Files (x86)\\Battle.net\\Battle.net.12000\\Battle.net.exe", LabProtocol.findBattleNetExe(tmp.root))
        file("Program Files (x86)/Battle.net/Battle.net.exe")
        assertEquals("C:\\Program Files (x86)\\Battle.net\\Battle.net.exe", LabProtocol.findBattleNetExe(tmp.root))
    }
}
