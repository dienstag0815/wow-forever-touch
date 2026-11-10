package app.gamenative.ui.screen.xserver.editor.sheet

import com.winlator.inputcontrols.Binding
import com.winlator.inputcontrols.BindingCombo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangeTrackerTest {
    private var applied = 0
    private var refreshes = 0
    private var commits = 0
    private val tracker = ChangeTracker(refresh = { refreshes++ }, commit = { commits++ })

    @Test
    fun `a discrete change is applied live and is one undo step`() {
        tracker.discrete { applied++ }
        assertEquals(1, applied)
        assertEquals(1, refreshes)
        assertEquals(1, commits)
        assertFalse(tracker.pending)
    }

    @Test
    fun `every discrete change is its own step`() {
        repeat(3) { tracker.discrete { applied++ } }
        assertEquals(3, commits)
    }

    @Test
    fun `a slider drag is live at every value but one step in the end`() {
        repeat(25) { tracker.continuous { applied++ } }
        assertEquals(25, applied)
        assertEquals(25, refreshes)
        assertEquals(0, commits)
        assertTrue(tracker.pending)

        tracker.flush()
        assertEquals(1, commits)
        assertFalse(tracker.pending)
    }

    @Test
    fun `flush without a pending change commits nothing`() {
        tracker.flush()
        tracker.discrete { applied++ }
        tracker.flush()
        assertEquals(1, commits)
    }

    @Test
    fun `a discrete change after a drag does not commit twice`() {
        tracker.continuous { applied++ }
        tracker.discrete { applied++ }
        tracker.flush()
        assertEquals(1, commits)
    }

    @Test
    fun `leaving the page flushes a pending typing change once`() {
        "Hallo".forEach { _ -> tracker.continuous { applied++ } }
        tracker.flush()
        tracker.flush()
        assertEquals(1, commits)
    }
}

class RecentListTest {
    @Test
    fun `newest first without duplicates`() {
        val list = RecentList(limit = 3)
        list.use("A")
        list.use("B")
        list.use("A")
        assertEquals(listOf("A", "B"), list.values)
    }

    @Test
    fun `keeps only the limit`() {
        val list = RecentList(limit = 3)
        listOf("A", "B", "C", "D").forEach { list.use(it) }
        assertEquals(listOf("D", "C", "B"), list.values)
    }

    @Test
    fun `blank entries are ignored`() {
        val list = RecentList()
        list.use("")
        list.use("  ")
        assertTrue(list.values.isEmpty())
    }

    @Test
    fun `recent items are lifted out of the list in recent order`() {
        val (recent, rest) = withRecentsFirst(listOf("MAP", "BAGS", "TALENTS", "QUESTS"), listOf("TALENTS", "GONE", "MAP")) { it }
        assertEquals(listOf("TALENTS", "MAP"), recent)
        assertEquals(listOf("BAGS", "QUESTS"), rest)
        val (none, all) = withRecentsFirst(listOf("A", "B"), emptyList()) { it }
        assertTrue(none.isEmpty())
        assertEquals(listOf("A", "B"), all)
    }

    @Test
    fun `round trip through the preferences text`() {
        val list = RecentList(limit = 4)
        listOf("TOGGLEWORLDMAP", "TOGGLETALENTS", "ACTIONBUTTON1").forEach { list.use(it) }
        val loaded = RecentList.parse(list.serialize(), limit = 4)
        assertEquals(list.values, loaded.values)
        assertTrue(RecentList.parse(null).values.isEmpty())
        assertTrue(RecentList.parse("").values.isEmpty())
    }
}

class SheetPlacementTest {
    @Test
    fun `inner display puts the panel opposite the element`() {
        assertEquals(SheetSide.LEFT, SheetPlacement.choose(900, 700f, 100f, 1000f, 800f))
        assertEquals(SheetSide.RIGHT, SheetPlacement.choose(900, 200f, 700f, 1000f, 800f))
    }

    @Test
    fun `outer display uses a sheet above or below`() {
        assertEquals(SheetSide.BOTTOM, SheetPlacement.choose(400, 100f, 200f, 1000f, 2000f))
        assertEquals(SheetSide.TOP, SheetPlacement.choose(400, 100f, 1500f, 1000f, 2000f))
    }

    @Test
    fun `panel is about forty percent but limited`() {
        assertEquals(360, SheetPlacement.panelWidthDp(900))
        assertEquals(320, SheetPlacement.panelWidthDp(600))
        assertEquals(460, SheetPlacement.panelWidthDp(1400))
    }
}

class BindingSelectionTest {
    @Test
    fun `a new key replaces the chosen one`() {
        val selection = BindingSelection(BindingCombo.of(Binding.KEY_A))
        assertTrue(selection.tap(Binding.KEY_B))
        assertEquals(listOf(Binding.KEY_B), selection.keys)
    }

    @Test
    fun `tapping the chosen key takes it away`() {
        val selection = BindingSelection(BindingCombo.of(Binding.KEY_A))
        selection.tap(Binding.KEY_A)
        assertTrue(selection.isEmpty)
        assertNull(selection.toCombo())
    }

    @Test
    fun `combination mode adds keys up to the limit`() {
        val selection = BindingSelection(null)
        selection.setComboMode(true)
        selection.tap(Binding.KEY_SHIFT_L)
        selection.tap(Binding.KEY_1)
        selection.tap(Binding.KEY_2)
        assertEquals(3, selection.keys.size)
        assertFalse(selection.canTap(Binding.KEY_3))
        assertFalse(selection.tap(Binding.KEY_3))
        assertTrue(selection.canTap(Binding.KEY_1))
        assertEquals(3, selection.toCombo()!!.size())
    }

    @Test
    fun `an existing combination starts in combination mode`() {
        val combo = BindingCombo.fromBindings(listOf(Binding.KEY_SHIFT_L, Binding.KEY_1))
        val selection = BindingSelection(combo)
        assertTrue(selection.comboMode)
        assertEquals(combo, selection.toCombo())
    }

    @Test
    fun `clear empties the choice and the picker keeps working for a new key`() {
        val selection = BindingSelection(BindingCombo.fromBindings(listOf(Binding.KEY_SHIFT_L, Binding.KEY_1)))
        assertTrue(selection.clear())
        assertTrue(selection.isEmpty)
        assertFalse(selection.comboMode)
        assertNull(selection.toCombo())
        assertFalse(selection.clear())

        assertTrue(selection.tap(Binding.KEY_Q))
        assertEquals(BindingCombo.of(Binding.KEY_Q), selection.toCombo())
    }

    @Test
    fun `sequence mode and delay are kept and limited`() {
        val selection = BindingSelection(null)
        selection.setComboMode(true)
        selection.tap(Binding.KEY_1)
        selection.tap(Binding.KEY_2)
        selection.setMode(BindingCombo.Mode.SEQUENCE)
        selection.setDelay(5)
        assertEquals(BindingCombo.MIN_SEQUENCE_DELAY_MS, selection.sequenceDelayMs)
        selection.setDelay(300)
        val combo = selection.toCombo()!!
        assertTrue(combo.isSequence)
        assertEquals(300, combo.sequenceDelayMs)
    }

    @Test
    fun `the empty binding clears`() {
        val selection = BindingSelection(BindingCombo.of(Binding.KEY_A))
        assertTrue(selection.tap(Binding.NONE))
        assertTrue(selection.isEmpty)
    }
}
