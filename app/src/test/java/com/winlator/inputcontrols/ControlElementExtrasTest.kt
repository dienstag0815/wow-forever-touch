package com.winlator.inputcontrols

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.winlator.widget.InputControlsView
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Pill/free size buttons, ARC buttons, scroll wheel and the key ring around a stick (touch, geometry, JSON). */
@RunWith(RobolectricTestRunner::class)
class ControlElementExtrasTest {
    private val newKeys = listOf(
        "widthScale", "heightScale", "rotation", "cornerRadius", "arcSweep",
        "scrollStep", "scrollReverse", "scrollInertia", "ring",
    )

    private fun mockView(): InputControlsView {
        val view = mockk<InputControlsView>(relaxed = true)
        every { view.snappingSize } returns 10
        every { view.maxWidth } returns 1000
        every { view.maxHeight } returns 1000
        return view
    }

    private fun button(view: InputControlsView, shape: ControlElement.Shape, x: Int = 100, y: Int = 100) =
        ControlElement(view).apply {
            setType(ControlElement.Type.BUTTON)
            setShape(shape)
            setX(x)
            setY(y)
        }

    private fun stickWithRing(view: InputControlsView, segments: Int = 8) = ControlElement(view).apply {
        setType(ControlElement.Type.STICK)
        setX(200)
        setY(200)
        setRingSegments(segments)
    }

    private fun wheel(view: InputControlsView) = ControlElement(view).apply {
        setType(ControlElement.Type.SCROLL_WHEEL)
        setX(100)
        setY(100)
    }

    // ---- shapes and sizes -------------------------------------------------------------------------------------

    @Test
    fun `button without the new fields keeps its old size and json`() {
        val view = mockView()
        val element = button(view, ControlElement.Shape.ROUND_RECT)

        assertEquals(80, element.boundingBox.width())
        assertEquals(40, element.boundingBox.height())
        val json = element.toJSONObject()!!
        for (key in newKeys) assertFalse(key, json.has(key))
        assertEquals("ROUND_RECT", json.getString("shape"))
    }

    @Test
    fun `pill is long and thin and width and height can be scaled apart`() {
        val view = mockView()
        val pill = button(view, ControlElement.Shape.PILL)
        assertEquals(100, pill.boundingBox.width())
        assertEquals(30, pill.boundingBox.height())

        pill.setWidthScale(0.5f)
        pill.setHeightScale(2f)
        assertEquals(50, pill.boundingBox.width())
        assertEquals(60, pill.boundingBox.height())

        pill.setWidthScale(1f)
        pill.setHeightScale(1f)
        pill.setScale(2f)
        assertEquals(200, pill.boundingBox.width())
        assertEquals(60, pill.boundingBox.height())
    }

    @Test
    fun `a quarter turn swaps width and height and the factors refer to the screen`() {
        val view = mockView()
        val pill = button(view, ControlElement.Shape.PILL)
        pill.setRotation(90)
        assertEquals(30, pill.boundingBox.width())
        assertEquals(100, pill.boundingBox.height())

        pill.setWidthScale(2f)
        assertEquals(60, pill.boundingBox.width())

        pill.setRotation(270)
        assertEquals(60, pill.boundingBox.width())
        pill.setRotation(180)
        assertEquals(200, pill.boundingBox.width())
        assertEquals(30, pill.boundingBox.height())
    }

    @Test
    fun `rotation and size factors are rounded and clamped`() {
        val view = mockView()
        val element = button(view, ControlElement.Shape.RECT)
        element.setRotation(100)
        assertEquals(90, element.rotation)
        element.setRotation(-90)
        assertEquals(270, element.rotation)
        element.setRotation(450)
        assertEquals(90, element.rotation)
        element.setWidthScale(0f)
        assertEquals(ControlElement.MIN_SIZE_FACTOR, element.widthScale, 0f)
        element.setHeightScale(99f)
        assertEquals(ControlElement.MAX_SIZE_FACTOR, element.heightScale, 0f)
        element.setCornerRadius(3f)
        assertEquals(1f, element.cornerRadius, 0f)
        element.setArcSweep(1)
        assertEquals(ControlElement.MIN_ARC_SWEEP, element.arcSweep)
    }

    @Test
    fun `arc button is hit only on its sector`() {
        val view = mockView()
        val arc = button(view, ControlElement.Shape.ARC)
        arc.setBindingAt(0, Binding.KEY_A)
        // box 60x60 around (100,100): outer radius 30, inner radius 18, centred on the top, 90 degrees wide

        assertTrue(arc.containsPoint(100f, 100f)) // the bounding box (editor selection) still covers the hole
        assertFalse(arc.handleTouchDown(1, 100f, 100f)) // the hole is not part of the button
        assertFalse(arc.handleTouchDown(1, 125f, 100f)) // right side is outside the sweep
        assertTrue(arc.handleTouchDown(1, 100f, 75f)) // top of the arc
        verify(exactly = 1) { view.handleInputEvent(Binding.KEY_A, true) }
        assertTrue(arc.handleTouchUp(1))
        verify(exactly = 1) { view.handleInputEvent(Binding.KEY_A, false) }

        arc.setRotation(90) // now centred on the right
        assertTrue(arc.handleTouchDown(2, 125f, 100f))
        assertTrue(arc.handleTouchUp(2))
        assertFalse(arc.handleTouchDown(3, 100f, 75f))
    }

    @Test
    fun `wider sweep reaches further around`() {
        val view = mockView()
        val arc = button(view, ControlElement.Shape.ARC)
        arc.setBindingAt(0, Binding.KEY_A) // fold8.19: an unassigned button is not tappable in the game
        // 60 degrees off the top, radius 25: outside of 90 degrees, inside of 140 degrees
        val x = 100f + 25f * Math.sin(Math.toRadians(60.0)).toFloat()
        val y = 100f - 25f * Math.cos(Math.toRadians(60.0)).toFloat()
        assertFalse(arc.handleTouchDown(1, x, y))
        arc.setArcSweep(140)
        assertTrue(arc.handleTouchDown(1, x, y))
    }

    // ---- scroll wheel -----------------------------------------------------------------------------------------

