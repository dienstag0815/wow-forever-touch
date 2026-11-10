package app.gamenative.wowicons

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BridgeBackupTest {
    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("bridge-backup").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private val text: String =
        javaClass.getResourceAsStream("/wowicons/TouchForever_sample.lua")!!.readBytes().toString(Charsets.UTF_8)

    private val data: BridgeData get() = BridgeParser.parse(text)!!

    private fun withUpdated(d: BridgeData, updated: Long) = d.copy(updated = updated)

    @Test
    fun savedDataComesBackForTheCharacter() {
        assertNull(BridgeBackup.load(dir))
        assertTrue(BridgeBackup.save(dir, data, text))
        val back = BridgeBackup.load(dir)
        assertNotNull(back)
        assertEquals("Anna", back!!.character)
        assertEquals(data.slots.keys, back.slots.keys)
        assertEquals("last_Blackrock_Anna.lua", BridgeBackup.fileNameFor(data))
    }

    @Test
    fun dataWithoutContentIsNotSaved() {
        val empty = data.copy(slots = emptyMap(), spells = emptyList(), mounts = emptyList(), toys = emptyList(), items = emptyList())
        assertFalse(BridgeBackup.save(dir, empty, text))
        assertNull(BridgeBackup.load(dir))
    }

    @Test
    fun theNewestCopyOfSeveralCharactersIsLoaded() {
        BridgeBackup.save(dir, data, text)
        val other = text.replace("\"Anna\"", "\"Berta\"").replace("1762560000", "1762999999")
        val otherData = BridgeParser.parse(other)!!
        assertEquals("Berta", otherData.character)
        BridgeBackup.save(dir, otherData, other)
        assertEquals("Berta", BridgeBackup.load(dir)!!.character)
        assertEquals(2, dir.listFiles()!!.size)
    }

    @Test
    fun aBrokenCopyIsSkipped() {
        File(dir, "last_x_y.lua").writeText("this is not lua {{{")
        assertNull(BridgeBackup.load(dir))
        BridgeBackup.save(dir, data, text)
        assertEquals("Anna", BridgeBackup.load(dir)!!.character)
    }

    @Test
    fun theFileWinsUnlessItIsMissingEmptyOrOlder() {
        val file = data
        val older = withUpdated(file, file.updated - 100)
        val newer = withUpdated(file, file.updated + 100)
        val empty = file.copy(slots = emptyMap(), spells = emptyList(), mounts = emptyList(), toys = emptyList(), items = emptyList())
        assertSame(file, BridgeBackup.choose(file, null))
        assertSame(file, BridgeBackup.choose(file, older))
        assertSame(file, BridgeBackup.choose(file, file))           // a tie: the file
        assertSame(newer, BridgeBackup.choose(file, newer))         // the file is older than the copy
        assertSame(older, BridgeBackup.choose(null, older))         // no file at all (new WTF folder)
        assertSame(older, BridgeBackup.choose(empty, older))        // empty file
        assertNull(BridgeBackup.choose(null, null))
        assertNull(BridgeBackup.choose(empty, empty))
    }
}
