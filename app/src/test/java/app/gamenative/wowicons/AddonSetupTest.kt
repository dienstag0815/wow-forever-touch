package app.gamenative.wowicons

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AddonSetupTest {
    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("addon-setup").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private val files = mapOf(
        "TouchForever.toc" to "## Interface: 16001\n".toByteArray(),
        "TouchForever.lua" to "print('hi')\n".toByteArray(),
    )

    private fun addonFile(name: String) = File(root, "_forever_/Interface/AddOns/TouchForever/$name")

    @Test
    fun installsAndVerifiesBeforeTheGameIsStarted() {
        File(root, "_forever_/WTF").mkdirs()
        File(root, "_forever_/WTF/Config.wtf").writeText("SET portal \"EU\"\n")
        val result = AddonSetup.install(root, files)
        assertEquals(2, result.written)
        assertTrue(result.upToDate)
        assertNull(result.problem)
        assertEquals("print('hi')\n", addonFile("TouchForever.lua").readText())
        // WoW must load the addon whatever its interface number: the line is there before the game reads Config.wtf
        assertTrue(File(root, "_forever_/WTF/Config.wtf").readLines().contains("SET checkAddonVersion \"0\""))
    }

    @Test
    fun anOldAddonOnTheDiskIsReplacedAndTheCopyIsChecked() {
        File(root, "_forever_").mkdirs()
        addonFile("TouchForever.lua").parentFile!!.mkdirs()
        addonFile("TouchForever.lua").writeText("old version\n")
        val result = AddonSetup.install(root, files)
        assertTrue(result.upToDate)
        assertEquals("print('hi')\n", addonFile("TouchForever.lua").readText())
    }

    @Test
    fun secondRunWritesNothingAndStaysCurrent() {
        File(root, "_forever_").mkdirs()
        AddonSetup.install(root, files)
        val again = AddonSetup.install(root, files)
        assertEquals(0, again.written)
        assertTrue(again.upToDate)
    }

    @Test
    fun withoutAFlavorFolderNothingIsInstalledAndTheDiskIsNotCurrent() {
        val result = AddonSetup.install(root, files)
        assertFalse(result.upToDate)
        assertEquals(0, result.written)
        assertFalse(File(root, "_forever_").exists())
    }

    @Test
    fun addonLanguageFileNamesGermanOnlyForGerman() {
        fun text(tag: String) = String(AddonSetup.addonLanguageFile(tag), Charsets.UTF_8)
        assertTrue(text("de").contains("ns.appLanguage = \"de\""))
        assertTrue(text("de-AT").contains("ns.appLanguage = \"de\""))
        assertTrue(text("en").contains("ns.appLanguage = \"en\""))
        assertTrue(text("fr").contains("ns.appLanguage = \"en\""))
        assertTrue(text("").contains("ns.appLanguage = \"en\""))
        assertTrue(text("de").startsWith("local _, ns = ...\n"))
    }

    @Test
    fun languageFileCarriesTheStandardLayoutSignalOnlyWhenOn() {
        fun text(on: Boolean) = String(AddonSetup.addonLanguageFile("de", on), Charsets.UTF_8)
        assertFalse(text(false).contains("layoutStandard"))
        assertEquals(text(false), String(AddonSetup.addonLanguageFile("de"), Charsets.UTF_8))
        assertTrue(text(true).contains("ns.layoutStandard = true"))
        assertTrue(text(true).contains("ns.appLanguage = \"de\""))
        assertTrue(text(true).endsWith("\n"))
    }

    @Test
    fun languageFileIsWrittenAndVerifiedLikeTheOtherFiles() {
        File(root, "_forever_").mkdirs()
        val withLanguage = files + (AddonSetup.LANG_FILE to AddonSetup.addonLanguageFile("de"))
        val result = AddonSetup.install(root, withLanguage)
        assertTrue(result.upToDate)
        assertTrue(addonFile(AddonSetup.LANG_FILE).readText().contains("\"de\""))
        assertTrue(BridgeInstaller.verify(root, withLanguage))
    }

    @Test
    fun noBundledFilesIsNotCurrent() {
        File(root, "_forever_").mkdirs()
        val result = AddonSetup.install(root, emptyMap())
        assertFalse(result.upToDate)
        assertTrue(result.problem != null)
    }

    @Test
    fun verifyDetectsAFileThatDiffers() {
        File(root, "_forever_").mkdirs()
        BridgeInstaller.install(root, files)
        assertTrue(BridgeInstaller.verify(root, files))
        addonFile("TouchForever.lua").writeText("changed\n")
        assertFalse(BridgeInstaller.verify(root, files))
        addonFile("TouchForever.lua").delete()
        assertFalse(BridgeInstaller.verify(root, files))
    }

    @Test
    fun anInstallationErrorIsReportedNotThrown() {
        // "_forever_" is a file, not a folder: nothing can be written below it
        File(root, "_forever_").writeText("not a folder")
        val result = AddonSetup.install(root, files)
        assertFalse(result.upToDate)
    }
}
