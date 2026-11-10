package app.gamenative.ui.screen.xserver.editor.sheet

import com.winlator.inputcontrols.ControlElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import app.gamenative.R
import app.gamenative.ui.screen.xserver.editor.EditorText

class SheetNavTest {
    @Test
    fun `starts on the overview without a back step`() {
        val nav = SheetNav()
        assertEquals(SheetRoute.OVERVIEW, nav.current)
        assertFalse(nav.canGoBack)
        assertSame(nav, nav.back())
    }

    @Test
    fun `sub pages stack and go back one by one`() {
        val nav = SheetNav().open(SheetRoute.BINDING).open(SheetRoute.picker(PickTarget.Slot(0)))
        assertEquals(SheetPage.KEY_PICKER, nav.current.page)
        assertEquals(SheetRoute.BINDING, nav.parent)
        assertTrue(nav.canGoBack)

        val back = nav.back()
        assertEquals(SheetRoute.BINDING, back.current)
        assertEquals(SheetRoute.OVERVIEW, back.back().current)
    }

    @Test
    fun `opening the current page again changes nothing`() {
        val nav = SheetNav().open(SheetRoute.APPEARANCE)
        assertSame(nav, nav.open(SheetRoute.APPEARANCE))
    }

    @Test
    fun `opening a page already on the stack goes back to it`() {
        val nav = SheetNav().open(SheetRoute.BINDING).open(SheetRoute.picker(PickTarget.Slot(1))).open(SheetRoute.BINDING)
        assertEquals(listOf(SheetRoute.OVERVIEW, SheetRoute.BINDING), nav.stack)
    }

    @Test
    fun `different pickers are different routes`() {
        val nav = SheetNav().open(SheetRoute.RING).open(SheetRoute.picker(PickTarget.RingSegment(0)))
        assertEquals(PickTarget.RingSegment(0), nav.current.target)
        assertEquals(3, nav.stack.size)
    }

    @Test
    fun `the outer ring field has its own picker route below the ring page`() {
        val inner = SheetNav().open(SheetRoute.RING).open(SheetRoute.picker(PickTarget.RingSegment(1)))
        val outer = SheetNav().open(SheetRoute.RING).open(SheetRoute.picker(PickTarget.RingOuter(1)))
        assertEquals(PickTarget.RingOuter(1), outer.current.target)
        assertEquals(3, outer.stack.size)
        assertEquals(false, inner.current == outer.current)
        // stays reachable for a stick, goes away with the ring page for other elements
        assertSame(outer, outer.sanitize(SheetPages.available(ControlElement.Type.STICK, false)))
        assertEquals(listOf(SheetRoute.OVERVIEW), outer.sanitize(SheetPages.available(ControlElement.Type.BUTTON, false)).stack)
    }

    @Test
    fun `toOverview drops the whole stack`() {
        val nav = SheetNav().open(SheetRoute.BINDING).open(SheetRoute.picker(PickTarget.Slot(0))).toOverview()
        assertEquals(listOf(SheetRoute.OVERVIEW), nav.stack)
    }

    @Test
    fun `sanitize drops pages the element does not offer any more`() {
        val nav = SheetNav().open(SheetRoute.RING).open(SheetRoute.picker(PickTarget.RingSegment(2)))
        val button = SheetPages.available(ControlElement.Type.BUTTON, false)
        assertEquals(listOf(SheetRoute.OVERVIEW), nav.sanitize(button).stack)

        val stick = SheetPages.available(ControlElement.Type.STICK, false)
        assertSame(nav, nav.sanitize(stick))
    }

    @Test
    fun `sanitize keeps a picker below the binding page`() {
        val nav = SheetNav().open(SheetRoute.BINDING).open(SheetRoute.picker(PickTarget.Slot(0)))
        assertSame(nav, nav.sanitize(SheetPages.available(ControlElement.Type.BUTTON, false)))
        // A range button has no binding page: the picker goes with it.
        assertEquals(1, nav.sanitize(SheetPages.available(ControlElement.Type.RANGE_BUTTON, false)).stack.size)
    }