    @Test
    fun `new scroll wheel taps as middle click and has the default step`() {
        val element = wheel(mockView())
        assertEquals(Binding.MOUSE_MIDDLE_BUTTON, element.getBindingAt(0))
        assertTrue(element.getBindingComboAt(1).isEmpty)
        assertEquals(ScrollWheelMath.DEFAULT_STEP, element.scrollStep, 0f)
        assertFalse(element.isScrollReverse)
        assertFalse(element.isScrollInertia)
        // vertical, tall and narrow
        assertEquals(40, element.boundingBox.width())
        assertEquals(100, element.boundingBox.height())
        element.setOrientation(1.toByte())
        assertEquals(100, element.boundingBox.width())
        assertEquals(40, element.boundingBox.height())
    }

    @Test
    fun `swiping up sends wheel up ticks and swiping down sends wheel down ticks`() {
        val view = mockView()
        val element = wheel(view)

        assertTrue(element.handleTouchDown(1, 100f, 100f))
        assertTrue(element.handleTouchMove(1, 100f, 80f)) // 20 px up = 3 ticks (step 6 px)
        verify(exactly = 3) { view.handleInputEvent(Binding.MOUSE_SCROLL_UP, true) }
        verify(exactly = 3) { view.handleInputEvent(Binding.MOUSE_SCROLL_UP, false) }

        assertTrue(element.handleTouchMove(1, 100f, 97f)) // 17 px down, 2 px of the earlier swipe left: 15 px = 2 ticks
        verify(exactly = 2) { view.handleInputEvent(Binding.MOUSE_SCROLL_DOWN, true) }
        assertTrue(element.handleTouchUp(1))
        // a swipe is no tap
        verify(exactly = 0) { view.handleInputEvent(Binding.MOUSE_MIDDLE_BUTTON, true) }
    }

    @Test
    fun `direction can be reversed and the wheel can lie flat`() {
        val view = mockView()
        val element = wheel(view)
        element.setScrollReverse(true)
        element.handleTouchDown(1, 100f, 100f)
        element.handleTouchMove(1, 100f, 80f)
        element.handleTouchUp(1)
        verify(exactly = 3) { view.handleInputEvent(Binding.MOUSE_SCROLL_DOWN, true) }
        verify(exactly = 0) { view.handleInputEvent(Binding.MOUSE_SCROLL_UP, true) }

        val flat = wheel(view)
        flat.setOrientation(1.toByte())
        flat.handleTouchDown(2, 100f, 100f)
        flat.handleTouchMove(2, 120f, 100f) // swipe right = up
        flat.handleTouchUp(2)
        verify(exactly = 3) { view.handleInputEvent(Binding.MOUSE_SCROLL_UP, true) }
    }

    @Test
    fun `a smaller step scrolls sooner`() {
        val view = mockView()
        val element = wheel(view)
        element.setScrollStep(0.2f) // 2 px per tick
        element.handleTouchDown(1, 100f, 100f)
        element.handleTouchMove(1, 100f, 95f) // 5 px = 2 ticks
        element.handleTouchUp(1)
        verify(exactly = 2) { view.handleInputEvent(Binding.MOUSE_SCROLL_UP, true) }
    }

    @Test
    fun `the speed setting of the editor reaches the touch handling`() {
        // ticks for the same 25 px swipe at snapping size 10: slow 12 px per tick, normal 6, fast 3, very fast 1.5 (limited per event)
        val expectedTicks = listOf(0.5f to 2, 1f to 4, 2f to 8, 4f to ScrollWheelMath.MAX_TICKS_PER_EVENT)
        for ((speed, expected) in expectedTicks) {
            val view = mockView()
            val element = wheel(view)
            element.setScrollStep(ScrollWheelMath.stepForSpeed(speed))
            element.handleTouchDown(1, 100f, 100f)
            element.handleTouchMove(1, 100f, 75f)
            element.handleTouchUp(1)
            verify(exactly = expected) { view.handleInputEvent(Binding.MOUSE_SCROLL_UP, true) }
        }
    }

    @Test
    fun `the tick limit per move event keeps a very fast swipe from flooding the game`() {
        val view = mockView()
        val element = wheel(view)
        element.setScrollStep(ScrollWheelMath.MIN_STEP) // 1 px per tick
        element.handleTouchDown(1, 100f, 100f)
        element.handleTouchMove(1, 100f, 40f) // 60 px in one event
        element.handleTouchUp(1)
        verify(exactly = ScrollWheelMath.MAX_TICKS_PER_EVENT) { view.handleInputEvent(Binding.MOUSE_SCROLL_UP, true) }
    }

    @Test
    fun `the speed is stored with the element and the default is not written`() {
        val view = mockView()
        val element = wheel(view)
        assertFalse(element.toJSONObject()!!.has("scrollStep"))

        element.setScrollStep(ScrollWheelMath.stepForSpeed(2f))
        val json = element.toJSONObject()!!
        assertEquals(0.3, json.getDouble("scrollStep"), 0.0005)

        val copy = wheel(view)
        copy.readExtrasFromJson(org.json.JSONObject().also { element.writeExtrasToJson(it) })
        assertEquals(2f, ScrollWheelMath.speedOf(copy.scrollStep), 0.01f)

        // "Standard" in the editor
        element.setScrollStep(ScrollWheelMath.DEFAULT_STEP)
        assertFalse(element.toJSONObject()!!.has("scrollStep"))
        assertEquals(1f, ScrollWheelMath.speedOf(element.scrollStep), 0.0001f)
    }

    @Test
    fun `a tap without swiping sends the tap binding`() {
        val view = mockView()
        val element = wheel(view)

        element.handleTouchDown(1, 100f, 100f)
        element.handleTouchMove(1, 100f, 98f) // 2 px: not a tick yet, still a tap
        element.handleTouchUp(1)

        verifyOrder {
            view.handleInputEvent(Binding.MOUSE_MIDDLE_BUTTON, true)
            view.handleInputEvent(Binding.MOUSE_MIDDLE_BUTTON, false)
        }
        verify(exactly = 0) { view.handleInputEvent(Binding.MOUSE_SCROLL_UP, any()) }
    }

    @Test
    fun `without a tap binding a tap does nothing`() {
        val view = mockView()
        val element = wheel(view)
        element.setBindingAt(0, Binding.NONE)
        element.handleTouchDown(1, 100f, 100f)
        element.handleTouchUp(1)
        verify(exactly = 0) { view.handleInputEvent(any<Binding>(), any()) }
    }

