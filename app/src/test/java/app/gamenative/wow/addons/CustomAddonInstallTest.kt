package app.gamenative.wow.addons

import app.gamenative.R
import app.gamenative.ui.screen.wow.launcher.UiText
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CustomAddonInstallTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val limit = 10L * 1024 * 1024
    private val none = emptySet<String>()

    private fun zipOf(vararg entries: Pair<String, String>): File {
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

    private fun toc(v: String = "1") = "## Interface: 16001\n## Title: Demo\n## Version: $v\n"

    private fun install(zip: File, dir: File = addons(), replace: Boolean = false, protectedFolders: Set<String> = none): List<String> {
        val scan = CustomAddonScan.scan(zip)
        return CustomAddonInstall.install(zip, dir, scan.addons, protectedFolders, replace, limit)
    }

    private fun failure(zip: File, dir: File = addons(), replace: Boolean = false, protectedFolders: Set<String> = none, max: Long = limit): UiText {
        try {
            val scan = CustomAddonScan.scan(zip)
            CustomAddonInstall.install(zip, dir, scan.addons, protectedFolders, replace, max)
        } catch (e: AddonInstallException) {
            return e.text
        }
        fail("Einbau hätte scheitern müssen")
        return UiText.raw("")
    }

    private fun noLeftovers(dir: File) {
        val names = dir.list().orEmpty().toList()
        assertTrue("Reste: $names", names.none { it.startsWith(AddonZip.STAGING_PREFIX) || it.startsWith(AddonZip.BACKUP_PREFIX) })
    }

    // ---- einbauen ----------------------------------------------------------------------------------------------------

    @Test
    fun installsAFolderFromTheTopLevel() {
        val dir = addons()
        val folders = install(zipOf("Demo/Demo.toc" to toc(), "Demo/Demo.lua" to "x", "Demo/Libs/L.lua" to "y"), dir)
        assertEquals(listOf("Demo"), folders)
        assertEquals("x", File(dir, "Demo/Demo.lua").readText())
        assertEquals("y", File(dir, "Demo/Libs/L.lua").readText())
        noLeftovers(dir)
    }

    @Test
    fun zipballWithWrapperAndAddonFolder() {
        val dir = addons()
        install(
            zipOf(
                "o-r-1a2b/README.md" to "x",
                "o-r-1a2b/Demo/Demo.toc" to toc(),
                "o-r-1a2b/Demo/Demo.lua" to "x",
                "o-r-1a2b/Demo/.hidden" to "no",
                "o-r-1a2b/Other/Other.toc" to toc(),
            ),
            dir,
        )
        assertTrue(File(dir, "Demo/Demo.toc").isFile)
        assertTrue(File(dir, "Other/Other.toc").isFile)
        assertFalse(File(dir, "Demo/.hidden").exists())
        assertFalse(File(dir, "README.md").exists())
        assertFalse(File(dir, "o-r-1a2b").exists())
        noLeftovers(dir)
    }

    @Test
    fun zipballWithTocInWrapperGetsTheTocName() {
        val dir = addons()
        install(
            zipOf(
                "o-DemoAddon-7fa/DemoAddon.toc" to toc(),
                "o-DemoAddon-7fa/main.lua" to "x",
                "o-DemoAddon-7fa/.github/FUNDING.yml" to "no",
                "o-DemoAddon-7fa/.editorconfig" to "no",
            ),
            dir,
        )
        assertTrue(File(dir, "DemoAddon/DemoAddon.toc").isFile)
        assertTrue(File(dir, "DemoAddon/main.lua").isFile)
        assertFalse(File(dir, "DemoAddon/.github").exists())
        assertFalse(File(dir, "DemoAddon/.editorconfig").exists())
        noLeftovers(dir)
    }

    @Test
    fun zipRootTocGetsWrappedIntoAFolder() {
        val dir = addons()
        install(zipOf("Demo.toc" to toc(), "Demo.lua" to "x", "sub/a.lua" to "y"), dir)
        assertTrue(File(dir, "Demo/Demo.toc").isFile)
        assertTrue(File(dir, "Demo/sub/a.lua").isFile)
    }

    @Test
    fun mapEntryPicksTheLongestPrefix() {
        val a = FoundAddon("A", listOf("w", "A"), "A", null, emptyList())
        val b = FoundAddon("B", listOf("w"), "B", null, emptyList())
        assertEquals(listOf("A", "x.lua"), CustomAddonInstall.mapEntry(listOf("w", "A", "x.lua"), listOf(a, b)))
        assertEquals(listOf("B", "y.lua"), CustomAddonInstall.mapEntry(listOf("w", "y.lua"), listOf(a, b)))
        assertNull(CustomAddonInstall.mapEntry(listOf("elsewhere", "y.lua"), listOf(a, b)))
        assertNull(CustomAddonInstall.mapEntry(listOf("w", "A", ".git", "x"), listOf(a, b)))
        assertNull(CustomAddonInstall.mapEntry(listOf("w", "A"), listOf(a))) // der Ordner selbst
    }

    // ---- Schutz ------------------------------------------------------------------------------------------------------

    @Test
    fun curatedFoldersAreNeverOverwritten() {
        val dir = addons()
        File(dir, "Questie").mkdirs()
        File(dir, "Questie/keep.txt").writeText("alt")
        val zip = zipOf("Questie/Questie.toc" to toc(), "Questie/a.lua" to "neu")
        val msg = failure(zip, dir, replace = true, protectedFolders = setOf("questie"))
        assertEquals(UiText.res(R.string.launcher_addon_assess_protected, "Questie"), msg)
        assertEquals("alt", File(dir, "Questie/keep.txt").readText())
        // auch bei anderer Schreibweise
        val zip2 = zipOf("QUESTIE/QUESTIE.toc" to toc())
        assertEquals(UiText.res(R.string.launcher_addon_assess_protected, "QUESTIE"), failure(zip2, dir, replace = true, protectedFolders = setOf("questie")))
        noLeftovers(dir)
    }

    @Test
    fun touchForeverAndBlizzardFoldersAreRefused() {
        val dir = addons()
        assertEquals(UiText.res(R.string.launcher_addon_assess_protected, "TouchForever"), failure(zipOf("TouchForever/TouchForever.toc" to toc()), dir, replace = true))
        assertEquals(UiText.res(R.string.launcher_addon_assess_protected, "WowForeverBridge"), failure(zipOf("WowForeverBridge/WowForeverBridge.toc" to toc()), dir, replace = true))
        assertEquals(UiText.res(R.string.launcher_addon_assess_protected, "Blizzard_Foo"), failure(zipOf("Blizzard_Foo/Blizzard_Foo.toc" to toc()), dir, replace = true))
        assertFalse(File(dir, "TouchForever").exists())
    }

    @Test
    fun oneBlockedFolderStopsTheWholePackage() {
        val dir = addons()
        val zip = zipOf("Demo/Demo.toc" to toc(), "Questie/Questie.toc" to toc())
        failure(zip, dir, replace = true, protectedFolders = setOf("questie"))
        assertFalse(File(dir, "Demo").exists())
        noLeftovers(dir)
    }

    @Test
    fun backupNamesAreReserved() {
        val dir = addons()
        assertEquals(UiText.res(R.string.launcher_addon_assess_bak_reserved, "_bak"), failure(zipOf("Demo_bak/Demo_bak.toc" to toc()), dir))
    }

    @Test
    fun similarlySpelledFolderIsRefused() {
        val dir = addons()
        File(dir, "demo").mkdirs()
        assertEquals(UiText.res(R.string.launcher_addon_assess_similar, "demo"), failure(zipOf("Demo/Demo.toc" to toc()), dir, replace = true))
    }

    // ---- ersetzen und Sicherung ---------------------------------------------------------------------------------------

    @Test
    fun existingFolderNeedsPermissionToReplace() {
        val dir = addons()
        File(dir, "Demo").mkdirs()
        File(dir, "Demo/old.txt").writeText("alt")
        val msg = failure(zipOf("Demo/Demo.toc" to toc()), dir, replace = false)
        assertEquals(UiText.res(R.string.launcher_addon_folder_exists, "Demo"), msg)
        assertEquals("alt", File(dir, "Demo/old.txt").readText())
        assertFalse(File(dir, "Demo_bak").exists())
    }

    @Test
    fun replacingKeepsOneBackupGeneration() {
        val dir = addons()
        install(zipOf("Demo/Demo.toc" to toc("1"), "Demo/v.txt" to "eins"), dir)
        install(zipOf("Demo/Demo.toc" to toc("2"), "Demo/v.txt" to "zwei"), dir, replace = true)
        assertEquals("zwei", File(dir, "Demo/v.txt").readText())
        assertEquals("eins", File(dir, "Demo_bak/v.txt").readText())
        install(zipOf("Demo/Demo.toc" to toc("3"), "Demo/v.txt" to "drei"), dir, replace = true)
        assertEquals("drei", File(dir, "Demo/v.txt").readText())
        assertEquals("zwei", File(dir, "Demo_bak/v.txt").readText())
        assertEquals(listOf("Demo", "Demo_bak"), dir.list().orEmpty().sorted())
        noLeftovers(dir)
    }

    @Test
    fun brokenPackageLeavesTheOldFolderAlone() {
        val dir = addons()
        install(zipOf("Demo/Demo.toc" to toc("1"), "Demo/v.txt" to "eins"), dir)
        // zu groß beim Entpacken
        val big = zipOf("Demo/Demo.toc" to toc("2"), "Demo/big.bin" to "x".repeat(5000))
        assertEquals(UiText.res(R.string.launcher_addon_zip_unpacked_too_big), failure(big, dir, replace = true, max = 1000))
        assertEquals("eins", File(dir, "Demo/v.txt").readText())
        assertFalse(File(dir, "Demo_bak").exists())
        noLeftovers(dir)
    }

    @Test
    fun leftoverBackupsComeBackAfterACrash() {
        val dir = addons()
        // Stand nach einem Absturz mitten im Tausch: nur die geparkte Sicherung ist übrig
        File(dir, "${AddonZip.BACKUP_PREFIX}123-Demo_bak").mkdirs()
        File(dir, "${AddonZip.BACKUP_PREFIX}123-Demo_bak/v.txt").writeText("alt")
        install(zipOf("Demo/Demo.toc" to toc()), dir)
        assertEquals("alt", File(dir, "Demo_bak/v.txt").readText())
        noLeftovers(dir)
    }

    // ---- Zip-Slip beim Einbau ----------------------------------------------------------------------------------------

    @Test
    fun zipSlipNeverWritesOutside() {
        val dir = addons()
        val zip = zipOf("Demo/Demo.toc" to toc(), "Demo/../../evil.txt" to "x")
        // scan lehnt schon ab
        try {
            CustomAddonScan.scan(zip)
            fail("scan hätte ablehnen müssen")
        } catch (e: AddonInstallException) {
            assertEquals(UiText.res(R.string.launcher_addon_zip_unsafe_path), e.text)
        }
        // und der Einbau prüft noch einmal selbst, falls jemand die Prüfung umgeht
        val found = FoundAddon("Demo", listOf("Demo"), "Demo", null, emptyList())
        try {
            CustomAddonInstall.install(zip, dir, listOf(found), none, false, limit)
            fail("install hätte ablehnen müssen")
        } catch (e: AddonInstallException) {
            assertEquals(UiText.res(R.string.launcher_addon_zip_unsafe_path), e.text)
        }
        assertFalse(File(tmp.root, "evil.txt").exists())
        assertFalse(File(dir.parentFile, "evil.txt").exists())
        assertFalse(File(dir, "Demo").exists())
        noLeftovers(dir)
    }

    @Test
    fun entryCountAndUnpackedSizeAreLimited() {
        val dir = addons()
        val big = zipOf("Demo/Demo.toc" to toc(), "Demo/big.bin" to "x".repeat(4000))
        assertEquals(UiText.res(R.string.launcher_addon_zip_unpacked_too_big), failure(big, dir, max = 3000))
        noLeftovers(dir)
    }

    // ---- Entfernen ---------------------------------------------------------------------------------------------------

    @Test
    fun removeDeletesFoldersAndBackup() {
        val dir = addons()
        install(zipOf("Demo/Demo.toc" to toc("1")), dir)
        install(zipOf("Demo/Demo.toc" to toc("2")), dir, replace = true)
        CustomAddonInstall.remove(dir, listOf("Demo"), none)
        assertEquals(emptyList<String>(), dir.list().orEmpty().toList())
    }

    @Test
    fun removeRefusesCuratedAndReservedFolders() {
        val dir = addons()
        File(dir, "Questie").mkdirs()
        File(dir, "TouchForever").mkdirs()
        try {
            CustomAddonInstall.remove(dir, listOf("Questie"), setOf("questie"))
            fail("hätte ablehnen müssen")
        } catch (e: AddonInstallException) {
            // erwartet
        }
        try {
            CustomAddonInstall.remove(dir, listOf("TouchForever"), none)
            fail("hätte ablehnen müssen")
        } catch (e: AddonInstallException) {
            // erwartet
        }
        assertTrue(File(dir, "Questie").isDirectory)
        assertTrue(File(dir, "TouchForever").isDirectory)
    }

    @Test
    fun protectedFoldersComeFromTheCatalog() {
        val json = """{"version":1,"stand":"x","addons":[{"id":"questie","name":"Questie","kurz":"k","warum":"w","lizenz":"l",
            "quelle":{"typ":"github","repo":"Questie/Questie","asset":"^Q.*\\.zip$"},"ordner":["Questie","QuestieDB"]}]}"""
        val catalog = (AddonCatalogParser.parse(json) as CatalogResult.Ok).catalog
        assertEquals(setOf("questie", "questiedb"), CustomAddonInstall.protectedFolders(catalog))
        assertEquals(emptySet<String>(), CustomAddonInstall.protectedFolders(null))
    }

    @Test
    fun assessReportsNewReplaceAndBlocked() {
        val dir = addons()
        File(dir, "Old").mkdirs()
        val list = listOf("New", "Old", "Questie").map { FoundAddon(it, listOf(it), it, null, emptyList()) }
        val result = CustomAddonInstall.assess(list, dir, setOf("questie")).associate { it.addon.folder to it.verdict }
        assertEquals(CustomAddonInstall.Verdict.NEW, result["New"])
        assertEquals(CustomAddonInstall.Verdict.REPLACE, result["Old"])
        assertEquals(CustomAddonInstall.Verdict.BLOCKED, result["Questie"])
    }
}
