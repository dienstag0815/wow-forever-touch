package app.gamenative.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Abschnitt `== Translator ==` (Auftrag labstabil2, Punkt 3): den ersten Block von Box64 herausziehen, FEX-Meldungen, Abgleich. */
class LabTranslatorInfoTest {
    private val block = listOf(
        "[BOX64] Dynarec for ARM64, with extension: ASIMD AES CRC32 PMULL ATOMICS SHA1 SHA2 USCAT FLAGM2 FRINT AFP RNDR",
        "[BOX64] BOX64ENV: Variables overridden:",
        "\tBOX64_DYNAREC_BIGBLOCK=3",
        "\tBOX64_DYNAREC_CALLRET=1",
        "\tBOX64_DYNAREC_SAFEFLAGS=2",
        "\tBOX64_DYNAREC_STRONGMEM=1",
        "\tBOX64_NOBANNER=1",
    )

    private fun plan(settings: LabSettings = LabSettings(), fex: Set<String> = emptySet()) = LabCommand.build(
        settings,
        LabTarget.BATTLENET,
        LabCommandContext(baseEnv = "", battleNetExe = "C:\\Program Files (x86)\\Battle.net\\Battle.net.exe", installedFex = fex),
    )

    @Test
    fun theFirstBlockIsTakenWithItsHeadAndOnlyIndentedLines() {
        val lines = listOf("100.0:0020:trace:process:x") + block + listOf("wine: something else", "\tBOX64_AFTER=1")
        val c = LabTranslatorCapture.scan(lines)
        assertEquals(
            listOf(
                "[BOX64] BOX64ENV: Variables overridden:",
                "BOX64_DYNAREC_BIGBLOCK=3",
                "BOX64_DYNAREC_CALLRET=1",
                "BOX64_DYNAREC_SAFEFLAGS=2",
                "BOX64_DYNAREC_STRONGMEM=1",
                "BOX64_NOBANNER=1",
            ),
            c.boxBlock,
        )
    }

    @Test
    fun aSecondBlockOfAnotherProcessIsIgnored() {
        val second = listOf("[BOX64] BOX64ENV: Variables overridden:", "\tBOX64_DYNAREC_WAIT=0")
        val c = LabTranslatorCapture.scan(block + listOf("mid") + second)
        assertFalse(c.boxBlock.contains("BOX64_DYNAREC_WAIT=0"))
        assertEquals(6, c.boxBlock.size)
    }

    @Test
    fun theBlockHasAtMostThirtyLines() {
        val long = listOf("[BOX64] BOX64ENV: Variables overridden:") + List(60) { "\tBOX64_X$it=1" }
        val c = LabTranslatorCapture.scan(long)
        assertEquals(30, c.boxBlock.size)
        assertEquals(LabTranslatorCapture.CUT_NOTE, c.boxBlock.last())
        val exact = LabTranslatorCapture.scan(listOf("[BOX64] BOX64ENV: Variables overridden:") + List(29) { "\tBOX64_X$it=1" })
        assertEquals(30, exact.boxBlock.size)
        assertFalse(exact.boxBlock.contains(LabTranslatorCapture.CUT_NOTE))
    }

    @Test
    fun noBlockMeansAnEmptyList() {
        assertTrue(LabTranslatorCapture.scan(listOf("100.0:0020:trace:process:x", "hello")).boxBlock.isEmpty())
    }

    @Test
    fun fexMessagesIgnoreWineTraceLinesThatOnlyNameAVariable() {
        val c = LabTranslatorCapture.scan(
            listOf(
                "178191.027:0034:trace:process:GetEnvironmentVariableW (L\"FEX_APP_DATA_LOCATION\" 000000000011EA28 1024)",
                "0034:err:module:load_so_dll libwow64fex.dll failed",
                "[FEX] something started",
                "plain line",
            ),
        )
        assertEquals(listOf("0034:err:module:load_so_dll libwow64fex.dll failed", "[FEX] something started"), c.fexMessages)
        assertTrue(LabTranslatorCapture.isFexMessage("libwow64fex.dll: upper=missing"))
        assertFalse(LabTranslatorCapture.isFexMessage("100.0:0034:trace:process:GetEnvironmentVariableW (L\"FEX_APP_CONFIG\" 0 1)"))
    }

    @Test
    fun requestedLinesAreComparedWithTheBlock() {
        val shown = LabTranslatorCapture.scan(block).boxBlock
        val checked = LabTranslatorCapture.check(
            listOf("BOX64_DYNAREC_SAFEFLAGS" to "2", "BOX64_DYNAREC_STRONGMEM" to "2", "BOX64_DYNAREC_ALIGNED_ATOMICS" to "0"),
            shown,
        )
        assertEquals("  BOX64_DYNAREC_SAFEFLAGS=2: listed", checked[0])
        assertEquals("  BOX64_DYNAREC_STRONGMEM=2: differs (block shows 1)", checked[1])
        assertEquals("  BOX64_DYNAREC_ALIGNED_ATOMICS=0: not listed", checked[2])
    }

    @Test
    fun sectionForBox64NamesStageRequestAndBlock() {
        val c = LabTranslatorCapture.scan(block)
        val lines = LabTranslatorReport.lines(plan(), c)
        assertEquals("Emulator: Box64", lines[0])
        assertEquals("Stability: 1 Careful", lines[1])
        assertTrue(lines[2], lines[2].contains("BOX64_DYNAREC_SAFEFLAGS=2") && lines[2].contains("BOX64_DYNAREC_ALIGNED_ATOMICS=0"))
        assertEquals("Translator log: off", lines[3])
        assertTrue(lines.contains("BOX64_DYNAREC_STRONGMEM=1"))
        assertTrue(lines.contains("  BOX64_DYNAREC_SAFEFLAGS=2: listed"))
        assertTrue(lines.contains("  BOX64_DYNAREC_ALIGNED_ATOMICS=0: not listed"))
    }

    @Test
    fun sectionWithoutABlockSaysSo() {
        val lines = LabTranslatorReport.lines(plan(), LabTranslatorCapture())
        assertTrue(lines.any { it.contains("(none found)") })
        assertFalse(lines.any { it.startsWith("-- Requested lines against the block") })
    }

    @Test
    fun sectionForFexListsTheFexMessagesNotTheBoxBlock() {
        val settings = LabSettings(translator = LabTranslator.FEX, fexVersion = "lab-2508", stability = LabStability.FEX)
        val p = plan(settings, fex = setOf("lab-2508"))
        val c = LabTranslatorCapture.scan(block + listOf("[FEX] hello"))
        val lines = LabTranslatorReport.lines(p, c)
        assertEquals("Emulator: FEXCore (FEX lab-2508)", lines[0])
        assertEquals("Stability: 4 FEX", lines[1])
        assertTrue(lines.contains("[FEX] hello"))
        assertFalse(lines.contains("BOX64_NOBANNER=1"))
    }

    @Test
    fun sectionWithoutTranslatorHasNothingToReport() {
        val lines = LabTranslatorReport.lines(plan(LabSettings(translator = LabTranslator.NONE)), LabTranslatorCapture.scan(block))
        assertTrue(lines.contains("(no translator chosen: nothing to report)"))
    }

    @Test
    fun translatorLogIsNamedInTheSection() {
        val lines = LabTranslatorReport.lines(plan(LabSettings(translatorLog = true)), LabTranslatorCapture())
        assertEquals("Translator log: on (BOX64_LOG=1, BOX64_DYNAREC_MISSING=1)", lines[3])
        assertNull(lines.firstOrNull { it.contains("Unhandled") })
    }
}
