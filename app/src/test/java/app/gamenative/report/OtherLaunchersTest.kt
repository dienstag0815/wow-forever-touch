package app.gamenative.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class OtherLaunchersTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val day = 86_400_000L
    private val install = 1_767_225_600_000L + 100 * day // 2026-04-11 UTC

    @Test
    fun thePackageListIsFixedShortAndWithoutThePackageOfThisApp() {
        assertEquals(
            listOf("com.winlator", "app.gamenative", "app.gamenative.gold", "app.wowforeverywhere", "com.wehavephones", "app.aynthorwow"),
            OtherLaunchers.PACKAGES.map { it.packageName },
        )
        // Diese App und das Original-Projekt haben dieselbe Kennung: sie wäre immer "installiert" und gehört nicht in die Liste.
        assertFalse(OtherLaunchers.PACKAGES.any { it.packageName == "app.wowforever" })
        assertEquals(OtherLaunchers.PACKAGES.size, OtherLaunchers.PACKAGES.map { it.packageName }.distinct().size)
    }

    @Test
    fun theManifestAsksForExactlyThesePackagesAndNeverForAllPackages() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        for (p in OtherLaunchers.PACKAGES) assertTrue(p.packageName, manifest.contains("<package android:name=\"${p.packageName}\" />"))
        assertFalse(manifest.contains("android.permission.QUERY_ALL_PACKAGES"))
    }

    @Test
    fun withoutAnyHitTheListSaysNotInstalledAndThereAreNoLabels() {
        val lines = OtherLaunchers.installedLines { false }
        assertEquals(OtherLaunchers.PACKAGES.size, lines.size)
        assertTrue(lines.all { it.endsWith(": not installed") })
        assertTrue(OtherLaunchers.installedLabels { false }.isEmpty())
        assertTrue(OtherLaunchers.compact(emptyList(), emptyList(), "origin").startsWith("other launcher apps installed: none; origin; other files in the game folder not from this app: 0"))
    }

    @Test
    fun aHitShowsOnlyYesAndAFailingLookupCountsAsNo() {
        val lines = OtherLaunchers.installedLines { it == "com.winlator" }
        assertEquals("com.winlator (Winlator): installed", lines.first())
        assertEquals(1, lines.count { it.endsWith(": installed") })
        assertEquals(listOf("Winlator"), OtherLaunchers.installedLabels { it == "com.winlator" })
        assertTrue(OtherLaunchers.installedLabels { throw SecurityException("no") }.isEmpty())
    }

    private fun game(): File = tmp.newFolder("game").also {
        File(it, "_forever_/Logs").mkdirs()
        File(it, "_forever_/WTF/Profiles").mkdirs()
    }

    @Test
    fun anExpectedFolderGivesNoTraces() {
        val root = game()
        File(root, "Data").mkdirs()
        File(root, ".build.info").writeText("x")
        File(root, "wowforever-kennung.txt").writeText("x")
        File(root, "wowforever-kennung-kopie.key").writeText("x")
        File(root, "_forever_/Wow-ARM64.exe").writeText("x")
        File(root, "_forever_/.flavor.info").writeText("x")
        File(root, "_forever_/Logs/Connection.log").apply { writeText("x"); setLastModified(install + day) }
        assertTrue(OtherLaunchers.scan(root, listOf("_forever_")).isEmpty())
    }

    @Test
    fun otherLaunchersLeaveProductFoldersAndNamedFiles() {
        val root = game()
        File(root, "_retail_").mkdirs()
        File(root, "_classic_era_").mkdirs()
        File(root, "WindowsIdentity.log").writeText("0123456789")
        File(root, "Foreverywhere-state.json").writeText("{}")
        File(root, "_forever_/gamenative-notes.txt").writeText("x")
        File(root, "Notizen.txt").writeText("x")
        val traces = OtherLaunchers.scan(root, listOf("_forever_")).associateBy { it.path }
        assertTrue(traces.getValue("_retail_").reason.startsWith("product folder"))
        assertTrue(traces.getValue("_classic_era_").isDirectory)
        assertEquals("name contains \"identity\"", traces.getValue("WindowsIdentity.log").reason)
        assertEquals(10L, traces.getValue("WindowsIdentity.log").sizeBytes)
        assertEquals("name contains \"foreverywhere\"", traces.getValue("Foreverywhere-state.json").reason)
        assertEquals("name contains \"gamenative\"", traces.getValue("_forever_/gamenative-notes.txt").reason)
        assertEquals("not expected here (origin unknown)", traces.getValue("Notizen.txt").reason)
    }

    @Test
    fun oldLogsAndWtfFilesAreNoTracesButAreFoundAsOldFilesWithNameDateSizeOnly() {
        val root = game()
        File(root, "_forever_/Logs/Connection.log").apply { writeText("account=player@example.com"); setLastModified(install - 50 * day) }
        File(root, "_forever_/Logs/Movie.log").apply { writeText("x"); setLastModified(install + 5 * day) }
        File(root, "_forever_/WTF/Profiles/saved.txt").apply { writeText("x"); setLastModified(install - 2 * day) }
        File(root, "_forever_/WTF/Config.wtf").apply { writeText("x"); setLastModified(install + 1 * day) }
        assertTrue(OtherLaunchers.scan(root, listOf("_forever_")).isEmpty())
        val old = OtherLaunchers.oldFileTraces(root, listOf("_forever_"), install)
        assertEquals(listOf("_forever_/Logs/Connection.log", "_forever_/WTF/Profiles/saved.txt"), old.map { it.path })
        val summary = OtherLaunchers.summarizeOld(old)
        val text = OtherLaunchers.report(OtherLaunchers.installedLines { false }, emptyList(), "origin line", summary)
        assertTrue(text.contains("2 files, oldest 2026-02-20, newest 2026-04-09"))
        assertTrue(text.contains("_forever_/Logs/Connection.log (2026-02-20)"))
        // Nur Name und Datum: der Inhalt der Datei kommt nie vor.
        assertFalse(text.contains("player@example.com"))
        assertFalse(OtherLaunchers.compact(emptyList(), emptyList(), "origin line").contains("player"))
    }

    @Test
    fun withoutAFirstInstallDateNoFileCountsAsOlder() {
        val root = game()
        File(root, "_forever_/Logs/Connection.log").apply { writeText("x"); setLastModified(1_000_000_000L) }
        assertTrue(OtherLaunchers.oldFileTraces(root, listOf("_forever_"), null).isEmpty())
    }

    private fun oldFiles(n: Int) = (1..n).map { FolderTrace("_forever_/Logs/file$it.log", install - (400L - it) * day, 10L, false, "older") }

    @Test
    fun theSummaryOfOldFilesForZeroThreeAndTwoHundredFiles() {
        assertEquals(OldFiles(0, null, null, emptyList()), OtherLaunchers.summarizeOld(emptyList()))
        val three = OtherLaunchers.summarizeOld(oldFiles(3).reversed())
        assertEquals(3, three.count)
        assertEquals(install - 399 * day, three.oldestMs)
        assertEquals(install - 397 * day, three.newestMs)
        assertEquals(3, three.examples.size)
        val many = OtherLaunchers.summarizeOld(oldFiles(200))
        assertEquals(200, many.count)
        assertEquals(OtherLaunchers.MAX_EXAMPLES, many.examples.size)
        assertEquals(install - 399 * day, many.oldestMs)
        assertEquals(install - 200 * day, many.newestMs)
        assertTrue(many.examples.first().startsWith("_forever_/Logs/file1.log ("))
        val text = OtherLaunchers.report(emptyList(), emptyList(), "origin line", many)
        assertTrue(text.contains("200 files"))
        assertTrue(text.contains("examples (5 of 200)"))
        assertFalse(text.contains("file6.log"))
        assertTrue(OtherLaunchers.report(emptyList(), emptyList(), "origin line", OtherLaunchers.summarizeOld(emptyList())).contains("(none)"))
    }

    @Test
    fun theShortLineCountsOnlyFilesNotFromThisAppAndNeverOldFiles() {
        val traces = listOf(
            FolderTrace("_retail_", install, 0, true, "product folder of another launcher or a copy from a PC"),
            FolderTrace("Notizen.txt", install, 3, false, "not expected here (origin unknown)"),
        )
        val line = OtherLaunchers.compact(listOf("Winlator"), traces, "game folder since 2026-01-01")
        assertEquals("other launcher apps installed: Winlator; game folder since 2026-01-01; other files in the game folder not from this app: 2", line)
        assertFalse(line.contains("older than install"))
    }

    @Test
    fun theListOfTracesIsCappedAndAMissingGameFolderGivesNone() {
        val root = tmp.newFolder("many")
        repeat(60) { File(root, "x$it.dat").writeText("x") }
        assertEquals(OtherLaunchers.MAX_TRACES, OtherLaunchers.scan(root, emptyList()).size)
        assertTrue(OtherLaunchers.scan(File(tmp.root, "missing"), listOf("_forever_")).isEmpty())
    }

    @Test
    fun theReportListsEveryPackageAndTheNoteAboutTheOwnId() {
        val text = OtherLaunchers.report(OtherLaunchers.installedLines { false }, emptyList(), "origin line", OtherLaunchers.summarizeOld(emptyList()))
        for (p in OtherLaunchers.PACKAGES) assertTrue(text.contains(p.packageName))
        assertTrue(text.contains("app.wowforever, the original project and its forks, is not listed"))
        assertTrue(text.contains("(none)"))
        assertTrue(text.contains("Age of the game folder: origin line"))
    }

    @Test
    fun theManifestAsksForTheGeneralHttpsLinkSoThatABrowserCanBeSeen() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("<data android:scheme=\"https\" />"))
        assertTrue(manifest.contains("android.intent.category.BROWSABLE"))
    }
}
