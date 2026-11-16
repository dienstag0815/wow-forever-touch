package app.gamenative.lab

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** labkopie2: Kopie im Unterordner `World of Warcraft`, Umzug einer vorhandenen Kopie, Auffrischen ohne Löschen von Launcher-Dateien. */
class LabCopyLayoutTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val game = LabIds.COPY_GAME_DIR
    private lateinit var original: File
    private lateinit var parent: File
    private lateinit var area: File

    private fun setUp() {
        original = tmp.newFolder("storage", "game")
        File(original, ".build.info").writeText("Product!STRING:0\nforever\n")
        File(original, "Data").mkdirs()
        File(original, "Data/data.000").writeBytes(ByteArray(5000) { (it % 251).toByte() })
        File(original, "_forever_").mkdirs()
        File(original, "_forever_/Client.exe").writeBytes(ByteArray(3000) { 9 })
        File(original, "Empty").mkdirs()
        parent = File(tmp.newFolder("app", "files", "bnetlab"), "gamecopy")
        area = File(parent, LabIds.COPY_CONTENT_DIR)
    }

    private fun listing(dir: File): Map<String, Long> =
        dir.walkTopDown().filter { it != dir }.associate { it.relativeTo(dir).path.replace('\\', '/') to (if (it.isFile) it.length() else -1L) }

    /** Eine Kopie im alten Aufbau: der Spielinhalt liegt direkt im Kopie-Bereich. */
    private fun legacyCopy(vararg extra: String) {
        assertTrue(LabGameCopier.copy(original, area, { false }, {}) is CopyOutcome.Done)
        for (rel in extra) File(area, rel).apply { parentFile.mkdirs() }.writeText("launcher $rel")
    }

    private fun contentOf(): File = File(area, game)

    // --- Zustand ---

    @Test
    fun stateTellsEmptyLegacyMovingAndCurrent() {
        setUp()
        assertEquals(LabCopyLayout.State.EMPTY, LabCopyLayout.state(parent, area))
        area.mkdirs()
        assertEquals(LabCopyLayout.State.EMPTY, LabCopyLayout.state(parent, area))
        File(area, "a.txt").writeText("x")
        assertEquals(LabCopyLayout.State.LEGACY, LabCopyLayout.state(parent, area))
        LabCopyLayout.writeMark(parent, LabIds.COPY_MOVING_FILE, "{}")
        assertEquals(LabCopyLayout.State.MOVING, LabCopyLayout.state(parent, area))
        File(parent, LabIds.COPY_MOVING_FILE).delete()
        File(area, LabCopyLayout.OLD_NAME).mkdirs()
        assertEquals(LabCopyLayout.State.MOVING, LabCopyLayout.state(parent, area))
        LabCopyLayout.writeMark(parent, LabIds.COPY_LAYOUT_FILE, LabCopyLayout.layoutJson())
        assertEquals(LabCopyLayout.State.CURRENT, LabCopyLayout.state(parent, area))
        assertEquals(0, LabCopyLayout.layoutVersion(null))
        assertEquals(0, LabCopyLayout.layoutVersion("garbage"))
        assertEquals(LabCopyLayout.VERSION, LabCopyLayout.layoutVersion(LabCopyLayout.layoutJson()))
    }

    @Test
    fun marksLiveBesideTheCopyAreaNeverInsideIt() {
        setUp()
        legacyCopy()
        assertTrue(LabCopyLayout.migrate(parent, area, original) is LayoutResult.Ready)
        assertTrue(File(parent, LabIds.COPY_LAYOUT_FILE).isFile)
        assertFalse(File(parent, LabIds.COPY_MOVING_FILE).exists())
        // Weder im Kopie-Bereich noch im Spielordner liegt eine Marke oder Teildatei von uns.
        assertEquals(setOf(game), area.list()!!.toSet())
        assertTrue(area.walkTopDown().none { it.name.endsWith(".json.tmp") || it.name == LabIds.COPY_LAYOUT_FILE || it.name == LabIds.COPY_INFO_FILE })
    }

    // --- Umzug ---

    @Test
    fun legacyCopyMovesIntoTheSubfolderWithoutCopyingData() {
        setUp()
        legacyCopy()
        val before = listing(area)
        val stamp = File(area, "Data/data.000").lastModified()
        val originalBefore = listing(original)
        val result = LabCopyLayout.migrate(parent, area, original)
        assertEquals(LayoutResult.Ready(4), result)
        // Jeder alte Eintrag liegt jetzt eine Ebene tiefer, Größe und Änderungszeit sind gleich (Umbenennen, kein Kopieren).
        assertEquals(before, listing(contentOf()))
        assertEquals(stamp, File(contentOf(), "Data/data.000").lastModified())
        assertEquals(setOf(game), area.list()!!.toSet())
        assertEquals(LabCopyLayout.State.CURRENT, LabCopyLayout.state(parent, area))
        assertTrue(LabCopyLayout.ready(area))
        assertEquals(originalBefore, listing(original))
        // Danach ist die Kopie einbindbar.
        val done = LabCopyInfo(true, 4, 1L, 0, 0L, 1L)
        assertEquals(area, LabCopyMount.resolve(true, done, area, original))
    }

    @Test
    fun migrationIsRepeatableAndAnAlreadyMovedCopyStaysAsItIs() {
        setUp()
        legacyCopy()
        assertTrue(LabCopyLayout.migrate(parent, area, original) is LayoutResult.Ready)
        val after = listing(area)
        assertEquals(LayoutResult.Ready(0), LabCopyLayout.migrate(parent, area, original))
        assertEquals(LayoutResult.Ready(0), LabCopyLayout.migrate(parent, area, original))
        assertEquals(after, listing(area))
    }

    @Test
    fun aMovePartwayContinuesWhereItStopped() {
        setUp()
        legacyCopy()
        // Abbruch mitten im Umzug: Marke gesetzt, Zielordner da, zwei Einträge schon verschoben, der Rest noch im alten Platz.
        LabCopyLayout.writeMark(parent, LabIds.COPY_MOVING_FILE, "{}")
        File(area, game).mkdirs()
        assertTrue(File(area, ".build.info").renameTo(File(area, "$game/.build.info")))
        assertTrue(File(area, "Empty").renameTo(File(area, "$game/Empty")))
        assertEquals(LabCopyLayout.State.MOVING, LabCopyLayout.state(parent, area))
        assertFalse(LabCopyMount.resolve(true, LabCopyInfo(true, 4, 1L, 0, 0L, 1L), area, original) != null)
        val result = LabCopyLayout.migrate(parent, area, original)
        assertEquals(LayoutResult.Ready(2), result)
        assertEquals(setOf(game), area.list()!!.toSet())
        assertEquals(setOf(".build.info", "Data", "Empty", "_forever_"), contentOf().list()!!.toSet())
        assertEquals(3000L, File(contentOf(), "_forever_/Client.exe").length())
        assertTrue(LabCopyLayout.ready(area))
    }

    @Test
    fun aMoveThatStoppedBeforeTheLayoutMarkButAfterTheLastRenameFinishes() {
        setUp()
        legacyCopy()
        assertTrue(LabCopyLayout.migrate(parent, area, original) is LayoutResult.Ready)
        // Abbruch zwischen letztem Umbenennen und Marke: `layout.json` fehlt, `moving.json` steht noch.
        File(parent, LabIds.COPY_LAYOUT_FILE).delete()
        LabCopyLayout.writeMark(parent, LabIds.COPY_MOVING_FILE, "{}")
        val before = listing(area)
        assertEquals(LayoutResult.Ready(0), LabCopyLayout.migrate(parent, area, original))
        assertEquals(before, listing(area))
        assertTrue(LabCopyLayout.ready(area))
    }

    @Test
    fun anEntryAlreadyNamedLikeTheSubfolderIsKeptAsGameContent() {
        setUp()
        // Das Original hat selbst einen Eintrag `World of Warcraft`.
        File(original, game).mkdirs()
        File(original, "$game/inner.txt").writeText("inner")
        legacyCopy()
        assertTrue(File(area, "$game/inner.txt").isFile)
        assertEquals(LayoutResult.Ready(5), LabCopyLayout.migrate(parent, area, original))
        assertEquals(setOf(game), area.list()!!.toSet())
        assertEquals("inner", File(contentOf(), "$game/inner.txt").readText())
        assertEquals(setOf(".build.info", "Data", "Empty", "_forever_", game), contentOf().list()!!.toSet())
        assertFalse(File(area, LabCopyLayout.OLD_NAME).exists())
    }

    @Test
    fun anEntryNamedLikeTheSubfolderSurvivesAnAbortAfterItsFirstRename() {
        setUp()
        File(original, game).mkdirs()
        File(original, "$game/inner.txt").writeText("inner")
        legacyCopy()
        // Abbruch nach dem ersten Schritt (Umbenennen in den Zwischennamen), noch ohne Marke und ohne Zielordner.
        assertTrue(File(area, game).renameTo(File(area, LabCopyLayout.OLD_NAME)))
        assertEquals(LabCopyLayout.State.MOVING, LabCopyLayout.state(parent, area))
        val result = LabCopyLayout.migrate(parent, area, original)
        assertTrue(result.toString(), result is LayoutResult.Ready)
        assertEquals("inner", File(contentOf(), "$game/inner.txt").readText())
        assertEquals(setOf(game), area.list()!!.toSet())
        assertEquals(LabCopyLayout.State.CURRENT, LabCopyLayout.state(parent, area))
    }

    @Test
    fun aSecondRunAfterANameClashDeletesNothing() {
        setUp()
        legacyCopy()
        // Widerspruch: der Zielordner enthält schon einen Eintrag gleichen Namens wie ein alter (kann nur nach Fremdeingriff entstehen).
        LabCopyLayout.writeMark(parent, LabIds.COPY_MOVING_FILE, "{}")
        File(area, "$game/Data").mkdirs()
        File(area, "$game/Data/mine.txt").writeText("mine")
        val before = listing(area)
        val result = LabCopyLayout.migrate(parent, area, original)
        assertEquals(LayoutResult.Failed(LayoutReason.NAME_CLASH, "Data"), result)
        // Der Fehler lässt alles stehen; nur das, was vor dem Konflikt verschoben wurde, liegt schon im Zielordner.
        val after = listing(area)
        assertTrue(after.keys.containsAll(before.keys.filter { it.startsWith("$game/") }))
        assertEquals(5000L, File(area, "Data/data.000").length())
        assertEquals("mine", File(area, "$game/Data/mine.txt").readText())
        assertFalse(LabCopyLayout.ready(area))
        assertNull(LabCopyMount.resolve(true, LabCopyInfo(true, 4, 1L, 0, 0L, 1L), area, original))
    }

    @Test
    fun withoutAnOriginalTheMoveDoesNotRun() {
        setUp()
        legacyCopy()
        val before = listing(area)
        assertEquals(LayoutResult.Failed(LayoutReason.NO_ORIGINAL), LabCopyLayout.migrate(parent, area, null))
        assertEquals(before, listing(area))
    }

    @Test
    fun aCopyAreaThatTouchesTheOriginalIsNeverRenamed() {
        setUp()
        // Kopie-Bereich im Original: nichts wird umbenannt.
        val bad = File(original, "Data")
        val badParent = original
        val before = listing(original)
        assertEquals(LayoutResult.Failed(LayoutReason.REFUSED), LabCopyLayout.migrate(badParent, bad, original))
        assertEquals(before, listing(original))
        // Kopie-Bereich = Original.
        assertEquals(LayoutResult.Failed(LayoutReason.REFUSED), LabCopyLayout.migrate(original.parentFile, original, original))
        assertEquals(before, listing(original))
        assertFalse(File(original, LabIds.COPY_LAYOUT_FILE).exists())
    }

    @Test
    fun anEmptyOrMissingAreaJustGetsTheMark() {
        setUp()
        assertEquals(LayoutResult.Ready(0), LabCopyLayout.migrate(parent, area, original))
        assertTrue(LabCopyLayout.isCurrent(parent))
        // Eine neue Kopie geht gleich in den Unterordner.
        assertTrue(LabGameCopier.copy(original, contentOf(), { false }, {}) is CopyOutcome.Done)
        assertTrue(LabCopyLayout.ready(area))
        assertEquals(listing(original), listing(contentOf()))
        assertEquals(setOf(game), area.list()!!.toSet())
    }

    // --- Schreibschicht: Umbenennen ---

    @Test
    fun renameStaysInsideTheCopyAndNeverOverwrites() {
        setUp()
        area.mkdirs()
        File(area, "a.txt").writeText("a")
        File(area, "b.txt").writeText("b")
        File(area, "dir").mkdirs()
        val t = LabCopyTarget(area, original)
        assertTrue(runCatching { t.rename("a.txt", "../out.txt") }.isFailure)
        assertTrue(runCatching { t.rename("../a.txt", "x.txt") }.isFailure)
        assertTrue(runCatching { t.rename("a.txt", "/abs") }.isFailure)
        assertTrue(runCatching { t.rename("a.txt", "b.txt") }.isFailure)
        assertTrue(runCatching { t.rename("missing", "m") }.isFailure)
        assertTrue(runCatching { t.rename("a.txt", "nofolder/a.txt") }.isFailure)
        assertEquals("a", File(area, "a.txt").readText())
        assertEquals("b", File(area, "b.txt").readText())
        t.rename("a.txt", "dir/a.txt")
        assertEquals("a", File(area, "dir/a.txt").readText())
        assertFalse(File(area, "a.txt").exists())
        assertFalse(File(parent, "out.txt").exists())
    }

    @Test
    fun renameRefusesATargetThatTouchesTheOriginal() {
        setUp()
        assertTrue(runCatching { LabCopyTarget(File(original, "Data"), original) }.isFailure)
        assertTrue(runCatching { LabCopyTarget(original.parentFile, original) }.isFailure)
        val before = listing(original)
        assertEquals(before, listing(original))
    }

    // --- Auffrischen ---

    @Test
    fun refreshKeepsWhatTheLauncherAddedWhileTheCopyWasComplete() {
        setUp()
        val content = contentOf()
        assertTrue(LabGameCopier.copy(original, content, { false }, {}) is CopyOutcome.Done)
        // Der Launcher legt Dateien an: eine Kennungsdatei, einen ganzen Ordner; dazu liegt noch eine Teildatei von uns.
        File(content, ".product.db").writeText("launcher db")
        File(content, "Launcher").mkdirs()
        File(content, "Launcher/state.bin").writeText("state")
        File(content, "Data/data.000.labpart").writeText("leftover")
        File(content, "Data/extra.idx").writeText("idx")
        // Eine Datei ist verändert (kürzer): sie wird mit der Fassung des Originals ersetzt, das ist der Zweck des Auffrischens.
        File(content, "_forever_/Client.exe").writeBytes(ByteArray(10))
        val out = LabGameCopier.copy(original, content, { false }, {}, keepExtras = true)
        assertTrue(out.toString(), out is CopyOutcome.Done)
        assertTrue((out as CopyOutcome.Done).info.keepExtras)
        assertEquals("launcher db", File(content, ".product.db").readText())
        assertEquals("state", File(content, "Launcher/state.bin").readText())
        assertEquals("idx", File(content, "Data/extra.idx").readText())
        assertFalse(File(content, "Data/data.000.labpart").exists())
        assertEquals(3000L, File(content, "_forever_/Client.exe").length())
        assertEquals(5000L, File(content, "Data/data.000").length())
    }

    @Test
    fun withoutAFinishedCopyTheExtrasStillGoAsBefore() {
        setUp()
        val content = contentOf()
        assertTrue(LabGameCopier.copy(original, content, { false }, {}) is CopyOutcome.Done)
        File(content, ".product.db").writeText("x")
        File(content, "Launcher").mkdirs()
        assertTrue(LabGameCopier.copy(original, content, { false }, {}) is CopyOutcome.Done)
        assertFalse(File(content, ".product.db").exists())
        assertFalse(File(content, "Launcher").exists())
        assertEquals(listing(original), listing(content))
    }

    @Test
    fun refreshResolvesAKindConflictInFavourOfTheOriginal() {
        setUp()
        val content = contentOf()
        assertTrue(LabGameCopier.copy(original, content, { false }, {}) is CopyOutcome.Done)
        // Im Original ist `Empty` ein Ordner, `Data/data.000` eine Datei; in der Kopie ist es vertauscht.
        File(content, "Empty").deleteRecursively()
        File(content, "Empty").writeText("file")
        File(content, "Data/data.000").delete()
        File(content, "Data/data.000").mkdirs()
        File(content, "Data/data.000/x").writeText("x")
        val out = LabGameCopier.copy(original, content, { false }, {}, keepExtras = true)
        assertTrue(out.toString(), out is CopyOutcome.Done)
        assertTrue(File(content, "Empty").isDirectory)
        assertEquals(5000L, File(content, "Data/data.000").length())
    }

    @Test
    fun refreshNeverTouchesTheOriginal() {
        setUp()
        val content = contentOf()
        assertTrue(LabGameCopier.copy(original, content, { false }, {}) is CopyOutcome.Done)
        File(content, ".product.db").writeText("x")
        val originalBefore = LabOriginalPrintLogic.take(original)
        assertTrue(LabGameCopier.copy(original, content, { false }, {}, keepExtras = true) is CopyOutcome.Done)
        assertTrue(LabOriginalPrintLogic.compare(originalBefore, LabOriginalPrintLogic.take(original)).isEmpty())
    }

    @Test
    fun refreshRuleKeepsExtrasOnlyAfterAFinishedCopy() {
        val done = LabCopyInfo(true, 4, 10L, 0, 1L, 2L)
        val running = LabCopyInfo(false, 0, 0L, 0, 0L, 0L, keepExtras = true)
        val never = LabCopyInfo(false, 0, 0L, 0, 0L, 0L)
        assertFalse(LabCopyRefresh.keepsExtras(null))
        assertFalse(LabCopyRefresh.keepsExtras(never))
        assertTrue(LabCopyRefresh.keepsExtras(done))
        assertTrue(LabCopyRefresh.keepsExtras(running))
        assertNull(LabCopyRefresh.markerWhileRunning(null))
        assertNull(LabCopyRefresh.markerWhileRunning(never))
        // Während des Auffrischens: nie fertig (nicht einbindbar), der Merker bleibt, auch nach einem Abbruch.
        val marker = LabCopyRefresh.markerWhileRunning(done)!!
        assertFalse(marker.complete)
        assertTrue(marker.keepExtras)
        assertTrue(LabCopyRefresh.keepsExtras(marker))
        assertEquals(done.files, marker.files)
    }

    @Test
    fun infoMarkKeepsTheMemoAndOldMarksReadAsFalse() {
        val info = LabCopyInfo(true, 4, 100L, 1, 1_000L, 6_000L, keepExtras = true)
        assertEquals(info, LabCopyInfo.parse(info.toJson()))
        assertFalse(LabCopyInfo.parse("{\"complete\":true,\"files\":3}")!!.keepExtras)
    }
}
