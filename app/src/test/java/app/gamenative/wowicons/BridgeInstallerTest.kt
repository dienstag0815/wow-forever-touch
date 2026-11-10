package app.gamenative.wowicons

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BridgeInstallerTest {
    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("wowicons-install").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private val files = mapOf(
        "TouchForever.toc" to "## Interface: 16001\n".toByteArray(),
        "TouchForever.lua" to "print('hi')\n".toByteArray(),
    )

    private fun addon(flavor: String = "_forever_") = File(root, "$flavor/Interface/AddOns/TouchForever")

    @Test
    fun nothingIsCreatedWithoutTheFlavorFolder() {
        assertEquals(0, BridgeInstaller.install(root, files))
        assertFalse(File(root, "_forever_").exists())
    }

    @Test
    fun copiesAllFiles() {
        File(root, "_forever_").mkdirs()
        assertEquals(2, BridgeInstaller.install(root, files))
        assertEquals("print('hi')\n", File(addon(), "TouchForever.lua").readText())
        assertTrue(File(addon(), "TouchForever.toc").isFile)
    }

    @Test
    fun secondRunWritesNothing() {
        File(root, "_forever_").mkdirs()
        BridgeInstaller.install(root, files)
        val toc = File(addon(), "TouchForever.toc")
        val stamp = 123_000L
        toc.setLastModified(stamp)
        assertEquals(0, BridgeInstaller.install(root, files))
        assertEquals(stamp, toc.lastModified())
    }

    @Test
    fun changedFileIsReplaced() {
        File(root, "_forever_").mkdirs()
        BridgeInstaller.install(root, files)
        File(addon(), "TouchForever.lua").writeText("old")
        assertEquals(1, BridgeInstaller.install(root, files))
        assertEquals("print('hi')\n", File(addon(), "TouchForever.lua").readText())
    }

    @Test
    fun betaFolderGetsItToo() {
        File(root, "_forever_").mkdirs()
        File(root, "_classic_beta_").mkdirs()
        assertEquals(4, BridgeInstaller.install(root, files))
        assertTrue(File(addon("_classic_beta_"), "TouchForever.toc").isFile)
    }

    @Test
    fun configGetsTheVersionCheckLineOnce() {
        val config = File(root, "Config.wtf")
        config.writeText("SET portal \"EU\"\nSET maxFPS \"60\"\n")
        assertTrue(BridgeInstaller.allowOutOfDateAddons(config))
        assertEquals("SET portal \"EU\"\nSET maxFPS \"60\"\nSET checkAddonVersion \"0\"\n", config.readText())
        assertFalse(BridgeInstaller.allowOutOfDateAddons(config))
        assertEquals(1, config.readLines().count { it.startsWith("SET checkAddonVersion") })
    }

    @Test
    fun playersChoiceIsNeverChanged() {
        val config = File(root, "Config.wtf")
        config.writeText("SET checkAddonVersion \"1\"\n")
        assertFalse(BridgeInstaller.allowOutOfDateAddons(config))
        assertEquals("SET checkAddonVersion \"1\"\n", config.readText())
    }

    @Test
    fun missingConfigIsLeftAlone() {
        assertFalse(BridgeInstaller.allowOutOfDateAddons(File(root, "none.wtf")))
        assertFalse(File(root, "none.wtf").exists())
    }

    @Test
    fun bundledAddonFilesAreConsistent() {
        // The files in assets must be what the installer expects: a .toc naming the lua file and the SavedVariables name.
        val dir = File("src/main/assets/${BridgeInstaller.ASSET_DIR}").takeIf { it.isDirectory }
            ?: File("app/src/main/assets/${BridgeInstaller.ASSET_DIR}")
        if (!dir.isDirectory) return // run from an unexpected working directory
        val toc = File(dir, "TouchForever.toc").readText()
        val lua = File(dir, "TouchForever.lua").readText()
        assertTrue(toc.contains("TouchForever.lua"))
        assertTrue(toc.contains("## SavedVariablesPerCharacter: ${BridgeParser.VARIABLE}"))
        assertTrue(toc.lines().any { it.startsWith("## Interface:") && it.contains("16001") })
        assertTrue(lua.contains("TouchForeverDB"))
        assertTrue(lua.contains("ReloadUI"))
        assertTrue(lua.contains("SLASH_TOUCHFOREVER1 = \"/tf\""))
        assertFalse("LF only", lua.contains("\r"))
    }

    @Test
    fun removesTheFolderOfTheOldAddonName() {
        val old = File(root, "_forever_/Interface/AddOns/${BridgeInstaller.OLD_ADDON_NAME}")
        old.mkdirs()
        File(old, "WowForeverBridge.toc").writeText("## Title: old\n")
        BridgeInstaller.install(root, files)
        assertFalse(old.exists())
        assertTrue(File(addon(), "TouchForever.toc").isFile)
    }

    @Test
    fun takesOverTheOldSavedVariablesOnce() {
        val sv = File(root, "_forever_/WTF/Account/ACC/Realm/Char/SavedVariables").apply { mkdirs() }
        val account = File(root, "_forever_/WTF/Account/ACC/SavedVariables").apply { mkdirs() }
        File(sv, "WowForeverBridge.lua").writeText("WowForeverBridgeDB = {\n[\"ui\"] = { [\"chatSize\"] = 10 },\n}\n")
        File(account, "WowForeverBridge.lua").writeText("WowForeverBridgeDB = {}\n")
        BridgeInstaller.install(root, files)
        val moved = File(sv, "TouchForever.lua")
        assertEquals("TouchForeverDB = {\n[\"ui\"] = { [\"chatSize\"] = 10 },\n}\n", moved.readText())
        assertTrue(File(account, "TouchForever.lua").isFile)
        assertTrue("old file stays", File(sv, "WowForeverBridge.lua").isFile)
        // A file of the new addon is never overwritten.
        moved.writeText("TouchForeverDB = { new = true }\n")
        assertEquals(0, BridgeInstaller.migrateSavedVariables(File(root, "_forever_/WTF/Account")))
        assertEquals("TouchForeverDB = { new = true }\n", moved.readText())
    }
}
