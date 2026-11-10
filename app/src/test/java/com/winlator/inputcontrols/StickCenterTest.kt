package com.winlator.inputcontrols

import com.winlator.widget.InputControlsView
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** "Mitte drücken": a short tap on the middle of a stick triggers an own assignment; dragging never does. */
@RunWith(RobolectricTestRunner::class)
class StickCenterTest {
    private class TimedStick(view: InputControlsView) : ControlElement(view) {
        var now = 0L
        override fun currentTimeMillis(): Long = now
    }

    private fun mockView(): InputControlsView {
        val view = mockk<InputControlsView>(relaxed = true)
        every { view.snappingSize } returns 10
        every { view.maxWidth } returns 1000
        every { view.maxHeight } returns 1000
        // The key is let go after a moment: run that at once.
        every { view.postDelayed(any(), any()) } answers {
            firstArg<Runnable>().run()
            true
        }
        return view
    }

    private fun stick(view: InputControlsView, center: BindingCombo? = BindingCombo.of(Binding.KEY_SPACE)) =
        TimedStick(view).apply {
            setType(ControlElement.Type.STICK)
            setX(200)
            setY(200)
            if (center != null) getCenterItem().setBindingCombo(center)
        }

    // ---- tap helper -------------------------------------------------------------------------------------------

    @Test
    fun `defaults are generous`() {
        assertEquals(24f, StickCenter.DEFAULT_TAP_DP, 0f)
        assertEquals(350L, StickCenter.DEFAULT_TAP_MILLIS)
    }

    @Test
    fun `tap area is the tap distance in dp but at most ninety percent of the stick`() {
        assertEquals(24f, StickCenter.tapRadius(600f, 1f, 24f), 0.001f)
        assertEquals(72f, StickCenter.tapRadius(1000f, 3f, 24f), 0.001f)
        // small stick (radius 60 px) at density 3: 72 px would be more than the stick, so 54 px
        assertEquals(54f, StickCenter.tapRadius(60f, 3f, 24f), 0.001f)
        // unknown density counts as 1
        assertEquals(24f, StickCenter.tapRadius(60f, 0f, 24f), 0.001f)
        // the setting is clamped to its range
        assertEquals(48f, StickCenter.tapRadius(1000f, 1f, 500f), 0.001f)
        assertEquals(8f, StickCenter.tapRadius(1000f, 1f, 1f), 0.001f)
    }

    @Test
    fun `settings are clamped`() {
        assertEquals(150L, StickCenter.clampMillis(10))
        assertEquals(600L, StickCenter.clampMillis(5000))
        assertEquals(350L, StickCenter.clampMillis(350))
        assertEquals(24f, StickCenter.clampDp(Float.NaN), 0f)
    }

    @Test
    fun `tap needs a short touch and no deflection`() {
        assertTrue(StickCenter.isTap(0f, 80, 24f, 350))
        assertTrue(StickCenter.isTap(24f, 349, 24f, 350))
        assertFalse(StickCenter.isTap(24.1f, 80, 24f, 350))
        assertFalse(StickCenter.isTap(0f, 350, 24f, 350))
        assertFalse(StickCenter.isTap(0f, -1, 24f, 350))
        assertFalse(StickCenter.isTap(0f, 200, 24f, 150))
    }

    // ---- touch ------------------------------------------------------------------------------------------------

    @Test
    fun `a short tap in the middle presses and releases the assignment`() {
        val view = mockView()
        val element = stick(view)

        assertTrue(element.hasCenterAction())
        assertTrue(element.handleTouchDown(1, 200f, 200f))
        element.now = 90
        assertTrue(element.handleTouchUp(1))

        verify(exactly = 1) { view.handleInputEvent(BindingCombo.of(Binding.KEY_SPACE), true) }
        verify(exactly = 1) { view.handleInputEvent(BindingCombo.of(Binding.KEY_SPACE), false) }
    }

    @Test
    fun `a tap a little beside the middle but inside the tap area counts`() {
        val view = mockView()
        val element = stick(view)
        element.handleTouchDown(1, 203f, 200f)
        element.now = 100
        element.handleTouchUp(1)
        verify(exactly = 1) { view.handleInputEvent(BindingCombo.of(Binding.KEY_SPACE), true) }
    }

