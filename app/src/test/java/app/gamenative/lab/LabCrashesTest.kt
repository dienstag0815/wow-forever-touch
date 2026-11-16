package app.gamenative.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Absturzzähler des Labors (Auftrag labstabil2, Punkt 2). Die Zeilen sind erfunden, im Format der echten Meldungen von Wine. */
class LabCrashesTest {
    private val exe = """C:\\Program Files (x86)\\Battle.net\\Battle.net.exe"""

    /** Die zwei Zeilen von `+process`, wenn ein Prozess entsteht: erst Programm und Befehlszeile, dann Kennung und Hauptthread. */
    private fun created(stamp: String, creator: String, pid: String, tid: String, type: String?): List<String> {
        val t = if (type == null) "" else " --type=$type --user-data-dir=\\\"C:\\\\users\\\\xuser\\\\AppData\\\\Local\\\\CEF\\\"..."
        return listOf(
            "$stamp:$creator:trace:process:NtCreateUserProcess L\"\\\\??\\\\$exe\" image L\"$exe\" cmdline L\"\\\"$exe\\\"$t\" parent 0x0 machine 0",
            "$stamp:$creator:trace:process:NtCreateUserProcess L\"\\\\??\\\\$exe\" pid $pid tid $tid handles 0x14/0x18",
        )
    }

    private fun pageFault(thread: String) = "wine: Unhandled page fault on read access to 00000003 at address 24528C24 (thread $thread), starting debugger..."
    private fun illegal(thread: String) = "wine: Unhandled illegal instruction at address 24528C24 (thread $thread), starting debugger..."
    private fun exception(thread: String) = "wine: Unhandled exception 0xc0000005 at address 24528C24 (thread $thread), starting debugger..."
    private fun debugger(stamp: String, thread: String, pidDec: Int) =
        "$stamp:$thread:trace:process:CreateProcessInternalW app (null) cmdline L\"winedbg --auto $pidDec 396\", inherit 1, flags 0x8000400"

    @Test
    fun threeKindsAreCountedSeparately() {
        val lines = listOf(pageFault("0384"), "noise", pageFault("0385"), illegal("0386"), exception("0387"), exception("0388"), exception("0389"))
        val s = LabCrashScanner.scan(lines)
        assertEquals(6, s.total)
        assertEquals(2, s.count(LabCrashKind.PAGE_FAULT))
        assertEquals(1, s.count(LabCrashKind.ILLEGAL_INSTRUCTION))
        assertEquals(3, s.count(LabCrashKind.EXCEPTION))
        assertEquals("Crashes: 6 (page fault: 2, illegal instruction: 1, exception: 3)", LabCrashReport.summaryLine(s))
    }

    @Test
    fun aLineThatOnlyMentionsTheWordsIsNotACrash() {
        val s = LabCrashScanner.scan(
            listOf(
                "100.0:0010:trace:seh:dispatch Unhandled exception code c0000005",
                "Unhandled page fault is the text of a test",
                "err: wine: unhandled something",
            ),
        )
        assertEquals(0, s.total)
    }

    @Test
    fun pidProgramAndTypeComeFromTheProcessMessagesOfTheSameRun() {
        val lines = created("100.000", "0020", "0380", "0384", "renderer") +
            created("100.100", "0020", "0390", "0394", null) +
            listOf(pageFault("0384"), debugger("160.215", "0384", 0x380))
        val s = LabCrashScanner().also { sc -> lines.forEach { sc.add(it, 62_900L) } }
        val c = s.crashes.single()
        assertEquals(LabCrashKind.PAGE_FAULT, c.kind)
        assertEquals("0380", c.pid)
        assertEquals("Battle.net.exe", c.program)
        assertEquals("renderer", c.type)
        assertEquals(62L, c.elapsedSec)
    }

    @Test
    fun aKnownProcessWithoutTypeIsNoneNotUnknown() {
        val lines = created("100.000", "0020", "0390", "0394", null) + listOf(illegal("0394"), debugger("100.5", "0394", 0x390))
        val c = LabCrashScanner.scan(lines).crashes.single()
        assertEquals("0390", c.pid)
        assertEquals(LabCrash.TYPE_NONE, c.type)
        assertEquals("Battle.net.exe", c.program)
    }

    @Test
    fun withoutProcessMessagesProgramAndTypeAreUnknown() {
        val s = LabCrashScanner.scan(listOf(pageFault("0384")))
        val c = s.crashes.single()
        assertNull(c.pid)
        assertNull(c.program)
        assertNull(c.type)
        val line = LabCrashReport.crashLine(1, c)
        assertTrue(line, line.contains("pid unknown") && line.contains("program unknown") && line.contains("type=unknown") && line.contains("time unknown"))
    }