    @Test
    fun `pages follow the element type`() {
        assertEquals(
            listOf(SheetPage.BINDING, SheetPage.APPEARANCE, SheetPage.ADVANCED),
            SheetPages.available(ControlElement.Type.BUTTON, false),
        )
        assertEquals(
            listOf(SheetPage.BINDING, SheetPage.RADIAL_MENU, SheetPage.APPEARANCE, SheetPage.ADVANCED),
            SheetPages.available(ControlElement.Type.BUTTON, true),
        )
        assertEquals(
            listOf(SheetPage.BINDING, SheetPage.RING, SheetPage.APPEARANCE, SheetPage.ADVANCED),
            SheetPages.available(ControlElement.Type.STICK, false),
        )
        assertEquals(
            listOf(SheetPage.APPEARANCE, SheetPage.ADVANCED),
            SheetPages.available(ControlElement.Type.SHOOTER_MODE, false),
        )
        assertEquals(
            listOf(SheetPage.APPEARANCE, SheetPage.ADVANCED),
            SheetPages.available(ControlElement.Type.RANGE_BUTTON, false),
        )
    }

    @Test
    fun `entries page only for a key strip with the free list`() {
        assertEquals(
            listOf(SheetPage.RANGE_ITEMS, SheetPage.APPEARANCE, SheetPage.ADVANCED),
            SheetPages.available(ControlElement.Type.RANGE_BUTTON, false, customRange = true),
        )
        assertFalse(SheetPage.RANGE_ITEMS in SheetPages.available(ControlElement.Type.RANGE_BUTTON, false))
        assertFalse(SheetPage.RANGE_ITEMS in SheetPages.available(ControlElement.Type.BUTTON, false, customRange = true))
    }

    @Test
    fun `picker of an entry stays below the entries page and goes with it`() {
        val nav = SheetNav().open(SheetRoute.RANGE_ITEMS).open(SheetRoute.picker(PickTarget.RangeItem(3)))
        assertSame(nav, nav.sanitize(SheetPages.available(ControlElement.Type.RANGE_BUTTON, false, customRange = true)))
        // Back to a template strip: the entries page and its picker are gone.
        assertEquals(1, nav.sanitize(SheetPages.available(ControlElement.Type.RANGE_BUTTON, false)).stack.size)
        assertEquals(EditorText(R.string.editor_entry_n, listOf(4)), SheetPages.title(nav.current, ControlElement.Type.RANGE_BUTTON))
        assertEquals(EditorText(R.string.editor_page_range_items), SheetPages.title(SheetRoute.RANGE_ITEMS, ControlElement.Type.RANGE_BUTTON))
    }

    @Test
    fun `radial page only for buttons`() {
        assertFalse(SheetPage.RADIAL_MENU in SheetPages.available(ControlElement.Type.D_PAD, true))
    }

    @Test
    fun `slot counts and names`() {
        assertEquals(2, SheetPages.slotCount(ControlElement.Type.BUTTON))
        assertEquals(2, SheetPages.slotCount(ControlElement.Type.SCROLL_WHEEL))
        assertEquals(4, SheetPages.slotCount(ControlElement.Type.D_PAD))
        assertEquals(0, SheetPages.slotCount(ControlElement.Type.RANGE_BUTTON))
        assertEquals(EditorText(R.string.editor_slot_primary), SheetPages.slotLabel(ControlElement.Type.BUTTON, 0))
        assertEquals(EditorText(R.string.editor_slot_secondary), SheetPages.slotLabel(ControlElement.Type.BUTTON, 1))
        assertEquals(EditorText(R.string.editor_dir_left), SheetPages.slotLabel(ControlElement.Type.STICK, 3))
        assertEquals(EditorText(R.string.editor_dir_up_mouse), SheetPages.slotLabel(ControlElement.Type.TRACKPAD, 0))
    }

    @Test
    fun `titles name what the picker assigns to`() {
        val type = ControlElement.Type.STICK
        assertEquals(EditorText(R.string.editor_page_binding), SheetPages.title(SheetRoute.BINDING, type))
        assertEquals(EditorText(R.string.editor_dir_right), SheetPages.title(SheetRoute.picker(PickTarget.Slot(1)), type))
        assertEquals(EditorText(R.string.editor_ring_key_n, listOf(3)), SheetPages.title(SheetRoute.picker(PickTarget.RingSegment(2)), type))
        assertEquals(EditorText(R.string.editor_ring_outer_key_n, listOf(3)), SheetPages.title(SheetRoute.picker(PickTarget.RingOuter(2)), type))
        assertEquals(EditorText(R.string.editor_field_n, listOf(1)), SheetPages.title(SheetRoute.picker(PickTarget.RadialSlot(0)), type))
        assertEquals(EditorText(R.string.editor_press_center), SheetPages.title(SheetRoute.picker(PickTarget.Center), type))
    }
}
