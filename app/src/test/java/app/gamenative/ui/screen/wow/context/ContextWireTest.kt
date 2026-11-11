package app.gamenative.ui.screen.wow.context

import app.gamenative.cooldown.CooldownStrip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextWireTest {
    @Test
    fun cooldownCodesHaveTheStatedResolution() {
        assertEquals(0, ContextWire.cooldownCode(0.0))
        assertEquals(0, ContextWire.cooldownCode(-3.0))
        assertEquals(1, ContextWire.cooldownCode(0.04))
        assertEquals(3, ContextWire.cooldownCode(0.3))
        assertEquals(100, ContextWire.cooldownCode(10.0))
        assertEquals(115, ContextWire.cooldownCode(25.0))
        assertEquals(255, ContextWire.cooldownCode(10_000.0))
        assertEquals(0f, ContextWire.cooldownFromCode(0), 0f)
        assertEquals(0.3f, ContextWire.cooldownFromCode(3), 0.0001f)
        assertEquals(10f, ContextWire.cooldownFromCode(100), 0.0001f)
        assertEquals(25f, ContextWire.cooldownFromCode(115), 0.0001f)
        assertEquals(165f, ContextWire.cooldownFromCode(255), 0.0001f)
        // every whole second beyond ten and every tenth up to ten survives the round trip
        for (tenths in 1..100) assertEquals(tenths / 10f, ContextWire.cooldownFromCode(ContextWire.cooldownCode(tenths / 10.0)), 0.0001f)
        for (seconds in 11..165) assertEquals(seconds.toFloat(), ContextWire.cooldownFromCode(ContextWire.cooldownCode(seconds.toDouble())), 0.0001f)
    }

    @Test
    fun theLayoutOfThePayload() {
        assertEquals(CooldownStrip.CONTEXT_NIBBLES, ContextWire.IDX_ENTRIES + ContextWire.ENTRY_NIBBLES * CooldownStrip.CONTEXT_ENTRIES)
        assertEquals(ContextWire.IDX_FOCUS + 6, ContextWire.IDX_BAR)
        assertEquals(ContextWire.IDX_BAR + 6, ContextWire.IDX_ENTRIES)
        assertEquals(CooldownStrip.CONTEXT_SPELLS * 2, CooldownStrip.CONTEXT_ENTRIES)
        val n = ContextWire.encode(ContextData())
        assertEquals(CooldownStrip.CONTEXT_NIBBLES, n.size)
        assertEquals(CooldownStrip.CONTEXT_VERSION, n[ContextWire.IDX_VERSION])
        assertTrue(n.all { it in 0..15 })
    }

    @Test
    fun theAttackableFlagSitsInTheSpareBitOfUnitB() {
        val n = ContextWire.encode(ContextData(unit = UnitInfo(exists = true, canAttack = true, hasTarget = true)))
        assertEquals(1 + 8, n[ContextWire.IDX_UNIT_B + 1])
        assertTrue(ContextWire.decode(n)!!.unit.canAttack)
        // without the unit the flag is not read; an older addon never sets it (false) and the reaction decides
        val none = ContextWire.encode(ContextData(unit = UnitInfo(canAttack = true)))
        assertFalse(ContextWire.decode(none)!!.unit.canAttack)
        val old = ContextWire.encode(ContextData(unit = UnitInfo(exists = true, relation = UnitRelation.HOSTILE)))
        val data = ContextWire.decode(old)!!.unit
        assertFalse(data.canAttack)
        assertTrue(data.hostile)
        // attackable but yellow (neutral by reaction): hostile for the menu
        assertTrue(UnitInfo(exists = true, relation = UnitRelation.NEUTRAL, canAttack = true).hostile)
        assertFalse(UnitInfo(exists = true, relation = UnitRelation.NEUTRAL).hostile)
    }

    @Test
    fun anEmptyContextRoundTrips() {
        val data = ContextData()
        assertEquals(data, ContextWire.decode(ContextWire.encode(data)))
    }

    @Test
    fun aFullContextRoundTrips() {
        val data = ContextData(
            ctxFlags = ContextData.KEYS or ContextData.SPELLS or ContextData.TRUNCATED,
            unit = UnitInfo(
                exists = true, isPlayer = true, dead = true, skinnable = true, relation = UnitRelation.NEUTRAL,
                source = UnitSource.TARGET, reach = Reach.CLOSE, inGroup = true, reaction = 8, hasTarget = true, isTarget = true, isSelf = true,
                canAttack = true,
            ),
            focus = FocusInfo(
                kind = FocusKind.EQUIPMENT, filled = true, equippable = true, usable = true, stacked = true, merchantOpen = true,
                cursorHasItem = true, count = 15,
            ),
            barClass = List(12) { (it + 1) % 4 },
            entries = List(16) { i ->
                if (i == 5) null else SpellEntry(0xFFFFFF - i, SpellEntry.FILLED or (i % 8) * 2, (i * 11).toFloat())
            },
        )
        val decoded = ContextWire.decode(ContextWire.encode(data))
        assertEquals(data, decoded)
    }

    @Test
    fun theFieldsLandOnTheNibblesTheLuaFileWrites() {
        // The same payload as the Lua check (tools/cooldown_strip_check.py, "pure coding"), 0-based here.
        val data = ContextData(
            ctxFlags = 3,
            unit = UnitInfo(
                exists = true, isPlayer = true, skinnable = true, relation = UnitRelation.HOSTILE, source = UnitSource.MOUSEOVER,
                reach = Reach.NEAR, inGroup = true, reaction = 2, hasTarget = true, isTarget = true,
            ),
            focus = FocusInfo(kind = FocusKind.BANK, filled = true, equippable = true, stacked = true, merchantOpen = true, cursorHasItem = true, count = 7),
            barClass = listOf(1, 2, 3, 0, 0, 0, 0, 0, 0, 0, 0, 3),
            entries = List(16) { i ->
                when (i) {
                    0 -> SpellEntry(1_048_577, 1 or 4, 25f)
                    15 -> SpellEntry(77, 1, 0.3f)
                    else -> null
                }
            },
        )
        val n = ContextWire.encode(data)
        assertEquals(1, n[0])
        assertEquals(3, n[2])
        assertEquals(1 + 2 + 8, n[3])
        assertEquals(2 + 4 * 1, n[4])
        assertEquals(3 + 8, n[5])
        assertEquals(2, n[6])
        assertEquals(3, n[7])
        assertEquals(2, n[9])
        assertEquals(1 + 2 + 8, n[10])
        assertEquals(1 + 2, n[11])
        assertEquals(7, n[12])
        assertEquals(1 + 4 * 2, n[15])
        assertEquals(3, n[16])
        assertEquals(0, n[17])
        assertEquals(4 * 3, n[20])
        assertEquals(listOf(1, 0, 0, 0, 0, 1, 5, 7, 3), (21 until 30).map { n[it] })
        assertEquals(listOf(0, 0, 0, 0, 4, 13, 1, 0, 3), (21 + 9 * 15 until 21 + 9 * 16).map { n[it] })
    }

    @Test
    fun otherVersionsAndShortListsAreRejected() {
        val n = ContextWire.encode(ContextData())
        val other = n.copyOf().also { it[ContextWire.IDX_VERSION] = 2 }
        assertNull(ContextWire.decode(other))
        assertNull(ContextWire.decode(IntArray(10)))
        assertNotNull(ContextWire.decode(n))
    }

    @Test
    fun aMissingUnitCannotCarryFlags() {
        // exists = 0 but the other bits set (garbage): they are not believed
        val n = ContextWire.encode(ContextData())
        n[ContextWire.IDX_UNIT_A] = 2 + 4 + 8
        val decoded = ContextWire.decode(n)!!
        assertFalse(decoded.unit.exists || decoded.unit.isPlayer || decoded.unit.dead || decoded.unit.skinnable)
        // an empty slot is not an item
        val focus = ContextWire.encode(ContextData())
        focus[ContextWire.IDX_FOCUS] = FocusKind.BAG.code
        assertFalse(ContextWire.decode(focus)!!.focus.isItem)
        focus[ContextWire.IDX_FOCUS + 1] = FocusInfo.F_FILLED
        assertTrue(ContextWire.decode(focus)!!.focus.isItem)
        // a filled flag without a slot kind is no item either
        focus[ContextWire.IDX_FOCUS] = 0
        assertFalse(ContextWire.decode(focus)!!.focus.isItem)
    }

    @Test
    fun emptyEntriesStayEmptyEvenWithGarbageInTheirNibbles() {
        val n = ContextWire.encode(ContextData())
        for (i in ContextWire.IDX_ENTRIES until ContextWire.IDX_ENTRIES + 6) n[i] = 9
        val decoded = ContextWire.decode(n)!!
        assertTrue(decoded.entries.all { it == null })
    }

    // ---- quest under the pointer (addon build 11) ----------------------------------------------------------

    @Test
    fun aQuestRoundTripsWithItsFlags() {
        val focus = FocusInfo(kind = FocusKind.QUEST, filled = true, shareable = true, tracked = true)
        val data = ContextData(ContextData.KEYS, focus = focus)
        val n = ContextWire.encode(data)
        assertEquals(4, n[ContextWire.IDX_FOCUS])
        assertEquals(1 + 2 + 4, n[ContextWire.IDX_FOCUS + 1])
        val decoded = ContextWire.decode(n)!!
        assertEquals(data, decoded)
        assertTrue(decoded.focus.isQuest)
        assertFalse("a quest is no item", decoded.focus.isItem)
        assertTrue(decoded.focus.hasMenu)
        val plain = ContextWire.decode(ContextWire.encode(ContextData(focus = FocusInfo(kind = FocusKind.QUEST, filled = true))))!!.focus
        assertTrue(plain.isQuest && !plain.shareable && !plain.tracked)
    }

    @Test
    fun theQuestFlagBitsAreNotTakenForItemFlags() {
        // the Lua side writes shareable / tracked on the bits 2 and 4 that mean equippable / usable for items
        val n = ContextWire.encode(ContextData())
        n[ContextWire.IDX_FOCUS] = FocusKind.QUEST.code
        n[ContextWire.IDX_FOCUS + 1] = FocusInfo.F_FILLED or FocusInfo.F_SHAREABLE or FocusInfo.F_TRACKED
        val quest = ContextWire.decode(n)!!.focus
        assertTrue(quest.shareable && quest.tracked && quest.isQuest)
        assertFalse(quest.equippable || quest.usable || quest.stacked)
        // and the other way round: an item slot never reads them as quest flags
        n[ContextWire.IDX_FOCUS] = FocusKind.BAG.code
        val item = ContextWire.decode(n)!!.focus
        assertTrue(item.isItem && item.equippable && item.usable)
        assertFalse(item.shareable || item.tracked || item.isQuest)
    }

    @Test
    fun aQuestNeedsItsPresentFlag() {
        val n = ContextWire.encode(ContextData())
        n[ContextWire.IDX_FOCUS] = FocusKind.QUEST.code
        assertFalse(ContextWire.decode(n)!!.focus.isQuest)
        n[ContextWire.IDX_FOCUS + 1] = FocusInfo.F_FILLED
        assertTrue(ContextWire.decode(n)!!.focus.isQuest)
    }

    @Test
    fun theOldFocusKindsAreUnchanged() {
        assertEquals(listOf(0, 1, 2, 3, 4), FocusKind.entries.map { it.code })
        assertEquals(listOf(false, true, true, true, false), FocusKind.entries.map { it.isItemSlot })
        for (kind in listOf(FocusKind.BAG, FocusKind.BANK, FocusKind.EQUIPMENT)) {
            val data = ContextData(focus = FocusInfo(kind = kind, filled = true, equippable = true, usable = true, stacked = true, count = 9))
            assertEquals(data, ContextWire.decode(ContextWire.encode(data)))
        }
        assertEquals(FocusKind.NONE, FocusKind.of(7))
    }
}
