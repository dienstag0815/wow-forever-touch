package app.gamenative.lab

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Nachweis "Originalordner unverändert" (Punkt 3 von labkopie): Abdruck, Vergleich, Kürzung, Sperre, Abschnitt im Protokoll. */
class LabOriginalPrintTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun game(): File {
        val root = tmp.newFolder("game")
        File(root, ".build.info").writeText("Product!STRING:0|Build\nforever|1\n")
        File(root, "Launcher.db").writeBytes(ByteArray(300) { it.toByte() })
        File(root, "small.txt").writeText("small file")
        File(root, "Data").mkdirs()
        File(root, "Data/data.000").writeBytes(ByteArray(5000) { 7 })
        File(root, "_forever_").mkdirs()
        File(root, "_forever_/.flavor.info").writeText("flavor")
        File(root, "_forever_/Client.exe").writeBytes(ByteArray(2_000_000) { 1 })
        return root
    }

    private fun pair(change: (File) -> Unit): List<PrintDeviation> {
        val root = game()
        val before = LabOriginalPrintLogic.take(root)
        change(root)
        return LabOriginalPrintLogic.compare(before, LabOriginalPrintLogic.take(root))
    }

    @Test
    fun unchangedFolderGivesNoDeviation() {
        val root = game()
        val a = LabOriginalPrintLogic.take(root)
        val b = LabOriginalPrintLogic.take(root)
        assertTrue(LabOriginalPrintLogic.compare(a, b).isEmpty())
        assertEquals(6, a.fileCount)
        assertEquals(root.walkTopDown().filter { it.isFile }.sumOf { it.length() }, a.bytes)
    }

    @Test
    fun changedFileIsReported() {
        val d = pair { File(it, "Data/data.000").writeBytes(ByteArray(5001) { 7 }) }
        assertEquals(1, d.size)
        assertEquals(PrintDeviation.Kind.CHANGED, d[0].kind)
        assertEquals("Data/data.000", d[0].path)
        assertTrue(LabOriginalPrintLogic.line(d[0]).startsWith("changed | Data/data.000 | 5000 -> 5001 | "))
    }

    @Test
    fun deletedFileIsReported() {
        val d = pair { File(it, "Launcher.db").delete() }
        assertEquals(listOf(PrintDeviation.Kind.MISSING), d.map { it.kind })
        assertEquals("Launcher.db", d[0].path)
        assertTrue(LabOriginalPrintLogic.line(d[0]).startsWith("missing | Launcher.db | 300 | "))
    }

    @Test
    fun newFileAndNewFolderAreReported() {
        val d = pair {
            File(it, ".product.db").writeText("new")
            File(it, "Cache").mkdirs()
        }
        assertEquals(setOf(".product.db", "Cache"), d.map { it.path }.toSet())
        assertTrue(d.all { it.kind == PrintDeviation.Kind.NEW })
    }

    @Test
    fun onlyTheTimeChangedIsReported() {
        val d = pair { assertTrue(File(it, "Data/data.000").setLastModified(1_600_000_000_000L)) }
        assertEquals(1, d.size)
        assertEquals(PrintDeviation.Kind.CHANGED, d[0].kind)
        // Gleiche Größe: die Zeile zeigt es an der Zeit, nicht am Inhalt.
        assertFalse(LabOriginalPrintLogic.line(d[0]).contains("content differs"))
    }

    @Test
    fun contentChangeWithSameSizeAndTimeIsSeenOnSmallFiles() {
        val root = game()
        val f = File(root, "small.txt")
        val time = f.lastModified()
        val before = LabOriginalPrintLogic.take(root)
        f.writeText("SMALL FILE") // gleiche Länge
        f.setLastModified(time)
        val d = LabOriginalPrintLogic.compare(before, LabOriginalPrintLogic.take(root))
        assertEquals(1, d.size)
        assertTrue(LabOriginalPrintLogic.line(d[0]).endsWith("| content differs"))
    }

    @Test
    fun hashesOnlyForIdentFilesAndSmallFilesInTheMainFolder() {
        val p = LabOriginalPrintLogic.take(game())
        assertNotNull(p.entries[".build.info"]!!.sha256)
        assertNotNull(p.entries["Launcher.db"]!!.sha256)
        assertNotNull(p.entries["small.txt"]!!.sha256)
        assertNotNull(p.entries["_forever_/.flavor.info"]!!.sha256)
        assertNull(p.entries["Data/data.000"]!!.sha256)
        assertNull(p.entries["_forever_/Client.exe"]!!.sha256)
        assertEquals(64, p.entries[".build.info"]!!.sha256!!.length)
    }

    @Test
    fun noFolderIsNotPresent() {
        val missing = LabOriginalPrintLogic.take(File(tmp.root, "nothing"))
        assertFalse(missing.present)
        assertEquals(listOf("not present", "unchanged: yes"), LabOriginalPrintLogic.sectionLines(missing, missing, emptyList()))
        assertFalse(LabOriginalPrintLogic.take(null).present)
    }

    @Test
    fun fileAppearingWhereNothingWasIsADeviation() {
        val root = tmp.newFolder("late")
        val gone = File(root.parentFile, "late-missing")
        val before = LabOriginalPrintLogic.take(gone)
        gone.mkdirs()
        File(gone, "a.txt").writeText("x")
        val d = LabOriginalPrintLogic.compare(before, LabOriginalPrintLogic.take(gone))
        assertEquals(1, d.size)
    }

    @Test
    fun deviationsAreCutAtFortyLinesWithoutAbsolutePaths() {
        val root = game()
        val before = LabOriginalPrintLogic.take(root)
        for (i in 0 until 60) File(root, "extra-%02d.txt".format(i)).writeText("x")
        val after = LabOriginalPrintLogic.take(root)
        val d = LabOriginalPrintLogic.compare(before, after)
        assertEquals(60, d.size)
        val lines = LabOriginalPrintLogic.lines(d)
        assertEquals(40, lines.size)
        assertTrue(lines.last().startsWith("... and 21 more"))
        assertTrue(lines.none { it.contains(root.path) || it.contains(root.name + File.separator) })
        val section = LabOriginalPrintLogic.sectionLines(before, after, d)
        assertTrue(section.contains("unchanged: no"))
        assertEquals(40, section.count { it.startsWith("new | ") || it.startsWith("... and") })
    }

    @Test
    fun sectionForUnchangedFolderSaysYesAndNamesCountsAndDuration() {
        val root = game()
        val a = LabOriginalPrintLogic.take(root)
        val b = LabOriginalPrintLogic.take(root)
        val lines = LabOriginalPrintLogic.sectionLines(a, b, LabOriginalPrintLogic.compare(a, b))
        assertTrue(lines.contains("unchanged: yes"))
        assertTrue(lines.first().startsWith("Files: 6, bytes: "))
        assertTrue(lines.any { it.startsWith("Fingerprint time: before ") && it.contains(" ms, after ") })
        assertTrue(lines.none { it.startsWith("new |") || it.startsWith("changed |") || it.startsWith("missing |") })
    }

    @Test
    fun sectionWithoutFingerprintsSaysUnknown() {
        val p = LabOriginalPrintLogic.take(game())
        assertTrue(LabOriginalPrintLogic.sectionLines(null, p, emptyList())[0].startsWith("unchanged: unknown"))
        assertTrue(LabOriginalPrintLogic.sectionLines(p, null, emptyList())[0].startsWith("unchanged: unknown"))
    }

    @Test
    fun controlCharactersInPathsAreReplaced() {
        val d = PrintDeviation(PrintDeviation.Kind.NEW, "a\nb\u0007c", null, PrintEntry(PrintKind.FILE, 1, 0))
        val line = LabOriginalPrintLogic.line(d)
        assertFalse(line.contains('\n'))
        assertTrue(line.contains("a?b?c"))
    }

    @Test
    fun theFingerprintTakingNeverWritesInTheOriginal() {
        val root = game()
        val times = root.walkTopDown().associate { it.path to it.lastModified() }
        val names = root.walkTopDown().map { it.path }.toSet()
        LabOriginalPrintLogic.take(root)
        assertEquals(names, root.walkTopDown().map { it.path }.toSet())
        assertEquals(times, root.walkTopDown().associate { it.path to it.lastModified() })
    }

    @Test
    fun stateRoundTripsThroughJson() {
        val p = LabOriginalPrintLogic.take(game())
        val s = LabOriginalGate.begun(OriginalCheckState(), p, "lab-1.log")
        val back = OriginalCheckState.fromJson(s.toJson())
        assertTrue(back.pending)
        assertEquals("lab-1.log", back.protocol)
        assertEquals(p.entries, back.before!!.entries)
        assertEquals(p.fileCount, back.before!!.fileCount)
        assertNull(back.unchanged)
        assertEquals(OriginalCheckState(), OriginalCheckState.fromJson("not json"))
        assertEquals(OriginalCheckState(), OriginalCheckState.fromJson(null))
    }

    @Test
    fun startIsLockedUntilTheUserConfirms() {
        val root = game()
        val before = LabOriginalPrintLogic.take(root)
        var state = LabOriginalGate.begun(OriginalCheckState(), before, "lab-1.log")
        assertFalse(LabOriginalGate.blocksStart(state))
        File(root, "Data/data.000").delete()
        val devs = LabOriginalPrintLogic.compare(before, LabOriginalPrintLogic.take(root))
        state = LabOriginalGate.afterCheck(state, devs, 1000L)
        assertFalse(state.pending)
        assertNull(state.before)
        assertEquals(false, state.unchanged)
        assertTrue(state.needsAck)
        assertTrue(LabOriginalGate.blocksStart(state))
        // Die Sperre übersteht das Speichern und Lesen.
        assertTrue(LabOriginalGate.blocksStart(OriginalCheckState.fromJson(state.toJson())))
        state = LabOriginalGate.acknowledge(state)
        assertFalse(LabOriginalGate.blocksStart(state))
    }

    @Test
    fun cleanCheckDoesNotBlock() {
        val p = LabOriginalPrintLogic.take(game())
        val state = LabOriginalGate.afterCheck(LabOriginalGate.begun(OriginalCheckState(), p, "x"), emptyList(), 5L)
        assertEquals(true, state.unchanged)
        assertFalse(LabOriginalGate.blocksStart(state))
    }

    @Test
    fun sectionIsInsertedBeforeEndOnceOnly() {
        val log = "head\n== Wine output ==\nline\n\n== End ==\nEnded: x\n"
        val once = LabOriginalSection.insert(log, listOf("unchanged: yes"))
        assertTrue(once.contains("\n== Original game folder ==\nunchanged: yes\n\n== End =="))
        assertEquals(once, LabOriginalSection.insert(once, listOf("unchanged: no")))
        assertTrue(LabOriginalSection.insert("no end marker\n", listOf("a")).endsWith("== Original game folder ==\na\n"))
    }
}