    @Test
    fun `the modifier key is held while scrolling and released afterwards`() {
        val view = mockView()
        val element = wheel(view)
        element.setBindingComboAt(1, BindingCombo.of(Binding.KEY_SHIFT_L))

        element.handleTouchDown(1, 100f, 100f)
        verify(exactly = 0) { view.handleInputEvent(Binding.KEY_SHIFT_L, true) }
        element.handleTouchMove(1, 100f, 80f)
        element.handleTouchMove(1, 100f, 60f)
        element.handleTouchUp(1)

        verify(exactly = 1) { view.handleInputEvent(Binding.KEY_SHIFT_L, true) }
        verify(exactly = 1) { view.handleInputEvent(Binding.KEY_SHIFT_L, false) }
        verifyOrder {
            view.handleInputEvent(Binding.KEY_SHIFT_L, true)
            view.handleInputEvent(Binding.MOUSE_SCROLL_UP, true)
            view.handleInputEvent(Binding.KEY_SHIFT_L, false)
        }
    }

    @Test
    fun `cancelling releases the modifier`() {
        val view = mockView()
        val element = wheel(view)
        element.setBindingComboAt(1, BindingCombo.of(Binding.KEY_CTRL_L))
        element.handleTouchDown(1, 100f, 100f)
        element.handleTouchMove(1, 100f, 80f)
        assertTrue(element.cancelTouch())
        verify(exactly = 1) { view.handleInputEvent(Binding.KEY_CTRL_L, false) }
        assertFalse(element.cancelTouch())
    }

    private class TimedWheel(view: InputControlsView) : ControlElement(view) {
        var now = 0L
        override fun currentTimeMillis(): Long = now
    }

    @Test
    fun `a fast flick coasts only when inertia is on`() {
        for (inertia in listOf(false, true)) {
            val view = mockView()
            val element = TimedWheel(view).apply {
                setType(ControlElement.Type.SCROLL_WHEEL)
                setX(100)
                setY(100)
                setScrollInertia(inertia)
            }
            element.handleTouchDown(1, 100f, 100f)
            element.now = 10
            element.handleTouchMove(1, 100f, 90f)
            element.now = 20
            element.handleTouchMove(1, 100f, 80f)
            element.now = 25
            element.handleTouchUp(1)
            if (inertia) {
                verify(atLeast = 1) { view.postDelayed(any<Runnable>(), ScrollWheelMath.inertiaDelayMillis(0)) }
            } else {
                verify(exactly = 0) { view.postDelayed(any<Runnable>(), any()) }
            }
        }
    }

    @Test
    fun `a swipe that rested before lifting does not coast`() {
        val view = mockView()
        val element = TimedWheel(view).apply {
            setType(ControlElement.Type.SCROLL_WHEEL)
            setX(100)
            setY(100)
            setScrollInertia(true)
        }
        element.handleTouchDown(1, 100f, 100f)
        element.now = 10
        element.handleTouchMove(1, 100f, 90f)
        element.now = 20
        element.handleTouchMove(1, 100f, 80f)
        element.now = 400
        element.handleTouchUp(1)
        verify(exactly = 0) { view.postDelayed(any<Runnable>(), any()) }
    }

    @Test
    fun `scroll wheel settings survive the json round trip`() {
        val view = mockView()
        val element = wheel(view).apply {
            setOrientation(1.toByte())
            setScrollStep(0.9f)
            setScrollReverse(true)
            setScrollInertia(true)
            setWidthScale(1.5f)
            setHeightScale(0.5f)
            setBindingComboAt(1, BindingCombo.of(Binding.KEY_SHIFT_L))
        }
        val json = element.toJSONObject()!!
        assertEquals("SCROLL_WHEEL", json.getString("type"))
        assertEquals(1, json.getInt("orientation"))
        assertEquals(0.9, json.getDouble("scrollStep"), 0.0001)
        assertTrue(json.getBoolean("scrollReverse"))
        assertTrue(json.getBoolean("scrollInertia"))
        assertEquals(1.5, json.getDouble("widthScale"), 0.0001)
        assertEquals(0.5, json.getDouble("heightScale"), 0.0001)

        val copy = ControlElement(view).apply {
            setType(ControlElement.Type.SCROLL_WHEEL)
            readExtrasFromJson(json)
        }
        assertEquals(0.9f, copy.scrollStep, 0.0001f)
        assertTrue(copy.isScrollReverse)
        assertTrue(copy.isScrollInertia)
        assertEquals(1.5f, copy.widthScale, 0.0001f)
        assertEquals(0.5f, copy.heightScale, 0.0001f)
    }

    // ---- key ring ---------------------------------------------------------------------------------------------

    @Test
    fun `ring makes the element bigger but keeps the stick circle`() {
        val view = mockView()
        val plain = ControlElement(view).apply {
            setType(ControlElement.Type.STICK)
            setX(200)
            setY(200)
        }
        assertEquals(120, plain.boundingBox.width())
        assertEquals(60f, plain.stickRadius, 0f)

        val ring = stickWithRing(view)
        // gap 0.75 + width 2.5 grid units = 32.5 px, rounded up
        assertEquals(186, ring.boundingBox.width())
        assertEquals(60f, ring.stickRadius, 0f)
        assertEquals(8, ring.ringSegments)
        ring.setScale(2f)
        assertEquals(120f, ring.stickRadius, 0f)
        assertEquals(2 * 120 + 2 * 65, ring.boundingBox.width())
    }

    @Test
    fun `a new ring is filled with the number keys`() {
        val ring = stickWithRing(mockView())
        for (i in 0 until 8) {
            assertEquals(BindingCombo.of(Binding.valueOf("KEY_${i + 1}")), ring.getRingItem(i).bindingCombo)
        }
        ring.setRingSegments(4)
        assertEquals(4, ring.ringSegments)
        ring.setRingSegments(5) // rounds to 6, keeps what was set before
        assertEquals(6, ring.ringSegments)
        assertEquals(BindingCombo.of(Binding.KEY_6), ring.getRingItem(5).bindingCombo)
        ring.setRingSegments(0)
        assertEquals(0, ring.ringSegments)
    }

    @Test
    fun `a finger on the stick never fires the ring and a second finger taps the segments`() {
        val view = mockView()
        val element = stickWithRing(view)

        // stick finger in the middle, then dragged far out into the ring area
        assertTrue(element.handleTouchDown(1, 200f, 200f))
        assertTrue(element.handleTouchMove(1, 200f, 120f))
        assertTrue(element.handleTouchMove(1, 290f, 200f))
        verify(exactly = 0) { view.handleInputEvent(any<BindingCombo>(), true) }

        // second finger taps the top segment (number key 1)
        assertTrue(element.handleTouchDown(2, 200f, 120f))
        verify(exactly = 1) { view.handleInputEvent(BindingCombo.of(Binding.KEY_1), true) }
        // sliding it around neither releases nor fires another segment
        assertTrue(element.handleTouchMove(2, 280f, 200f))
        verify(exactly = 0) { view.handleInputEvent(BindingCombo.of(Binding.KEY_3), true) }
        verify(exactly = 0) { view.handleInputEvent(BindingCombo.of(Binding.KEY_1), false) }

        assertTrue(element.handleTouchUp(2))
        verify(exactly = 1) { view.handleInputEvent(BindingCombo.of(Binding.KEY_1), false) }
        // the stick finger is still the stick finger
        assertTrue(element.handleTouchUp(1))
    }

