package app.gamenative.ui.screen.xserver.editor.sheet

import app.gamenative.wowaction.BindingCaption
import com.winlator.inputcontrols.Binding
import com.winlator.inputcontrols.BindingCombo
import com.winlator.inputcontrols.ControlElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import app.gamenative.R

class IgnoredLabelHintTest {
    @Test
    fun `a blank text or an own label needs no note`() {
        assertNull(ignoredLabelHint("", own = false))
        assertNull(ignoredLabelHint("   ", own = false))
        assertNull(ignoredLabelHint("Heilen", own = true))
    }

    @Test
    fun `a bare field number is not shown and says so`() {
        // The outer field 2 of a ring: the label "2" only repeats its number, so the ring names the field itself.
        val own = BindingCaption.isOwnLabel("2", null, null, BindingCombo.of(Binding.MOUSE_RIGHT_BUTTON), 2)
        assertFalse(own)
        assertNotNull(ignoredLabelHint("2", own))
    }

    @Test
    fun `a name with the number in it is an own label`() {
        val own = BindingCaption.isOwnLabel("Nr. 2", null, null, BindingCombo.of(Binding.MOUSE_RIGHT_BUTTON), 2)
        assertTrue(own)
        assertNull(ignoredLabelHint("Nr. 2", own))
    }

    @Test
    fun `typing a name over an outer field keeps the name`() {
        // What the label field writes into the field is exactly what the ring then shows (no number, no automatic text).
        val caption = BindingCaption.forSlot("Tab", null, null, BindingCombo.of(Binding.MOUSE_RIGHT_BUTTON), 2)
        assertEquals("Tab", caption.text)
        assertFalse(caption.automatic)
    }

    @Test
    fun `an empty label is automatic again`() {
        val caption = BindingCaption.forSlot("", null, null, BindingCombo.of(Binding.MOUSE_RIGHT_BUTTON), 2)
        assertTrue(caption.automatic)
    }
}

class ElementPositionTest {
    @Test
    fun `values stay inside the area`() {
        assertEquals(0, ElementPosition.clamp(-5, 1000))
        assertEquals(1000, ElementPosition.clamp(2000, 1000))
        assertEquals(420, ElementPosition.clamp(420, 1000))
        assertEquals(0, ElementPosition.clamp(10, -3))
    }

    @Test
    fun `an arrow step moves by one grid step and stops at the border`() {
        assertEquals(120, ElementPosition.nudge(100, 1, 20, 1000))
        assertEquals(80, ElementPosition.nudge(100, -1, 20, 1000))
        assertEquals(0, ElementPosition.nudge(10, -1, 20, 1000))
        assertEquals(1000, ElementPosition.nudge(990, 1, 20, 1000))
        assertEquals(101, ElementPosition.nudge(100, 1, 0, 1000))
    }

    @Test
    fun `only whole non-negative numbers are taken from the field`() {
        assertEquals(240, ElementPosition.parse(" 240 "))
        assertEquals(0, ElementPosition.parse("0"))
        assertNull(ElementPosition.parse(""))
        assertNull(ElementPosition.parse("-3"))
        assertNull(ElementPosition.parse("12.5"))
        assertNull(ElementPosition.parse("abc"))
    }
}

class SheetStructureTest {
    @Test
    fun `the overview lists binding before look before behavior`() {
        val pages = SheetPages.available(ControlElement.Type.STICK, false)
        assertEquals(listOf(SheetPage.BINDING, SheetPage.RING, SheetPage.APPEARANCE, SheetPage.ADVANCED), pages)
        assertEquals(R.string.editor_page_advanced, SheetPage.ADVANCED.title)
    }

    @Test
    fun `every page the overview offers is one level below it`() {
        for (type in ControlElement.Type.values()) {
            for (page in SheetPages.available(type, isRadialMenuButton = type == ControlElement.Type.BUTTON, customRange = true)) {
                val nav = SheetNav().open(SheetRoute(page))
                assertEquals(2, nav.stack.size)
                assertEquals(SheetRoute.OVERVIEW, nav.parent)
            }
        }
    }
}