    @Test
    fun pidIsKnownFromTheDebuggerLineEvenWithoutProcessMessages() {
        val s = LabCrashScanner.scan(listOf(pageFault("0384"), debugger("160.2", "0384", 896)))
        val c = s.crashes.single()
        assertEquals("0380", c.pid)
        assertNull(c.program)
        assertNull(c.type)
    }

    @Test
    fun aDebuggerLineFarAfterTheCrashBelongsToSomethingElse() {
        val far = listOf(pageFault("0384")) + List(30) { "filler $it" } + listOf(debugger("999.0", "0010", 100))
        assertNull(LabCrashScanner.scan(far).crashes.single().pid)
    }

    @Test
    fun theDebuggerProcessItselfIsNotMistakenForTheCrashedOne() {
        // winedbg wird selbst über NtCreateUserProcess erzeugt, mit `winedbg --auto ...` in der Befehlszeile.
        val lines = created("100.000", "0020", "0380", "0384", "renderer") + listOf(
            pageFault("0384"),
            "160.2:0384:trace:process:NtCreateUserProcess L\"\\\\??\\\\C:\\\\windows\\\\system32\\\\winedbg.exe\" image L\"C:\\\\windows\\\\system32\\\\winedbg.exe\" cmdline L\"winedbg --auto 896 396\" parent 0x0 machine 0",
            "160.3:0384:trace:process:NtCreateUserProcess L\"\\\\??\\\\C:\\\\windows\\\\system32\\\\winedbg.exe\" pid 03a0 tid 03a4 handles 0x14/0x18",
        )
        val c = LabCrashScanner.scan(lines).crashes.single()
        assertEquals("0380", c.pid)
        assertEquals("Battle.net.exe", c.program)
        assertEquals("renderer", c.type)
    }

    @Test
    fun timeSinceStartFromTheWineTimestampsWhenTheClockIsNotGiven() {
        val lines = listOf("100.000:0020:trace:process:foo", "130.500:0020:trace:process:bar", pageFault("0020"))
        assertEquals(30L, LabCrashScanner.scan(lines).crashes.single().elapsedSec)
        assertNull(LabCrashScanner.scan(listOf(pageFault("0020"))).crashes.single().elapsedSec)
    }

    @Test
    fun sectionHasAtMostFortyLinesAndNoCommandLineRemnants() {
        val lines = created("100.000", "0020", "0380", "0384", "renderer").toMutableList()
        repeat(100) { lines += pageFault("0384") }
        val s = LabCrashScanner.scan(lines)
        assertEquals(100, s.total)
        val out = LabCrashReport.lines(s, outputCut = false)
        assertTrue(out.size.toString(), out.size <= LabCrashReport.MAX_LINES)
        assertTrue(out.last().startsWith("(+"))
        assertTrue(out.last(), out.last().contains("more not listed"))
        val text = out.joinToString("\n")
        for (leftover in listOf("--user-data-dir", "Program Files", "cmdline", "xuser", "\\")) assertFalse(leftover, text.contains(leftover))
        assertTrue(text.contains("type=renderer"))
    }

    @Test
    fun anEmptyRunIsReportedAsZero() {
        val out = LabCrashReport.lines(LabCrashScanner(), outputCut = false)
        assertEquals("Crashes: 0 (page fault: 0, illegal instruction: 0, exception: 0)", out.first())
        assertEquals(2, out.size)
    }

    @Test
    fun cutOutputIsCountedOnEveryLineAndSaysSo() {
        // Anfang 40 Byte, Ende 60 Byte: die Mitte entfällt, die Abstürze dort sind trotzdem gezählt.
        val cap = LabOutputCap(headBytes = 40, tailBytes = 60)
        val scanner = LabCrashScanner()
        val lines = listOf(pageFault("0384"), "a".repeat(30)) + List(20) { "filler line $it" } + listOf(illegal("0385"), exception("0386"))
        val inFile = mutableListOf<String>()
        for (l in lines) {
            scanner.add(l)
            cap.add(l)?.let { inFile += it }
        }
        assertTrue(cap.omittedLines > 0)
        assertEquals(3, scanner.total)
        val out = LabCrashReport.lines(scanner, outputCut = cap.omittedLines > 0, omittedLines = cap.omittedLines)
        assertTrue(out[1], out[1].startsWith("Output cut: ${cap.omittedLines} lines"))
        // Nur das, was in der Datei steht, zählt weniger: das ist der Unterschied, den der Vermerk benennt.
        val onlyWhatIsInTheFile = LabCrashScanner.scan(inFile + cap.tailText().lines())
        assertTrue(onlyWhatIsInTheFile.total < scanner.total)
    }
}