    @Test
    fun `dragging the stick never triggers the middle`() {
        val view = mockView()
        val element = stick(view)

        element.handleTouchDown(1, 200f, 200f)
        element.handleTouchMove(1, 200f, 150f)
        // back to the middle before lifting: it was a drag
        element.handleTouchMove(1, 200f, 200f)
        element.now = 100
        element.handleTouchUp(1)

        verify(exactly = 0) { view.handleInputEvent(BindingCombo.of(Binding.KEY_SPACE), any<Boolean>()) }
    }

    // ---- undecided phase: a small wobble must not reach the camera -----------------------------------------------

    private val keyRight = Binding.KEY_D

    private fun cameraStick(view: InputControlsView, center: BindingCombo? = BindingCombo.of(Binding.KEY_SPACE)) =
        stick(view, center).apply { setBindingAt(1, keyRight) }

    private fun verifyRightPressed(view: InputControlsView, times: Int) =
        verify(exactly = times) { view.handleInputEvent(keyRight, true, any<Float>()) }

    @Test
    fun `a small wobble and a quick lift is a jump without camera output`() {
        val view = mockView()
        val element = cameraStick(view)

        element.handleTouchDown(1, 200f, 200f)
        // 13 px to the side: more than the dead zone (9 px) of the stick, but inside the tap area
        element.now = 40
        element.handleTouchMove(1, 212f, 205f)
        element.now = 90
        element.handleTouchMove(1, 205f, 198f)
        element.now = 140
        element.handleTouchUp(1)

        verify(exactly = 1) { view.handleInputEvent(BindingCombo.of(Binding.KEY_SPACE), true) }
        verify(exactly = 0) { view.handleInputEvent(any<Binding>(), any<Boolean>(), any<Float>()) }
    }

    @Test
    fun `a slow drag is camera and no jump`() {
        val view = mockView()
        val element = cameraStick(view)

        element.handleTouchDown(1, 200f, 200f)
        element.now = 100
        element.handleTouchMove(1, 210f, 200f)
        verifyRightPressed(view, 0)
        // after the tap time the stick works from the current point
        element.now = 400
        element.handleTouchMove(1, 212f, 200f)
        verifyRightPressed(view, 1)
        element.now = 450
        element.handleTouchUp(1)

        verify(exactly = 0) { view.handleInputEvent(BindingCombo.of(Binding.KEY_SPACE), any<Boolean>()) }
    }

    @Test
    fun `dragging over the border before the time is up is camera and no jump`() {
        val view = mockView()
        val element = cameraStick(view)

        element.handleTouchDown(1, 200f, 200f)
        element.now = 60
        element.handleTouchMove(1, 235f, 200f)
        verifyRightPressed(view, 1)
        // going back at once does not make it a tap again
        element.now = 100
        element.handleTouchMove(1, 200f, 200f)
        element.now = 120
        element.handleTouchUp(1)

        verify(exactly = 0) { view.handleInputEvent(BindingCombo.of(Binding.KEY_SPACE), any<Boolean>()) }
    }

    @Test
    fun `a finger resting inside the tap area starts the camera when the time is up`() {
        val view = mockView()
        val scheduled = mutableListOf<Runnable>()
        every { view.postDelayed(any(), any()) } answers {
            scheduled.add(firstArg())
            true
        }
        val element = cameraStick(view)

        element.handleTouchDown(1, 213f, 200f)
        assertEquals(1, scheduled.size)
        // too early: nothing yet
        element.now = 100
        scheduled[0].run()
        verifyRightPressed(view, 0)
        // the time is over, no further move event came
        element.now = 351
        scheduled[0].run()
        verifyRightPressed(view, 1)
        element.handleTouchUp(1)
        verify(exactly = 0) { view.handleInputEvent(BindingCombo.of(Binding.KEY_SPACE), any<Boolean>()) }
    }

