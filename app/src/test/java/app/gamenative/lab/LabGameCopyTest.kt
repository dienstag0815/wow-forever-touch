package app.gamenative.lab

import java.io.File
import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Spielkopie (Punkte 1 und 2 von labkopie): Pfadregeln, Kopieren, Fortsetzen, Löschen, Einbinden, Durchstich. */
class LabGameCopyTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var original: File
    private lateinit var labRoot: File
    private lateinit var area: File
    private lateinit var content: File

    private fun setUp() {
        original = tmp.newFolder("storage", "game")
        File(original, ".build.info").writeText("Product!STRING:0\nforever\n")
        File(original, "Data/indices").mkdirs()
        File(original, "Data/data.000").writeBytes(ByteArray(700_000) { (it % 251).toByte() })
        File(original, "Data/indices/a.index").writeBytes(ByteArray(1234) { 3 })
        File(original, "_forever_").mkdirs()
        File(original, "_forever_/Client.exe").writeBytes(ByteArray(300_000) { 9 })
        File(original, "Empty").mkdirs()
        labRoot = tmp.newFolder("app", "files", "bnetlab")
        area = File(labRoot, "gamecopy/game")
        content = File(area, LabIds.COPY_GAME_DIR)
    }

    /** Wie der Job nach einer fertigen Kopie: die Marke für den neuen Aufbau steht. */
    private fun markLayoutCurrent() = LabCopyLayout.writeMark(File(labRoot, "gamecopy"), LabIds.COPY_LAYOUT_FILE, LabCopyLayout.layoutJson())

    private fun copy(cancelAfterFiles: Int = -1): CopyOutcome {
        var seen = 0
        return LabGameCopier.copy(original, content, { cancelAfterFiles >= 0 && seen >= cancelAfterFiles }, { seen = it.filesDone })
    }

    /** Pfad ohne Laufwerksbuchstaben und mit `/`: so sieht `drives` auf Android aus (unter Windows würde `C:` die Laufwerksliste zerlegen). */
    private fun posix(f: File): String = f.absolutePath.replace('\\', '/').substringAfter(':')

    private fun listing(dir: File): Map<String, Long> =
        dir.walkTopDown().filter { it != dir }.associate { it.relativeTo(dir).path.replace('\\', '/') to (if (it.isFile) it.length() else -1L) }

    // --- Pfadregeln ---

    @Test
    fun pathCheckRefusesSameInsideAndContaining() {
        setUp()
        assertEquals(PathCheck.OK, LabCopyPaths.check(original, content))
        assertEquals(PathCheck.SAME, LabCopyPaths.check(original, original))
        assertEquals(PathCheck.SAME, LabCopyPaths.check(original, File(original, "Data/..")))
        assertEquals(PathCheck.TARGET_IN_ORIGINAL, LabCopyPaths.check(original, File(original, "Data/copy")))
        assertEquals(PathCheck.ORIGINAL_IN_TARGET, LabCopyPaths.check(original, original.parentFile))
        assertEquals(PathCheck.NO_ORIGINAL, LabCopyPaths.check(null, content))
        // Ein Ordner mit gleichem Namensanfang liegt nicht darin.
        assertEquals(PathCheck.OK, LabCopyPaths.check(original, File(original.parentFile, "game2")))
    }

    @Test
    fun pathCheckSeesThroughALink() {
        setUp()
        val link = File(tmp.root, "link-to-original")
        val made = runCatching { Files.createSymbolicLink(link.toPath(), original.toPath()) }.isSuccess
        assumeTrue("no symbolic links on this system", made)
        assertEquals(PathCheck.SAME, LabCopyPaths.check(original, link))
        assertEquals(PathCheck.TARGET_IN_ORIGINAL, LabCopyPaths.check(original, File(link, "Data/x")))
    }

    // --- Kanonischer Pfad (ohne Verknüpfungen, läuft überall) ---

    private fun realSlash(f: File): String = f.toPath().toRealPath().toString().replace('\\', '/')

    @Test
    fun canonicalOfAnExistingFolderIsItsRealPath() {
        val dir = tmp.newFolder("canon", "here")
        assertEquals(realSlash(dir), LabCopyPaths.canonical(dir))
    }

    @Test
    fun canonicalOfAMissingChildKeepsTheRealPathOfTheFolderAbove() {
        val dir = tmp.newFolder("canon2")
        assertEquals(realSlash(dir) + "/kind/x", LabCopyPaths.canonical(File(dir, "kind/x")))
    }

    @Test
    fun canonicalNormalizesDotDotInTheMissingPart() {
        val dir = tmp.newFolder("canon3", "sub")
        assertEquals(realSlash(dir.parentFile) + "/x", LabCopyPaths.canonical(File(dir, "../nope/../x")))
        assertEquals(realSlash(dir) + "/x", LabCopyPaths.canonical(File(dir, "../sub/./x")))
    }

    @Test
    fun copierRefusesATargetThatTouchesTheOriginal() {
        setUp()
        val before = listing(original)
        for (bad in listOf(original, File(original, "Data/copy"), original.parentFile)) {
            val out = LabGameCopier.copy(original, bad, { false }, {})
            assertTrue("$bad", out is CopyOutcome.Refused)
        }
        assertEquals(before, listing(original))
    }

    @Test
    fun targetWriterRejectsPathsOutsideTheCopyAndInsideTheOriginal() {
        setUp()
        val t = LabCopyTarget(content, original)
        assertTrue(runCatching { t.resolve("../x") }.isFailure)
        assertTrue(runCatching { t.resolve("/abs") }.isFailure)
        assertTrue(runCatching { t.resolve("a/../../x") }.isFailure)
        assertTrue(runCatching { t.resolve("a/b.txt") }.isSuccess)
        assertTrue(runCatching { t.remove(content) }.isFailure)
        assertTrue(runCatching { t.remove(original) }.isFailure)
        assertTrue(runCatching { LabCopyTarget(original, original) }.isFailure)
        assertTrue(runCatching { LabCopyTarget(File(original, "Data"), original) }.isFailure)
    }

    @Test
    fun spellingsCoverTheSharedStorageNames() {
        val sp = LabCopyPaths.spellings(File("/storage/emulated/0/Some Game"))
        assertTrue(sp.containsAll(listOf("/storage/emulated/0/Some Game", "/sdcard/Some Game", "/data/media/0/Some Game", "/storage/self/primary/Some Game")))
        assertTrue(LabCopyPaths.mentionsOriginal("X:/sdcard/Some Game/Data", File("/storage/emulated/0/Some Game")))
        assertFalse(LabCopyPaths.mentionsOriginal("L:/data/data/app/files/bnetlab/share", File("/storage/emulated/0/Some Game")))
        assertFalse(LabCopyPaths.mentionsOriginal("anything", null))
    }

    @Test
    fun driveHostsAreSplitLikeTheContainerDoes() {
        assertEquals(listOf("/a/share", "/b/game"), LabCopyPaths.driveHosts("L:/a/shareK:/b/game"))
        assertEquals(listOf("/a/share"), LabCopyPaths.driveHosts("L:/a/share"))
    }

    // --- Platz ---

    @Test
    fun neededSpaceIsSizePlusTwoGigabytes() {
        val gb = 1024L * 1024 * 1024
        assertEquals(12 * gb, LabCopySpace.needed(10 * gb))
        assertTrue(LabCopySpace.fits(12 * gb, 10 * gb))
        assertFalse(LabCopySpace.fits(12 * gb - 1, 10 * gb))
        // Angefangene Kopie: was schon im Ziel liegt, zählt nicht noch einmal.
        assertEquals(4 * gb, LabCopySpace.needed(10 * gb, 8 * gb))
        assertEquals(2 * gb, LabCopySpace.needed(10 * gb, 50 * gb))
    }

    // --- Kopieren ---

    @Test
    fun copyIsCompleteAndTheOriginalStaysUntouched() {
        setUp()
        val originalBefore = LabOriginalPrintLogic.take(original)
        val out = copy()
        assertTrue(out.toString(), out is CopyOutcome.Done)
        val info = (out as CopyOutcome.Done).info
        assertTrue(info.complete)
        assertEquals(4, info.files)
        assertEquals(original.walkTopDown().filter { it.isFile }.sumOf { it.length() }, info.bytes)
        assertEquals(listing(original), listing(content))
        assertTrue(File(content, "Empty").isDirectory)
        assertEquals(File(original, "Data/data.000").readBytes().toList(), File(content, "Data/data.000").readBytes().toList())
        assertTrue(Math.abs(File(original, ".build.info").lastModified() - File(content, ".build.info").lastModified()) < 2000)
        assertTrue(content.walkTopDown().none { it.name.endsWith(LabCopyTarget.PART_SUFFIX) })
        // Das Original ist nach dem Kopieren Datei für Datei gleich (Pfad, Größe, Zeit, SHA-256).
        assertTrue(LabOriginalPrintLogic.compare(originalBefore, LabOriginalPrintLogic.take(original)).isEmpty())
    }

    @Test
    fun cancelledCopyIsNotCompleteAndResumesWithoutRecopyingDoneFiles() {
        setUp()
        val cancelled = copy(cancelAfterFiles = 2)
        assertTrue(cancelled.toString(), cancelled is CopyOutcome.Cancelled)
        assertTrue(content.walkTopDown().none { it.name.endsWith(LabCopyTarget.PART_SUFFIX) })
        val partial = content.walkTopDown().filter { it.isFile }.associate { it.path to it.lastModified() }
        assertTrue(partial.size in 1..3)
        // Eine fertige Datei im Ziel bekommt eine Marke; wird sie beim Fortsetzen übersprungen, bleibt sie unberührt.
        val marker = partial.keys.first()
        File(marker).setLastModified(File(marker).lastModified())
        val sizeBefore = File(marker).length()
        val resumed = copy()
        assertTrue(resumed.toString(), resumed is CopyOutcome.Done)
        assertEquals(listing(original), listing(content))
        assertEquals(sizeBefore, File(marker).length())
    }

    @Test
    fun resumeSkipsCompleteFilesAndReplacesPartsAndExtras() {
        setUp()
        assertTrue(copy() is CopyOutcome.Done)
        val keep = File(content, "Data/data.000")
        val keepTime = keep.lastModified()
        // Eine Teildatei, eine überzählige Datei und eine zu kurze Datei im Ziel.
        File(content, "Data/data.000.labpart").writeText("leftover")
        File(content, "extra.txt").writeText("not in the original")
        File(content, "Data/extra-dir").mkdirs()
        File(content, "Data/extra-dir/x").writeText("x")
        File(content, "_forever_/Client.exe").writeBytes(ByteArray(10))
        val again = copy()
        assertTrue(again.toString(), again is CopyOutcome.Done)
        assertEquals(listing(original), listing(content))
        assertEquals(keepTime, keep.lastModified())
        assertEquals(300_000L, File(content, "_forever_/Client.exe").length())
    }

    @Test
    fun linksInTheOriginalAreNotFollowedAndAreCounted() {
        setUp()
        val outside = tmp.newFolder("outside")
        File(outside, "secret.txt").writeText("outside")
        val link = File(original, "linked")
        val made = runCatching { Files.createSymbolicLink(link.toPath(), outside.toPath()) }.isSuccess
        assumeTrue("no symbolic links on this system", made)
        val scan = LabGameScan.scan(original)
        assertEquals(1, scan.skipped)
        assertTrue(scan.files.none { it.rel.startsWith("linked") })
        val out = copy()
        assertTrue(out is CopyOutcome.Done)
        assertEquals(1, (out as CopyOutcome.Done).info.skipped)
        assertFalse(File(content, "linked").exists())
        assertTrue(File(outside, "secret.txt").isFile)
        // Auch der Abdruck folgt der Verknüpfung nicht.
        assertEquals(PrintKind.LINK, LabOriginalPrintLogic.take(original).entries["linked"]!!.kind)
        assertNull(LabOriginalPrintLogic.take(original).entries["linked/secret.txt"])
    }

    @Test
    fun scanCountsFilesAndBytes() {
        setUp()
        val s = LabGameScan.scan(original)
        assertEquals(4, s.fileCount)
        assertEquals(700_000L + 1234 + 300_000 + original.resolve(".build.info").length(), s.bytes)
        assertTrue("Empty" in s.dirs && "Data/indices" in s.dirs)
    }

    // --- Fertig-Marke ---

    @Test
    fun infoMarkRoundTripsAndABrokenMarkIsNotComplete() {
        val info = LabCopyInfo(true, 4, 100L, 1, 1_000L, 6_000L)
        val back = LabCopyInfo.parse(info.toJson())!!
        assertEquals(info, back)
        assertEquals(5L, back.seconds)
        assertNull(LabCopyInfo.parse("garbage"))
        assertNull(LabCopyInfo.parse(null))
        assertFalse(LabCopyInfo.parse("{\"complete\":false}")!!.complete)
    }

    // --- Löschen ---

    @Test
    fun deleteCopyRemovesOnlyTheCopyFolder() {
        setUp()
        assertTrue(copy() is CopyOutcome.Done)
        val keepOutside = File(labRoot, "share/Battle.net-Setup.exe").also { it.parentFile.mkdirs(); it.writeText("x") }
        val before = listing(original)
        assertEquals(LabCopyDelete.Result.DELETED, LabCopyDelete.delete(File(labRoot, "gamecopy"), labRoot, original))
        assertFalse(File(labRoot, "gamecopy").exists())
        assertTrue(keepOutside.isFile)
        assertEquals(before, listing(original))
        assertEquals(LabCopyDelete.Result.NOTHING_THERE, LabCopyDelete.delete(File(labRoot, "gamecopy"), labRoot, original))
    }

    @Test
    fun deleteCopyRefusesTheOriginalAndAnythingOutside() {
        setUp()
        val before = listing(original)
        assertEquals(LabCopyDelete.Result.REFUSED, LabCopyDelete.delete(original, labRoot, original))
        assertEquals(LabCopyDelete.Result.REFUSED, LabCopyDelete.delete(labRoot, labRoot, original))
        assertEquals(LabCopyDelete.Result.REFUSED, LabCopyDelete.delete(labRoot.parentFile, labRoot, original))
        assertEquals(LabCopyDelete.Result.REFUSED, LabCopyDelete.delete(File(labRoot, "../../storage/game"), labRoot, original))
        assertEquals(before, listing(original))
    }

    @Test
    fun deleteCopyRefusesACopyFolderInsideTheOriginal() {
        setUp()
        // Wäre der Lab-Ordner (fälschlich) im Original, darf nichts gelöscht werden.
        val inside = File(original, "bnetlab")
        File(inside, "gamecopy").mkdirs()
        File(inside, "gamecopy/x").writeText("x")
        val before = listing(original)
        assertEquals(LabCopyDelete.Result.REFUSED, LabCopyDelete.delete(File(inside, "gamecopy"), inside, original))
        assertEquals(before, listing(original))
    }

    @Test
    fun deleteCopyDoesNotFollowALinkInsideTheCopy() {
        setUp()
        File(labRoot, "gamecopy").mkdirs()
        val link = File(labRoot, "gamecopy/to-original")
        val made = runCatching { Files.createSymbolicLink(link.toPath(), original.toPath()) }.isSuccess
        assumeTrue("no symbolic links on this system", made)
        val before = listing(original)
        assertEquals(LabCopyDelete.Result.DELETED, LabCopyDelete.delete(File(labRoot, "gamecopy"), labRoot, original))
        assertEquals(before, listing(original))
    }

    // --- Einbinden ---

    @Test
    fun mountOnlyForACompleteCopyWithTheSwitchOn() {
        setUp()
        assertTrue(copy() is CopyOutcome.Done)
        val done = LabCopyInfo(true, 4, 1L, 0, 0L, 1L)
        val unfinished = LabCopyInfo(false, 0, 0L, 0, 0L, 0L)
        // Ohne die Marke des neuen Aufbaus (alter Aufbau oder Umzug nicht gelaufen) wird nie eingebunden.
        assertNull(LabCopyMount.resolve(true, done, area, original))
        markLayoutCurrent()
        assertEquals(area, LabCopyMount.resolve(true, done, area, original))
        assertNull(LabCopyMount.resolve(false, done, area, original))
        assertNull(LabCopyMount.resolve(true, unfinished, area, original))
        assertNull(LabCopyMount.resolve(true, null, area, original))
        assertNull(LabCopyMount.resolve(true, done, File(labRoot, "gamecopy/missing"), original))
        // Läuft ein Umzug noch (Marke `moving.json`), wird nicht eingebunden.
        LabCopyLayout.writeMark(File(labRoot, "gamecopy"), LabIds.COPY_MOVING_FILE, "{}")
        assertNull(LabCopyMount.resolve(true, done, area, original))
        File(labRoot, "gamecopy/" + LabIds.COPY_MOVING_FILE).delete()
        // Der Spielordner im Kopie-Bereich fehlt: nicht einbinden.
        content.deleteRecursively()
        assertNull(LabCopyMount.resolve(true, done, area, original))
        // Ein Kopie-Bereich im Original wird nie eingebunden.
        assertNull(LabCopyMount.resolve(true, done, File(original, "Data"), original))
        assertNull(LabCopyMount.resolve(true, done, original, original))
    }

    @Test
    fun protocolLineNamesDriveAndWindowsPath() {
        setUp()
        val info = LabCopyInfo(true, 4, 1000L, 0, 0L, 1L)
        assertEquals("K:\\", LabCopyMount.windowsRoot())
        assertEquals("K:\\World of Warcraft\\", LabCopyMount.windowsGameRoot())
        val on = LabCopyMount.protocolLine(area, info)
        assertTrue(on.contains("drive K:") && on.contains("K:\\") && on.contains("4 files, 1000 bytes"))
        assertTrue(on, on.contains("game folder K:\\World of Warcraft\\,"))
        assertTrue(LabCopyMount.protocolLine(null, info, "the copy could not be moved").contains("Game copy: off (the copy could not be moved;"))
        assertEquals("Game copy: off (the original folder is not mounted)", LabCopyMount.protocolLine(null, info))
    }

    /** Durchstich: `drives` und `envVars` des Labor-Containers nennen den Originalpfad nie, in keiner Lage und keiner Schreibweise. */
    @Test
    fun drivesAndEnvNeverNameTheOriginalInAnySituation() {
        setUp()
        assertTrue(copy() is CopyOutcome.Done)
        markLayoutCurrent()
        val share = File(labRoot, "share").also { it.mkdirs() }
        val done = LabCopyInfo(true, 4, 1L, 0, 0L, 1L)
        val unfinished = LabCopyInfo(false, 0, 0L, 0, 0L, 0L)
        val shared = File("/storage/emulated/0/Some Game")
        val plan = LabCommand.build(
            LabSettings(),
            LabTarget.BATTLENET,
            LabCommandContext(baseEnv = "MESA_GLTHREAD=true", battleNetExe = "C:\\Program Files (x86)\\Battle.net\\Battle.net.exe"),
        )
        val base = LabContainer.Base(JSONObject("{\"envVars\":\"MESA_GLTHREAD=true\"}"))
        val names = listOf(original, shared)
        for (useCopy in listOf(true, false)) {
            for (info in listOf(done, unfinished, null)) {
                for (copyDir in listOf(area, File(labRoot, "gamecopy/missing"))) {
                    for (orig in names) {
                        val mounted = LabCopyMount.resolve(useCopy, info, copyDir, orig)
                        val drives = LabContainer.drivesText(posix(share), mounted?.let { posix(it) })
                        val data = LabContainer.config(base, plan, drives)
                        val d = data.getString("drives")
                        val e = data.getString("envVars")
                        val label = "useCopy=$useCopy info=$info copy=$copyDir original=$orig"
                        assertTrue("L: always there ($label)", d.startsWith("${LabIds.SHARE_DRIVE}:"))
                        assertFalse("original in drives ($label): $d", LabCopyPaths.mentionsOriginal(d, orig))
                        assertFalse("original in envVars ($label): $e", LabCopyPaths.mentionsOriginal(e, orig))
                        assertFalse("drive touches original ($label)", LabCopyPaths.driveHosts(d).any { LabCopyPaths.driveTouchesOriginal(it, orig) })
                        assertFalse("leak guard ($label)", LabContainer.leaksOriginal(d, e, orig, extraHosts = emptyList()))
                        val expectCopy = useCopy && info === done && copyDir == area
                        assertEquals(label, expectCopy, d.contains("${LabIds.COPY_DRIVE}:"))
                    }
                }
            }
        }
        // Schalter an, Kopie fertig: genau L: und K:.
        val mounted = LabCopyMount.resolve(true, done, area, original)!!
        val drives = LabContainer.drivesText(posix(share), posix(mounted))
        assertEquals(listOf(posix(share), posix(area)), LabCopyPaths.driveHosts(drives))
        assertTrue(drives.contains("${LabIds.COPY_DRIVE}:${posix(mounted)}"))
    }

    @Test
    fun theLeakGuardCatchesEveryWayTheOriginalCouldBeReached() {
        val original = File("/storage/emulated/0/Some Game")
        val none = emptyList<String>()
        assertFalse(LabContainer.leaksOriginal("L:/data/data/app/files/bnetlab/share", "A=1", original, none))
        // Laufwerk auf den Original, in einer anderen Schreibweise, auf einen Oberordner oder einen Unterordner.
        assertTrue(LabContainer.leaksOriginal("L:/x/shareK:/sdcard/Some Game", "", original, none))
        assertTrue(LabContainer.leaksOriginal("L:/x/shareK:/data/media/0/Some Game/Data", "", original, none))
        assertTrue(LabContainer.leaksOriginal("L:/x/shareK:/storage/emulated/0", "", original, none))
        // Umgebung nennt den Pfad.
        assertTrue(LabContainer.leaksOriginal("L:/x/share", "GAME=/storage/emulated/0/Some Game", original, none))
        // Wine ergänzt beim Start Downloads: liegt das Original darunter, gilt es als erreichbar.
        val downloads = "/storage/emulated/0/Download"
        assertFalse(LabContainer.leaksOriginal("L:/x/share", "", original, listOf(downloads)))
        assertTrue(LabContainer.leaksOriginal("L:/x/share", "", File("/storage/emulated/0/Download/Some Game"), listOf(downloads)))
    }

    @Test
    fun protocolHeadNamesTheCopyDriveOrSaysOff() {
        val settings = LabSettings()
        val plan = LabCommand.build(settings, LabTarget.BATTLENET, LabCommandContext(baseEnv = "", battleNetExe = "C:\\Program Files (x86)\\Battle.net\\Battle.net.exe"))
        val off = LabProtocol.header(LabProtocolInfo(0L, "1.0", "d", "a", settings, plan, 1L))
        assertTrue(off.contains("\nGame copy: off\n"))
        val line = LabCopyMount.protocolLine(File("/x/game"), LabCopyInfo(true, 4, 1000L, 0, 0L, 1L))
        val on = LabProtocol.header(LabProtocolInfo(0L, "1.0", "d", "a", settings, plan, 1L, gameCopyLine = line))
        assertTrue(on.contains("\nGame copy: on, drive K: -> Windows path K:\\ "))
        assertFalse(on.contains("Game copy: off"))
    }

    @Test
    fun settingsKeepTheSwitchOffByDefaultAndStoreIt() {
        assertFalse(LabSettings().useGameCopy)
        assertTrue(LabSettings.fromJson(LabSettings(useGameCopy = true).toJson()).useGameCopy)
        assertFalse(LabSettings.fromJson(null).useGameCopy)
        assertFalse(LabSettings.fromJson("{}").useGameCopy)
    }
}
