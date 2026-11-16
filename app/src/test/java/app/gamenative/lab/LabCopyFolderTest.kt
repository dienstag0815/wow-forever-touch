package app.gamenative.lab

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** labkopie2 Punkt 4: Abschnitt `== Game copy folder ==` im Labor-Bericht. */
class LabCopyFolderTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val game = LabIds.COPY_GAME_DIR
    private lateinit var area: File

    private fun setUp() {
        area = tmp.newFolder("gamecopy", "game")
        File(area, "$game/Data").mkdirs()
        File(area, "$game/_forever_/Interface").mkdirs()
        File(area, "$game/.build.info").writeText("build")
        File(area, "$game/Data/data.000").writeBytes(ByteArray(900) { 1 })
        File(area, "$game/_forever_/Client.exe").writeBytes(ByteArray(400) { 2 })
        File(area, "$game/_forever_/Interface/deep.txt").writeText("deeper than two levels")
    }

    private fun section(before: OriginalPrint?, after: OriginalPrint, mounted: Boolean = true) = LabCopyFolderLogic.sectionLines(before, after, mounted)

    @Test
    fun theFingerprintHasNoContentsAndFollowsNoLinks() {
        setUp()
        val p = LabCopyFolderLogic.take(area)
        assertTrue(p.present)
        assertEquals(4, p.fileCount)
        assertEquals(5L + 900 + 400 + 22, p.bytes)
        assertTrue(p.entries.values.all { it.sha256 == null })
        assertEquals(PrintKind.DIR, p.entries[game]!!.kind)
        assertFalse(LabCopyFolderLogic.take(File(tmp.root, "missing")).present)
        assertFalse(LabCopyFolderLogic.take(null).present)
    }

    @Test
    fun theListingHasTheTwoLevelsOnly() {
        setUp()
        val lines = LabCopyFolderLogic.listing(LabCopyFolderLogic.take(area))
        // Hauptordner: .build.info, Data, _forever_; darin: Client.exe, Interface. Nichts tiefer (Data/data.000, Interface/deep.txt).
        assertEquals(
            listOf(".build.info", "Data", "_forever_", "_forever_/Client.exe", "_forever_/Interface"),
            lines.map { it.substringBefore(" | ") },
        )
        assertTrue(lines[0], lines[0].startsWith(".build.info | file | 5 | "))
        assertTrue(lines[1], lines[1].startsWith("Data | folder | - | -"))
        assertTrue(lines.none { it.contains("data.000") || it.contains("deep.txt") })
        assertTrue(LabCopyFolderLogic.listing(OriginalPrint.NOT_PRESENT).isEmpty())
    }

    @Test
    fun theListingIsCutAtEightyLinesAndSaysHowManyAreMissing() {
        setUp()
        for (i in 1..120) File(area, "$game/f%03d.bin".format(i)).writeText("x")
        val lines = LabCopyFolderLogic.listing(LabCopyFolderLogic.take(area))
        assertEquals(80, lines.size)
        assertTrue(lines.last(), lines.last().startsWith("... and "))
        assertTrue(lines.last().endsWith("more entries not listed"))
        // 120 Dateien + .build.info + Data + _forever_ + Client.exe + Interface = 125; 79 gezeigt, 46 nicht.
        assertEquals("... and 46 more entries not listed", lines.last())
    }

    @Test
    fun theChangesNameNewChangedAndRemovedWithPathSizeAndTime() {
        setUp()
        val before = LabCopyFolderLogic.take(area)
        File(area, "$game/.product.db").writeText("launcher")
        File(area, "$game/Launcher").mkdirs()
        File(area, "$game/Launcher/x.bin").writeBytes(ByteArray(7))
        File(area, "$game/Data/data.000").writeBytes(ByteArray(901))
        assertTrue(File(area, "$game/_forever_/Client.exe").delete())
        val after = LabCopyFolderLogic.take(area)
        val lines = LabCopyFolderLogic.changeLines(before, after)
        assertEquals(
            listOf(
                "$game/.product.db",
                "$game/Data/data.000",
                "$game/Launcher",
                "$game/Launcher/x.bin",
                "$game/_forever_/Client.exe",
            ),
            lines.map { it.split(" | ")[1] },
        )
        assertEquals(listOf("new", "changed", "new", "new", "removed"), lines.map { it.substringBefore(" | ") })
        assertTrue(lines[0], lines[0].startsWith("new | $game/.product.db | 8 | "))
        assertTrue(lines[1], lines[1].contains("900 -> 901"))
        assertTrue(lines[4], lines[4].startsWith("removed | $game/_forever_/Client.exe | 400 | "))
        // Keine Dateiinhalte im Abschnitt.
        val text = section(before, after).joinToString("\n")
        assertFalse(text.contains("launcher"))
        assertFalse(text.contains("deeper than two levels"))
    }

    @Test
    fun noChangeMeansNoneAndTheCountsAreBeforeAndAfter() {
        setUp()
        val before = LabCopyFolderLogic.take(area)
        val lines = section(before, LabCopyFolderLogic.take(area))
        assertTrue(lines.toString(), lines.any { it == "Whole copy after the run: 4 files, 1327 bytes" })
        assertTrue(lines.any { it == "Whole copy before the run: 4 files, 1327 bytes" })
        assertTrue(lines.any { it.startsWith("-- changes during the run (new / changed / removed): none") })
        assertTrue(lines.any { it == "Mounted in this run: yes, as drive K:" })
        assertTrue(lines.any { it.startsWith("Game folder: K:\\World of Warcraft\\ (present)") })
    }

    @Test
    fun theChangesAreCutAtFortyLines() {
        setUp()
        val before = LabCopyFolderLogic.take(area)
        for (i in 1..70) File(area, "$game/new%03d.db".format(i)).writeText("x")
        val lines = LabCopyFolderLogic.changeLines(before, LabCopyFolderLogic.take(area))
        assertEquals(40, lines.size)
        assertEquals("... and 31 more changes not listed", lines.last())
    }

    @Test
    fun withoutAFingerprintBeforeTheChangesAreUnknownAndWithoutAFolderTheSectionSaysSo() {
        setUp()
        val unknown = section(null, LabCopyFolderLogic.take(area), mounted = false)
        assertTrue(unknown.toString(), unknown.any { it.contains("unknown (no fingerprint before the start)") })
        assertTrue(unknown.any { it == "-- changes during the run: unknown --" })
        assertTrue(unknown.any { it == "Mounted in this run: no" })
        assertEquals(listOf("no game copy folder"), section(OriginalPrint.NOT_PRESENT, OriginalPrint.NOT_PRESENT))
    }

    @Test
    fun controlCharactersInANameBecomeQuestionMarks() {
        setUp()
        val name = "bad\u0007name.txt"
        val made = runCatching { File(area, "$game/$name").writeText("x") }.isSuccess
        if (!made) return
        val lines = LabCopyFolderLogic.listing(LabCopyFolderLogic.take(area))
        assertTrue(lines.any { it.startsWith("bad?name.txt | file") })
        assertTrue(lines.none { it.contains('\u0007') })
    }

    @Test
    fun theSectionGoesBeforeTheEndMarkOnlyOnce() {
        val text = "head\n\n== End ==\nexit 0\n"
        val once = LabCopyFolderSection.insert(text, listOf("a", "b"))
        assertTrue(once, once.contains("\n== Game copy folder ==\na\nb\n\n== End =="))
        assertEquals(once, LabCopyFolderSection.insert(once, listOf("c")))
        assertTrue(LabCopyFolderSection.insert("no end marker\n", listOf("a")).endsWith("== Game copy folder ==\na\n"))
        assertEquals("Game copy folder", LabCopyFolderSection.TITLE)
    }
}