    @Test
    fun `the timer of an earlier touch does nothing`() {
        val view = mockView()
        val scheduled = mutableListOf<Runnable>()
        every { view.postDelayed(any(), any()) } answers {
            scheduled.add(firstArg())
            true
        }
        val element = cameraStick(view)

        element.handleTouchDown(1, 213f, 200f)
        element.now = 100
        element.handleTouchUp(1)
        element.now = 500
        scheduled[0].run()
        verifyRightPressed(view, 0)
    }

    @Test
    fun `a stick without a middle assignment works at once and schedules nothing`() {
        val view = mockView()
        val element = cameraStick(view, center = null)

        element.handleTouchDown(1, 200f, 200f)
        element.now = 10
        element.handleTouchMove(1, 212f, 200f)
        verifyRightPressed(view, 1)
        verify(exactly = 0) { view.postDelayed(any(), any()) }
    }

    @Test
    fun `tap distance and time can be set`() {
        val view = mockView()
        // small tap area: 12 px is already a drag
        val tight = cameraStick(view).apply { setCenterTapDp(8f) }
        tight.handleTouchDown(1, 200f, 200f)
        tight.now = 30
        tight.handleTouchMove(1, 212f, 200f)
        verifyRightPressed(view, 1)
        tight.handleTouchUp(1)

        // short tap time: 200 ms is too long
        val view2 = mockView()
        val quick = cameraStick(view2).apply { setCenterTapMillis(150) }
        quick.handleTouchDown(1, 200f, 200f)
        quick.now = 200
        quick.handleTouchUp(1)
        verify(exactly = 0) { view2.handleInputEvent(BindingCombo.of(Binding.KEY_SPACE), any<Boolean>()) }

        // wide tap area and long time: still a tap
        val view3 = mockView()
        val loose = cameraStick(view3).apply {
            setCenterTapDp(40f)
            setCenterTapMillis(600)
        }
        loose.handleTouchDown(1, 200f, 200f)
        loose.now = 20
        loose.handleTouchMove(1, 230f, 200f)
        loose.now = 550
        loose.handleTouchUp(1)
        verify(exactly = 1) { view3.handleInputEvent(BindingCombo.of(Binding.KEY_SPACE), true) }
    }

    @Test
    fun `sensitivity survives json and copies, defaults write nothing`() {
        val view = mockView()
        val plain = stick(view)
        val plainJson = plain.toJSONObject()!!
        assertFalse(plainJson.has("centerTapDp"))
        assertFalse(plainJson.has("centerTapMillis"))

        val element = stick(view).apply {
            setCenterTapDp(32f)
            setCenterTapMillis(450)
        }
        val json = element.toJSONObject()!!
        assertEquals(32.0, json.getDouble("centerTapDp"), 0.0)
        assertEquals(450L, json.getLong("centerTapMillis"))

        val loaded = ControlElement(view).apply {
            setType(ControlElement.Type.STICK)
            setX(200)
            setY(200)
            readExtrasFromJson(json)
        }
        assertEquals(32f, loaded.centerTapDp, 0f)
        assertEquals(450L, loaded.centerTapMillis)

        val copy = ControlElement(view).apply {
            setType(ControlElement.Type.STICK)
            copyExtrasFrom(element)
        }
        assertEquals(32f, copy.centerTapDp, 0f)

        // old files and out of range values
        val old = ControlElement(view).apply {
            setType(ControlElement.Type.STICK)
            readExtrasFromJson(plainJson)
        }
        assertEquals(StickCenter.DEFAULT_TAP_DP, old.centerTapDp, 0f)
        assertEquals(StickCenter.DEFAULT_TAP_MILLIS, old.centerTapMillis)
        element.setCenterTapMillis(99999)
        assertEquals(StickCenter.MAX_TAP_MILLIS, element.centerTapMillis)
    }

    @Test
    fun `a touch that starts far from the middle is a drag, not a tap`() {
        val view = mockView()
        val element = stick(view)
        element.handleTouchDown(1, 240f, 200f)
        element.now = 50
        element.handleTouchUp(1)
        verify(exactly = 0) { view.handleInputEvent(BindingCombo.of(Binding.KEY_SPACE), any<Boolean>()) }
    }

