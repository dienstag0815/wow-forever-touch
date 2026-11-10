package app.gamenative.wow.addons

import app.gamenative.R
import app.gamenative.ui.screen.wow.launcher.UiText
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AddonZipTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val limit = 10L * 1024 * 1024

    /** Baut ein Zip aus (Name, Inhalt); Namen mit Schrägstrich am Ende sind Ordner. */
    fun zipOf(vararg entries: Pair<String, String>): File {
        val file = tmp.newFile()
        ZipOutputStream(file.outputStream()).use { z ->
            for ((name, content) in entries) {
                z.putNextEntry(ZipEntry(name))
                if (!name.endsWith("/")) z.write(content.toByteArray())
                z.closeEntry()
            }
        }
        return file
    }

    private fun addons(): File = File(tmp.root, "AddOns").also { it.mkdirs() }

    private fun install(zip: File, folders: List<String>, dir: File = addons(), max: Long = limit) =
        AddonZip.install(zip, dir, folders, max)

    private fun expectFail(zip: File, folders: List<String> = listOf("Demo"), dir: File = addons(), max: Long = limit): UiText {
        try {
            install(zip, folders, dir, max)
        } catch (e: AddonInstallException) {
            return e.text
        }
        fail("Installation hätte scheitern müssen")
        return UiText.raw("")
    }

    private fun noLeftovers(dir: File) {
        val names = dir.list().orEmpty().toList()
        assertTrue("Reste: $names", names.none { it.startsWith(AddonZip.STAGING_PREFIX) || it.startsWith(AddonZip.BACKUP_PREFIX) })
    }

    private val good = arrayOf(
        "Demo/Demo.toc" to "## Interface: 16001",
        "Demo/Demo.lua" to "print('hi')",
        "Demo/Libs/Lib.lua" to "x",
    )

    @Test
    fun anAddonIsInstalled() {
        val dir = addons()
        val result = install(zipOf(*good), listOf("Demo"), dir)
        assertEquals(listOf("Demo"), result)
        assertEquals("print('hi')", File(dir, "Demo/Demo.lua").readText())
        assertTrue(File(dir, "Demo/Libs/Lib.lua").isFile)
        noLeftovers(dir)
    }

    @Test
    fun severalFoldersAreInstalledSorted() {
        val zip = zipOf("Demo_B/Demo_B.toc" to "b", "Demo/Demo.toc" to "a", "Demo_A/Demo_A.toc" to "c")
        val result = install(zip, listOf("Demo", "Demo_A", "Demo_B"))
        assertEquals(listOf("Demo", "Demo_A", "Demo_B"), result)
    }

    @Test
    fun pathTraversalIsRefusedAndNothingIsWritten() {
        val dir = addons()
        for (bad in listOf("../evil.lua", "Demo/../../evil.lua", "Demo/../../../evil.lua", "..\\evil.lua", "Demo\\..\\..\\evil.lua")) {
            expectFail(zipOf(*good, bad to "x"), dir = dir)
        }
        assertFalse(File(tmp.root, "evil.lua").exists())
        assertFalse(File(dir.parentFile.parentFile, "evil.lua").exists())
        assertFalse("nichts installiert", File(dir, "Demo").exists())
        noLeftovers(dir)
    }

    @Test
    fun absoluteAndDrivePathsAreRefused() {
        for (bad in listOf("/etc/evil.lua", "C:/evil.lua", "c:\\evil.lua", "\\evil.lua", "Demo/C:evil.lua", "Demo/a:b.lua")) {
            expectFail(zipOf(*good, bad to "x"))
        }
    }

    @Test
    fun nulBytesAndHugeSegmentsAreRefused() {
        expectFail(zipOf(*good, "Demo/a\u0000.lua" to "x"))
        expectFail(zipOf(*good, ("Demo/" + "a".repeat(300)) to "x"))
    }

    @Test
    fun unexpectedTopLevelFoldersAreRefused() {
        val dir = addons()
        val msg = expectFail(zipOf(*good, "Fremd/Fremd.toc" to "x"), dir = dir)
        assertEquals(UiText.res(R.string.launcher_addon_zip_unexpected_folder, "Fremd"), msg)
        assertFalse(File(dir, "Fremd").exists())
        assertFalse(File(dir, "Demo").exists())
        // Auch ein Ordner, der nur in leerer Form vorkommt.
        expectFail(zipOf(*good, "Leer/" to ""))
    }

    @Test
    fun ownAddonFolderCanNeverBeWritten() {
        val dir = addons()
        File(dir, "TouchForever").mkdirs()
        File(dir, "TouchForever/TouchForever.toc").writeText("unser Addon")
        // Als erlaubter Ordner im Aufruf: sofort abgelehnt.
        expectFail(zipOf("TouchForever/TouchForever.toc" to "fremd"), folders = listOf("TouchForever"), dir = dir)
        // Im Archiv, aber nicht erlaubt: abgelehnt.
        expectFail(zipOf(*good, "TouchForever/TouchForever.toc" to "fremd"), dir = dir)
        assertEquals("unser Addon", File(dir, "TouchForever/TouchForever.toc").readText())
    }

    @Test
    fun filesAtTheTopLevelAreIgnoredAndMacJunkIsSkipped() {
        val dir = addons()
        install(zipOf(*good, "README.md" to "x", "LICENSE" to "y", "__MACOSX/Demo/._Demo.toc" to "z"), listOf("Demo"), dir)
        assertFalse(File(dir, "README.md").exists())
        assertFalse(File(dir, "__MACOSX").exists())
        assertTrue(File(dir, "Demo/Demo.toc").isFile)
    }

    @Test
    fun aSingleWrapperFolderIsRemoved() {
        val dir = addons()
        install(zipOf("Demo-1.2/Demo/Demo.toc" to "a", "Demo-1.2/Demo/Demo.lua" to "b", "Demo-1.2/README" to "c"), listOf("Demo"), dir)
        assertTrue(File(dir, "Demo/Demo.toc").isFile)
        assertFalse(File(dir, "Demo-1.2").exists())
        assertFalse(File(dir, "README").exists())
    }

    @Test
    fun aWrapperAroundUnexpectedFoldersStillFails() {
        expectFail(zipOf("Wrapper/Fremd/Fremd.toc" to "x"))
    }

    @Test
    fun folderNamesTakeTheCatalogSpelling() {
        val dir = addons()
        val result = install(zipOf("demo/demo.toc" to "a"), listOf("Demo"), dir)
        assertEquals(listOf("Demo"), result)
        assertTrue(File(dir, "Demo/demo.toc").isFile)
    }

    @Test
    fun mainFolderMustBeThereAndHaveAToc() {
        assertEquals(
            UiText.res(R.string.launcher_addon_zip_missing_main, "Demo"),
            expectFail(zipOf("Demo_B/Demo_B.toc" to "x"), folders = listOf("Demo", "Demo_B")),
        )
        assertEquals(UiText.res(R.string.launcher_addon_zip_no_toc, "Demo"), expectFail(zipOf("Demo/Demo.lua" to "x")))
        expectFail(zipOf("README.md" to "x"))
        expectFail(zipOf("Demo/" to ""), folders = listOf("Demo"))
    }

    @Test
    fun brokenArchivesLeaveExistingFoldersAlone() {
        val dir = addons()
        install(zipOf(*good), listOf("Demo"), dir)
        val notAZip = tmp.newFile().also { it.writeText("das ist kein zip") }
        assertEquals(UiText.res(R.string.launcher_addon_zip_corrupt), expectFail(notAZip, dir = dir))
        assertEquals("print('hi')", File(dir, "Demo/Demo.lua").readText())
        noLeftovers(dir)
    }

    @Test
    fun unpackedSizeIsCountedWhileWritingNotFromTheHeader() {
        val dir = addons()
        install(zipOf(*good), listOf("Demo"), dir)
        val big = zipOf("Demo/Demo.toc" to "x", "Demo/big.bin" to "A".repeat(200_000))
        assertEquals(UiText.res(R.string.launcher_addon_zip_unpacked_too_big), expectFail(big, dir = dir, max = 100_000))
        assertEquals("print('hi')", File(dir, "Demo/Demo.lua").readText())
        assertFalse(File(dir, "Demo/big.bin").exists())
        noLeftovers(dir)
        install(big, listOf("Demo"), dir, max = 300_000)
        assertTrue(File(dir, "Demo/big.bin").isFile)
    }

    @Test
    fun tooManyEntriesAreRefused() {
        val file = tmp.newFile()
        ZipOutputStream(file.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("Demo/Demo.toc")); z.write(1); z.closeEntry()
            for (i in 0..AddonZip.MAX_ENTRIES) {
                z.putNextEntry(ZipEntry("Demo/f$i")); z.closeEntry()
            }
        }
        assertEquals(UiText.res(R.string.launcher_addon_zip_too_many_entries), expectFail(file))
    }

    @Test
    fun updateReplacesTheFolderCompletely() {
        val dir = addons()
        install(zipOf(*good), listOf("Demo"), dir)
        install(zipOf("Demo/Demo.toc" to "## Interface: 16001\n## Version: 2", "Demo/New.lua" to "n"), listOf("Demo"), dir)
        assertTrue(File(dir, "Demo/New.lua").isFile)
        assertFalse("alte Dateien sind weg", File(dir, "Demo/Demo.lua").exists())
        noLeftovers(dir)
    }

    @Test
    fun otherAddonFoldersAreNeverTouched() {
        val dir = addons()
        File(dir, "Andere").mkdirs()
        File(dir, "Andere/Andere.toc").writeText("fremd")
        install(zipOf(*good), listOf("Demo"), dir)
        install(zipOf(*good), listOf("Demo"), dir)
        assertEquals("fremd", File(dir, "Andere/Andere.toc").readText())
    }

    @Test
    fun leftoversFromACrashAreCleanedUp() {
        val dir = addons()
        // Zwischenordner eines abgebrochenen Vorgangs
        File(dir, "${AddonZip.STAGING_PREFIX}123/Demo").mkdirs()
        // Sicherung, deren Ordner fehlt: kommt zurück
        File(dir, "${AddonZip.BACKUP_PREFIX}77-Lost-Addon").mkdirs()
        File(dir, "${AddonZip.BACKUP_PREFIX}77-Lost-Addon/Lost-Addon.toc").writeText("alt")
        // Sicherung, deren Ordner da ist: wird gelöscht
        File(dir, "${AddonZip.BACKUP_PREFIX}78-Here").mkdirs()
        File(dir, "Here").mkdirs()
        AddonZip.recoverLeftovers(dir)
        assertEquals("alt", File(dir, "Lost-Addon/Lost-Addon.toc").readText())
        assertTrue(File(dir, "Here").isDirectory)
        noLeftovers(dir)
    }

    @Test
    fun safeSegmentsNormalizesSeparators() {
        assertEquals(listOf("A", "b", "c.lua"), AddonZip.safeSegments("A\\b\\c.lua"))
        assertEquals(listOf("A", "b"), AddonZip.safeSegments("./A//b/"))
        assertEquals(emptyList<String>(), AddonZip.safeSegments("./"))
    }
}
