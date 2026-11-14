package app.gamenative.report

import app.gamenative.utils.MachineIdentityLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class InstallTimelineTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val day = 86_400_000L
    private val jan1 = 1_767_225_600_000L // 2026-01-01 UTC

    private fun input(
        trace: List<MachineIdentityLog.Entry> = emptyList(),
        installer: String? = "com.android.vending",
        game: FolderAge? = FolderAge(jan1 + 3 * day, 120, true),
        wtf: FolderAge? = FolderAge(jan1 + 40 * day, 5, true),
    ) = TimelineInput(
        firstInstallMs = jan1 + 10 * day,
        lastUpdateMs = jan1 + 20 * day,
        installer = installer,
        gameFolder = game,
        wtfFolder = wtf,
        identityTrace = trace,
        identitySinceSec = (jan1 + 10 * day) / 1000,
        journalVersions = 3,
        journalFirstVersion = "2.3.1",
        journalLastVersion = "2.3.3",
    )

    private fun event(day: Int, e: MachineIdentityLog.Event) = MachineIdentityLog.Entry((jan1 + day * 86_400_000L) / 1000, "ab12cd34", 233, e)

    @Test
    fun theLinesNameInstallUpdateSourceFoldersIdentityAndVersions() {
        val text = InstallTimeline.lines(input(listOf(event(10, MachineIdentityLog.Event.FROM_FOLDER)))).joinToString("\n")
        assertTrue(text.contains("App first installed: 2026-01-11; last updated: 2026-01-21 (UTC)"))
        assertTrue(text.contains("Install source: com.android.vending"))
        assertTrue(text.contains("Game folder: oldest change 2026-01-04 (120 entries checked)"))
        assertTrue(text.contains("WTF folder: oldest change 2026-02-10 (5 entries checked)"))
        assertTrue(text.contains("Windows ID first recorded: 2026-01-11; current ID since: 2026-01-11"))
        assertTrue(text.contains("taken over from the game folder: yes (1x, last 2026-01-11)"))
        assertTrue(text.contains("App versions in the login journal: 3 (first 2.3.1, last 2.3.3)"))
    }

    @Test
    fun withoutTheFolderEventTheIdentityWasNotTakenOver() {
        val text = InstallTimeline.lines(input(listOf(event(10, MachineIdentityLog.Event.NEW_ON_REQUEST)))).joinToString("\n")
        assertTrue(text.contains("taken over from the game folder: no"))
        assertTrue(InstallTimeline.compact(input(listOf(event(10, MachineIdentityLog.Event.NEW_ON_REQUEST)))).last().contains("from game folder: no"))
    }

    @Test
    fun unknownThingsSayUnknownInsteadOfInventingADate() {
        val empty = TimelineInput(null, null, null, null, null, emptyList(), null, 0, null, null)
        val text = InstallTimeline.lines(empty).joinToString("\n")
        assertTrue(text.contains("App first installed: unknown; last updated: unknown"))
        assertTrue(text.contains("Install source: not reported"))
        assertTrue(text.contains("Game folder: not present"))
        assertTrue(text.contains("Windows ID first recorded: unknown"))
        assertTrue(text.contains("App versions in the login journal: 0"))
        assertFalse(text.contains("(first"))
    }

    @Test
    fun anIncompleteScanSaysSo() {
        val text = InstallTimeline.lines(input(game = FolderAge(jan1, 3000, false))).joinToString("\n")
        assertTrue(text.contains("not complete: older entries may exist"))
    }

    @Test
    fun compactIsTwoShortLinesWithTheKeyDates() {
        val lines = InstallTimeline.compact(input())
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("App installed 2026-01-11, updated 2026-01-21; source com.android.vending; game folder since 2026-01-04, WTF since 2026-02-10"))
        assertTrue(lines.all { it.length < 220 })
    }

    @Test
    fun onlyARealPackageNameCountsAsInstaller() {
        assertEquals("com.android.vending", InstallTimeline.installerName(" com.android.vending "))
        assertEquals("com.google.android.packageinstaller", InstallTimeline.installerName("com.google.android.packageinstaller"))
        assertNull(InstallTimeline.installerName(null))
        assertNull(InstallTimeline.installerName(""))
        assertNull(InstallTimeline.installerName("player@example.com"))
        assertNull(InstallTimeline.installerName("not a package"))
        assertNull(InstallTimeline.installerName("a".repeat(30) + "." + "b".repeat(100)))
    }

    @Test
    fun scanFindsTheOldestChangeAndCountsTheEntries() {
        val root = tmp.newFolder("game")
        val a = File(root, "a.txt").apply { writeText("x"); setLastModified(jan1 + 30 * day) }
        val sub = File(root, "Data").apply { mkdirs() }
        File(sub, "old.bin").apply { writeText("x"); setLastModified(jan1 + 5 * day) }
        File(sub, "new.bin").apply { writeText("x"); setLastModified(jan1 + 50 * day) }
        // Ein Ordner behält sein eigenes Datum; es gilt mit.
        sub.setLastModified(jan1 + 60 * day)
        root.setLastModified(jan1 + 70 * day)
        val age = InstallTimeline.scan(root)!!
        assertEquals(jan1 + 5 * day, age.oldestMs)
        assertEquals(4, age.checked)
        assertTrue(age.complete)
        assertTrue(a.isFile)
    }

    @Test
    fun scanStopsAtTheLimitAndSaysItIsNotComplete() {
        val root = tmp.newFolder("many")
        repeat(30) { File(root, "f$it.txt").writeText("x") }
        val age = InstallTimeline.scan(root, maxEntries = 10)!!
        assertEquals(10, age.checked)
        assertFalse(age.complete)
        val deep = tmp.newFolder("deep")
        File(deep, "a/b/c/d/e").mkdirs()
        assertFalse(InstallTimeline.scan(deep, maxDepth = 2)!!.complete)
    }

    @Test
    fun scanOfAMissingFolderIsNull() {
        assertNull(InstallTimeline.scan(File(tmp.root, "nope")))
    }

    @Test
    fun versionsInTheJournalAreCounted() {
        fun e(v: String) = JournalEntry(1, v, "fill", "all", false)
        assertEquals(Triple(2, "2.3.1", "2.3.3"), InstallTimeline.versionsIn(listOf(e("2.3.1"), e("2.3.1"), e("2.3.3"))))
        assertEquals(Triple(0, null, null), InstallTimeline.versionsIn(emptyList()))
    }
}
