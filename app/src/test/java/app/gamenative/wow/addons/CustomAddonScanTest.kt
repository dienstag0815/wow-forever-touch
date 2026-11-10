package app.gamenative.wow.addons

import app.gamenative.R
import app.gamenative.ui.screen.wow.launcher.UiText
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CustomAddonScanTest {

    @get:Rule
    val tmp = TemporaryFolder()

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

    private fun toc(title: String = "Demo", version: String = "1.2.3", iface: String = "16001") =
        "## Interface: $iface\n## Title: $title\n## Version: $version\n\nDemo.lua\n"

    private fun failure(zip: File): UiText {
        try {
            CustomAddonScan.scan(zip)
        } catch (e: AddonInstallException) {
            return e.text
        }
        fail("Prüfung hätte scheitern müssen")
        return UiText.raw("")
    }

    // ---- .toc lesen --------------------------------------------------------------------------------------------------

    @Test
    fun tocFieldsAreRead() {
        val info = TocParser.parse("\uFEFF## Interface: 120105, 120100, 16001\r\n## Title: |cff00ff00Demo|r Addon\r\n## Version: 2.0\r\nfile.lua")
        assertEquals("Demo Addon", info.title)
        assertEquals("2.0", info.version)
        assertEquals(listOf(120105, 120100, 16001), info.interfaces)
    }

    @Test
    fun tocPlaceholderVersionIsIgnoredAndMissingFieldsAreNull() {
        val info = TocParser.parse("## Version: @project-version@\n## Title-deDE: Anders\n")
        assertNull(info.version)
        assertNull(info.title)
        assertTrue(info.interfaces.isEmpty())
    }

    @Test
    fun tocBaseStripsGameSuffix() {
        assertEquals("Questie", CustomAddonScan.tocBase("Questie.toc"))
        assertEquals("Questie", CustomAddonScan.tocBase("Questie_Forever.toc"))
        assertEquals("Questie", CustomAddonScan.tocBase("Questie-Classic.toc"))
        assertEquals("My_Addon", CustomAddonScan.tocBase("My_Addon.toc"))
    }

    // ---- Ordner finden -----------------------------------------------------------------------------------------------

    @Test
    fun folderAtTopLevel() {
        val scan = CustomAddonScan.scan(zipOf("Demo/Demo.toc" to toc(), "Demo/Demo.lua" to "x"))
        assertEquals(1, scan.addons.size)
        val a = scan.addons.single()
        assertEquals("Demo", a.folder)
        assertEquals(listOf("Demo"), a.prefix)
        assertEquals("Demo", a.title)
        assertEquals("1.2.3", a.version)
        assertEquals(listOf(16001), a.interfaces)
    }

    @Test
    fun zipballWithWrapperFolderAndAddonFolder() {
        // GitHub-zipball: Hüllordner repo-main, darin der Addon-Ordner
        val scan = CustomAddonScan.scan(
            zipOf(
                "owner-repo-1a2b3c/" to "",
                "owner-repo-1a2b3c/README.md" to "x",
                "owner-repo-1a2b3c/.github/FUNDING.yml" to "x",
                "owner-repo-1a2b3c/Demo/Demo.toc" to toc(),
                "owner-repo-1a2b3c/Demo/Demo.lua" to "x",
            ),
        )
        val a = scan.addons.single()
        assertEquals("Demo", a.folder)
        assertEquals(listOf("owner-repo-1a2b3c", "Demo"), a.prefix)
    }

    @Test
    fun zipballWithTocDirectlyInTheWrapperTakesTheNameFromTheToc() {
        val scan = CustomAddonScan.scan(
            zipOf(
                "Stanzilla-AdvancedInterfaceOptions-7fa425b/AdvancedInterfaceOptions.toc" to toc(title = "Advanced Interface Options"),
                "Stanzilla-AdvancedInterfaceOptions-7fa425b/basicOptions.lua" to "x",
                "Stanzilla-AdvancedInterfaceOptions-7fa425b/semlib/eve.lua" to "x",
            ),
        )
        val a = scan.addons.single()
        assertEquals("AdvancedInterfaceOptions", a.folder)
        assertEquals(listOf("Stanzilla-AdvancedInterfaceOptions-7fa425b"), a.prefix)
        assertEquals("Advanced Interface Options", a.title)
    }

    @Test
    fun tocAtZipRootTakesTheNameFromTheToc() {
        val scan = CustomAddonScan.scan(zipOf("Demo.toc" to toc(), "Demo.lua" to "x"))
        val a = scan.addons.single()
        assertEquals("Demo", a.folder)
        assertTrue(a.prefix.isEmpty())
    }

    @Test
    fun flavourTocInsideMatchingFolderIsEnough() {
        val scan = CustomAddonScan.scan(zipOf("Questie/Questie_Forever.toc" to toc(title = "Questie"), "Questie/a.lua" to "x"))
        assertEquals("Questie", scan.addons.single().folder)
    }

    @Test
    fun severalAddonFoldersAreAllFound() {
        val scan = CustomAddonScan.scan(
            zipOf(
                "WeakAuras/WeakAuras.toc" to toc(title = "WeakAuras"),
                "WeakAurasOptions/WeakAurasOptions.toc" to toc(title = "WeakAuras Options"),
                "WeakAuras/Libs/LibStub/LibStub.toc" to toc(title = "LibStub"),
                "WeakAuras/Sub/Sub.toc" to toc(title = "Sub"),
                "WeakAuras/Modules/Sub/Sub.toc" to toc(title = "Sub"),
            ),
        )
        assertEquals(listOf("WeakAuras", "WeakAurasOptions"), scan.addons.map { it.folder })
    }

    @Test
    fun nestedLibraryTocsAreNotAddons() {
        val scan = CustomAddonScan.scan(
            zipOf(
                "Demo/Demo.toc" to toc(),
                "Demo/Ace/Ace.toc" to toc(title = "Ace"),
                "Demo/Libs/Deep/Deep.toc" to toc(title = "Deep"),
            ),
        )
        assertEquals(listOf("Demo"), scan.addons.map { it.folder })
    }

    @Test
    fun macResourceFolderIsIgnored() {
        val scan = CustomAddonScan.scan(zipOf("__MACOSX/Demo/._Demo.toc" to "x", "Demo/Demo.toc" to toc()))
        assertEquals(listOf("Demo"), scan.addons.map { it.folder })
    }

    @Test
    fun folderNameDifferingFromTocTwoLevelsDeepIsNotAnAddon() {
        val msg = failure(zipOf("wrap/Other/Demo.toc" to toc(), "wrap/Other/a.lua" to "x"))
        assertEquals(UiText.res(R.string.launcher_addon_scan_no_addon), msg)
    }

    @Test
    fun noTocMeansNoAddon() {
        val msg = failure(zipOf("Demo/Demo.lua" to "x", "README.md" to "x"))
        assertEquals(UiText.res(R.string.launcher_addon_scan_no_addon), msg)
    }

    @Test
    fun notAZipIsRefused() {
        val file = tmp.newFile()
        file.writeText("<html>nope</html>")
        assertEquals(UiText.res(R.string.launcher_addon_zip_corrupt), failure(file))
    }

    @Test
    fun invalidFolderNameFromTocIsRefused() {
        val msg = failure(zipOf("wrap/Bad Name.toc" to toc()))
        assertEquals(UiText.res(R.string.launcher_addon_scan_bad_folder, "Bad Name"), msg)
    }

    // ---- Zip-Slip und unsichere Pfade --------------------------------------------------------------------------------

    @Test
    fun zipSlipIsRefused() {
        assertEquals(UiText.res(R.string.launcher_addon_zip_unsafe_path), failure(zipOf("../evil/Evil.toc" to toc())))
        assertEquals(UiText.res(R.string.launcher_addon_zip_unsafe_path), failure(zipOf("Demo/Demo.toc" to toc(), "Demo/../../x.lua" to "x")))
        assertEquals(UiText.res(R.string.launcher_addon_zip_unsafe_path), failure(zipOf("Demo/Demo.toc" to toc(), "Demo/a/../../../x.lua" to "x")))
    }

    @Test
    fun absoluteAndDrivePathsAreRefused() {
        assertEquals(UiText.res(R.string.launcher_addon_zip_unsafe_path), failure(zipOf("/etc/Demo/Demo.toc" to toc())))
        assertEquals(UiText.res(R.string.launcher_addon_zip_unsafe_path), failure(zipOf("Demo/Demo.toc" to toc(), "C:/Windows/x.lua" to "x")))
        assertEquals(UiText.res(R.string.launcher_addon_zip_unsafe_path), failure(zipOf("Demo/Demo.toc" to toc(), "\\abs\\x.lua" to "x")))
        assertEquals(UiText.res(R.string.launcher_addon_zip_unsafe_path), failure(zipOf("Demo/Demo.toc" to toc(), "Demo/a:b.lua" to "x")))
    }

    // ---- Interface-Hinweis -------------------------------------------------------------------------------------------

    @Test
    fun interfaceMatchesTheForeverSeries() {
        fun found(vararg i: Int) = FoundAddon("Demo", listOf("Demo"), "Demo", null, i.toList())
        assertEquals(true, CustomAddonScan.interfaceMatches(found(16001)))
        assertEquals(true, CustomAddonScan.interfaceMatches(found(120105, 16001)))
        assertEquals(false, CustomAddonScan.interfaceMatches(found(120105, 11509)))
        assertNull(CustomAddonScan.interfaceMatches(found()))
    }

    @Test
    fun interfaceWarningTexts() {
        val bad = FoundAddon("Demo", listOf("Demo"), "Demo", null, listOf(120105))
        val none = bad.copy(interfaces = emptyList())
        val good = bad.copy(interfaces = listOf(16001))
        assertEquals(UiText.res(R.string.launcher_addon_interface_warning_other, "120105", 16000, 16999), CustomAddonText.interfaceWarning(bad))
        assertEquals(UiText.res(R.string.launcher_addon_interface_warning_unknown), CustomAddonText.interfaceWarning(none))
        assertNull(CustomAddonText.interfaceWarning(good))
        assertEquals(UiText.res(R.string.launcher_addon_interface_line, "120105"), CustomAddonText.interfaceLine(bad))
        assertEquals(UiText.res(R.string.launcher_addon_interface_none), CustomAddonText.interfaceLine(none))
    }

    @Test
    fun declaredSizeIsSummed() {
        val scan = CustomAddonScan.scan(zipOf("Demo/Demo.toc" to toc(), "Demo/a.lua" to "12345"))
        assertTrue(scan.declaredBytes > 5)
        assertEquals(2, scan.entryCount)
        assertNotNull(scan.addons.firstOrNull())
        assertFalse(scan.addons.isEmpty())
    }
}
