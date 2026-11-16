package app.gamenative.lab

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Spieleinrichtung der Kopie (labspiel, Punkt 2): nur unterhalb des Kopie-Ordners, nie im oder über dem Original. */
class LabGameSetupTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun client(root: File) {
        File(root, "_forever_").mkdirs()
        File(root, "_forever_/Wow-ARM64.exe").writeText("x")
        File(root, ".build.info").writeText("Branch!STRING:0|Product!STRING:0\nus|wowf\n")
    }

    private class Recorder {
        val roots = ArrayList<File>()
        fun step(name: String, paths: List<String> = LabGameSetup.WRITE_PATHS, problem: String? = null) =
            LabSetupStep(name, paths) { root -> roots += root; problem }
    }

    @Test
    fun withoutAMountedCopyNothingRunsAndThereIsNoLine() {
        val rec = Recorder()
        val original = tmp.newFolder("orig")
        assertNull(LabGameSetup.run(null, File(tmp.root, "copy/World of Warcraft"), original, listOf(rec.step("a"))))
        assertTrue(rec.roots.isEmpty())
    }

    @Test
    fun theStepsRunInTheGameFolderOfTheCopyOnly() {
        val original = tmp.newFolder("orig")
        client(original)
        val mount = tmp.newFolder("copy")
        val content = File(mount, "World of Warcraft").apply { mkdirs() }
        client(content)
        val rec = Recorder()
        val result = LabGameSetup.run(mount, content, original, listOf(rec.step("Config.wtf"), rec.step("addon")))!!
        assertEquals(listOf(content, content), rec.roots)
        assertEquals("Game setup on copy: Config.wtf, addon", result.line())
        assertNull(result.skipped)
        // Das Original wurde nie übergeben.
        assertTrue(rec.roots.none { LabCopyPaths.check(original, it) != PathCheck.OK })
    }

    @Test
    fun withoutAKnownOriginalNothingRuns() {
        val mount = tmp.newFolder("copy")
        val content = File(mount, "World of Warcraft").apply { mkdirs() }
        client(content)
        val rec = Recorder()
        val result = LabGameSetup.run(mount, content, null, listOf(rec.step("a")))!!
        assertTrue(rec.roots.isEmpty())
        assertTrue(result.line().startsWith("Game setup on copy: skipped ("))
    }

    @Test
    fun aCopyFolderInsideTheOriginalIsRefused() {
        val original = tmp.newFolder("orig")
        val content = File(original, "World of Warcraft").apply { mkdirs() }
        client(content)
        val rec = Recorder()
        val result = LabGameSetup.run(original, content, original, listOf(rec.step("a")))!!
        assertTrue(rec.roots.isEmpty())
        assertEquals("the copy folder touches the original game folder", result.skipped)
    }

    @Test
    fun theOriginalItselfAsCopyFolderIsRefused() {
        val original = tmp.newFolder("orig")
        client(original)
        val rec = Recorder()
        val result = LabGameSetup.run(original, original, original, listOf(rec.step("a")))!!
        assertTrue(rec.roots.isEmpty())
        assertTrue(result.skipped != null)
    }

    @Test
    fun aCopyWithoutClientFilesIsLeftAlone() {
        val original = tmp.newFolder("orig")
        client(original)
        val mount = tmp.newFolder("copy")
        val content = File(mount, "World of Warcraft").apply { mkdirs() }
        val rec = Recorder()
        val result = LabGameSetup.run(mount, content, original, listOf(rec.step("a")))!!
        assertTrue(rec.roots.isEmpty())
        assertEquals("the client files are not in the copy yet", result.skipped)
        assertFalse("nothing is created in the copy", File(content, "_forever_").exists())
    }

    @Test
    fun aMissingGameFolderInTheCopyIsLeftAlone() {
        val original = tmp.newFolder("orig")
        client(original)
        val mount = tmp.newFolder("copy")
        val rec = Recorder()
        val result = LabGameSetup.run(mount, File(mount, "World of Warcraft"), original, listOf(rec.step("a")))!!
        assertTrue(rec.roots.isEmpty())
        assertEquals("there is no game folder in the copy", result.skipped)
    }

    @Test
    fun aStepWhosePathLeavesTheCopyDoesNotRun() {
        val original = tmp.newFolder("orig")
        client(original)
        val mount = tmp.newFolder("copy")
        val content = File(mount, "World of Warcraft").apply { mkdirs() }
        client(content)
        val rec = Recorder()
        val result = LabGameSetup.run(mount, content, original, listOf(rec.step("escape", listOf("../x")), rec.step("ok")))!!
        assertEquals(1, rec.roots.size)
        assertEquals(listOf("ok"), result.done)
        assertEquals(1, result.failed.size)
        assertTrue(result.failed[0].startsWith("escape"))
        assertTrue(result.line().contains("failed:"))
    }

    @Test
    fun aFailingStepIsReportedAndTheOthersStillRun() {
        val original = tmp.newFolder("orig")
        client(original)
        val mount = tmp.newFolder("copy")
        val content = File(mount, "World of Warcraft").apply { mkdirs() }
        client(content)
        val rec = Recorder()
        val boom = LabSetupStep("boom", LabGameSetup.WRITE_PATHS) { throw IllegalStateException("x") }
        val result = LabGameSetup.run(mount, content, original, listOf(boom, rec.step("addon")))!!
        assertEquals(listOf("addon"), result.done)
        assertEquals(listOf("boom (IllegalStateException)"), result.failed)
    }

    @Test
    fun aLinkInsideTheCopyThatLeadsIntoTheOriginalStopsTheStep() {
        val original = tmp.newFolder("orig")
        client(original)
        File(original, "WTF").mkdirs()
        val mount = tmp.newFolder("copy")
        val content = File(mount, "World of Warcraft").apply { mkdirs() }
        client(content)
        // `_forever_/WTF` in der Kopie ist eine Verknüpfung auf einen Ordner im Original.
        val link = File(content, "_forever_/WTF")
        val linked = runCatching { java.nio.file.Files.createSymbolicLink(link.toPath(), File(original, "WTF").toPath()) }.isSuccess
        // Dateisystem ohne Verknüpfungen (Windows ohne Recht): der Fall ist dort nicht herstellbar, der Test gilt dann als übersprungen.
        org.junit.Assume.assumeTrue(linked)
        val rec = Recorder()
        val result = LabGameSetup.run(mount, content, original, listOf(rec.step("Config.wtf")))!!
        assertTrue("the step must not run", rec.roots.isEmpty())
        assertEquals(1, result.failed.size)
    }

    @Test
    fun theRealGameSetupWritesOnlyBelowTheCopyAndNeverTouchesTheOriginal() {
        val original = tmp.newFolder("orig")
        client(original)
        File(original, "_forever_/WTF").mkdirs()
        File(original, "_forever_/WTF/Config.wtf").writeText("SET gxApi \"D3D12\"\n")
        val beforeOriginal = snapshot(original)

        val mount = tmp.newFolder("copy")
        val content = File(mount, "World of Warcraft").apply { mkdirs() }
        client(content)
        File(content, "_forever_/WTF").mkdirs()
        File(content, "_forever_/WTF/Config.wtf").writeText("SET gxApi \"D3D12\"\nSET portal \"EU\"\n")
        File(content, "Data").mkdirs()
        File(content, "Data/data.idx").writeText("d")
        val others = tmp.newFolder("elsewhere").also { File(it, "keep.txt").writeText("k") }

        // Die echte Funktion des Spielstarts mit dem Zielordner der Kopie, im Schritt wie im Labor, nur ohne Addon (braucht Android-Assets).
        val steps = listOf(
            LabSetupStep("Config.wtf", LabGameSetup.CONFIG_PATHS) { root ->
                app.gamenative.ui.screen.wow.ensureGameConfig(root, "1440x900", null, 60, null, emptyMap())
                null
            },
        )
        val result = LabGameSetup.run(mount, content, original, steps)!!
        assertEquals("Game setup on copy: Config.wtf", result.line())
        val config = File(content, "_forever_/WTF/Config.wtf").readLines()
        assertTrue(config.contains("SET gxApi \"D3D11\""))
        assertTrue("the portal of the copy stays", config.contains("SET portal \"EU\""))
        assertTrue(config.contains("SET gxWindowedResolution \"1440x900\""))
        assertEquals("the original is byte for byte unchanged", beforeOriginal, snapshot(original))
        assertEquals(listOf("keep.txt"), others.list()!!.toList())
        assertEquals("d", File(content, "Data/data.idx").readText())
    }

    @Test
    fun theWritePathsCoverBothFlavorsAndNoIdentityFile() {
        assertTrue(LabGameSetup.WRITE_PATHS.any { it.startsWith("_forever_/") })
        assertTrue(LabGameSetup.WRITE_PATHS.any { it.startsWith("_classic_beta_/") })
        assertTrue(LabGameSetup.WRITE_PATHS.none { it.contains("kennung") })
    }

    @Test
    fun theLabLauncherAppliesTheSetupOnlyToTheCopyAreaAndNeverPassesTheOriginal() {
        val launcher = File("src/main/java/app/gamenative/lab/LabLauncher.kt").readText()
        assertTrue(launcher.contains("LabGameSetup.run("))
        assertTrue(launcher.contains("LabDirs.gameCopyContent(app)"))
        // Das Original geht nur als Vergleich in die Prüfung, nie als Zielordner.
        val setup = File("src/main/java/app/gamenative/lab/LabGameSetup.kt").readText()
        assertFalse(setup.contains("GamePath"))
        assertFalse(setup.contains("originalFolder"))
    }

    private fun snapshot(dir: File): Map<String, String> =
        dir.walkTopDown().filter { it.isFile }.associate { it.relativeTo(dir).path to it.readText() + "|" + it.length() }
}
