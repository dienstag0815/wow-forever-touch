package app.gamenative.lab

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Der Stufentest des Labors (Punkt 3 von labstart2): Inhalt der Batch-Datei und Auswertung der Ergebnisdatei. */
class LabStepTestTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val batch = LabStepTest.batchText()

    @Test
    fun batchIsAsciiWithCrlfOnly() {
        assertTrue(batch.all { it.code in 0..127 })
        assertTrue(batch.endsWith("\r\n"))
        assertFalse("lone LF", batch.replace("\r\n", "").contains('\n'))
        assertFalse("lone CR", batch.replace("\r\n", "").contains('\r'))
    }

    @Test
    fun theStepsComeInTheAgreedOrderAndAreAnnouncedBeforeTheirWork() {
        val lines = batch.split("\r\n")
        fun at(text: String) = lines.indexOfFirst { it.contains(text) }.also { assertTrue("missing: $text", it >= 0) }
        val order = listOf(
            "STEP 1 begin", "STEP 2 begin", "dir L:\\ 2>&1", "STEP 3 begin", "dir C:\\windows\\winhandler.exe C:\\windows\\wfm.exe",
            "STEP 4 begin", "C:\\windows\\syswow64\\cmd.exe /c echo x86-32 ok", "STEP 4 result", "STEP 5 begin", "start \"\" /wait \"L:\\Battle.net-Setup.exe\"",
            "STEP 5 result", "STEP 6 done",
        ).map { at(it) }
        assertEquals(order.sorted(), order)
        // Vor jedem Schritt steht seine Anfangszeile.
        assertTrue(at("STEP 2 begin") < at("dir L:\\ 2>&1"))
        assertTrue(at("STEP 4 begin") < at("syswow64\\cmd.exe /c"))
        assertTrue(at("STEP 5 begin") < at("start \"\" /wait"))
    }

    @Test
    fun pathsAreLiteral() {
        assertTrue(batch.contains(">L:\\labtest.txt echo STEP 1 begin: start %DATE% %TIME%"))
        assertTrue(batch.contains("if exist C:\\windows\\winhandler.exe"))
        assertTrue(batch.contains("if exist C:\\windows\\wfm.exe"))
        assertTrue(batch.contains("if not exist L:\\Battle.net-Setup.exe goto noinstaller"))
        assertEquals("C:\\windows\\system32\\cmd.exe", LabStepTest.CMD_EXE)
        assertEquals("L:\\labtest.cmd", LabStepTest.BATCH_WIN_PATH)
        assertEquals("L:\\labtest.txt", LabStepTest.RESULT_WIN_PATH)
    }

    @Test
    fun theHelpersAreOnlyChecked_neverStarted() {
        for (line in batch.split("\r\n")) {
            val startsIt = Regex("^(>>?\\S+ )?C:\\\\windows\\\\(winhandler|wfm)\\.exe").containsMatchIn(line) || line.startsWith("start") && line.contains("winhandler")
            assertFalse("started: $line", startsIt)
        }
    }

    @Test
    fun withoutTheInstallerTheBatchEndsAfterStep4WithANote() {
        val lines = batch.split("\r\n")
        val jump = lines.indexOf("if not exist L:\\Battle.net-Setup.exe goto noinstaller")
        val label = lines.indexOf(":noinstaller")
        val skip = lines.indexOfFirst { it.contains("STEP 5 SKIPPED") }
        val end = lines.indexOf(":end")
        assertTrue(jump > lines.indexOfFirst { it.contains("STEP 4 result") })
        assertTrue(label in (jump + 1) until skip)
        assertTrue(skip < end)
    }

    @Test
    fun evaluateFindsTheLastStep() {
        assertEquals(LabStepResult(0, 0, false, false), LabStepTest.evaluate(null))
        assertEquals(LabStepResult(0, 0, false, false), LabStepTest.evaluate(""))
        assertEquals(LabStepResult(1, 0, false, false), LabStepTest.evaluate("STEP 1 begin: start 15.11.2026 10:00:00,00\r\n"))
        val upTo4 = "STEP 1 begin: x\nSTEP 1 result: batch is running\nSTEP 2 begin: dir\n Volume in drive L\nSTEP 2 result: listing done\nSTEP 3 begin: y\nSTEP 3 result: check done\nSTEP 4 begin: z\n"
        assertEquals(LabStepResult(4, 3, false, false), LabStepTest.evaluate(upTo4))
        val all = upTo4 + "x86-32 ok\nSTEP 4 result: exit code 0\nSTEP 5 begin: s\nSTEP 5 result: exit code 0\nSTEP 6 done\n"
        assertEquals(LabStepResult(5, 5, true, false), LabStepTest.evaluate(all))
        val noInstaller = upTo4 + "STEP 4 result: exit code 0\nSTEP 5 SKIPPED: L:\\Battle.net-Setup.exe is missing, test ends after step 4\n"
        assertEquals(LabStepResult(4, 4, false, true), LabStepTest.evaluate(noInstaller))
    }

    @Test
    fun evaluateIgnoresLinesThatAreNotOfTheBatch() {
        val text = listOf("STEP 9 begin", "STEPS 3 begin", " STEP 2 begin: ok", "STEP 7 begin: too far", "something STEP 4 begin", "STEP 0 result")
            .joinToString("\n")
        val r = LabStepTest.evaluate(text)
        assertEquals(2, r.lastBegun)
        assertEquals(0, r.lastCompleted)
    }

    @Test
    fun summaryNamesTheLastStep() {
        assertTrue(LabStepTest.summary(LabStepTest.evaluate(null)).contains("no step reached"))
        assertTrue(LabStepTest.summary(LabStepResult(4, 3, false, false)).contains("last step reached 4 of 6, finished up to 3"))
        assertTrue(LabStepTest.summary(LabStepResult(5, 5, true, false)).contains("all 6 steps"))
        assertTrue(LabStepTest.summary(LabStepResult(4, 4, false, true)).contains("installer missing"))
    }

    @Test
    fun prepareWritesTheBatchAndRemovesAnOldResult() {
        val share = File(tmp.root, "share")
        share.mkdirs()
        File(share, "labtest.txt").writeText("STEP 6 done\n")
        LabStepTest.prepare(share)
        assertFalse(File(share, "labtest.txt").exists())
        assertEquals(batch, String(File(share, "labtest.cmd").readBytes(), Charsets.US_ASCII))
        assertNull(LabStepTest.lastResult(share))
    }

    @Test
    fun readResultIsCappedAndRedacted() {
        val share = File(tmp.root, "s2").apply { mkdirs() }
        val big = "STEP 1 begin: x\n" + "a".repeat(LabStepTest.MAX_READ_BYTES + 1000) + "\n"
        File(share, "labtest.txt").writeText(big)
        val text = LabStepTest.readResult(share)!!
        assertTrue(text.length < LabStepTest.MAX_READ_BYTES + 200)
        assertTrue(text.contains("(cut off at 64 kB)"))
        File(share, "labtest.txt").writeText("STEP 1 begin: mail someone@example.org\nSTEP 2 begin: y\n")
        val redacted = LabStepTest.readResult(share)!!
        assertFalse(redacted, redacted.contains("someone@example.org"))
        assertEquals(2, LabStepTest.lastResult(share)!!.lastBegun)
        assertNull(LabStepTest.readResult(File(tmp.root, "nothing")))
    }
}