    @Test
    fun `segments are chosen by the angle of the touch`() {
        val view = mockView()
        val element = stickWithRing(view)

        // 8 segments, the first one up: right is segment index 2 (key 3), bottom 4 (key 5), left 6 (key 7)
        val cases = listOf(
            Triple(280f, 200f, Binding.KEY_3),
            Triple(200f, 280f, Binding.KEY_5),
            Triple(120f, 200f, Binding.KEY_7),
        )
        for ((x, y, key) in cases) {
            assertTrue(element.handleTouchDown(5, x, y))
            verify(exactly = 1) { view.handleInputEvent(BindingCombo.of(key), true) }
            assertTrue(element.handleTouchUp(5))
            verify(exactly = 1) { view.handleInputEvent(BindingCombo.of(key), false) }
        }
    }

    @Test
    fun `start angle turns the ring`() {
        val view = mockView()
        val element = stickWithRing(view, 4)
        element.setRingStartAngle(0f) // segment 0 (key 1) now on the right
        assertTrue(element.handleTouchDown(5, 280f, 200f))
        verify(exactly = 1) { view.handleInputEvent(BindingCombo.of(Binding.KEY_1), true) }
        element.handleTouchUp(5)
        assertTrue(element.handleTouchDown(5, 200f, 280f)) // bottom = segment 1
        verify(exactly = 1) { view.handleInputEvent(BindingCombo.of(Binding.KEY_2), true) }
    }

    @Test
    fun `an empty ring field is hidden in the game and cannot be tapped`() {
        val view = mockView()   // not in the editor
        val element = stickWithRing(view)
        element.setRingItem(0, RadialMenu.Slot())           // top: nothing assigned
        element.setRingItem(2, RadialMenu.Slot("", Binding.NONE)) // right: no key
        // a touch on the hidden field is not handled (it passes on), nothing is pressed
        assertFalse(element.handleTouchDown(2, 200f, 120f))
        assertFalse(element.handleTouchDown(2, 280f, 200f))
        verify(exactly = 0) { view.handleInputEvent(any<BindingCombo>(), true) }
        // the neighbours still work (bottom = segment 4 = key 5)
        assertTrue(element.handleTouchDown(3, 200f, 280f))
        verify(exactly = 1) { view.handleInputEvent(BindingCombo.of(Binding.KEY_5), true) }
        element.handleTouchUp(3)
        // a field with a chat command only counts as filled
        element.setRingItem(0, RadialMenu.Slot().apply { setChatCommand("/dance") })
        assertTrue(element.handleTouchDown(4, 200f, 120f))
        element.handleTouchUp(4)
        // an action bar field whose state is not known (no strip, no addon data) is not hidden on suspicion
        element.setRingItem(2, RadialMenu.Slot("", BindingCombo.of(Binding.KEY_1), "ACTIONBUTTON1"))
        assertTrue(element.handleTouchDown(5, 280f, 200f))
    }

    @Test
    fun `the editor still hits every ring field`() {
        val view = mockView()
        every { view.isEditMode } returns true
        val element = stickWithRing(view)
        element.setRingItem(0, RadialMenu.Slot())
        element.setRingItem(2, RadialMenu.Slot("", Binding.NONE))
        assertTrue(element.handleTouchDown(2, 200f, 120f))
        element.handleTouchUp(2)
        assertTrue(element.handleTouchDown(3, 280f, 200f))
    }

    @Test
    fun `gap and outside are not part of the ring`() {
        val view = mockView()
        val element = stickWithRing(view)

        // the gap (radius 60 to 67.5) belongs to the stick: it starts the stick, not a ring key
        assertTrue(element.handleTouchDown(1, 200f, 200f - 65f))
        verify(exactly = 0) { view.handleInputEvent(any<BindingCombo>(), true) }
        element.handleTouchUp(1)

        // the corner of the bounding square lies outside of the ring: nothing is hit
        assertFalse(element.handleTouchDown(2, 200f + 88f, 200f - 88f))
        verify(exactly = 0) { view.handleInputEvent(any<BindingCombo>(), true) }
    }

    @Test
    fun `a second finger on the ring is ignored while a ring finger is down`() {
        val view = mockView()
        val element = stickWithRing(view)
        assertTrue(element.handleTouchDown(1, 200f, 120f))
        assertFalse(element.handleTouchDown(2, 280f, 200f))
        verify(exactly = 0) { view.handleInputEvent(BindingCombo.of(Binding.KEY_3), true) }
        // the stick itself stays free
        assertTrue(element.handleTouchDown(3, 200f, 200f))
    }

    @Test
    fun `cancelling releases a held ring key`() {
        val view = mockView()
        val element = stickWithRing(view)
        element.handleTouchDown(1, 200f, 120f)
        assertTrue(element.cancelTouch())
        verify(exactly = 1) { view.handleInputEvent(BindingCombo.of(Binding.KEY_1), false) }
        assertFalse(element.cancelTouch())
    }

    @Test
    fun `a chat command segment types the text and holds no key`() {
        val view = mockView()
        val element = stickWithRing(view)
        element.getRingItem(0).setChatCommand("/dance")
        element.getRingItem(0).setBindingCombo(BindingCombo.none())

        assertTrue(element.handleTouchDown(1, 200f, 120f))
        verify(exactly = 1) { view.runChatCommand("/dance") }
        assertTrue(element.handleTouchUp(1))
        verify(exactly = 0) { view.handleInputEvent(any<BindingCombo>(), any()) }
    }

    @Test
    fun `a wow action segment follows the key set in wow`() {
        try {
            WowActionBindings.setResolved(mapOf("TOGGLEWORLDMAP" to BindingCombo.of(Binding.KEY_N)))
            val view = mockView()
            val element = stickWithRing(view)
            element.setRingItem(0, RadialMenu.Slot("", BindingCombo.of(Binding.KEY_M), "TOGGLEWORLDMAP"))

            assertTrue(element.handleTouchDown(1, 200f, 120f))
            verify(exactly = 1) { view.handleInputEvent(BindingCombo.of(Binding.KEY_N), true) }
        } finally {
            WowActionBindings.setResolved(null)
        }
    }

