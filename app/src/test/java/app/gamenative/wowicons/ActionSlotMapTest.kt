package app.gamenative.wowicons

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ActionSlotMapTest {
    @Test
    fun mainBarIsSlots1To12() {
        for (n in 1..12) assertEquals(n, ActionSlotMap.slotFor("ACTIONBUTTON$n"))
    }

    @Test
    fun multiBarsFollowWowsPages() {
        // bar 1 = bottom left (61-72), 2 = bottom right (49-60), 3 = right (25-36), 4 = right 2 (37-48)
        assertEquals(61, ActionSlotMap.slotFor("MULTIACTIONBAR1BUTTON1"))
        assertEquals(72, ActionSlotMap.slotFor("MULTIACTIONBAR1BUTTON12"))
        assertEquals(49, ActionSlotMap.slotFor("MULTIACTIONBAR2BUTTON1"))
        assertEquals(60, ActionSlotMap.slotFor("MULTIACTIONBAR2BUTTON12"))
        assertEquals(25, ActionSlotMap.slotFor("MULTIACTIONBAR3BUTTON1"))
        assertEquals(36, ActionSlotMap.slotFor("MULTIACTIONBAR3BUTTON12"))
        assertEquals(37, ActionSlotMap.slotFor("MULTIACTIONBAR4BUTTON1"))
        assertEquals(48, ActionSlotMap.slotFor("MULTIACTIONBAR4BUTTON12"))
        assertEquals(145, ActionSlotMap.slotFor("MULTIACTIONBAR5BUTTON1"))
        assertEquals(157, ActionSlotMap.slotFor("MULTIACTIONBAR6BUTTON1"))
        assertEquals(180, ActionSlotMap.slotFor("MULTIACTIONBAR7BUTTON12"))
    }

    @Test
    fun everySlotOfTheBarsIsDistinct() {
        val all = buildList {
            for (n in 1..12) add(ActionSlotMap.slotFor("ACTIONBUTTON$n")!!)
            for (bar in 1..7) for (n in 1..12) add(ActionSlotMap.slotFor("MULTIACTIONBAR${bar}BUTTON$n")!!)
        }
        assertEquals(96, all.size)
        assertEquals(96, all.toSet().size)
    }

    @Test
    fun otherCommandsHaveNoSlot() {
        assertNull(ActionSlotMap.slotFor(null))
        assertNull(ActionSlotMap.slotFor(""))
        assertNull(ActionSlotMap.slotFor("TOGGLEWORLDMAP"))
        assertNull(ActionSlotMap.slotFor("ACTIONBUTTON0"))
        assertNull(ActionSlotMap.slotFor("ACTIONBUTTON13"))
        assertNull(ActionSlotMap.slotFor("MULTIACTIONBAR8BUTTON1"))
        assertNull(ActionSlotMap.slotFor("MULTIACTIONBAR1BUTTON13"))
        assertNull(ActionSlotMap.slotFor("EMOTE_WAVE"))
        assertNull(ActionSlotMap.slotFor("actionbutton1"))
    }
}

class BridgeLocatorTest {
    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("wowicons-locator").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun write(path: String, modified: Long, text: String = "TouchForeverDB = {}\n"): File {
        val file = File(root, path)
        file.parentFile.mkdirs()
        file.writeText(text)
        file.setLastModified(modified)
        return file
    }

    @Test
    fun noFolderNoFile() {
        assertNull(BridgeLocator.find(root))
        File(root, "_forever_/WTF/Account").mkdirs()
        assertNull(BridgeLocator.find(root))
    }

    @Test
    fun findsCharacterFile() {
        val f = write("_forever_/WTF/Account/ACC1/Realm/Char/SavedVariables/TouchForever.lua", 1_000_000)
        assertEquals(f.canonicalPath, BridgeLocator.find(root)!!.canonicalPath)
    }

    @Test
    fun picksTheCharacterWrittenMostRecently() {
        write("_forever_/WTF/Account/ACC1/Realm/Old/SavedVariables/TouchForever.lua", 1_000_000)
        val newest = write("_forever_/WTF/Account/ACC1/Realm/New/SavedVariables/TouchForever.lua", 3_000_000)
        write("_forever_/WTF/Account/ACC1/Realm2/Mid/SavedVariables/TouchForever.lua", 2_000_000)
        assertEquals(newest.canonicalPath, BridgeLocator.find(root)!!.canonicalPath)
    }

    @Test
    fun picksAcrossAccounts() {
        write("_forever_/WTF/Account/ACC1/R/A/SavedVariables/TouchForever.lua", 1_000_000)
        val newest = write("_forever_/WTF/Account/ACC2/R/B/SavedVariables/TouchForever.lua", 5_000_000)
        assertEquals(newest.canonicalPath, BridgeLocator.find(root)!!.canonicalPath)
    }

    @Test
    fun accountWideFileCounts() {
        val f = write("_forever_/WTF/Account/ACC1/SavedVariables/TouchForever.lua", 1_000_000)
        assertEquals(f.canonicalPath, BridgeLocator.find(root)!!.canonicalPath)
    }

    @Test
    fun otherAddonsFilesAreIgnored() {
        write("_forever_/WTF/Account/ACC1/R/A/SavedVariables/Questie.lua", 9_000_000)
        assertNull(BridgeLocator.find(root))
    }

    @Test
    fun betaFolderIsUsedWhenForeverHasNothing() {
        val f = write("_classic_beta_/WTF/Account/ACC1/R/A/SavedVariables/TouchForever.lua", 1_000_000)
        assertEquals(f.canonicalPath, BridgeLocator.find(root)!!.canonicalPath)
        val forever = write("_forever_/WTF/Account/ACC1/R/A/SavedVariables/TouchForever.lua", 500_000)
        assertEquals(forever.canonicalPath, BridgeLocator.find(root)!!.canonicalPath)
    }

    @Test
    fun readReturnsNullForBrokenFile() {
        val bad = write("_forever_/WTF/Account/A/R/C/SavedVariables/TouchForever.lua", 1, "TouchForeverDB = { slots = {")
        assertNull(BridgeLocator.read(bad))
        assertNull(BridgeLocator.read(File(root, "missing.lua")))
        val good = write("_forever_/WTF/Account/A/R/D/SavedVariables/TouchForever.lua", 1, "TouchForeverDB = { class = \"MAGE\" }")
        assertEquals("MAGE", BridgeLocator.read(good)!!.playerClass)
    }

    @Test
    fun oldFileNameIsUsedOnlyWithoutANewFile() {
        val old = write("_forever_/WTF/Account/ACC1/R/A/SavedVariables/WowForeverBridge.lua", 9_000_000, "WowForeverBridgeDB = {}\n")
        assertEquals(old.canonicalPath, BridgeLocator.find(root)!!.canonicalPath)
        val fresh = write("_forever_/WTF/Account/ACC1/R/A/SavedVariables/TouchForever.lua", 1_000_000)
        assertEquals(fresh.canonicalPath, BridgeLocator.find(root)!!.canonicalPath)
    }
}
