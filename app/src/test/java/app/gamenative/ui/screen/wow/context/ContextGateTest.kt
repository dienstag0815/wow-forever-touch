package app.gamenative.ui.screen.wow.context

import org.junit.Rule
import app.gamenative.TestTexts
import app.gamenative.cooldown.CooldownStrip
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ContextGateTest {
    @get:Rule
    val texts = TestTexts.rule("de")

    @Before
    fun reset() {
        ContextGate.setMode(ContextMode.AUTO)
        ContextGate.setTapViewOnly(true)
        ContextGate.clear()
    }

    @After
    fun cleanUp() {
        ContextGate.setMode(ContextMode.AUTO)
        ContextGate.setTapViewOnly(true)
        ContextGate.clear()
    }

    private fun decoded(data: ContextData?): CooldownStrip.Decoded {
        val d = CooldownStrip.Decoded()
        if (data != null) {
            ContextWire.encode(data).copyInto(d.contextNibbles)
            d.contextKnown = true
        }
        return d
    }

    private val mob = ContextData(ContextData.KEYS, UnitInfo(exists = true, relation = UnitRelation.HOSTILE, source = UnitSource.MOUSEOVER))

    @Test
    fun theModesCycleAndKnowTheirIds() {
        assertEquals(listOf(ContextMode.OFF, ContextMode.AUTO, ContextMode.BAR), ContextMode.entries)
        assertEquals(ContextMode.AUTO, ContextMode.OFF.next())
        assertEquals(ContextMode.BAR, ContextMode.AUTO.next())
        assertEquals(ContextMode.OFF, ContextMode.BAR.next())
        for (mode in ContextMode.entries) assertEquals(mode, ContextMode.fromId(mode.id))
        // default: automatic
        assertEquals(ContextMode.AUTO, ContextMode.fromId(null))
        assertEquals(ContextMode.AUTO, ContextMode.fromId("unsinn"))
        assertEquals("Kontextmenü: Aktionsleiste 1", ContextSetting.label(ContextMode.BAR))
        assertEquals("Tippen in Taschen: nur ansehen", ContextSetting.tapViewLabel(true))
        assertEquals("Tippen in Taschen: nimmt auf", ContextSetting.tapViewLabel(false))
    }

    @Test
    fun theStripValueBecomesTheSnapshot() {
        assertNull(ContextGate.snapshot())
        ContextGate.onStrip(decoded(mob), 1_000L)
        val snap = ContextGate.snapshot()
        assertNotNull(snap)
        assertEquals(mob, snap!!.data)
        assertEquals(1_000L, snap.receivedMs)
    }

    @Test
    fun anUnchangedValueOnlyRefreshesTheTime() {
        ContextGate.onStrip(decoded(mob), 1_000L)
        val first = ContextGate.snapshot()
        ContextGate.onStrip(decoded(mob), 1_033L)
        assertSame(first, ContextGate.snapshot())
        assertEquals(1_033L, ContextGate.snapshot()!!.receivedMs)
        val other = mob.copy(unit = mob.unit.copy(dead = true))
        ContextGate.onStrip(decoded(other), 1_066L)
        assertEquals(other, ContextGate.snapshot()!!.data)
        assertEquals(1_066L, ContextGate.snapshot()!!.receivedMs)
    }

    @Test
    fun aStripWithoutContextKeepsTheLastValueForAWhileThenForgets() {
        ContextGate.onStrip(decoded(mob), 1_000L)
        ContextGate.onStrip(decoded(null), 1_100L)
        assertNotNull(ContextGate.snapshot())
        ContextGate.onStrip(decoded(null), 1_900L)
        assertNotNull(ContextGate.snapshot())
        ContextGate.onStrip(decoded(null), 2_200L)
        assertNull(ContextGate.snapshot())
    }

    @Test
    fun aKnownValueInBetweenRestartsTheForgetting() {
        ContextGate.onStrip(decoded(mob), 1_000L)
        ContextGate.onStrip(decoded(null), 1_100L)
        ContextGate.onStrip(decoded(mob), 1_900L)
        ContextGate.onStrip(decoded(null), 2_000L)
        ContextGate.onStrip(decoded(null), 2_500L)
        assertNotNull(ContextGate.snapshot())
    }

    @Test
    fun switchedOffNothingIsReadOrKept() {
        ContextGate.onStrip(decoded(mob), 1_000L)
        assertTrue(ContextGate.needsSignal())
        ContextGate.setMode(ContextMode.OFF)
        assertFalse(ContextGate.needsSignal())
        assertNull(ContextGate.snapshot())
        ContextGate.onStrip(decoded(mob), 2_000L)
        assertNull(ContextGate.snapshot())
        ContextGate.setMode(ContextMode.BAR)
        assertTrue(ContextGate.needsSignal())
        assertEquals(ContextMode.BAR, ContextGate.mode())
    }

    @Test
    fun tapViewOnlyNeedsTheSwitchAndTheMenu() {
        assertTrue(ContextGate.tapViewOnly())
        ContextGate.setTapViewOnly(false)
        assertFalse(ContextGate.tapViewOnly())
        ContextGate.setTapViewOnly(true)
        ContextGate.setMode(ContextMode.OFF)
        assertFalse(ContextGate.tapViewOnly())
    }

    @Test
    fun clearForgetsEverything() {
        ContextGate.onStrip(decoded(mob), 1_000L)
        ContextGate.clear()
        assertNull(ContextGate.snapshot())
        // after clearing the same value is a new one
        ContextGate.onStrip(decoded(mob), 1_100L)
        assertEquals(1_100L, ContextGate.snapshot()!!.receivedMs)
    }
}