    @Test
    fun `stick without ring still takes touches on its whole square`() {
        val view = mockView()
        val plain = ControlElement(view).apply {
            setType(ControlElement.Type.STICK)
            setX(200)
            setY(200)
        }
        // corner of the 120 px square, outside of the circle: as before
        assertTrue(plain.handleTouchDown(1, 200f + 55f, 200f - 55f))
    }

    @Test
    fun `ring gamepad bindings make the profile a virtual gamepad`() {
        val element = stickWithRing(mockView())
        assertFalse(element.hasRingGamepadBinding())
        element.getRingItem(2).setBinding(Binding.GAMEPAD_BUTTON_A)
        assertTrue(element.hasRingGamepadBinding())
        element.setRingSegments(0)
        assertFalse(element.hasRingGamepadBinding())
    }

    @Test
    fun `ring survives json with symbols commands and settings`() {
        val view = mockView()
        val element = stickWithRing(view, 6).apply {
            setRingStartAngle(-60f)
            setRingWidth(3f)
            setRingGap(1.25f)
            setRingItem(1, RadialMenu.Slot("", BindingCombo.of(Binding.KEY_M), "TOGGLEWORLDMAP"))
            getRingItem(2).setChatCommand("/dance")
            getRingItem(2).setBindingCombo(BindingCombo.none())
            getRingItem(2).setLabel("Tanz")
        }

        val json = element.toJSONObject()!!
        val ring = json.getJSONObject("ring")
        assertEquals(6, ring.getInt("segments"))
        assertEquals(-60.0, ring.getDouble("startAngle"), 0.0001)
        assertEquals(3.0, ring.getDouble("width"), 0.0001)
        assertEquals(1.25, ring.getDouble("gap"), 0.0001)
        val items = ring.getJSONArray("items")
        assertEquals(6, items.length())
        assertEquals("TOGGLEWORLDMAP", items.getJSONObject(1).getString("wowAction"))
        assertEquals("/dance", items.getJSONObject(2).getString("chatCommand"))
        assertEquals("KEY_1", items.getJSONObject(0).getString("binding"))

        val loaded = ControlElement(view).apply {
            setType(ControlElement.Type.STICK)
            setX(200)
            setY(200)
            readExtrasFromJson(json)
        }
        assertEquals(6, loaded.ringSegments)
        assertEquals(-60f, loaded.ringStartAngle, 0f)
        assertEquals(3f, loaded.ringWidth, 0f)
        assertEquals(1.25f, loaded.ringGap, 0f)
        assertEquals("TOGGLEWORLDMAP", loaded.getRingItem(1).wowAction)
        assertEquals("/dance", loaded.getRingItem(2).chatCommand)
        assertEquals("Tanz", loaded.getRingItem(2).label)
        assertEquals(element.toJSONObject().toString(), loaded.toJSONObject().toString())
    }

    @Test
    fun `ring items missing in the file stay empty`() {
        val view = mockView()
        val json = org.json.JSONObject("""{"ring":{"segments":4,"items":[{"label":"A","binding":"KEY_A"}]}}""")
        val element = ControlElement(view).apply {
            setType(ControlElement.Type.STICK)
            readExtrasFromJson(json)
        }
        assertEquals(4, element.ringSegments)
        assertEquals(BindingCombo.of(Binding.KEY_A), element.getRingItem(0).bindingCombo)
        assertTrue(element.getRingItem(1).bindingCombo.isEmpty)
        assertEquals(StickRing.DEFAULT_START_ANGLE, element.ringStartAngle, 0f)
        assertEquals(StickRing.DEFAULT_WIDTH, element.ringWidth, 0f)
    }

    @Test
    fun `ring is only written for sticks`() {
        val view = mockView()
        val element = stickWithRing(view)
        element.setTypeWithoutReset(ControlElement.Type.BUTTON)
        assertFalse(element.toJSONObject()!!.has("ring"))
    }

    // ---- type change, copy, profile ---------------------------------------------------------------------------

    @Test
    fun `changing the type forgets the new settings`() {
        val view = mockView()
        val element = stickWithRing(view)
        element.setWidthScale(2f)
        element.setScrollReverse(true)
        element.setType(ControlElement.Type.BUTTON)
        assertEquals(0, element.ringSegments)
        assertEquals(1f, element.widthScale, 0f)
        assertFalse(element.isScrollReverse)
        assertEquals(0, element.rotation)
    }

    @Test
    fun `settings can be copied from one element to another`() {
        val view = mockView()
        val source = stickWithRing(view, 4).apply {
            getRingItem(3).setChatCommand("/wave")
            setRingStartAngle(45f)
        }
        val target = ControlElement(view).apply { setType(ControlElement.Type.STICK) }
        target.copyExtrasFrom(source)
        assertEquals(4, target.ringSegments)
        assertEquals(45f, target.ringStartAngle, 0f)
        assertEquals("/wave", target.getRingItem(3).chatCommand)
        assertEquals(source.toJSONObject().getJSONObject("ring").toString(), target.toJSONObject().getJSONObject("ring").toString())

        val pill = button(view, ControlElement.Shape.PILL).apply {
            setWidthScale(1.25f)
            setRotation(90)
        }
        val pillCopy = button(view, ControlElement.Shape.PILL)
        pillCopy.copyExtrasFrom(pill)
        assertEquals(1.25f, pillCopy.widthScale, 0f)
        assertEquals(90, pillCopy.rotation)
    }

    /** Saves the element like the profile does and reads it into a fresh stick. */
    private fun saveAndLoad(view: InputControlsView, element: ControlElement): ControlElement {
        val json = element.toJSONObject()!!
        return ControlElement(view).apply {
            setType(ControlElement.Type.STICK)
            setX(200)
            setY(200)
            readExtrasFromJson(json)
        }
    }

