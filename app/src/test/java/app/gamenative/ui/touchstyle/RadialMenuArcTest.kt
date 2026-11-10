package app.gamenative.ui.touchstyle

import app.gamenative.wowaction.WowActionCatalog
import com.winlator.inputcontrols.Binding
import com.winlator.inputcontrols.BindingCombo
import com.winlator.inputcontrols.RadialMenu
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The existing radial menus (button menus, "WoW menu") on the one arc of all radial menus: the assignment and the order stay, the fields
 * are spread over the 270 degrees, nothing lies in the free wedge at the bottom, more than eight fields continue on a second ring.
 */
class RadialMenuArcTest {
    private val geo = RadialArc.Geometry.forDensity(1f)
    private val dead = 38f

    private fun shapeFor(menu: RadialMenu) =
        RadialArc.build(menu.slots.size, geo, RadialArc.Order.SEQUENTIAL, RadialArc.WIDE_STEP_DEG)

    private fun wowMenu(commands: List<String>): RadialMenu {
        val menu = RadialMenu()
        for (command in commands.take(RadialMenu.MAX_SLOTS)) {
            menu.addSlot(RadialMenu.Slot("", BindingCombo.of(Binding.KEY_M), command))
        }
        return menu
    }

    private fun menus(): List<Pair<String, RadialMenu>> {
        val list = ArrayList<Pair<String, RadialMenu>>()
        list.add("default" to RadialMenu.createDefault())
        for (n in RadialMenu.MIN_SLOTS..RadialMenu.MAX_SLOTS) list.add("numbers $n" to RadialMenu.createNumberKeys("m$n", "m$n", n))
        list.add("WoW menu" to wowMenu(WowActionCatalog.WOW_MENU_COMMANDS))
        list.add("WoW menu 2" to wowMenu(WowActionCatalog.WOW_MENU_2_COMMANDS))
        return list
    }

    @Test
    fun everyFieldKeepsItsNumberAndNoSegmentLiesInTheBottomWedge() {
        for ((name, menu) in menus()) {
            val s = shapeFor(menu)
            assertEquals(name, menu.slots.size, s.segments.size)
            for ((i, seg) in s.segments.withIndex()) {
                assertEquals("$name field $i keeps its number", i, seg.index)
                // the free wedge is the 90 degrees below the centre: 45..135; every segment lies on the arc from -225 to 45
                assertTrue("$name field $i start ${seg.startDeg}", seg.startDeg >= -225f - 0.01f)
                assertTrue("$name field $i end", seg.startDeg + seg.sweepDeg <= 45f + 0.01f)
            }
            // and a touch straight below the middle (any distance beyond the empty middle) chooses nothing
            for (r in listOf(dead + 2f, 90f, 130f, 170f, 220f)) {
                assertEquals("$name wedge r=$r", -1, RadialArc.indexAt(s, 0f, r, dead))
            }
        }
    }

    @Test
    fun theOrderOfTheFieldsIsKeptClockwiseAcrossTheArc() {
        for ((name, menu) in menus()) {
            val s = shapeFor(menu)
            for (ring in s.rings) {
                val mids = s.segments.subList(ring.first, ring.first + ring.count).map { it.midDeg }
                assertEquals("$name ring order", mids.sorted(), mids)
            }
        }
        // six fields (the standard ring button): 45 degrees each, field 0 at the left end, field 5 at the right end
        val six = shapeFor(RadialMenu.createNumberKeys("m6", "m6", 6))
        assertEquals(1, six.rings.size)
        assertEquals(-202.5f, six.segments[0].midDeg, 0.01f)
        assertEquals(22.5f, six.segments[5].midDeg, 0.01f)
    }

    @Test
    fun everyFieldIsChosenByATouchOnItsSegment() {
        for ((name, menu) in menus()) {
            val s = shapeFor(menu)
            for ((i, seg) in s.segments.withIndex()) {
                assertEquals("$name field $i", i, RadialArc.indexAt(s, seg.midX(), seg.midY(), dead))
            }
        }
    }

    @Test
    fun moreThanEightFieldsContinueOnASecondRing() {
        val twelve = shapeFor(wowMenu(WowActionCatalog.WOW_MENU_COMMANDS))
        if (WowActionCatalog.WOW_MENU_COMMANDS.size > RadialArc.FIRST_RING_CAP) {
            assertEquals(2, twelve.rings.size)
            assertEquals(RadialArc.FIRST_RING_CAP, twelve.rings[0].count)
            assertTrue(twelve.rings[1].inner > twelve.rings[0].outer)
        }
        val eight = shapeFor(RadialMenu.createDefault())
        assertEquals(1, eight.rings.size)
    }

    @Test
    fun theAssignmentOfTheMenusIsNotTouchedByTheLayout() {
        val menu = RadialMenu.createDefault()
        val before = menu.slots.map { it.label to it.binding }
        shapeFor(menu)
        assertEquals(before, menu.slots.map { it.label to it.binding })
        assertEquals(
            listOf("Inventory", "Map", "Journal", "Quick Save", "Quick Load", "Escape", "Tab", "Keyboard"),
            menu.slots.map { it.label },
        )
    }
}
