package app.gamenative.wowicons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeDataTest {
    private fun sample(): String =
        javaClass.getResourceAsStream("/wowicons/TouchForever_sample.lua")!!.readBytes().toString(Charsets.UTF_8)

    private fun data(): BridgeData = BridgeParser.parse(sample())!!

    @Test
    fun readsHeader() {
        val d = data()
        assertEquals("MAGE", d.playerClass)
        assertEquals("Fire", d.specName)
        assertEquals("Anna", d.character)
        assertEquals("Blackrock", d.realm)
        assertEquals(1762560000L, d.updated)
    }

    @Test
    fun readsSlotsWithGaps() {
        val d = data()
        assertEquals(setOf(1, 3, 61, 62, 145), d.slots.keys)
        val fireball = d.slots.getValue(1)
        assertEquals("spell", fireball.type)
        assertEquals(133L, fireball.spellId)
        assertEquals(135812L, fireball.iconFileId)
        assertEquals("Fireball", fireball.name)
        assertNull(fireball.itemId)
    }

    @Test
    fun itemSlotHasItemId() {
        val slot = data().slots.getValue(3)
        assertEquals(6948L, slot.itemId)
        assertNull(slot.spellId)
        assertEquals(134414L, slot.iconFileId)
    }

    @Test
    fun macroKeepsEscapedNameAndSpell() {
        val slot = data().slots.getValue(61)
        assertEquals("macro", slot.type)
        assertEquals("Mount \"Up\"", slot.name)
        assertEquals(783L, slot.spellId)
    }

    @Test
    fun texturePathBecomesIconName() {
        val slot = data().slots.getValue(62)
        assertEquals("spell_holy_magicalsentry", slot.iconName)
        assertNull(slot.iconFileId)
        assertTrue(slot.hasIconSource)
    }

    @Test
    fun textIdsDoNotBecomeNumbers() {
        val pet = data().slots.getValue(145)
        assertEquals("summonpet", pet.type)
        assertNull(pet.id)
        assertEquals(132598L, pet.iconFileId)
    }

    @Test
    fun spellListUsesTheIdAsSpellId() {
        val spells = data().spells
        assertEquals(2, spells.size)
        assertEquals(133L, spells[0].spellId)
        assertEquals(135826L, spells[1].iconFileId)
        assertEquals("Flamestrike", spells[1].name)
    }

    @Test
    fun mountsKeepSpellAndFavourite() {
        val mounts = data().mounts
        assertEquals(2, mounts.size)
        assertEquals(458L, mounts[0].spellId)
        assertTrue(mounts[0].favorite)
        assertFalse(mounts[1].favorite)
    }

    @Test
    fun toysAndConsumablesCarryItemIds() {
        val d = data()
        assertEquals(1973L, d.toys.single().itemId)
        assertNull(d.toys.single().spellId)
        assertEquals(118L, d.items.single().itemId)
        assertEquals(5 + 2 + 2 + 1 + 1, d.totalEntries)
    }

    @Test
    fun brokenOrForeignFilesGiveNull() {
        assertNull(BridgeParser.parse(""))
        assertNull(BridgeParser.parse("OtherAddonDB = { a = 1 }"))
        assertNull(BridgeParser.parse("TouchForeverDB = { slots = {"))
        assertNull(BridgeParser.parse("TouchForeverDB = 5"))
    }

    @Test
    fun emptyDatabaseIsValid() {
        val d = BridgeParser.parse("TouchForeverDB = {\n}\n")
        assertNotNull(d)
        assertTrue(d!!.slots.isEmpty())
        assertEquals(0, d.totalEntries)
    }

    @Test
    fun iconNameFromPaths() {
        assertEquals("spell_fire_flamebolt", BridgeParser.iconNameOf("Interface\\Icons\\Spell_Fire_Flamebolt"))
        assertEquals("spell_fire_flamebolt", BridgeParser.iconNameOf("interface/icons/spell_fire_flamebolt.blp"))
        assertNull(BridgeParser.iconNameOf(""))
        assertNull(BridgeParser.iconNameOf(null))
        assertNull(BridgeParser.iconNameOf("Interface\\Icons\\bad name!"))
    }

    @Test
    fun readsTheVariableOfTheOldAddonName() {
        val d = BridgeParser.parse("WowForeverBridgeDB = {\n}\n")
        assertNotNull(d)
    }
}
