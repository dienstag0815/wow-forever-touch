package com.winlator.inputcontrols

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.winlator.widget.InputControlsView
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Key strip with the free list ("Eigene"): format, round trip, converting, ordering, hit test with scroll offset, touch. */
@RunWith(RobolectricTestRunner::class)
class RangeCustomTest {
    private fun mockView(): InputControlsView {
        val view = mockk<InputControlsView>(relaxed = true)
        every { view.snappingSize } returns 10
        every { view.maxWidth } returns 1000
        every { view.maxHeight } returns 1000
        return view
    }

    /** Horizontal strip at (500, 500): with 4 visible keys it spans x 420..580, y 480..520 and every cell is 40 wide. */
    private fun strip(
        view: InputControlsView,
        range: ControlElement.Range = ControlElement.Range.FROM_A_TO_Z,
        visible: Int = 4,
    ) = ControlElement(view).apply {
        setType(ControlElement.Type.RANGE_BUTTON)
        setX(500)
        setY(500)
        setRange(range)
        setBindingCount(visible)
    }

    /** Free strip whose entries are the keys of [range] (one per key, as a template had them), [visible] keys on show. */
    private fun customStrip(view: InputControlsView, range: ControlElement.Range = ControlElement.Range.FROM_0_TO_9, visible: Int = 4) =
        strip(view, range, visible).apply {
            convertToCustomRange()
            while (rangeItemCount < range.max) addRangeItem()
            for (i in 0 until range.max) setRangeItem(i, RadialMenu.Slot("", BindingCombo.of(range.bindingAt(i))))
            setBindingCount(visible)
        }

    private fun key(name: String) = BindingCombo.of(Binding.valueOf(name))

    // ---- format ---------------------------------------------------------------------------------------------

    @Test
    fun `a template strip keeps its json and stores no entries`() {
        val element = strip(mockView())
        val json = element.toJSONObject()!!
        assertEquals("FROM_A_TO_Z", json.getString("range"))
        assertFalse(json.has("rangeItems"))
        assertEquals(4, json.getJSONArray("bindings").length())
    }

    @Test
    fun `a free strip writes its entries in order`() {
        val element = customStrip(mockView())
        element.getRangeItem(2).setLabel("Drei")
        element.getRangeItem(3).setChatCommand("/sit")
        element.getRangeItem(3).setBindingCombo(BindingCombo.none())
        val json = element.toJSONObject()!!

        assertEquals("CUSTOM", json.getString("range"))
        val items = json.getJSONArray("rangeItems")
        assertEquals(10, items.length())
        assertEquals("KEY_1", items.getJSONObject(0).getString("binding"))
        assertEquals("Drei", items.getJSONObject(2).getString("label"))
        assertEquals("/sit", items.getJSONObject(3).getString("chatCommand"))
        assertEquals("KEY_0", items.getJSONObject(9).getString("binding"))
    }

    @Test
    fun `switching back to a template drops the entries from the json but not from memory`() {
        val element = customStrip(mockView())
        element.setRange(ControlElement.Range.FROM_A_TO_Z)
        assertFalse(element.toJSONObject()!!.has("rangeItems"))
        assertFalse(element.isCustomRange)
        assertEquals(26, element.rangeCount)
        assertEquals(10, element.rangeItemCount)
    }

    @Test
    fun `old profile with a template strip loads unchanged`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = mockView()
        ControlsProfile.getProfileFile(context, 4820).writeText(
            """{"id":4820,"name":"alt","cursorSpeed":1,"elements":[
            {"type":"RANGE_BUTTON","shape":"ROUND_RECT","bindings":["NONE","NONE","NONE","NONE"],"scale":1.0,"x":0.5,"y":0.5,"toggleSwitch":false,"text":"","iconId":0,"range":"FROM_F1_TO_F12","orientation":1},
            {"type":"RANGE_BUTTON","shape":"ROUND_RECT","bindings":["NONE","NONE","NONE"],"scale":1.0,"x":0.5,"y":0.25,"toggleSwitch":false,"text":"","iconId":0,"range":"FROM_A_TO_Z"}
            ]}""",
        )
        val profile = ControlsProfile(context, 4820)
        profile.loadElements(view)