    @Test
    fun `a long press in the middle does not trigger it`() {
        val view = mockView()
        val element = stick(view)
        element.handleTouchDown(1, 200f, 200f)
        element.now = 400
        element.handleTouchUp(1)
        verify(exactly = 0) { view.handleInputEvent(BindingCombo.of(Binding.KEY_SPACE), any<Boolean>()) }
    }

    @Test
    fun `without an assignment nothing happens`() {
        val view = mockView()
        val element = stick(view, center = null)
        assertFalse(element.hasCenterAction())
        element.handleTouchDown(1, 200f, 200f)
        element.now = 50
        element.handleTouchUp(1)
        verify(exactly = 0) { view.handleInputEvent(any<BindingCombo>(), any<Boolean>()) }
    }

    @Test
    fun `a chat command in the middle is typed once and no key is held`() {
        val view = mockView()
        val element = stick(view, center = null)
        element.getCenterItem().setChatCommand("/dance")
        element.handleTouchDown(1, 200f, 200f)
        element.now = 50
        element.handleTouchUp(1)
        verify(exactly = 1) { view.runChatCommand("/dance") }
        verify(exactly = 0) { view.handleInputEvent(any<BindingCombo>(), any<Boolean>()) }
    }

    @Test
    fun `the middle works on a stick with ring and a ring tap does not fire it`() {
        val view = mockView()
        val element = stick(view).apply { setRingSegments(8) }

        // a finger on the ring (second finger) fires its segment, not the middle
        element.handleTouchDown(2, 200f, 120f)
        element.handleTouchUp(2)
        verify(exactly = 0) { view.handleInputEvent(BindingCombo.of(Binding.KEY_SPACE), any<Boolean>()) }

        element.handleTouchDown(1, 200f, 200f)
        element.now = 60
        element.handleTouchUp(1)
        verify(exactly = 1) { view.handleInputEvent(BindingCombo.of(Binding.KEY_SPACE), true) }
    }

    // ---- JSON -------------------------------------------------------------------------------------------------

    @Test
    fun `middle assignment survives json and copies`() {
        val view = mockView()
        val element = stick(view, center = null).apply {
            getCenterItem().setWowAction("JUMP")
            getCenterItem().setBindingCombo(BindingCombo.of(Binding.KEY_SPACE))
        }
        val json = element.toJSONObject()!!
        assertEquals("JUMP", json.getJSONObject("center").getString("wowAction"))

        val loaded = ControlElement(view).apply {
            setType(ControlElement.Type.STICK)
            setX(200)
            setY(200)
            readExtrasFromJson(json)
        }
        assertEquals("JUMP", loaded.getCenterItem().wowAction)
        assertEquals(BindingCombo.of(Binding.KEY_SPACE), loaded.getCenterItem().bindingCombo)
        assertEquals(json.toString(), loaded.toJSONObject().toString())

        val copy = ControlElement(view).apply {
            setType(ControlElement.Type.STICK)
            copyExtrasFrom(element)
        }
        assertEquals("JUMP", copy.getCenterItem().wowAction)
    }

    @Test
    fun `sticks without a middle assignment keep their old json`() {
        val view = mockView()
        val plain = stick(view, center = null)
        assertFalse(plain.toJSONObject()!!.has("center"))
        assertFalse(plain.hasCenterAction())

        // set and then cleared: nothing is written either
        val cleared = stick(view)
        cleared.getCenterItem().setBindingCombo(BindingCombo.none())
        assertFalse(cleared.toJSONObject()!!.has("center"))
    }

    @Test
    fun `other element types never write or use the middle`() {
        val view = mockView()
        val button = ControlElement(view).apply { setType(ControlElement.Type.BUTTON) }
        button.getCenterItem().setBindingCombo(BindingCombo.of(Binding.KEY_SPACE))
        assertFalse(button.toJSONObject()!!.has("center"))
        assertFalse(button.hasCenterAction())
    }

    @Test
    fun `changing the type forgets the middle assignment`() {
        val view = mockView()
        val element = stick(view)
        element.setType(ControlElement.Type.D_PAD)
        element.setType(ControlElement.Type.STICK)
        assertNull(element.toJSONObject()!!.optJSONObject("center"))
    }
}