    @Test
    fun `ring count can be changed in both directions with save and load in between`() {
        val view = mockView()
        var element = stickWithRing(view, 4)
        element.getRingItem(0).setBindingCombo(BindingCombo.of(Binding.KEY_SPACE))
        element.getRingItem(3).setBindingCombo(BindingCombo.of(Binding.KEY_TAB))
        element.getRingItem(2).setLabel("Eigen")

        for (target in listOf(8, 4, 6, 8, 4, 6)) {
            element.setRingSegments(target)
            assertEquals(target, element.ringSegments)
            element = saveAndLoad(view, element)
            assertEquals("count after save and load for $target", target, element.ringSegments)
        }
        // What was set on the first four segments is still there after all changes.
        assertEquals(BindingCombo.of(Binding.KEY_SPACE), element.getRingItem(0).bindingCombo)
        assertEquals(BindingCombo.of(Binding.KEY_TAB), element.getRingItem(3).bindingCombo)
        assertEquals("Eigen", element.getRingItem(2).label)

        // Off and on again: 4 -> off -> 8 works as well (an off ring stores nothing, the new one starts with the number keys).
        for (target in listOf(4, 0, 8)) {
            element.setRingSegments(target)
            element = saveAndLoad(view, element)
            assertEquals(target, element.ringSegments)
        }
        assertEquals(BindingCombo.of(Binding.KEY_1), element.getRingItem(0).bindingCombo)
        assertEquals("", element.getRingItem(7).label)
    }

    @Test
    fun `ring count keeps assignments of existing segments and new ones start without label`() {
        val view = mockView()
        val element = stickWithRing(view, 4)
        element.getRingItem(1).setBindingCombo(BindingCombo.of(Binding.KEY_ESC))

        element.setRingSegments(8)
        assertEquals(8, element.ringSegments)
        assertEquals(BindingCombo.of(Binding.KEY_ESC), element.getRingItem(1).bindingCombo)
        for (i in 4 until 8) {
            assertEquals("", element.getRingItem(i).label)
            assertEquals(BindingCombo.of(Binding.valueOf("KEY_${i + 1}")), element.getRingItem(i).bindingCombo)
        }

        element.setRingSegments(4)
        element.setRingSegments(6)
        assertEquals(6, element.ringSegments)
        assertEquals(BindingCombo.of(Binding.KEY_ESC), element.getRingItem(1).bindingCombo)
        assertEquals(6, element.toJSONObject()!!.getJSONObject("ring").getJSONArray("items").length())
    }

    @Test
    fun `discarding restores the snapshot`() {
        val view = mockView()
        val element = stickWithRing(view)
        val snapshot = org.json.JSONObject().also { element.writeExtrasToJson(it) }.toString()

        element.setRingSegments(4)
        element.setRingStartAngle(10f)
        element.getRingItem(0).setLabel("X")
        element.readExtrasFromJson(org.json.JSONObject(snapshot))

        assertEquals(8, element.ringSegments)
        assertEquals(StickRing.DEFAULT_START_ANGLE, element.ringStartAngle, 0f)
        // New segments start without label: the ring names them after their key.
        assertEquals("", element.getRingItem(0).label)
    }

    @Test
    fun `ring edits after creation are saved with the profile and come back after reloading`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = mockView()
        val profile = ControlsProfile(context, 4712)
        val stick = stickWithRing(view, 4)
        profile.addElement(stick)
        profile.save()

        // Like the element dialog: the element is changed in place, then the profile is saved.
        stick.getRingItem(1).setBindingCombo(BindingCombo.of(Binding.KEY_TAB))
        stick.setRingSegments(8)
        stick.setRingStartAngle(-45f)
        stick.setRingWidth(3.5f)
        stick.setRingGap(1.5f)
        stick.getRingItem(5).setLabel("Hallo")
        profile.save()

        val reloaded = ControlsProfile(context, 4712)
        reloaded.loadElements(view)
        val loaded = reloaded.elements.single()
        assertEquals(8, loaded.ringSegments)
        assertEquals(BindingCombo.of(Binding.KEY_TAB), loaded.getRingItem(1).bindingCombo)
        assertEquals(-45f, loaded.ringStartAngle, 0f)
        assertEquals(3.5f, loaded.ringWidth, 0f)
        assertEquals(1.5f, loaded.ringGap, 0f)
        assertEquals("Hallo", loaded.getRingItem(5).label)

