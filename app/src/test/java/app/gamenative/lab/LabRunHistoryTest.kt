package app.gamenative.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Liste "Letzte Läufe" und die Abschnitte im Protokoll (Auftrag labstabil2, Punkt 2). */
class LabRunHistoryTest {
    private val ctx = LabCommandContext(baseEnv = "", battleNetExe = "C:\\Program Files (x86)\\Battle.net\\Battle.net.exe")

    private fun entry(t: Long, crashes: Int = 0) = LabRunEntry(t, "battlenet", 100, 1, "Careful", "Box64", crashes)

    @Test
    fun theListKeepsTheNewestTenNewestFirst() {
        var list = emptyList<LabRunEntry>()
        for (t in 1L..15L) list = LabRunHistory.add(list, entry(t * 1000))
        assertEquals(10, list.size)
        assertEquals(15_000L, list.first().startedMs)
        assertEquals(6_000L, list.last().startedMs)
        assertEquals(list.sortedByDescending { it.startedMs }, list)
    }

    @Test
    fun aRunWithTheSameStartReplacesTheOldEntry() {
        val list = LabRunHistory.add(LabRunHistory.add(emptyList(), entry(1000, crashes = 0)), entry(1000, crashes = 4))
        assertEquals(1, list.size)
        assertEquals(4, list.single().crashes)
    }

    @Test
    fun jsonRoundTripAndGarbage() {
        val list = listOf(entry(2000, 3).copy(outputCut = true, translator = "FEXCore lab-2508", stage = 4, stageName = "FEX"), entry(1000))
        assertEquals(list, LabRunHistory.parse(LabRunHistory.toJson(list)))
        assertTrue(LabRunHistory.parse(null).isEmpty())
        assertTrue(LabRunHistory.parse("").isEmpty())
        assertTrue(LabRunHistory.parse("not json").isEmpty())
        assertTrue(LabRunHistory.parse("[1,2,{\"x\":1}]").isEmpty())
        assertEquals(1, LabRunHistory.parse("[{\"t\":5}]").size)
    }

    @Test
    fun theStoredTextHasOnlyNumbersAndNamesOfTheSettings() {
        val plan = LabCommand.build(LabSettings(extraEnv = "SECRET_TOKEN=abc", battleNetLine = "--token=geheim"), LabTarget.BATTLENET, ctx)
        val text = LabRunHistory.toJson(listOf(LabRunHistory.entryOf(plan, 1000, 91_500, 2, false)))
        for (word in listOf("geheim", "abc", "SECRET", "Program Files", "--token", "WOWFOREVER_HOSTNAME")) assertFalse(word, text.contains(word))
        val e = LabRunHistory.entryOf(plan, 1000, 91_500, 2, true)
        assertEquals(LabRunEntry(1000, "battlenet", 90, 1, "Careful", "Box64", 2, true), e)
    }

    @Test
    fun targetsAreNamedForThePage() {
        for ((target, name) in listOf(LabTarget.BATTLENET to "battlenet", LabTarget.INSTALLER to "installer", LabTarget.STEP_TEST to "step_test", LabTarget.ANY to "any")) {
            val settings = LabSettings(anyProgram = "C:\\x.exe")
            assertEquals(name, LabRunHistory.entryOf(LabCommand.build(settings, target, ctx), 0, 0, 0, false).target)
        }
    }

    @Test
    fun theOutcomeOfARunIsStoredAndReadBack() {
        val plan = LabCommand.build(LabSettings(), LabTarget.BATTLENET, ctx)
        val outcome = LabRunOutcome("exit requested: force_close", "no_window", noWindowSeen = true, mainWindowSeen = true, gameWindowSeen = false, restartRequested = true)
        val e = LabRunHistory.entryOf(plan, 1000, 91_500, 0, false, outcome)
        assertEquals("exit requested: force_close", e.endReason)
        assertEquals("no_window", e.watchEnd)
        assertTrue(e.noWindowSeen && e.mainWindowSeen && e.restartRequested)
        assertFalse(e.gameWindowSeen)
        assertEquals(e, LabRunHistory.parse(LabRunHistory.toJson(listOf(e))).single())
    }

    @Test
    fun entriesFromOlderBuildsReadWithEmptyOutcome() {
        val old = "[{\"t\":5,\"target\":\"battlenet\",\"sec\":9,\"stage\":4,\"stageName\":\"FEX\",\"translator\":\"FEXCore\",\"crashes\":0,\"cut\":false}]"
        val e = LabRunHistory.parse(old).single()
        assertEquals("", e.endReason)
        assertEquals("", e.watchEnd)
        assertFalse(e.mainWindowSeen || e.gameWindowSeen || e.noWindowSeen || e.restartRequested)
    }

    @Test
    fun theEndReasonIsCleanedAndShort() {
        assertEquals("a b", LabRunHistory.cleanReason("a\u0000 b\n"))
        assertEquals(60, LabRunHistory.cleanReason("x".repeat(200)).length)
    }

    private fun info(settings: LabSettings = LabSettings()): String {
        val plan = LabCommand.build(settings, LabTarget.BATTLENET, ctx)
        return LabProtocol.header(LabProtocolInfo(0L, "1.0", "dev", "17", settings, plan, 1024L * 1024 * 100))
    }

    @Test
    fun headerNamesTheStageAndHasAPendingCrashLine() {
        val h = info()
        assertTrue(h.contains("\nStability: 1 Careful\n"))
        assertTrue(h.contains("\nTranslator log: off\n"))
        assertTrue(h.contains("\n" + LabProtocol.CRASHES_PENDING + "\n"))
        assertTrue(info(LabSettings(stability = LabStability.CAREFUL_NO_JIT)).contains("\nStability: 2 Careful, no JIT\n"))
        assertTrue(info(LabSettings(translatorLog = true)).contains("\nTranslator log: on\n"))
        // Die Zeilen der Stufe stehen in der Umgebung des Protokolls.
        assertTrue(h.contains("\nBOX64_DYNAREC_SAFEFLAGS=2\n"))
    }

    @Test
    fun sectionsAreInsertedRightAfterTheHeadAndTheCrashLineGetsTheNumber() {
        val text = info() + "wine line 1\nwine line 2\n== End ==\n"
        val out = LabProtocol.insertAfterHead(text, listOf("Emulator: Box64"), listOf("Crashes: 3 (...)"), 3)!!
        assertFalse(out.contains(LabProtocol.CRASHES_PENDING))
        assertTrue(out.contains("\nCrashes: 3\n"))
        val iTr = out.indexOf("== Translator ==")
        val iCr = out.indexOf("== Crashes ==")
        val iSet = out.indexOf("== Settings ==")
        val iOut = out.indexOf("== Wine output ==")
        assertTrue(iTr in 1 until iCr)
        assertTrue(iCr < iSet && iSet < iOut)
        assertTrue(out.endsWith("wine line 1\nwine line 2\n== End ==\n"))
        assertTrue(out.contains("== Translator ==\nEmulator: Box64\n\n== Crashes ==\nCrashes: 3 (...)\n\n== Settings =="))
    }

    @Test
    fun withoutTheMarkersNothingIsInsertedAndTheCallerAppends() {
        assertEquals(null, LabProtocol.insertAfterHead("just text", listOf("a"), listOf("b"), 0))
        assertEquals(null, LabProtocol.insertAfterHead("== Settings ==\nno pending line\n", listOf("a"), listOf("b"), 0))
    }
}
