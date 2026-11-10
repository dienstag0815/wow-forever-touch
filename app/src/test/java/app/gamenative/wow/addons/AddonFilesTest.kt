package app.gamenative.wow.addons

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AddonFilesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val mb = 1024L * 1024
    private val demo = CatalogEntry(
        "demo", "Demo", "k", "w", "l", AddonSource.GitHub("o", "demo", "x"), listOf("Demo", "Demo_B"), 30 * mb, 100 * mb,
    )

    private fun dir(): File = File(tmp.root, "AddOns").also { it.mkdirs() }

    private fun addon(dir: File, folder: String, vararg tocs: String) {
        File(dir, folder).mkdirs()
        val files = if (tocs.isEmpty()) listOf("$folder.toc") else tocs.toList()
        for (toc in files) File(dir, "$folder/$toc").writeText("## Interface: 16001")
        File(dir, "$folder/code.lua").writeText("x")
    }

    private fun record(tag: String = "v1.0", folders: List<String> = demo.folders) =
        InstalledRecord("demo", tag, "ab".repeat(32), 1234, 99L, folders, "Demo-$tag.zip")

    // ---- Verzeichnis --------------------------------------------------------------------------------------------

    @Test
    fun manifestRoundTrips() {
        val d = dir()
        assertTrue(AddonManifest.read(d).isEmpty())
        AddonManifest.put(d, record())
        AddonManifest.put(d, record().copy(id = "zwei", folders = listOf("Zwei")))
        val read = AddonManifest.read(d)
        assertEquals(setOf("demo", "zwei"), read.keys)
        assertEquals(record(), read["demo"])
        AddonManifest.remove(d, "demo")
        assertEquals(setOf("zwei"), AddonManifest.read(d).keys)
    }

    @Test
    fun brokenManifestIsTreatedAsEmpty() {
        val d = dir()
        File(d, AddonManifest.FILE).writeText("{kaputt")
        assertTrue(AddonManifest.read(d).isEmpty())
        File(d, AddonManifest.FILE).writeText("""{"addons":{"x":{"tag":"","folders":["A"]},"y":{"tag":"v1","folders":[]},"z":5}}""")
        assertTrue(AddonManifest.read(d).isEmpty())
        AddonManifest.put(d, record())
        assertEquals(setOf("demo"), AddonManifest.read(d).keys)
    }

    @Test
    fun manifestIsNotAnAddonFolder() {
        val d = dir()
        AddonManifest.put(d, record())
        assertTrue(File(d, AddonManifest.FILE).isFile)
        assertTrue("WoW sieht keinen Ordner", d.listFiles().orEmpty().none { it.isDirectory })
    }

    // ---- Aktiv-Schalter -----------------------------------------------------------------------------------------

    @Test
    fun anAddonCanBeSwitchedOffAndOn() {
        val d = dir()
        addon(d, "Demo"); addon(d, "Demo_B")
        assertEquals(EnableState.ENABLED, AddonToggle.state(d, demo.folders))
        assertEquals(2, AddonToggle.setEnabled(d, demo.folders, false))
        assertEquals(EnableState.DISABLED, AddonToggle.state(d, demo.folders))
        assertFalse(File(d, "Demo/Demo.toc").exists())
        assertTrue(File(d, "Demo/Demo.toc.aus").isFile)
        assertTrue("Dateien bleiben", File(d, "Demo/code.lua").isFile)
        assertEquals(0, AddonToggle.setEnabled(d, demo.folders, false))
        assertEquals(2, AddonToggle.setEnabled(d, demo.folders, true))
        assertEquals(EnableState.ENABLED, AddonToggle.state(d, demo.folders))
        assertEquals("## Interface: 16001", File(d, "Demo/Demo.toc").readText())
    }

    @Test
    fun allTocVariantsAreSwitchedAndNestedLibsAreNot() {
        val d = dir()
        addon(d, "Demo", "Demo.toc", "Demo_Mainline.toc", "Demo-Forever.toc")
        File(d, "Demo/Libs/LibStub").mkdirs()
        File(d, "Demo/Libs/LibStub/LibStub.toc").writeText("lib")
        assertEquals(3, AddonToggle.setEnabled(d, listOf("Demo"), false))
        assertTrue(File(d, "Demo/Libs/LibStub/LibStub.toc").isFile)
        assertEquals(EnableState.DISABLED, AddonToggle.state(d, listOf("Demo")))
        AddonToggle.setEnabled(d, listOf("Demo"), true)
        assertTrue(File(d, "Demo/Demo-Forever.toc").isFile)
    }

    @Test
    fun mixedAndMissingStates() {
        val d = dir()
        assertEquals(EnableState.MISSING, AddonToggle.state(d, demo.folders))
        addon(d, "Demo"); addon(d, "Demo_B")
        AddonToggle.setEnabled(d, listOf("Demo_B"), false)
        assertEquals(EnableState.MIXED, AddonToggle.state(d, demo.folders))
        AddonToggle.setEnabled(d, demo.folders, true)
        assertEquals(EnableState.ENABLED, AddonToggle.state(d, demo.folders))
    }

    @Test
    fun enablingWithBothFilesKeepsTheLiveOne() {
        val d = dir()
        addon(d, "Demo")
        File(d, "Demo/Demo.toc.aus").writeText("alt")
        AddonToggle.setEnabled(d, listOf("Demo"), true)
        assertEquals("## Interface: 16001", File(d, "Demo/Demo.toc").readText())
        assertFalse(File(d, "Demo/Demo.toc.aus").exists())
    }

    @Test
    fun touchForeverIsNeverSwitchedOrRemoved() {
        val d = dir()
        addon(d, "TouchForever")
        for (action in listOf<() -> Unit>(
            { AddonToggle.setEnabled(d, listOf("TouchForever"), false) },
            { AddonToggle.setEnabled(d, listOf("Demo", "touchforever"), false) },
            { AddonFiles.remove(d, listOf("TouchForever")) },
            { AddonFiles.remove(d, listOf("Blizzard_Foo")) },
        )) {
            try {
                action()
                fail("hätte abgelehnt werden müssen")
            } catch (e: AddonInstallException) {
                // erwartet
            }
        }
        assertTrue(File(d, "TouchForever/TouchForever.toc").isFile)
        assertTrue(File(d, "TouchForever/code.lua").isFile)
    }

    // ---- Entfernen ----------------------------------------------------------------------------------------------

    @Test
    fun removeDeletesOnlyTheEntrysFolders() {
        val d = dir()
        addon(d, "Demo"); addon(d, "Demo_B"); addon(d, "Andere"); addon(d, "TouchForever")
        assertEquals(2, AddonFiles.remove(d, demo.folders))
        assertFalse(File(d, "Demo").exists())
        assertFalse(File(d, "Demo_B").exists())
        assertTrue(File(d, "Andere/Andere.toc").isFile)
        assertTrue(File(d, "TouchForever/TouchForever.toc").isFile)
        assertEquals(0, AddonFiles.remove(d, demo.folders))
    }

    @Test
    fun removeAlsoDeletesSwitchedOffFolders() {
        val d = dir()
        addon(d, "Demo")
        AddonToggle.setEnabled(d, listOf("Demo"), false)
        assertEquals(1, AddonFiles.remove(d, listOf("Demo")))
    }

    // ---- Anzeige-Zustand -------------------------------------------------------------------------------------------

    @Test
    fun statusFollowsRecordFilesAndLatestTag() {
        val d = dir()
        // nichts da
        var s = AddonStatusLogic.compute(demo, null, d, "v1.0")
        assertEquals(AddonInstallState.NOT_INSTALLED, s.state)
        assertEquals("v1.0", s.latestTag)
        // Ordner da, aber nicht von uns
        addon(d, "Demo")
        s = AddonStatusLogic.compute(demo, null, d, "v1.0")
        assertEquals(AddonInstallState.FOREIGN, s.state)
        // von uns installiert und aktuell
        addon(d, "Demo_B")
        s = AddonStatusLogic.compute(demo, record("v1.0"), d, "v1.0")
        assertEquals(AddonInstallState.INSTALLED, s.state)
        assertEquals("v1.0", s.installedTag)
        assertEquals(EnableState.ENABLED, s.enable)
        // neuere Version bekannt
        s = AddonStatusLogic.compute(demo, record("v1.0"), d, "v1.1")
        assertEquals(AddonInstallState.UPDATE_AVAILABLE, s.state)
        // neueste Version unbekannt (kein Netz): kein Update behauptet
        s = AddonStatusLogic.compute(demo, record("v1.0"), d, null)
        assertEquals(AddonInstallState.INSTALLED, s.state)
        // Ordner von Hand gelöscht: gilt als nicht installiert
        AddonFiles.remove(d, demo.folders)
        s = AddonStatusLogic.compute(demo, record("v1.0"), d, "v1.0")
        assertEquals(AddonInstallState.NOT_INSTALLED, s.state)
        assertNull(s.installedTag)
    }

    @Test
    fun statusShowsSwitchedOff() {
        val d = dir()
        addon(d, "Demo"); addon(d, "Demo_B")
        AddonToggle.setEnabled(d, demo.folders, false)
        val s = AddonStatusLogic.compute(demo, record(), d, "v1.0")
        assertEquals(AddonInstallState.INSTALLED, s.state)
        assertEquals(EnableState.DISABLED, s.enable)
    }
}
