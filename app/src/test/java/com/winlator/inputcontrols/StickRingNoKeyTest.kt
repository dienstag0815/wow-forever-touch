package com.winlator.inputcontrols

import app.gamenative.ui.touchstyle.RadialSlotVisibility
import com.winlator.widget.InputControlsView
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Left stick with a key ring from an action bar without any key (bars 6 to 8: bars 2 to 5 have replacement keys, see ReplacementKeys): the editor stores the
 * fields as WoW actions with no key. They must survive save and load and stay in the game's drawing list (dimmed), only really empty
 * fields disappear. Before: every field without a key counted as empty and the whole ring was missing in the game.
 */
@RunWith(RobolectricTestRunner::class)
class StickRingNoKeyTest {
    private fun mockView(): InputControlsView {
        val view = mockk<InputControlsView>(relaxed = true)
        every { view.snappingSize } returns 10
        every { view.maxWidth } returns 1000
        every { view.maxHeight } returns 1000
        return view
    }

    private fun unkeyedField(place: Int) =
        RadialMenu.Slot("", BindingCombo.none(), "MULTIACTIONBAR5BUTTON$place")

    /** The stick as the editor saves it: segments 0 to 2 are bar places without a key, the others stay empty. */
    private fun editorStick(view: InputControlsView) = ControlElement(view).apply {
        setType(ControlElement.Type.STICK)
        setX(200)
        setY(200)
        setRingSegments(8)
        for (i in 0 until 8) setRingItem(i, if (i < 3) unkeyedField(i + 1) else RadialMenu.Slot())
    }

    private fun loaded(view: InputControlsView, saved: ControlElement): ControlElement {
        val json = JSONObject()
        saved.writeExtrasToJson(json)
        return ControlElement(view).apply {
            setType(ControlElement.Type.STICK)
            setX(200)
            setY(200)
            readExtrasFromJson(json)
        }
    }

    @Test
    fun `fields with a WoW action but no key survive the round trip`() {
        val element = loaded(mockView(), editorStick(mockView()))
        assertEquals(8, element.ringSegments)
        for (i in 0 until 3) {
            val item = element.getRingItem(i)
            assertEquals("MULTIACTIONBAR5BUTTON${i + 1}", item.wowAction)
            assertTrue(item.bindingCombo.isEmpty)
            assertFalse("field $i stays in the game", RadialSlotVisibility.isEmptyInRing(item))
            assertTrue(RadialSlotVisibility.isInactive(item))
        }
        for (i in 3 until 8) assertTrue("field $i is really empty", RadialSlotVisibility.isEmptyInRing(element.getRingItem(i)))
    }

    @Test
    fun `a field with a key is not dimmed and a field without anything is empty`() {
        val keyed = RadialMenu.Slot("", BindingCombo.of(Binding.KEY_1), "ACTIONBUTTON1")
        assertFalse(RadialSlotVisibility.isEmptyInRing(keyed))
        assertFalse(RadialSlotVisibility.isInactive(keyed))
        assertTrue(RadialSlotVisibility.isEmptyInRing(RadialMenu.Slot()))
        assertTrue(RadialSlotVisibility.isEmptyInRing(null))
        assertFalse(RadialSlotVisibility.isInactive(RadialMenu.Slot().apply { setChatCommand("/dance") }))
    }

    @Test
    fun `in the game the unkeyed field takes the touch but presses nothing, the empty field passes it on`() {
        val view = mockView()
        val element = loaded(view, editorStick(view))

        // second finger on the top field (segment 0): hit, no key is sent
        assertTrue(element.handleTouchDown(2, 200f, 120f))
        verify(exactly = 0) { view.handleInputEvent(any<BindingCombo>(), any()) }
        assertTrue(element.handleTouchUp(2))

        // bottom field (segment 4) is empty: not tappable, the touch passes on
        assertFalse(element.handleTouchDown(3, 200f, 280f))
    }
}