        assertEquals(2, profile.elements.size)
        val first = profile.elements[0]
        assertEquals(ControlElement.Range.FROM_F1_TO_F12, first.range)
        assertEquals(12, first.rangeCount)
        assertEquals(0, first.rangeItemCount)
        assertEquals(4, first.bindingCount)
        assertFalse(first.toJSONObject()!!.has("rangeItems"))
        // a strip with fewer than four visible keys keeps that number now
        assertEquals(3, profile.elements[1].bindingCount)
    }

    // ---- round trip ----------------------------------------------------------------------------------------------

    @Test
    fun `free strip survives saving and loading`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = mockView()
        val profile = ControlsProfile(context, 4821)
        val element = customStrip(view, ControlElement.Range.FROM_A_TO_Z, 3).apply {
            setOrientation(1.toByte())
            isScrollLocked = true
            getRangeItem(0).setLabel("Karte")
            getRangeItem(0).setWowAction("TOGGLEWORLDMAP")
            getRangeItem(1).setChatCommand("/dance")
            getRangeItem(1).setBindingCombo(BindingCombo.none())
            getRangeItem(2).setBindingCombo(key("KEY_F5"))
            moveRangeItem(2, 0)
        }
        profile.addElement(element)
        profile.save()

        val loadedProfile = ControlsProfile(context, 4821)
        loadedProfile.loadElements(view)
        val loaded = loadedProfile.elements.single()
        assertEquals(element.toJSONObject().toString(), loaded.toJSONObject().toString())
        assertEquals(ControlElement.Range.CUSTOM, loaded.range)
        assertEquals(26, loaded.rangeItemCount)
        assertEquals(3, loaded.bindingCount)
        assertEquals(1.toByte(), loaded.orientation)
        assertTrue(loaded.isScrollLocked)
        assertEquals(key("KEY_F5"), loaded.getRangeItem(0).bindingCombo)
        assertEquals("Karte", loaded.getRangeItem(1).label)
        assertEquals("TOGGLEWORLDMAP", loaded.getRangeItem(1).wowAction)
        assertEquals("/dance", loaded.getRangeItem(2).chatCommand)

        // a second round with a change on the loaded element
        loaded.removeRangeItem(0)
        loadedProfile.save()
        val again = ControlsProfile(context, 4821)
        again.loadElements(view)
        assertEquals(25, again.elements.single().rangeItemCount)
        assertEquals("Karte", again.elements.single().getRangeItem(0).label)
    }

    @Test
    fun `copying an element takes the entries along`() {
        val view = mockView()
        val source = customStrip(view)
        source.getRangeItem(4).setLabel("fünf")
        val copy = ControlElement(view)
        copy.setType(ControlElement.Type.RANGE_BUTTON)
        copy.copyExtrasFrom(source)
        copy.setRange(source.range)

        assertEquals(10, copy.rangeItemCount)
        assertEquals("fünf", copy.getRangeItem(4).label)
        assertEquals(10, copy.rangeCount)
    }

    // ---- damaged files -------------------------------------------------------------------------------------------

    @Test
    fun `damaged entries load without a crash`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = mockView()
        ControlsProfile.getProfileFile(context, 4822).writeText(
            """{"id":4822,"name":"kaputt","cursorSpeed":1,"elements":[
            {"type":"RANGE_BUTTON","shape":"ROUND_RECT","bindings":["NONE","NONE","NONE","NONE"],"scale":1.0,"x":0.5,"y":0.5,"toggleSwitch":false,"text":"","iconId":0,"range":"CUSTOM",
             "rangeItems":[123,"x",null,{"label":"Q","binding":"KEY_Q"},{"label":"X","binding":"NOT_A_KEY"},{"bindings":42},{"binding":"KEY_W","wowAction":""}]},
            {"type":"RANGE_BUTTON","shape":"ROUND_RECT","bindings":["NONE","NONE","NONE","NONE"],"scale":1.0,"x":0.5,"y":0.25,"toggleSwitch":false,"text":"","iconId":0,"range":"CUSTOM"},
            {"type":"RANGE_BUTTON","shape":"ROUND_RECT","bindings":["NONE","NONE","NONE","NONE"],"scale":1.0,"x":0.5,"y":0.75,"toggleSwitch":false,"text":"","iconId":0,"range":"FROM_X_TO_Y"},
            {"type":"BUTTON","shape":"CIRCLE","bindings":["KEY_SPACE","NONE","NONE","NONE"],"scale":1.0,"x":0.1,"y":0.1,"toggleSwitch":false,"text":"","iconId":0}
            ]}""",
        )
        val profile = ControlsProfile(context, 4822)
        profile.loadElements(view)

        assertEquals(4, profile.elements.size)
        val damaged = profile.elements[0]
        assertTrue(damaged.isCustomRange)
        assertEquals(7, damaged.rangeItemCount)
        assertEquals(key("KEY_Q"), damaged.getRangeItem(3).bindingCombo)
        assertEquals(key("KEY_W"), damaged.getRangeItem(6).bindingCombo)
        // the damaged entries are empty ones, they press nothing
        assertTrue(damaged.getRangeItem(0).bindingCombo.isEmpty)
        assertEquals(4, damaged.bindingCount)
        // a free strip without entries and an unknown range name become the default strip
        assertEquals(ControlElement.Range.FROM_A_TO_Z, profile.elements[1].range)
        assertEquals(ControlElement.Range.FROM_A_TO_Z, profile.elements[2].range)
        assertEquals(ControlElement.Type.BUTTON, profile.elements[3].type)
    }

    @Test
    fun `too many entries are cut off at the limit`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = mockView()
        val entries = (1..60).joinToString(",") { """{"binding":"KEY_A"}""" }
        ControlsProfile.getProfileFile(context, 4823).writeText(
            """{"id":4823,"name":"viel","cursorSpeed":1,"elements":[
            {"type":"RANGE_BUTTON","shape":"ROUND_RECT","bindings":["NONE","NONE","NONE","NONE"],"scale":1.0,"x":0.5,"y":0.5,"toggleSwitch":false,"text":"","iconId":0,"range":"CUSTOM","rangeItems":[$entries]}
            ]}""",
        )
        val profile = ControlsProfile(context, 4823)
        profile.loadElements(view)
        assertEquals(ControlElement.MAX_RANGE_ITEMS, profile.elements.single().rangeItemCount)
    }

    // ---- converting ------------------------------------------------------------------------------------------------

    @Test
    fun `choosing the free list starts with its own keys, not with the keys of the template`() {
        val view = mockView()
        val az = strip(view, ControlElement.Range.FROM_A_TO_Z, 4)
        az.convertToCustomRange()
        assertTrue(az.isCustomRange)
        assertEquals(5, az.rangeItemCount)
        assertEquals(5, az.rangeCount)
        assertEquals(key("KEY_ENTER"), az.getRangeItem(0).bindingCombo)
        assertEquals(key("KEY_SPACE"), az.getRangeItem(1).bindingCombo)
        assertEquals(key("KEY_TAB"), az.getRangeItem(2).bindingCombo)
        assertEquals(key("KEY_ESC"), az.getRangeItem(3).bindingCombo)
        assertEquals(key("KEY_BKSP"), az.getRangeItem(4).bindingCombo)
        assertEquals("", az.getRangeItem(0).label)
        assertEquals(5, az.bindingCount)

        // the digits and the function keys start the same way
        val digits = strip(view, ControlElement.Range.FROM_0_TO_9, 2).apply { convertToCustomRange() }
        assertEquals(5, digits.rangeItemCount)
        assertEquals(key("KEY_ENTER"), digits.getRangeItem(0).bindingCombo)
        assertEquals(5, digits.bindingCount)
    }

    @Test
    fun `converting a free list again changes nothing`() {
        val element = customStrip(mockView())
        element.getRangeItem(1).setLabel("zwei")
        val second = element.getRangeItem(1)
        element.convertToCustomRange()
        assertEquals(10, element.rangeItemCount)
        assertSame(second, element.getRangeItem(1))
        assertEquals("zwei", element.getRangeItem(1).label)
    }

    @Test
    fun `template keys come from the range`() {
        assertEquals(Binding.KEY_C, ControlElement.Range.FROM_A_TO_Z.bindingAt(2))
        assertEquals(Binding.NONE, ControlElement.Range.FROM_A_TO_Z.bindingAt(26))
        assertEquals(Binding.NONE, ControlElement.Range.CUSTOM.bindingAt(0))
        assertEquals(Binding.KEY_0, ControlElement.Range.FROM_0_TO_9.bindingAt(9))
    }

    // ---- ordering --------------------------------------------------------------------------------------------------

    @Test
    fun `moving an entry shifts the ones in between`() {
        val element = customStrip(mockView())
        val first = element.getRangeItem(0)
        assertTrue(element.moveRangeItem(0, 3))
        assertEquals(key("KEY_2"), element.getRangeItem(0).bindingCombo)
        assertEquals(key("KEY_4"), element.getRangeItem(2).bindingCombo)
        assertSame(first, element.getRangeItem(3))
        assertEquals(key("KEY_5"), element.getRangeItem(4).bindingCombo)

        assertTrue(element.moveRangeItem(9, 0))
        assertEquals(key("KEY_0"), element.getRangeItem(0).bindingCombo)
        assertEquals(10, element.rangeItemCount)
    }

    @Test
    fun `moving to the same place or outside the list does nothing`() {
        val element = customStrip(mockView())
        assertFalse(element.moveRangeItem(2, 2))
        assertFalse(element.moveRangeItem(-1, 2))
        assertFalse(element.moveRangeItem(2, 10))
        assertFalse(element.moveRangeItem(10, 0))
        assertEquals(key("KEY_3"), element.getRangeItem(2).bindingCombo)
    }

    @Test
    fun `entries are added at the end up to the limit and removed one by one`() {
        val element = customStrip(mockView())
        assertEquals(10, element.addRangeItem())
        assertTrue(element.getRangeItem(10).bindingCombo.isEmpty)
        assertFalse(element.getRangeItem(10).isEnabled)
        while (element.rangeItemCount < ControlElement.MAX_RANGE_ITEMS) element.addRangeItem()
        assertEquals(-1, element.addRangeItem())
        assertEquals(ControlElement.MAX_RANGE_ITEMS, element.rangeItemCount)

        assertTrue(element.removeRangeItem(0))
        assertEquals(key("KEY_2"), element.getRangeItem(0).bindingCombo)
        assertFalse(element.removeRangeItem(99))
        assertNull(element.getRangeItem(ControlElement.MAX_RANGE_ITEMS))
    }

    @Test
    fun `the last entry cannot be removed`() {
        val element = customStrip(mockView())
        while (element.rangeItemCount > 1) assertTrue(element.removeRangeItem(0))
        assertFalse(element.removeRangeItem(0))
        assertEquals(1, element.rangeItemCount)
        assertEquals(1, element.bindingCount)
    }

    @Test
    fun `the strip never shows more keys than it has entries`() {
        val element = customStrip(mockView(), visible = 6)
        while (element.rangeItemCount > 3) element.removeRangeItem(0)
        assertEquals(3, element.bindingCount)
        // 3 keys of 40 each
        assertEquals(120, element.boundingBox.width())
    }

    @Test
    fun `a new entry shows up at once while everything was visible`() {
        val element = customStrip(mockView(), visible = 4)
        while (element.rangeItemCount > 3) element.removeRangeItem(0)
        assertEquals(3, element.bindingCount)
        element.addRangeItem()
        assertEquals(4, element.bindingCount)
        // with more entries than keys on show the new entry waits behind the scrolling
        val many = customStrip(mockView(), visible = 4)
        many.addRangeItem()
        assertEquals(4, many.bindingCount)
    }

    // ---- hit test with scroll offset --------------------------------------------------------------------------------

    @Test
    fun `cell under a position follows the scroll offset`() {
        // strip starts at 420, cells of 40, 10 entries
        assertEquals(0, RangeScroller.indexAt(430f, 420f, 0f, 40f, 10))
        assertEquals(1, RangeScroller.indexAt(460f, 420f, 0f, 40f, 10))
        assertEquals(3, RangeScroller.indexAt(579f, 420f, 0f, 40f, 10))
        // pulled 60 to the left: the third entry lies under the first cell
        assertEquals(2, RangeScroller.indexAt(440f, 420f, -60f, 40f, 10))
        // pulled to the right: entries from the end of the list come in
        assertEquals(9, RangeScroller.indexAt(440f, 420f, 30f, 40f, 10))
        // a whole number of rounds more or less changes nothing
        assertEquals(2, RangeScroller.indexAt(440f, 420f, -60f - 400f * 3, 40f, 10))
        assertEquals(2, RangeScroller.indexAt(440f, 420f, -60f + 400f * 2, 40f, 10))
        // nothing to hit without entries or without a cell size
        assertEquals(-1, RangeScroller.indexAt(440f, 420f, 0f, 40f, 0))
        assertEquals(-1, RangeScroller.indexAt(440f, 420f, 0f, 0f, 10))
    }

    @Test
    fun `scroller shows and hits the same cells after scrolling`() {
        val view = mockView()
        val element = customStrip(view)
        val scroller = RangeScroller(view, element)
        assertEquals(40f, scroller.elementSize, 0f)
        assertEquals(400f, scroller.scrollSize, 0f)
        assertEquals(listOf<Byte>(0, 5), scroller.rangeIndex.toList())

        scroller.handleTouchDown(440f, 500f)
        scroller.handleTouchMove(380f, 500f)
        scroller.handleTouchUp()
        assertEquals(60f, scroller.scrollOffset, 0.001f)
        // first visible entry is the second one, partly pushed out to the left
        assertEquals(listOf<Byte>(1, 6), scroller.rangeIndex.toList())

        // 60 pulled to the left: the finger at 440 is on entry 2, the one at 560 on entry 5
        scroller.handleTouchDown(440f, 500f)
        assertEquals(2, scroller.activeIndex)
        scroller.cancelTouch()
        scroller.handleTouchDown(560f, 500f)
        assertEquals(5, scroller.activeIndex)
        scroller.cancelTouch()
    }

    @Test
    fun `a strip that shows all entries does not scroll`() {
        val view = mockView()
        val element = customStrip(view, visible = 4)
        while (element.rangeItemCount > 4) element.removeRangeItem(0)
        val scroller = RangeScroller(view, element)
        scroller.handleTouchDown(440f, 500f)
        scroller.handleTouchMove(300f, 500f)
        scroller.handleTouchUp()
        assertEquals(0f, scroller.scrollOffset, 0f)
    }

    // ---- touch -----------------------------------------------------------------------------------------------------

    /** The scroller of [element] with a fixed clock and without the real long press timer: a quick up after down is a tap. */
    private fun fixedClockScroller(view: InputControlsView, element: ControlElement) = object : RangeScroller(view, element) {
        override fun scheduleLongPress(callback: Runnable) {
        }

        override fun currentTimeMillis(): Long = 0L
    }

    private fun tap(scroller: RangeScroller, x: Float) {
        scroller.handleTouchDown(x, 500f)
        scroller.handleTouchUp()
    }

    @Test
    fun `a tap on an entry presses its key`() {
        val view = mockView()
        val element = customStrip(view, ControlElement.Range.FROM_A_TO_Z)
        element.getRangeItem(1).setBindingCombo(key("KEY_F5"))
        val scroller = fixedClockScroller(view, element)

        // cell 1 spans 460..500
        tap(scroller, 480f)
        verify(exactly = 1) { view.handleInputEvent(key("KEY_F5"), true) }
        // the template key of the cell is not pressed
        verify(exactly = 0) { view.handleInputEvent(Binding.KEY_B, true) }
    }

    @Test
    fun `a tap on a chat entry types the text once and holds no key`() {
        val view = mockView()
        val element = customStrip(view, ControlElement.Range.FROM_A_TO_Z)
        element.getRangeItem(2).setChatCommand("/sit")
        element.getRangeItem(2).setBindingCombo(BindingCombo.none())
        val scroller = fixedClockScroller(view, element)

        // cell 2 spans 500..540
        tap(scroller, 520f)
        verify(exactly = 1) { view.runChatCommand("/sit") }
        verify(exactly = 0) { view.handleInputEvent(any<BindingCombo>(), any()) }
        verify(exactly = 0) { view.handleInputEvent(any<Binding>(), any()) }
    }

    @Test
    fun `a tap on an empty entry does nothing`() {
        val view = mockView()
        val element = customStrip(view)
        element.getRangeItem(3).setBindingCombo(BindingCombo.none())
        val scroller = fixedClockScroller(view, element)

        // cell 3 spans 540..580
        tap(scroller, 560f)
        verify(exactly = 0) { view.handleInputEvent(any<BindingCombo>(), any()) }
        verify(exactly = 0) { view.handleInputEvent(any<Binding>(), any()) }
        verify(exactly = 0) { view.runChatCommand(any()) }
    }

    @Test
    fun `after scrolling a tap hits the entry now under the finger`() {
        val view = mockView()
        val element = customStrip(view)
        val scroller = fixedClockScroller(view, element)

        // swipe left by 60: no key goes down
        scroller.handleTouchDown(440f, 500f)
        scroller.handleTouchMove(380f, 500f)
        scroller.handleTouchUp()
        verify(exactly = 0) { view.handleInputEvent(any<BindingCombo>(), true) }

        // entry 3 (key 3) is under the first cell now
        tap(scroller, 440f)
        verify(exactly = 1) { view.handleInputEvent(key("KEY_3"), true) }
        verify(exactly = 0) { view.handleInputEvent(key("KEY_1"), true) }
    }

    @Test
    fun `a moved entry presses its key at the new place`() {
        val view = mockView()
        val element = customStrip(view)
        element.moveRangeItem(0, 1)
        val scroller = fixedClockScroller(view, element)

        tap(scroller, 440f)
        verify(exactly = 1) { view.handleInputEvent(key("KEY_2"), true) }
    }

    @Test
    fun `a held entry presses on the long press and lets go on lift`() {
        val view = mockView()
        val element = customStrip(view)
        var longPress: Runnable? = null
        var posted: Runnable? = null
        var now = 1000L
        every { view.post(any<Runnable>()) } answers {
            posted = firstArg()
            true
        }
        val scroller = object : RangeScroller(view, element) {
            override fun scheduleLongPress(callback: Runnable) {
                longPress = callback
            }

            override fun currentTimeMillis(): Long = now
        }

        scroller.handleTouchDown(440f, 500f)
        now += 300L
        longPress!!.run()
        posted!!.run()
        verify(exactly = 1) { view.handleInputEvent(key("KEY_1"), true) }
        scroller.handleTouchUp()
        verify(exactly = 1) { view.handleInputEvent(key("KEY_1"), false) }
    }

    @Test
    fun `a template strip still presses the key of its cell`() {
        val view = mockView()
        val element = strip(view)
        val scroller = fixedClockScroller(view, element)
        tap(scroller, 480f)
        verify(exactly = 1) { view.handleInputEvent(Binding.KEY_B, true) }
        assertNotNull(element.range)
    }

    @Test
    fun `the element hands the touch to the strip`() {
        val view = mockView()
        val element = customStrip(view)
        assertTrue(element.handleTouchDown(1, 440f, 500f))
        assertTrue(element.handleTouchUp(1))
        // outside of the strip nothing is hit
        assertFalse(element.handleTouchDown(2, 440f, 900f))
    }
}