        // A second round: 8 -> 4 -> 6 on the loaded element, saved again.
        loaded.setRingSegments(4)
        loaded.setRingSegments(6)
        reloaded.save()
        val again = ControlsProfile(context, 4712)
        again.loadElements(view)
        assertEquals(6, again.elements.single().ringSegments)
        assertEquals(BindingCombo.of(Binding.KEY_TAB), again.elements.single().getRingItem(1).bindingCombo)
    }

    @Test
    fun `profile with all new element kinds survives save and load`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = mockView()
        val profile = ControlsProfile(context, 4711)
        profile.addElement(
            wheel(view).apply {
                setX(500)
                setY(250)
                setOrientation(1.toByte())
                setScrollStep(1.2f)
                setScrollReverse(true)
                setScrollInertia(true)
                setBindingComboAt(1, BindingCombo.of(Binding.KEY_CTRL_L))
            },
        )
        profile.addElement(
            stickWithRing(view, 8).apply {
                setX(250)
                setY(750)
                getRingItem(4).setChatCommand("/sit")
                getRingItem(4).setBindingCombo(BindingCombo.none())
                getRingItem(5).useSymbolOfTheMap()
            },
        )
        profile.addElement(
            button(view, ControlElement.Shape.PILL, 750, 500).apply {
                setWidthScale(1.5f)
                setHeightScale(0.75f)
                setRotation(90)
                setBindingAt(0, Binding.KEY_F)
            },
        )
        profile.addElement(
            button(view, ControlElement.Shape.ROUND_RECT, 500, 500).apply {
                setCornerRadius(0.4f)
            },
        )
        profile.addElement(
            button(view, ControlElement.Shape.ARC, 250, 250).apply {
                setArcSweep(120)
                setRotation(180)
            },
        )
        profile.save()

        val loadedProfile = ControlsProfile(context, 4711)
        loadedProfile.loadElements(view)
        assertEquals(5, loadedProfile.elements.size)
        assertEquals(
            profile.elements.map { it.toJSONObject().toString() },
            loadedProfile.elements.map { it.toJSONObject().toString() },
        )

        val loadedWheel = loadedProfile.elements[0]
        assertEquals(ControlElement.Type.SCROLL_WHEEL, loadedWheel.type)
        assertEquals(1.toByte(), loadedWheel.orientation)
        assertEquals(1.2f, loadedWheel.scrollStep, 0.0001f)
        assertEquals(BindingCombo.of(Binding.KEY_CTRL_L), loadedWheel.getBindingComboAt(1))
        val loadedStick = loadedProfile.elements[1]
        assertEquals(8, loadedStick.ringSegments)
        assertEquals("/sit", loadedStick.getRingItem(4).chatCommand)
        val loadedPill = loadedProfile.elements[2]
        assertEquals(ControlElement.Shape.PILL, loadedPill.shape)
        assertEquals(1.5f, loadedPill.widthScale, 0f)
        assertEquals(0.75f, loadedPill.heightScale, 0f)
        assertEquals(90, loadedPill.rotation)
        assertEquals(0.4f, loadedProfile.elements[3].cornerRadius, 0.0001f)
        assertEquals(120, loadedProfile.elements[4].arcSweep)
        assertEquals(180, loadedProfile.elements[4].rotation)
    }

    @Test
    fun `old profile without the new fields loads unchanged`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = mockView()
        ControlsProfile.getProfileFile(context, 4712).writeText(
            """{"id":4712,"name":"alt","cursorSpeed":1,"elements":[
            {"type":"BUTTON","shape":"ROUND_RECT","bindings":["KEY_SPACE","NONE","NONE","NONE"],"scale":1.0,"x":0.5,"y":0.25,"toggleSwitch":false,"text":"","iconId":0},
            {"type":"STICK","shape":"CIRCLE","bindings":["GAMEPAD_LEFT_THUMB_UP","GAMEPAD_LEFT_THUMB_RIGHT","GAMEPAD_LEFT_THUMB_DOWN","GAMEPAD_LEFT_THUMB_LEFT"],"scale":1.0,"x":0.25,"y":0.75,"toggleSwitch":false,"text":"","iconId":0},
            {"type":"RANGE_BUTTON","shape":"ROUND_RECT","bindings":["NONE","NONE","NONE","NONE"],"scale":1.0,"x":0.75,"y":0.75,"toggleSwitch":false,"text":"","iconId":0,"range":"FROM_A_TO_Z"}
            ]}""",
        )

        val profile = ControlsProfile(context, 4712)
        profile.loadElements(view)

        assertEquals(3, profile.elements.size)
        val button = profile.elements[0]
        assertEquals(ControlElement.Shape.ROUND_RECT, button.shape)
        assertEquals(80, button.boundingBox.width())
        assertEquals(40, button.boundingBox.height())
        assertEquals(1f, button.widthScale, 0f)
        assertEquals(1f, button.heightScale, 0f)
        assertEquals(0, button.rotation)
        assertEquals(ControlElement.DEFAULT_CORNER_RADIUS, button.cornerRadius, 0f)
        val stick = profile.elements[1]
        assertEquals(0, stick.ringSegments)
        assertEquals(120, stick.boundingBox.width())
        assertEquals(60f, stick.stickRadius, 0f)
        for (element in profile.elements) {
            val json = element.toJSONObject()!!
            for (key in newKeys) assertFalse("${element.type} $key", json.has(key))
        }
        assertNotNull(profile.elements[2].range)
        assertNull(profile.elements[0].wowAction)
    }

    // ---- outer second ring ------------------------------------------------------------------------------------

    @Test
    fun `a ring without outer fields keeps its size and its json`() {
        val ring = stickWithRing(mockView())
        assertFalse(ring.hasAnyRingOuter())
        assertEquals(186, ring.boundingBox.width())
        assertFalse(ring.toJSONObject()!!.getJSONObject("ring").has("outer"))
        assertNull(ring.getRingOuterItem(0))
    }

    @Test
    fun `an outer field widens the element and is removed again`() {
        val ring = stickWithRing(mockView())
        val slot = ring.addRingOuter(0)
        assertNotNull(slot)
        assertTrue(ring.hasRingOuter(0))
        assertFalse(ring.hasRingOuter(1))
        // gap 0.75 + width 2.5 + outer gap 0.5 + width 2.5 = 62.5 px, rounded up
        assertEquals(120 + 2 * 63, ring.boundingBox.width())
        assertEquals(60f, ring.stickRadius, 0f)

        ring.removeRingOuter(0)
        assertFalse(ring.hasAnyRingOuter())
        assertEquals(186, ring.boundingBox.width())
    }

    @Test
    fun `outer fields exist only inside the ring`() {
        val ring = stickWithRing(mockView(), 4)
        assertNull(ring.addRingOuter(4))
        assertNull(ring.addRingOuter(-1))
        val plainStick = ControlElement(mockView()).apply { setType(ControlElement.Type.STICK) }
        assertNull(plainStick.addRingOuter(0))
        val button = button(mockView(), ControlElement.Shape.CIRCLE)
        assertNull(button.addRingOuter(0))
    }

    @Test
    fun `a tap in the outer band fires the outer field and the inner ring still fires its own key`() {
        val view = mockView()
        val element = stickWithRing(view)
        element.addRingOuter(0)!!.setBindingCombo(BindingCombo.of(Binding.KEY_ESC))

        // inner ring on top: key 1 as before
        assertTrue(element.handleTouchDown(1, 200f, 120f))
        verify(exactly = 1) { view.handleInputEvent(BindingCombo.of(Binding.KEY_1), true) }
        assertTrue(element.handleTouchUp(1))

        // outer band on top (distance 110 from the middle): ESC, not key 1
        assertTrue(element.handleTouchDown(2, 200f, 90f))
        verify(exactly = 1) { view.handleInputEvent(BindingCombo.of(Binding.KEY_ESC), true) }
        verify(exactly = 1) { view.handleInputEvent(BindingCombo.of(Binding.KEY_1), true) }
        // sliding around changes nothing, lifting releases the outer key
        assertTrue(element.handleTouchMove(2, 290f, 200f))
        verify(exactly = 0) { view.handleInputEvent(BindingCombo.of(Binding.KEY_ESC), false) }
        assertTrue(element.handleTouchUp(2))
        verify(exactly = 1) { view.handleInputEvent(BindingCombo.of(Binding.KEY_ESC), false) }
    }

    @Test
    fun `a tap in the outer band at a segment without outer field hits nothing`() {
        val view = mockView()
        val element = stickWithRing(view)
        element.addRingOuter(0)!!.setBindingCombo(BindingCombo.of(Binding.KEY_ESC))

        // right side, distance 110: segment 2 has no outer field
        assertFalse(element.handleTouchDown(3, 310f, 200f))
        verify(exactly = 0) { view.handleInputEvent(any<BindingCombo>(), true) }
    }

    @Test
    fun `the gap between inner and outer ring taps the inner segment`() {
        val view = mockView()
        val element = stickWithRing(view)
        element.addRingOuter(0)!!.setBindingCombo(BindingCombo.of(Binding.KEY_ESC))

        // distance 95: between 92.5 (end of the ring) and 97.5 (start of the outer band)
        assertTrue(element.handleTouchDown(1, 200f, 105f))
        verify(exactly = 1) { view.handleInputEvent(BindingCombo.of(Binding.KEY_1), true) }
        assertTrue(element.handleTouchUp(1))
    }

    @Test
    fun `an outer field without any key is hidden in the game and lets the touch pass`() {
        // fold8.15: empty fields are not shown and not tappable in the game (the editor still hits them)
        val view = mockView()
        val element = stickWithRing(view)
        element.addRingOuter(0)
        assertFalse(element.handleTouchDown(1, 200f, 90f))
        verify(exactly = 0) { view.handleInputEvent(any<BindingCombo>(), true) }
    }

    @Test
    fun `an outer field with a chat command types the command`() {
        val view = mockView()
        val element = stickWithRing(view)
        element.addRingOuter(4)!!.setChatCommand("/sit")
        // segment 4 is at the bottom
        assertTrue(element.handleTouchDown(1, 200f, 310f))
        verify(exactly = 1) { view.runChatCommand("/sit") }
        assertTrue(element.handleTouchUp(1))
    }

    @Test
    fun `outer fields are written to json and read back, empty places stay empty`() {
        val view = mockView()
        val element = stickWithRing(view)
        element.addRingOuter(1)!!.apply {
            setBindingCombo(BindingCombo.of(Binding.KEY_TAB))
            setLabel("T")
        }
        element.addRingOuter(5)!!.setWowAction("TOGGLEWORLDMAP")

        val json = element.toJSONObject()!!
        val outer = json.getJSONObject("ring").getJSONArray("outer")
        assertEquals(8, outer.length())
        assertTrue(outer.isNull(0))
        assertEquals("KEY_TAB", outer.getJSONObject(1).getString("binding"))
        assertTrue(outer.isNull(2))
        assertEquals("TOGGLEWORLDMAP", outer.getJSONObject(5).getString("wowAction"))

        val extras = org.json.JSONObject().also { element.writeExtrasToJson(it) }
        val copy = ControlElement(view).apply {
            setType(ControlElement.Type.STICK)
            readExtrasFromJson(extras)
        }
        assertFalse(copy.hasRingOuter(0))
        assertTrue(copy.hasRingOuter(1))
        assertEquals(BindingCombo.of(Binding.KEY_TAB), copy.getRingOuterItem(1)!!.bindingCombo)
        assertEquals("T", copy.getRingOuterItem(1)!!.label)
        assertTrue(copy.hasRingOuter(5))
        assertEquals("TOGGLEWORLDMAP", copy.getRingOuterItem(5)!!.wowAction)
        assertFalse(copy.hasRingOuter(7))
        assertEquals(extras.toString(), org.json.JSONObject().also { copy.writeExtrasToJson(it) }.toString())
    }

    @Test
    fun `a layout from before the outer ring loads without outer fields`() {
        val view = mockView()
        val old = org.json.JSONObject(
            """{"ring":{"segments":4,"startAngle":-90.0,"width":2.5,"gap":0.75,"items":[
                {"label":"","binding":"KEY_1"},{"label":"","binding":"KEY_2"},
                {"label":"","binding":"KEY_3"},{"label":"","binding":"KEY_4"}]}}""",
        )
        val element = ControlElement(view).apply {
            setType(ControlElement.Type.STICK)
            setX(200)
            setY(200)
            readExtrasFromJson(old)
        }
        assertEquals(4, element.ringSegments)
        assertFalse(element.hasAnyRingOuter())
        assertEquals(BindingCombo.of(Binding.KEY_3), element.getRingItem(2).bindingCombo)
        assertEquals(2 * 60 + 2 * 33, element.boundingBox.width())
        // writing it again does not add an outer array
        assertFalse(org.json.JSONObject().also { element.writeExtrasToJson(it) }.getJSONObject("ring").has("outer"))
    }

    @Test
    fun `a layout with a broken outer array does not crash and ignores bad entries`() {
        val view = mockView()
        val json = org.json.JSONObject(
            """{"ring":{"segments":4,"items":[{"binding":"KEY_1"},{"binding":"KEY_2"},{"binding":"KEY_3"},{"binding":"KEY_4"}],
                "outer":[null,"x",{"binding":"KEY_ESC"},{"binding":"KEY_F"},{"binding":"KEY_G"},{"binding":"KEY_H"}]}}""",
        )
        val element = ControlElement(view).apply {
            setType(ControlElement.Type.STICK)
            readExtrasFromJson(json)
        }
        assertFalse(element.hasRingOuter(0))
        assertFalse(element.hasRingOuter(1))
        assertTrue(element.hasRingOuter(2))
        assertTrue(element.hasRingOuter(3))
        // more entries than segments are dropped
        assertFalse(element.hasRingOuter(4))
        assertEquals(4, element.toJSONObject()!!.getJSONObject("ring").getJSONArray("outer").length())
    }

    @Test
    fun `outer fields survive saving and loading the profile and a smaller ring hides them`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = mockView()
        val profile = ControlsProfile(context, 4713)
        val stick = stickWithRing(view, 8)
        profile.addElement(stick)
        stick.addRingOuter(3)!!.setBindingCombo(BindingCombo.of(Binding.KEY_SPACE))
        profile.save()

        val reloaded = ControlsProfile(context, 4713)
        reloaded.loadElements(view)
        val loaded = reloaded.elements.single()
        assertTrue(loaded.hasRingOuter(3))
        assertEquals(BindingCombo.of(Binding.KEY_SPACE), loaded.getRingOuterItem(3)!!.bindingCombo)

        // 8 -> 4 keeps the outer field of segment 3 (still inside the ring), segments that do not exist have none
        loaded.setRingSegments(4)
        assertTrue(loaded.hasRingOuter(3))
        loaded.setRingSegments(6)
        assertTrue(loaded.hasRingOuter(3))
        assertFalse(loaded.hasRingOuter(6))
    }

    @Test
    fun `an outer field with a gamepad key makes the profile a virtual gamepad`() {
        val element = stickWithRing(mockView())
        assertFalse(element.hasRingGamepadBinding())
        element.addRingOuter(2)!!.setBindingCombo(BindingCombo.of(Binding.GAMEPAD_BUTTON_A))
        assertTrue(element.hasRingGamepadBinding())
    }

    private fun RadialMenu.Slot.useSymbolOfTheMap() {
        setWowAction("TOGGLEWORLDMAP")
        setLabel("")
    }
}
